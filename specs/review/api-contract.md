# Reviewed API Contract

Base path: `/api/v1`

All responses are JSON unless `204 No Content`.

## Common Headers

Requests:

- `Authorization: Bearer <accessToken>`
- `Idempotency-Key: <uuid>` for replayable commands
- `If-Match: <version>` for optimistic resource updates

Responses:

- `ETag: <version>` for versioned resources
- `X-Request-Id: <requestId>`

## Error Shape

```json
{
  "error": {
    "code": "TASK_VERSION_CONFLICT",
    "message": "Task was changed by another user.",
    "requestId": "req_123",
    "details": {
      "taskId": "task_123",
      "currentVersion": 8
    }
  }
}
```

## Auth

### `POST /auth/login`

Returns an access token. Prefer setting the refresh token as a secure HttpOnly SameSite cookie.

### `POST /auth/refresh`

Rotates refresh token and returns a new access token.

### `POST /auth/logout`

Revokes refresh token.

### `GET /me`

Returns current user, roles, and worker profile if applicable.

## Tasks

### `POST /tasks`

Roles: `DISPATCHER`, `ADMIN`

Creates a task.

### `GET /tasks`

Roles: `DISPATCHER`, `ADMIN`

Query parameters:

- `status`
- `assigneeId`
- `priority`
- `type`
- `dueBefore`
- `bbox=<west>,<south>,<east>,<north>`
- `page`
- `size`
- `sort`

Use list DTOs only. Do not include full audit history in list results.

### `GET /tasks/{taskId}`

Returns task detail visible to the current user.

### `PATCH /tasks/{taskId}`

Roles: `DISPATCHER`, `ADMIN`

Requires:

- `If-Match: <version>`

Updates mutable task fields but does not change lifecycle status.

### `POST /tasks/{taskId}/assign`

Roles: `DISPATCHER`, `ADMIN`

Rules:

- assignment is atomic
- task must be assignable
- duplicate assignment returns `409 Conflict`
- event is published after commit

### `POST /tasks/{taskId}/unassign`

Roles: `DISPATCHER`, `ADMIN`

Requires reason if task is already in progress.

### `POST /tasks/{taskId}/start`

Roles: `FIELD_WORKER`

Requires:

- `Idempotency-Key`

Rules:

- current user must be assignee
- task must be `ASSIGNED`

### `POST /tasks/{taskId}/complete`

Roles: `FIELD_WORKER`

Requires:

- `Idempotency-Key`

Rules:

- current user must be assignee
- command is replay-safe
- repeated command with same key returns original result

### `POST /tasks/{taskId}/block`

Roles: `FIELD_WORKER`

Requires:

- `Idempotency-Key`

### `POST /tasks/{taskId}/comments`

Roles: `FIELD_WORKER`, `DISPATCHER`, `ADMIN`

Requires:

- `Idempotency-Key` for offline replay

### `GET /tasks/{taskId}/events`

Returns immutable audit events.

## Shifts

### `POST /shifts/start`

Roles: `FIELD_WORKER`

Starts current worker shift.

### `POST /shifts/{shiftId}/end`

Roles: `FIELD_WORKER`

Ends current worker shift.

### `GET /shifts/current`

Roles: `FIELD_WORKER`

Returns active shift summary.

## Workers

### `GET /workers/{workerId}/tasks`

Roles: `FIELD_WORKER`, `DISPATCHER`, `ADMIN`

Workers can only access themselves.

### `GET /workers/{workerId}/route`

Returns suggested route order.

### `POST /workers/me/location`

Roles: `FIELD_WORKER`

Optional MVP endpoint for dispatcher map. Rate limit and retain for a short period.

## Vehicles

### `GET /vehicles/{vehicleId}`

Returns vehicle detail.

### `PATCH /vehicles/{vehicleId}`

Requires:

- `If-Match: <version>`

## Sync

### `GET /sync?since=<eventId>`

Returns durable events after a known event ID.

```json
{
  "serverTime": "2026-04-19T10:00:00Z",
  "latestEventId": 30291,
  "events": [
    {
      "eventId": 30291,
      "type": "TASK_COMPLETED",
      "entityType": "TASK",
      "entityId": "task_123",
      "version": 9,
      "occurredAt": "2026-04-19T09:59:00Z",
      "payload": {}
    }
  ]
}
```

### `POST /sync/outbox`

Accepts offline commands.

```json
{
  "commands": [
    {
      "clientMutationId": "cm_124",
      "idempotencyKey": "8497b6bb-6a0d-41a5-9a4c-c7e8dd3b3a6f",
      "type": "COMPLETE_TASK",
      "createdAt": "2026-04-19T09:45:00Z",
      "payload": {
        "taskId": "task_123",
        "completionNotes": "Battery swapped successfully."
      }
    }
  ]
}
```

Response:

```json
{
  "results": [
    {
      "clientMutationId": "cm_124",
      "status": "APPLIED",
      "serverEventId": 30291
    }
  ]
}
```

Statuses:

- `APPLIED`
- `DUPLICATE`
- `REJECTED`
- `CONFLICT`

## Realtime

Endpoint:

- `/ws`

Topics:

- worker personal queue/topic
- dispatcher task topic
- dispatcher worker topic

Every subscription must be authorized.

Event shape:

```json
{
  "eventId": 30291,
  "type": "TASK_STATUS_CHANGED",
  "occurredAt": "2026-04-19T10:00:00Z",
  "entityType": "TASK",
  "entityId": "task_123",
  "version": 9,
  "payload": {
    "status": "COMPLETED",
    "assigneeId": "worker_123"
  }
}
```
