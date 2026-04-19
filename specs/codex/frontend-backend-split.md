# Frontend and Backend Split

## Backend Responsibilities

The backend owns business correctness.

Required responsibilities:

- Authentication and refresh-token handling.
- Role-based authorization.
- Task lifecycle validation.
- Race-safe task assignment.
- Optimistic locking for task and vehicle edits.
- Idempotency for replayable worker commands.
- Durable audit events.
- Realtime event publishing.
- Sync endpoint for missed events.
- Geospatial search and route suggestion.
- Input validation and normalized error responses.
- Rate limiting and request tracing.
- Database migrations and seed data.

The frontend may make the workflow smooth, but the backend must reject invalid transitions even if a client is buggy or offline.

## Frontend Responsibilities

The frontend owns field usability.

Required worker features:

- Mobile-first task list.
- Task map.
- Task detail and requirement checklist.
- Start, complete, block, and comment actions.
- Active shift start/end.
- Offline cache for assigned tasks.
- Offline action queue with sync status.
- Reconnect and missed-event refresh.
- Conflict display for stale server data.

Required dispatcher features:

- Desktop-friendly task list.
- Task creation and edit form.
- Worker assignment and reassignment.
- City map with task markers.
- Live status updates.
- Filters by status, worker, priority, due time, and task type.
- Conflict handling when task was edited elsewhere.

## Recommended Frontend Stack

- Vue 3 with Composition API.
- TypeScript.
- Vite.
- Vue Router.
- Pinia for client-owned state.
- TanStack Query/Vue Query for server state.
- IndexedDB using Dexie for offline cache and outbox.
- Mapbox GL JS or Leaflet for maps.
- Zod or Valibot for client-side schema validation where useful.

Pinia remains recommended, but not as the only state tool. Use it for app state and use a query library for server state.

Suggested Pinia stores:

- `authStore`: user, token lifecycle, role helpers.
- `connectivityStore`: online/offline, socket status, last sync event ID.
- `offlineOutboxStore`: pending commands, sync progress, conflicts.
- `uiStore`: selected task, filters, map viewport.

Suggested query domains:

- `useAssignedTasksQuery`
- `useTaskDetailQuery`
- `useDispatcherTasksQuery`
- `useVehicleQuery`
- `useWorkerRouteQuery`

## Recommended Backend Stack

- Java 21.
- Spring Boot 3.
- Spring Web.
- Spring Security.
- Spring Data JPA.
- PostgreSQL with PostGIS.
- Flyway.
- Bean Validation.
- springdoc-openapi.
- WebSocket/STOMP or SSE.
- Testcontainers for integration tests.
- Micrometer for metrics.

## REST and Realtime Boundary

Use REST for:

- Creating tasks.
- Assigning tasks.
- Updating task details.
- Worker commands.
- Loading task lists.
- Loading task history.
- Sync recovery.

Use WebSockets/SSE for:

- New assignment notification.
- Task update notification.
- Dispatcher dashboard refresh.
- Worker task status broadcast.

Do not rely on WebSocket messages as the only source of truth. They are hints that prompt the client to update local cache or refetch.

## Offline Frontend Flow

Normal online command:

1. Worker taps Complete.
2. Frontend creates a `clientMutationId` and `Idempotency-Key`.
3. Frontend optimistically updates local task state to pending-completed.
4. Frontend sends command to backend.
5. Backend validates, applies, stores event, and publishes realtime message.
6. Frontend marks local command as synced.

Offline command:

1. Worker taps Complete.
2. Frontend writes command to IndexedDB outbox.
3. Frontend updates local task state as pending sync.
4. On reconnect, frontend calls `/sync`.
5. Frontend replays queued commands via `/sync/outbox`.
6. Frontend applies command results.
7. Frontend refetches assigned tasks.

Conflict case:

1. Worker has stale task requirements.
2. Dispatcher changes task requirements before worker syncs.
3. Worker completes old version offline.
4. Backend returns `CONFLICT` if completion no longer satisfies current requirements.
5. Frontend keeps worker's captured data and asks for review/retry.

## Security Split

Backend:

- Enforces all authorization.
- Hashes passwords.
- Rotates and revokes refresh tokens.
- Validates JWT signatures and claims.
- Applies CORS allowlist.
- Sanitizes error responses.
- Validates request size and content type.
- Stores audit logs.

Frontend:

- Avoids storing long-lived secrets in local storage.
- Uses short-lived access tokens.
- Clears sensitive cache on logout.
- Hides unauthorized UI affordances.
- Treats hidden UI as convenience only, not security.

## Performance Split

Backend:

- Paginate all list endpoints.
- Require viewport bounds for dense map queries.
- Use PostGIS indexes for map and nearby searches.
- Add indexes for `(assignee_id, status)` and `(priority, due_at)`.
- Avoid N+1 queries with explicit fetch plans/projections.
- Return summary DTOs for lists and detailed DTOs for detail views.
- Use event IDs for efficient sync.

Frontend:

- Virtualize long task lists if needed.
- Cluster map markers.
- Debounce map viewport queries.
- Cache task details.
- Refetch on focus/reconnect.
- Avoid full-list reloads for every socket event.
- Apply socket events to cache when safe, otherwise invalidate targeted queries.

## Testing Split

Backend tests:

- Unit tests for task state machine.
- Integration tests for assignment race.
- Integration tests for optimistic locking.
- Integration tests for idempotency.
- API tests for auth and authorization.
- Sync endpoint tests.

Frontend tests:

- Worker task workflow tests.
- Dispatcher assignment flow tests.
- Offline outbox tests.
- Reconnect sync tests.
- Conflict state tests.
- Role-based route guard tests.
