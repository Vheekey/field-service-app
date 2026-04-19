# Reviewed Backend Implementation

## Stack

- Java 21
- Spring Boot 3
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL 16 with PostGIS
- Flyway
- Bean Validation
- springdoc-openapi
- Spring WebSocket with STOMP or SSE
- Testcontainers
- Micrometer + Actuator + Prometheus
- Bucket4j or gateway-level rate limiting

## Project Structure

Prefer one deployable backend with domain modules:

```text
backend/
├── pom.xml
└── src/main/java/com/example/fieldservice/
    ├── FieldServiceApplication.java
    ├── identity/
    │   ├── api/
    │   ├── application/
    │   ├── domain/
    │   └── persistence/
    ├── tasks/
    │   ├── api/
    │   ├── application/
    │   ├── domain/
    │   └── persistence/
    ├── assignments/
    ├── shifts/
    ├── vehicles/
    ├── workers/
    ├── dispatch/
    ├── sync/
    ├── realtime/
    └── common/
        ├── api/
        ├── security/
        ├── errors/
        └── observability/
```

If the team prefers Maven multi-module, use modules only where they add value:

- `fieldservice-api`
- `fieldservice-application`
- `fieldservice-domain`
- `fieldservice-infrastructure`

Avoid splitting too early into many deployables.

## Backend Responsibilities

The backend owns correctness:

- authentication and refresh-token rotation
- role-based authorization
- task lifecycle validation
- atomic task assignment
- optimistic locking
- idempotent command handling
- durable audit events
- sync visibility filtering
- realtime publication after commit
- geospatial queries
- normalized error responses

The frontend may hide invalid actions, but the backend must reject them.

## Task Lifecycle

Recommended statuses:

- `OPEN`
- `ASSIGNED`
- `IN_PROGRESS`
- `BLOCKED`
- `COMPLETED`
- `CANCELLED`

Allowed transitions:

- `OPEN -> ASSIGNED`
- `OPEN -> CANCELLED`
- `ASSIGNED -> OPEN`
- `ASSIGNED -> IN_PROGRESS`
- `ASSIGNED -> BLOCKED`
- `ASSIGNED -> CANCELLED`
- `IN_PROGRESS -> BLOCKED`
- `IN_PROGRESS -> COMPLETED`
- `BLOCKED -> ASSIGNED`
- `BLOCKED -> CANCELLED`

`COMPLETED` is terminal.

## Optimistic Locking

Use JPA `@Version` on mutable aggregate roots:

- `Task`
- `Vehicle`

API clients should send `If-Match: <version>` for dispatcher edits. Worker command endpoints may include a command-specific expected version when the command depends on current requirements.

On stale update:

- return `409 Conflict`
- include current version
- include a URL or embedded summary of current server state

## Atomic Assignment

Do not rely on a partial unique index on `tasks(id)`. `id` is already unique and that index does not protect assignment.

Recommended assignment flow:

1. Begin transaction.
2. Lock task row with `SELECT ... FOR UPDATE` or execute a conditional update.
3. Validate task status and current assignee.
4. Close any active assignment history row if reassignment.
5. Insert new active `task_assignments` row.
6. Update `tasks.assignee_id`, `tasks.assigned_at`, `tasks.status`, and `tasks.version`.
7. Insert `TASK_ASSIGNED` event.
8. Commit.
9. Publish realtime message after commit.

Database constraint:

```sql
CREATE UNIQUE INDEX ux_task_assignments_active
    ON task_assignments(task_id)
    WHERE unassigned_at IS NULL;
```

Alternative for first implementation:

```sql
UPDATE tasks
SET assignee_id = :workerId,
    assigned_at = now(),
    status = 'ASSIGNED',
    version = version + 1
WHERE id = :taskId
  AND status = 'OPEN'
  AND assignee_id IS NULL;
```

If affected rows is `0`, return `409 Conflict`.

## Durable Events and Realtime

Persist events in `task_events` inside the same transaction as the business mutation.

Publish after commit with one of:

- `@TransactionalEventListener(phase = AFTER_COMMIT)`
- an outbox/event relay table
- a message broker fed from committed events

Do not publish WebSocket events directly from inside an uncommitted transaction.

Event envelope:

```json
{
  "eventId": 30291,
  "type": "TASK_ASSIGNED",
  "entityType": "TASK",
  "entityId": "task_123",
  "version": 9,
  "occurredAt": "2026-04-19T10:00:00Z",
  "payload": {
    "assigneeId": "worker_123"
  }
}
```

## Sync and Idempotency

Use event-ID sync:

- `GET /api/v1/sync?since=<eventId>`
- returns events visible to the current user in ascending event order

Use offline command replay:

- `POST /api/v1/sync/outbox`
- accepts commands with `clientMutationId` and `idempotencyKey`
- returns per-command result: `APPLIED`, `DUPLICATE`, `REJECTED`, `CONFLICT`

Idempotency records:

- unique by `(user_id, idempotency_key)`
- store request hash
- store original response body/status
- expire after the offline replay window, for example 7 days

Same key with different payload hash must return `409 Conflict`.

## WebSocket Security

Required:

- authenticate STOMP `CONNECT`
- authorize every `SUBSCRIBE`
- restrict worker subscriptions to their own queue/topic
- use strict allowed origins, not wildcard origins
- disconnect expired sessions

Do not use:

```java
setAllowedOriginPatterns("*")
```

Use environment-specific allowed origins:

```yaml
app:
  cors:
    allowed-origins:
      - https://field.example.com
      - https://dispatch.example.com
```

## Caching Guidance

Do not start with Redis caching for live task lists.

First optimize with:

- proper database indexes
- DTO projections
- pagination
- viewport filtering for maps
- query cache on the frontend via Vue Query
- targeted cache invalidation on socket events

Redis is useful later for:

- rate limiting
- broker-backed realtime fanout
- short-lived dispatcher dashboard read models
- distributed locks only if unavoidable

Avoid cache keys that omit pagination/filter parameters. Avoid `allEntries = true` on hot task caches unless the data is tiny.

## JPA Practices

- Set `spring.jpa.open-in-view=false`.
- Use DTO projections for list endpoints.
- Use `@EntityGraph` or explicit fetch joins for detail endpoints.
- Avoid `FetchType.EAGER`.
- Add `hibernate.default_batch_fetch_size=20` or similar.
- Keep transaction boundaries in application services.
- Keep controllers thin.
- Keep entities out of API responses.

## Testing

Required backend tests:

- task state machine unit tests
- assignment race integration test with Testcontainers/Postgres
- optimistic locking integration test
- idempotency replay integration test
- sync visibility tests
- WebSocket subscription authorization tests
- authorization tests for worker/dispatcher/admin
- geospatial query tests

## Observability

Include:

- request ID
- user ID
- role
- endpoint
- task ID for task commands
- latency
- status code

Metrics:

- API p95 by endpoint
- assignment conflict count
- idempotency duplicate count
- sync replay failures
- WebSocket connection count
- reconnect sync duration
