# Security and Performance Specification

## Security Requirements

### Authentication

- Use email/password for MVP with secure password hashing.
- Prefer Argon2id when available; BCrypt is acceptable.
- Use short-lived JWT access tokens.
- Use opaque refresh tokens stored server-side as hashes.
- Rotate refresh tokens on use.
- Revoke refresh tokens on logout.

### Authorization

Roles:

- `FIELD_WORKER`
- `DISPATCHER`
- `ADMIN`

Rules:

- Workers can only see tasks assigned to them, plus historical tasks they completed during permitted retention windows.
- Workers can only mutate tasks assigned to them.
- Dispatchers can create, edit, assign, unassign, and cancel tasks in their operating city/tenant.
- Admins can manage users and operational settings.

### API Safety

- Validate all request bodies with Bean Validation.
- Reject unknown or invalid enum values.
- Limit request body sizes.
- Rate-limit login, refresh, task creation, and sync replay endpoints.
- Use structured error responses without stack traces.
- Require HTTPS in production.
- Use a strict CORS allowlist.
- Add request IDs to all logs and responses.

### Data Protection

- Store password hashes only, never raw passwords.
- Avoid storing precise historical worker location unless needed.
- If storing worker location history later, define retention and access policy.
- Keep audit events immutable.
- Log security-sensitive events: login failure, permission denial, assignment change, task completion, user disablement.

### WebSocket Security

- Authenticate socket connections.
- Authorize topic subscriptions.
- Do not allow clients to subscribe to arbitrary worker topics.
- Treat socket messages as notifications, not privileged commands, unless explicitly designed and authorized.
- Disconnect expired/invalid sessions.

## Performance Requirements

### Backend Targets

- Worker assigned tasks p95 under 200 ms for 100 assigned tasks.
- Dispatcher task list p95 under 300 ms for 1,000 active tasks.
- Map viewport query p95 under 400 ms for dense city data.
- Task command p95 under 250 ms excluding media upload.
- Reconnect sync of 500 events under 1 second.
- WebSocket task update delivered to connected clients under 2 seconds.

### Database Performance

Required indexes:

- `tasks(status)`
- `tasks(assignee_id)`
- `tasks(assignee_id, status)`
- `tasks(priority, due_at)`
- `tasks(location)` as GiST/PostGIS index.
- Partial active-work index for open/assigned/in-progress/blocked tasks.
- `task_events(event_id)`
- `task_events(task_id)`
- `task_assignments(task_id)` with partial unique active assignment index.

Query rules:

- Always paginate list endpoints.
- Use cursor or event ID pagination for sync.
- Avoid returning full task details in list responses.
- Use DTO projections for dispatcher maps and summaries.
- Add database constraints for invariants.

### Frontend Performance

- Use map marker clustering when many tasks are visible.
- Query tasks by map viewport, not entire city.
- Debounce map movement requests.
- Keep task detail fetch separate from task list fetch.
- Use query cache invalidation by task ID.
- Do not re-render full map/list on every socket event.
- Store offline cache in IndexedDB, not localStorage.

## REST vs GraphQL Decision

Use REST for the MVP.

REST is the better initial choice because:

- Commands map naturally to task lifecycle endpoints.
- Offline replay is simpler with idempotent command endpoints.
- OpenAPI gives fast backend/frontend contract alignment.
- Spring Security authorization is straightforward at endpoint/service level.
- Pagination, filtering, and operational logs are easier to understand.

GraphQL should be reconsidered only when:

- Dispatcher screens need many customizable nested views.
- Multiple clients repeatedly overfetch from REST.
- The team is ready to invest in query complexity limits, field authorization, persisted queries, and GraphQL cache policy.

## Realtime Reliability

WebSockets/SSE are not durable delivery.

Reliability comes from:

- Persisting every important task event in `task_events`.
- Including `eventId` in every realtime event.
- Having the client store the latest event ID.
- Calling `/sync?since=<eventId>` after reconnect.
- Making worker commands idempotent.

## Concurrency Controls

### Optimistic Locking

Use for:

- Task description updates.
- Task requirement updates.
- Priority/due time changes.
- Vehicle metadata updates.

Implementation:

- Add `version` column to mutable tables.
- Include version in task detail responses.
- Require `If-Match` on updates from dispatcher edit forms.
- Return `409 Conflict` with current version if stale.

### Atomic Assignment

Use for:

- Assigning an open task.
- Reassigning a task.
- Ensuring only one active assignee exists.

Implementation options:

- Conditional update with affected-row check.
- `SELECT FOR UPDATE` inside a transaction.
- Partial unique index on active assignment records.

Recommended MVP implementation:

- Use a transaction.
- Lock the task row.
- Validate current status and assignment.
- Insert assignment history row.
- Update task `assignee_id`, `assigned_at`, `status`, and `version`.
- Commit and publish event after commit.

### Idempotency

Use for:

- Start task.
- Complete task.
- Block task.
- Add comment.
- Sync outbox commands.

Rules:

- Same user and same idempotency key returns the original response.
- Same key with different payload returns `409 Conflict`.
- Store idempotency records long enough to cover offline replay windows, for example 7 days.

## Observability

Logs:

- Include request ID, user ID, role, endpoint, status, latency.
- Include task ID for task commands.
- Avoid logging passwords, tokens, or precise sensitive data unnecessarily.

Metrics:

- API latency by endpoint.
- Error rate by endpoint.
- Task command success/failure counts.
- Assignment conflict count.
- Sync replay count and failure count.
- WebSocket active connections.
- Reconnect sync duration.

Alerts:

- Login failure spike.
- Assignment conflict spike.
- Sync failure spike.
- WebSocket connection failures.
- API p95 latency above target.

## Launch Checklist

- All MVP endpoints require authentication except login/refresh.
- Authorization tests cover worker/dispatcher/admin boundaries.
- Database migrations run from empty database.
- OpenAPI document matches frontend client usage.
- Production CORS configured.
- Secrets are environment-based.
- HTTPS enforced by deployment.
- Structured logs available in production.
- Backup/restore plan exists for PostgreSQL.
- Demo data can be seeded in non-production.
