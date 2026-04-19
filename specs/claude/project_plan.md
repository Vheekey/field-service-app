# FieldOps — Project Plan

> A field-worker task management platform for urban vehicle operations.
> Stack: Java Spring Boot 3 · PostgreSQL · Vue 3 · WebSockets · REST

---

## Vision

A dispatcher assigns tasks to field workers each morning. Workers open the app, see their list sorted by urgency and travel efficiency, navigate to each vehicle, complete the task (swap battery, quality check, relocate, retrieve), and close their shift. Dispatchers watch progress in real time. Everything syncs the moment connectivity is restored.

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────┐
│                        CLIENTS                             │
│  Vue 3 PWA (field worker)   Vue 3 SPA (dispatcher/admin)  │
│  Pinia stores · Vue Router  WebSocket client · REST/HTTP   │
└───────────────────┬────────────────────────────────────────┘
                    │ HTTPS + WSS
┌───────────────────▼────────────────────────────────────────┐
│                  API GATEWAY / NGINX                        │
│         TLS termination · rate limiting · gzip             │
└───────────────────┬────────────────────────────────────────┘
                    │
┌───────────────────▼────────────────────────────────────────┐
│              Spring Boot 3 Application                      │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  REST API    │  │  WebSocket   │  │  Scheduling /    │  │
│  │  Controllers │  │  STOMP Broker│  │  Background Jobs │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────────┘  │
│         │                 │                  │              │
│  ┌──────▼─────────────────▼──────────────────▼───────────┐  │
│  │                   Service Layer                        │  │
│  │  TaskService · ShiftService · WorkerService ·         │  │
│  │  ConflictResolutionService · NotificationService      │  │
│  └──────────────────────────┬────────────────────────────┘  │
│                             │                               │
│  ┌──────────────────────────▼────────────────────────────┐  │
│  │               Repository Layer (JPA)                   │  │
│  └──────────────────────────┬────────────────────────────┘  │
│                             │                               │
│  ┌──────────────────────────▼────────────────────────────┐  │
│  │         PostgreSQL  +  Redis (session/pubsub)          │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## Technology Decisions

### REST vs GraphQL

**Decision: REST with targeted WebSocket push — no GraphQL.**

Rationale:

| Concern | REST verdict |
|---|---|
| Query complexity | Tasks have a well-defined, shallow shape. No graph traversal needed. |
| Mobile/offline | REST + HTTP caching headers (ETag, Last-Modified) are simpler to cache offline. |
| Real-time | GraphQL subscriptions add protocol complexity; STOMP over WebSocket is battle-tested with Spring. |
| Team onboarding | REST is universally understood; GraphQL adds tooling and mental overhead for a field ops tool. |
| Performance | Field workers fetch their own task list (~20–100 tasks). No N+1 risk that would justify GraphQL. |

GraphQL would be worth revisiting **only** if the dispatcher analytics dashboard evolves into a complex reporting tool with deep filtering across many entities.

### State Management: Pinia vs Alternatives

**Decision: Keep Pinia — it is the right choice here.**

| Library | Verdict |
|---|---|
| **Pinia** ✅ | Vue 3 native, devtools support, composable, works perfectly with offline-first pattern |
| Vuex 4 | Legacy; Pinia is the official successor |
| TanStack Query (Vue Query) | Excellent for server-state caching and background refetch — **recommend adding this alongside Pinia** |
| XState | Overkill unless task state machine becomes very complex |

**Recommended pattern**: Pinia for UI state (current worker session, shift metadata, UI mode) + **Vue Query (TanStack)** for server-state (task lists, worker lists). Vue Query gives you automatic background refetch, stale-while-revalidate, and offline retry out of the box — it eliminates a lot of manual cache logic.

### Concurrency & Conflict Resolution

**Optimistic locking via `@Version` in JPA** on the `Task` entity.  
Pattern: every task update request must include the current `version` number. If two workers (or a dispatcher + worker) submit a change simultaneously, the second writer gets a `409 Conflict` response and the client shows a "this task was updated — reload" prompt.

For assignment specifically: **a database-level unique partial index** ensures a task can only be assigned to one worker at a time. No application-level locking needed.

```sql
-- Ensures atomic single-assignment
CREATE UNIQUE INDEX unique_active_assignment
  ON task_assignments(task_id)
  WHERE status = 'ACTIVE';
```

### WebSocket Strategy

Spring STOMP over WebSocket with a **Redis pub/sub broker relay** for multi-instance deployments.

