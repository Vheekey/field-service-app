# Field Service App Project Plan

## Product Summary

The application helps field workers manage vehicle-related tasks across a city during a work shift. Workers can see assigned work, plan an efficient route, complete tasks, capture progress, and continue working when network connectivity is unstable. Dispatchers can create, assign, update, and monitor tasks in near real time.

Example task types:

- Swap battery
- Quality check
- Move vehicle to another point
- Bring vehicle back to warehouse
- Inspect/report damage

## Core Users

- Field Worker: completes assigned vehicle tasks during a shift.
- Dispatcher/Operations Admin: creates tasks, assigns work, monitors progress, and updates requirements.
- System Admin: manages users, roles, and operational settings.

## Recommended Architecture

Use a modular monolith for the MVP:

- Backend: Java 21, Spring Boot 3, Spring Security, Spring Data JPA, PostgreSQL/PostGIS, Flyway, Bean Validation, OpenAPI.
- Frontend: Vue 3, TypeScript, Vite, Pinia, Vue Router, TanStack Query or Vue Query for server-state caching, Mapbox GL or Leaflet for maps.
- Realtime: Spring WebSocket with STOMP or Server-Sent Events for task assignment/status updates.
- Offline support: browser IndexedDB queue for worker actions, sync-on-reconnect, server-side optimistic locking.
- Deployment: Docker Compose for local development, containerized backend/frontend, managed PostgreSQL for production.

This is the best starting shape because the domain is still compact, the team can move quickly, and transactional correctness is easier when assignment, task state, and audit events live in one backend. The codebase should still be organized by domain modules so individual modules can later become services if operational scale demands it.

Suggested backend modules:

- identity: users, roles, authentication, authorization.
- tasks: task lifecycle, task metadata, assignments, status changes.
- vehicles: vehicle identity, current known location, battery/status metadata.
- shifts: worker shifts, start/end, active worker context.
- dispatch: assignment rules, workload views, reassignment.
- realtime: WebSocket/SSE events and connection handling.
- audit: immutable task event history.

See [specs/architecture.md](architecture.md) for the full component design and "Uber for tasks" dispatch model.

## REST vs GraphQL

REST is enough for the MVP and is the recommended default.

Reasons:

- The domain has clear resources: tasks, assignments, vehicles, users, shifts.
- Mobile/offline synchronization is easier to reason about with idempotent REST commands and sync endpoints.
- HTTP caching, pagination, OpenAPI documentation, authorization, observability, and operational debugging are simpler.
- Spring Boot REST support is mature and fast to build.

GraphQL is not necessary initially. It can be useful later if dispatcher screens need highly flexible nested queries across many entities, but it adds complexity around caching, authorization per field, rate limiting, offline mutation replay, and query performance. For this product, use REST for commands and queries, plus WebSockets or SSE for live updates.

## Realtime Model

Live updates should be event-driven, not a replacement for durable REST APIs.

The server publishes events when:

- A task is created.
- A task is assigned, unassigned, or reassigned.
- A task is updated.
- A task status changes.
- A task priority changes.
- A task is cancelled.
- A task comment or attachment is added.

Workers subscribe to their own task stream:

- `/topic/workers/{workerId}/tasks`

Dispatchers subscribe to operational streams:

- `/topic/dispatch/tasks`
- `/topic/dispatch/workers`

On reconnect, the frontend must not trust missed socket events. It should:

1. Reconnect WebSocket/SSE.
2. Fetch `/api/v1/sync?since=<lastKnownServerEventId>`.
3. Reconcile local cache with server state.
4. Replay pending offline commands from IndexedDB.
5. Surface conflicts that cannot be auto-resolved.

## Concurrency Strategy

Use optimistic locking for task edits and pessimistic/atomic protection for assignment.

Task updates:

- Each mutable task row has a `version` column.
- API clients send `If-Match: <version>` or a `version` field.
- Backend rejects stale updates with `409 Conflict`.
- Conflict responses include the current server version and changed fields.

Task assignment:

- Assignment must be atomic.
- Backend should use a transaction and either:
  - conditional update: `UPDATE tasks SET assignee_id = ?, version = version + 1 WHERE id = ? AND assignee_id IS NULL AND status = 'OPEN'`
  - or row-level lock: `SELECT ... FOR UPDATE`
- If another dispatcher/automation assigned the task first, return `409 Conflict`.

Task completion:

- Completion commands should be idempotent using an `Idempotency-Key` header.
- The same offline completion replayed twice should not duplicate events, attachments, or status transitions.

## Offline-First Worker Experience

