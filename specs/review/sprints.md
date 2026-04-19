# Reviewed Sprint Plan

## Sprint 1: Foundation

Backend:

- Spring Boot 3 project.
- PostgreSQL/PostGIS and Flyway.
- Security foundation.
- Auth endpoints.
- Core schema migrations.
- Health check and OpenAPI.

Frontend:

- Vue 3 + Vite + TypeScript.
- Vue Router.
- Pinia.
- Vue Query.
- API client.
- Auth shell and route guards.

Done:

- Local Docker setup runs.
- User can log in.
- Protected route works.
- Database migrations run from empty database.

## Sprint 2: Task Lifecycle

Backend:

- Task creation/list/detail.
- Task assignment/unassignment.
- Start/block/complete/comment.
- Optimistic locking.
- Atomic assignment.
- Task events.

Frontend:

- Worker task list and detail.
- Dispatcher task creation and assignment.
- Basic task actions.
- Conflict display.

Done:

- Dispatcher creates and assigns tasks.
- Worker completes tasks.
- Duplicate assignment is prevented by test.
- Stale task update returns `409`.

## Sprint 3: Maps and Field Workflow

Backend:

- PostGIS location columns/indexes.
- Viewport task queries.
- Worker route endpoint.
- Shift start/end.
- Vehicle detail/update.

Frontend:

- Worker map.
- Dispatcher map.
- Active shift screen.
- Route-ordered list.
- Filters.

Done:

- Worker sees route and map.
- Dispatcher sees city task overview.
- Map queries are bounded and indexed.

## Sprint 4: Realtime and Offline

Backend:

- WebSocket/SSE endpoint.
- Durable event publishing after commit.
- `/sync?since=<eventId>`.
- `/sync/outbox`.
- Idempotency records.
- Subscription authorization.

Frontend:

- Realtime client.
- IndexedDB cache and outbox.
- Reconnect sync.
- Per-command replay.
- Conflict handling.

Done:

- New assignments arrive live.
- Missed events recover after reconnect.
- Offline completion syncs later without duplication.
- Failed replay does not lose queued commands.

## Sprint 5: Hardening and Launch

Backend:

- Authorization pass.
- Rate limiting.
- Structured logging.
- Metrics.
- Testcontainers integration suite.
- Performance smoke tests.

Frontend:

- PWA install support.
- Mobile polish.
- Accessibility pass.
- E2E tests.
- Production environment config.

Done:

- MVP acceptance flows pass.
- Security checklist complete.
- Performance targets met.
- Staging deployment ready.