Events pushed to clients:
- `task.assigned` — a new task was assigned to the worker
- `task.updated` — description/priority changed on one of the worker's tasks
- `task.status_changed` — another worker's task changed (for dispatcher view)
- `shift.closed` — the shift has been ended by a dispatcher

Workers subscribe to a personal topic: `/user/{workerId}/queue/tasks`  
Dispatcher subscribes to: `/topic/dispatch`

### Offline / Reconnect Behaviour

The Vue 3 PWA uses a **service worker** (Vite PWA plugin) with a background sync strategy:

1. Worker goes offline → all task status mutations are queued in IndexedDB via a background sync queue.
2. On reconnect → queued mutations are replayed in order against the REST API.
3. WebSocket reconnects automatically (exponential backoff, max 30s).
4. On reconnect, the client fetches a full diff via `GET /tasks?updatedSince={lastSyncTs}` to catch any missed events.
5. Vue Query's `refetchOnReconnect: true` handles the cache invalidation automatically.

---

## Data Model (Summary)

See `specs/data-model.md` for full schema.

Core entities: `Worker` → `Shift` → `Task` → `TaskStatusHistory`  
Supporting: `Vehicle`, `TaskType`, `Location`

---

## Sprints

### Sprint 1 — Foundation & Auth (Weeks 1–2)

**Goal**: Both apps are running, authentication works, worker can log in.

**Backend**
- Spring Boot 3 project scaffold (Maven multi-module)
- PostgreSQL schema + Flyway migrations for all core tables
- Spring Security + JWT (access token 15 min, refresh token 7 days)
- `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`
- `GET /workers/me` — current worker profile
- Role model: `WORKER`, `DISPATCHER`, `ADMIN`
- Docker Compose for local dev (Postgres + Redis)

**Frontend**
- Vue 3 + Vite + TypeScript scaffold
- Pinia auth store (JWT storage in memory, refresh token in httpOnly cookie)
- Login page → redirect to shift view
- Axios instance with request interceptor for token injection + 401 auto-refresh
- Vue Router with route guards

**Definition of Done**: A worker can log in, receive a JWT, and the token is refreshed silently. A dispatcher can log in and see a different home route.

---

### Sprint 2 — Tasks & Shift Core (Weeks 3–4)

**Goal**: Workers can see and work through their task list.

**Backend**
- `GET /shifts/current` — worker's active shift + task count summary
- `GET /tasks?shiftId=&status=&page=&size=` — paginated task list
- `GET /tasks/{id}` — task detail with location + vehicle metadata
- `PATCH /tasks/{id}/status` — update status (with `version` field for OCC)
- `POST /shifts/{id}/start`, `POST /shifts/{id}/end`
- Task entity with `@Version` optimistic locking
- Unique partial index for single-assignment enforcement
- TaskStatusHistory audit table written on every status transition

**Frontend**
- Task list view: grouped by status, sorted by urgency then proximity
- Task card component with type badge + urgency indicator
- Task detail drawer/page: description, vehicle info, map pin (static)
- Status action button (contextual: "Start", "Complete", "Report Issue")
- Vue Query for task fetching + optimistic UI updates
- Offline detection banner

**Definition of Done**: A worker can open their shift, see all tasks, tap one, and mark it complete. The status persists on reload.

---

### Sprint 3 — Real-Time & Maps (Weeks 5–6)

**Goal**: Dispatcher sees live updates; worker sees optimised route.

**Backend**
- STOMP/WebSocket endpoint with Spring WebSocket
- Redis pub/sub broker relay for scale-out
- Event publishing on task status change, task assignment, task update
- `GET /tasks/{id}/nearby?radius=` — tasks near a GPS coordinate
- `POST /tasks` — dispatcher creates and assigns a task
- `PUT /tasks/{id}` — dispatcher edits task (conflict detection via `version`)
- `GET /workers/{id}/location` — last known GPS ping
- `POST /workers/me/location` — worker pings GPS position (batched, every 30s)

**Frontend**
- WebSocket client (SockJS + STOMP.js) wired into Pinia
- Toast notification on `task.assigned` push event
- Dispatcher view: map with worker pins + task pins (Leaflet or MapLibre)
- Worker view: ordered route list with "navigate" deeplink to OS maps
- Background sync queue (IndexedDB via idb-keyval) for offline mutations
- Reconnect logic with `GET /tasks?updatedSince=` diff fetch

**Definition of Done**: A dispatcher assigns a new task; the worker's device shows a push notification within 2 seconds. Going offline and back online syncs all pending status changes.