The worker frontend should support unstable connectivity:

- Cache assigned tasks, route data, user profile, and active shift in IndexedDB.
- Store pending mutations in an outbox table in IndexedDB.
- Use idempotency keys for all commands that may be replayed.
- Show whether each change is synced, pending, or conflicted.
- On reconnect, replay queued commands in creation order.
- Pull `/api/v1/sync` after reconnect to recover missed server events.
- For conflict cases, keep the worker's local note/proof and ask them to reconcile only when necessary.

Offline-safe actions for MVP:

- Start task.
- Add note.
- Mark task blocked.
- Complete task with basic completion metadata.

Actions that should require online confirmation for MVP:

- Reassign task.
- Accept newly assigned urgent task.
- Upload large media, unless background upload is implemented.

## State Management Recommendation

Use Pinia for local application state and TanStack Query/Vue Query for server state.

Pinia is a good fit for:

- Auth/session state.
- UI preferences.
- Connectivity status.
- Offline outbox state.
- Current map filters and selected task.

TanStack Query/Vue Query is a better fit for:

- Fetching task lists.
- Caching task details.
- Refetching on reconnect.
- Request deduplication.
- Loading/error states.

Using both avoids turning Pinia into a manual server-cache implementation. Pinia remains simple and intentional, while query caching handles the network lifecycle.

## Minimum Viable Product Scope

The MVP is marketable when it supports the complete daily loop:

- Dispatcher creates tasks with type, priority, location, due window, and vehicle metadata.
- Dispatcher assigns tasks to workers.
- Worker signs in and sees assigned tasks for an active shift.
- Worker views tasks on list and map.
- Worker starts, blocks, completes, and comments on tasks.
- Worker can continue with cached assigned tasks during temporary network loss.
- Backend prevents stale overwrites and duplicate assignment.
- Dispatcher sees near-real-time updates.
- Audit history exists for task lifecycle changes.

## MVP-Qualifying Endpoints

Authentication:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/me`

Tasks:

- `POST /api/v1/tasks`
- `GET /api/v1/tasks`
- `GET /api/v1/tasks/{taskId}`
- `PATCH /api/v1/tasks/{taskId}`
- `POST /api/v1/tasks/{taskId}/assign`
- `POST /api/v1/tasks/{taskId}/unassign`
- `POST /api/v1/tasks/{taskId}/start`
- `POST /api/v1/tasks/{taskId}/complete`
- `POST /api/v1/tasks/{taskId}/block`
- `POST /api/v1/tasks/{taskId}/comments`
- `GET /api/v1/tasks/{taskId}/events`

Worker workflow:

- `POST /api/v1/shifts/start`
- `POST /api/v1/shifts/{shiftId}/end`
- `GET /api/v1/workers/{workerId}/tasks`
- `GET /api/v1/workers/{workerId}/route`

Vehicles:

- `GET /api/v1/vehicles/{vehicleId}`
- `PATCH /api/v1/vehicles/{vehicleId}`

Synchronization:

- `GET /api/v1/sync?since=<eventId>`
- `POST /api/v1/sync/outbox`

Realtime:

- WebSocket endpoint: `/ws`
- Worker topic: `/topic/workers/{workerId}/tasks`
- Dispatcher topic: `/topic/dispatch/tasks`

## Definition of Done

Project-level Definition of Done:

- Backend exposes documented OpenAPI endpoints for all MVP workflows.
- Database schema is versioned with Flyway migrations.
- Authentication and role-based authorization are enforced.
- Task lifecycle state machine is validated server-side.
- Task assignment is race-safe.
- Task updates use optimistic locking.
- Worker task actions are idempotent.
- Realtime events are published for task create, update, assignment, and status change.
- Offline worker actions queue locally and replay on reconnect.
- Reconnect flow fetches missed changes from `/sync`.
- Frontend has responsive worker views for mobile and dispatcher views for desktop.
- Critical flows have automated tests.
- Logs, metrics, and error responses are structured enough for production support.
- Production build runs with Docker Compose or equivalent deployment manifests.
- Security review covers JWT/session handling, CORS, validation, rate limits, audit logs, and authorization boundaries.
- Performance smoke test confirms MVP target load.

Suggested MVP performance targets:

- Task list API p95 under 300 ms for 1,000 active tasks in a city.
- Worker assigned task API p95 under 200 ms for 100 assigned tasks.
- Realtime task assignment visible to online worker under 2 seconds.
- Offline reconnect sync handles 100 queued worker actions without duplicates.
- Map query returns bounded results using viewport filters and indexed geospatial columns.

## Five Autonomous Sprints

### Sprint 1: Foundation and Domain Skeleton

Goal: establish the backend/frontend foundations and domain model.

Backend:

- Create Spring Boot 3 project structure.
- Add PostgreSQL, Flyway, Spring Security, validation, OpenAPI, and test setup.
- Define core entities: user, role, worker profile, vehicle, task, task assignment, shift, task event.
- Add initial database migrations.
- Implement health check and basic auth endpoints.

Frontend:

- Create Vue 3 + Vite + TypeScript app.
- Add Vue Router, Pinia, query caching library, API client structure, and base layout.
- Implement login screen and authenticated shell.
- Add placeholder worker and dispatcher routes.

Deliverables:

- App runs locally with Docker Compose.
- Backend connects to PostgreSQL.
- Initial OpenAPI document generated.
- User can log in and access protected frontend routes.

### Sprint 2: Task Lifecycle MVP

Goal: create, assign, view, and progress tasks.

Backend:

- Implement task CRUD with validation.
- Implement assignment/unassignment with race-safe transaction handling.
- Implement start, complete, block, and comment commands.
- Add optimistic locking to task updates.
- Add task event audit trail.
- Add filtering by status, assignee, priority, due window, and viewport.

Frontend:

- Dispatcher task list and task creation form.
- Worker assigned task list.
- Task detail view with status actions.
- Basic error and conflict handling.

Deliverables:

- Dispatcher can create and assign tasks.
- Worker can start, complete, and block tasks.
- Task event history is stored.
- Race condition tests cover double assignment.

### Sprint 3: Maps, Routing, and Field Workflow

Goal: make the product useful for actual city work.

Backend:

- Add geospatial columns and indexes.
- Add viewport task search.
- Add worker route endpoint using simple priority/distance ordering.
- Add shift start/end endpoints.
- Add vehicle detail/update endpoints.

Frontend:

- Worker map view with assigned tasks.
- Dispatcher map view with city task overview.
- Worker active shift screen.
- Route-ordered task list.
- Task filters for status, urgency, and due time.

Deliverables:

- Worker can see tasks on a map and list.
- Dispatcher can monitor tasks geographically.
- Active shift workflow exists.
- Geospatial queries are indexed and tested.

### Sprint 4: Realtime and Offline Sync

Goal: support live dispatching and unstable field connectivity.

Backend:

- Add WebSocket/STOMP or SSE infrastructure.
- Publish task lifecycle events.
- Add sync endpoint using durable task event IDs.
- Add idempotency key handling for worker commands.
- Add outbox ingestion endpoint for offline replay.

Frontend:

- Subscribe to worker and dispatcher task updates.
- Add connectivity detection.
- Add IndexedDB cache for assigned tasks.
- Add offline outbox for worker actions.
- Implement reconnect, refresh, and replay behavior.
- Add conflict UI for stale updates.

Deliverables:

- Online workers receive new assignments live.
- Dispatcher sees task status updates live.
- Worker can complete cached tasks offline and sync later.
- Reconnect flow recovers missed events.

### Sprint 5: Hardening, Security, Observability, and Launch Readiness

Goal: prepare the MVP for first production users.

Backend:

- Tighten role-based authorization.
- Add rate limiting for auth and write endpoints.
- Add structured logging and request correlation IDs.
- Add metrics for API latency, sync failures, WebSocket connections, and task command failures.
- Add production-ready error responses.
- Add seed/demo data and admin bootstrap flow.

Frontend:

- Polish mobile worker experience.
- Add dispatcher dashboard summary.
- Add loading, empty, error, offline, and conflict states.
- Add accessibility pass.
- Add production build and environment configuration.

Quality:

- Add integration tests for all MVP endpoints.
- Add frontend component and workflow tests for critical paths.
- Add performance smoke tests.
- Add security checklist verification.

Deliverables:

- MVP acceptance test suite passes.
- Launch checklist completed.
- Product can be deployed to staging/production.

## Key Risks

- Offline conflict complexity can grow quickly. Keep offline writes narrow for MVP.
- Route optimization can become a separate product. Start with priority/distance ordering.
- Realtime can hide missed events. Always pair sockets with durable sync.
- Map performance can degrade without viewport filters and geospatial indexes.
- Assignment correctness must be protected server-side, not only in UI.

## Future Enhancements

- Advanced route optimization with traffic and vehicle constraints.
- Push notifications.
- Photo/video proof upload with background sync.
- Dispatcher workload balancing.
- Auto-assignment engine.
- SLA and urgency prediction.
- Multi-city tenancy.
- Native mobile app or PWA install support.