---

### Sprint 4 — Dispatcher Dashboard & Admin (Weeks 7–8)

**Goal**: Full dispatcher workflow; shift management.

**Backend**
- `GET /shifts?date=&workerId=` — shift list with completion stats
- `POST /shifts` — create shift and pre-load task list for a worker
- `GET /workers` — list all workers with current shift status
- `GET /tasks/report?shiftId=` — completed/incomplete/issue breakdown
- Bulk task import: `POST /tasks/bulk` (JSON array, validated)
- `DELETE /tasks/{id}` — soft delete (sets `deleted_at`, never hard delete)
- `GET /audit/tasks/{id}` — full status history for a task
- Admin-only role guards on fleet/worker CRUD endpoints

**Frontend**
- Dispatcher dashboard: shift progress bars per worker
- Task assignment modal: search by vehicle ID or location
- Bulk task upload (CSV → parsed client-side → POST /tasks/bulk)
- Task audit trail drawer (status timeline)
- Admin: worker CRUD, task type management

**Definition of Done**: A dispatcher can create a shift, load tasks, watch progress live, and pull an end-of-day completion report.

---

### Sprint 5 — Hardening, Performance & PWA (Weeks 9–10)

**Goal**: Production-ready. Observable, tested, installable.

**Backend**
- Database indexes review + EXPLAIN ANALYZE on all hot queries
- Connection pool tuning (HikariCP)
- Redis caching for `GET /tasks` responses (TTL 10s, invalidated on write)
- Rate limiting per worker (Bucket4j: 100 req/min)
- Spring Actuator + Micrometer + Prometheus metrics endpoint
- Integration test suite (Testcontainers + Postgres)
- API versioning prefix `/api/v1/`
- Structured logging (JSON via Logback) with correlation IDs

**Frontend**
- Vite PWA plugin: service worker + web manifest (installable)
- Lighthouse audit → target 90+ PWA score
- E2E tests (Playwright): login → complete task → end shift
- Error boundary components + Sentry integration
- Bundle analysis + lazy route loading
- Accessibility audit (WCAG 2.1 AA for field worker screens)

**Definition of Done**: App installs on Android Chrome. All Sprint 2–4 flows work offline then sync on reconnect. p95 API latency < 200ms under 100 concurrent workers.

---

## Definition of Done (Project-Wide)

A feature is done when:
1. All acceptance criteria in the sprint are met
2. Unit tests cover the service layer (>80% line coverage)
3. The API contract in `specs/api-contract.md` is satisfied
4. No P0/P1 bugs open
5. Migrations run cleanly on a fresh DB
6. The feature works offline and syncs correctly on reconnect

The **MVP is shippable** when Sprints 1–3 are complete and the following endpoints are live and tested:

### MVP Endpoint Checklist

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/auth/login` | Worker login |
| POST | `/api/v1/auth/refresh` | Token refresh |
| GET | `/api/v1/workers/me` | Current worker |
| GET | `/api/v1/shifts/current` | Active shift |
| POST | `/api/v1/shifts/{id}/start` | Start shift |
| GET | `/api/v1/tasks` | Task list (paginated) |
| GET | `/api/v1/tasks/{id}` | Task detail |
| PATCH | `/api/v1/tasks/{id}/status` | Complete / start / flag task |
| POST | `/api/v1/tasks` | Dispatcher creates task |
| POST | `/api/v1/workers/me/location` | GPS ping |
| WS | `/ws` + `/user/queue/tasks` | Real-time push |

---

## Security Checklist

- [ ] JWT in memory (access) + httpOnly SameSite cookie (refresh)
- [ ] CSRF protection on cookie-based endpoints
- [ ] Role-based method security (`@PreAuthorize`)
- [ ] Input validation on all DTOs (`@Valid` + custom validators)
- [ ] Rate limiting (Bucket4j per JWT subject)
- [ ] SQL injection: JPA parameterized queries only, no native string concat
- [ ] Secrets in environment variables / Vault — never in source
- [ ] WebSocket auth: JWT validated on STOMP CONNECT frame
- [ ] HTTPS enforced; HSTS header set
- [ ] Audit log for all task mutations

---

## Performance Targets

| Metric | Target |
|---|---|
| Task list load (cold) | < 300ms p95 |
| Task status update | < 150ms p95 |
| WebSocket event delivery | < 2s end-to-end |
| Offline → online sync | < 5s for up to 50 queued mutations |
| Bundle size (worker app) | < 200KB gzipped initial chunk |
