# API Contract

Base path: `/api/v1`

All JSON responses use `application/json`.

## Common Headers

Requests:

- `Authorization: Bearer <accessToken>`
- `Idempotency-Key: <uuid>` for replayable command endpoints.
- `If-Match: <version>` for optimistic updates.

Responses:

- `ETag: <version>` for versioned resources.
- `X-Request-Id: <requestId>` for support and tracing.

## Error Shape

```json
{
  "error": {
    "code": "TASK_VERSION_CONFLICT",
    "message": "Task was changed by another user.",
    "requestId": "req_123",
    "details": {
      "taskId": "018f4c7a-9c0e-7a21-a7e2-b26ff71dd38f",
      "currentVersion": 8
    }
  }
}
```

Common status codes:

- `400 Bad Request`: validation error.
- `401 Unauthorized`: missing/invalid token.
- `403 Forbidden`: authenticated but not allowed.
- `404 Not Found`: resource does not exist or is not visible to user.
- `409 Conflict`: stale version, duplicate assignment, invalid state transition.
- `422 Unprocessable Entity`: valid JSON but invalid domain command.
- `429 Too Many Requests`: rate limit exceeded.

## Authentication

### POST `/auth/login`

```json
{
  "email": "worker@example.com",
  "password": "secret"
}
```

Response:

```json
{
  "accessToken": "jwt",
  "refreshToken": "opaque-refresh-token",
  "expiresAt": "2026-04-19T12:30:00Z",
  "user": {
    "id": "user_123",
    "name": "Amina Worker",
    "email": "worker@example.com",
    "roles": ["FIELD_WORKER"]
  }
}
```

### POST `/auth/refresh`

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

### POST `/auth/logout`

Revokes the refresh token.

### GET `/me`

Returns the authenticated user's profile and roles.

## Tasks

### POST `/tasks`

Roles: `DISPATCHER`, `ADMIN`

Creates a task.

```json
{
  "type": "SWAP_BATTERY",
  "priority": "HIGH",
  "title": "Swap low battery",
  "description": "Battery below threshold.",
  "vehicleId": "veh_123",
  "location": {
    "lat": 59.3293,
    "lng": 18.0686,
    "address": "Central Stockholm"
  },
  "dueAt": "2026-04-19T15:00:00Z",
  "requirements": [
    "Scan vehicle QR code",
    "Attach completion photo"
  ]
}
```

Response: `201 Created`

```json
{
  "id": "task_123",
  "version": 1,
  "status": "OPEN",
  "type": "SWAP_BATTERY",
  "priority": "HIGH",
  "title": "Swap low battery",
  "description": "Battery below threshold.",
  "vehicleId": "veh_123",
  "assigneeId": null,
  "location": {
    "lat": 59.3293,
    "lng": 18.0686,
    "address": "Central Stockholm"
  },
  "dueAt": "2026-04-19T15:00:00Z",
  "createdAt": "2026-04-19T08:00:00Z",
  "updatedAt": "2026-04-19T08:00:00Z"
}
```

### GET `/tasks`

Roles: `DISPATCHER`, `ADMIN`

Query parameters:

- `status=OPEN|ASSIGNED|IN_PROGRESS|BLOCKED|COMPLETED|CANCELLED`
- `assigneeId=<userId>`
- `priority=LOW|NORMAL|HIGH|URGENT`
- `type=SWAP_BATTERY|QUALITY_CHECK|MOVE_VEHICLE|RETURN_TO_WAREHOUSE`
- `dueBefore=<isoDateTime>`
- `bbox=<west>,<south>,<east>,<north>`
- `page=0`
- `size=50`
- `sort=dueAt,asc`

Response:

```json
{
  "items": [],
  "page": 0,
  "size": 50,
  "totalItems": 0,
  "totalPages": 0
}
```

### GET `/tasks/{taskId}`

Returns one task visible to the authenticated user.

### PATCH `/tasks/{taskId}`

Roles: `DISPATCHER`, `ADMIN`

Headers:

- `If-Match: 7`

Updates mutable task fields. Does not change lifecycle status.

```json
{
  "priority": "URGENT",
  "description": "Use spare battery from van 3.",
  "requirements": [
    "Scan vehicle QR code",
    "Attach completion photo",
    "Confirm battery serial number"
  ],
  "dueAt": "2026-04-19T14:30:00Z"
}
```

Conflict response:

```json
{
  "error": {
    "code": "TASK_VERSION_CONFLICT",
    "message": "Task was changed by another user.",
    "details": {
      "currentVersion": 8,
      "currentTaskUrl": "/api/v1/tasks/task_123"
    }
  }
}
```

### POST `/tasks/{taskId}/assign`

Roles: `DISPATCHER`, `ADMIN`

```json
{
  "assigneeId": "user_worker_123"
}
```

Rules:

- Task must be `OPEN` or `ASSIGNED`.
- Assignment update must be atomic.
- If another user assigned the task concurrently, return `409 Conflict`.

### POST `/tasks/{taskId}/unassign`

Roles: `DISPATCHER`, `ADMIN`

Rules:

- Task cannot be completed.
- If task is in progress, require reason.

```json
{
  "reason": "Worker shift ended."
}
```

### POST `/tasks/{taskId}/start`

Roles: `FIELD_WORKER`

Headers:

- `Idempotency-Key: <uuid>`

```json
{
  "startedAt": "2026-04-19T09:15:00Z",
  "clientMutationId": "cm_123"
}
```

Rules:

- Task must be assigned to current worker.
- Task status becomes `IN_PROGRESS`.

### POST `/tasks/{taskId}/complete`

Roles: `FIELD_WORKER`

Headers:

- `Idempotency-Key: <uuid>`

```json
{
  "completedAt": "2026-04-19T09:45:00Z",
  "completionNotes": "Battery swapped successfully.",
  "location": {
    "lat": 59.3301,
    "lng": 18.0712
  },
  "proof": {
    "photoIds": ["att_123"],
    "vehicleQrCode": "VEH-001"
  },
  "clientMutationId": "cm_124"
}
```

Rules:

- Task must be assigned to current worker.
- Task status becomes `COMPLETED`.
- Replayed completion with same idempotency key returns the original result.

### POST `/tasks/{taskId}/block`

Roles: `FIELD_WORKER`

```json
{
  "reasonCode": "VEHICLE_NOT_FOUND",
  "note": "Vehicle was not at the expected location.",
  "blockedAt": "2026-04-19T09:30:00Z",
  "clientMutationId": "cm_125"
}
```

### POST `/tasks/{taskId}/comments`

Roles: `FIELD_WORKER`, `DISPATCHER`, `ADMIN`

```json
{
  "body": "Need a replacement part.",
  "clientMutationId": "cm_126"
}
```

### GET `/tasks/{taskId}/events`

Returns immutable audit events for a task.

## Worker Workflow

### POST `/shifts/start`

Roles: `FIELD_WORKER`

```json
{
  "startedAt": "2026-04-19T08:00:00Z",
  "startLocation": {
    "lat": 59.3293,
    "lng": 18.0686
  }
}
```

### POST `/shifts/{shiftId}/end`

Roles: `FIELD_WORKER`

```json
{
  "endedAt": "2026-04-19T16:00:00Z",
  "endLocation": {
    "lat": 59.3300,
    "lng": 18.0600
  }
}
```

### GET `/workers/{workerId}/tasks`

Roles: `FIELD_WORKER`, `DISPATCHER`, `ADMIN`

Workers can only access their own task list. Dispatchers/admins can access any worker.

Query parameters:

- `status=ASSIGNED,IN_PROGRESS,BLOCKED`
- `includeCompletedSince=<isoDateTime>`

### GET `/workers/{workerId}/route`

Returns suggested task order.

```json
{
  "workerId": "user_worker_123",
  "generatedAt": "2026-04-19T08:10:00Z",
  "strategy": "PRIORITY_THEN_DISTANCE",
  "stops": [
    {
      "taskId": "task_123",
      "sequence": 1,
      "estimatedTravelMinutes": 8
    }
  ]
}
```

## Vehicles

### GET `/vehicles/{vehicleId}`

Returns vehicle metadata and latest known status.

### PATCH `/vehicles/{vehicleId}`

Roles: `FIELD_WORKER`, `DISPATCHER`, `ADMIN`

Used for limited mutable vehicle fields such as latest location or reported condition.

## Sync

### GET `/sync`

Query parameters:

- `since=<eventId>`

Returns durable events after a known event ID.

```json
{
  "serverTime": "2026-04-19T10:00:00Z",
  "latestEventId": 30291,
  "events": [
    {
      "eventId": 30290,
      "type": "TASK_ASSIGNED",
      "occurredAt": "2026-04-19T09:59:00Z",
      "entityType": "TASK",
      "entityId": "task_123",
      "payload": {
        "assigneeId": "user_worker_123"
      }
    }
  ]
}
```

### POST `/sync/outbox`

Roles: `FIELD_WORKER`

Accepts queued offline commands.

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

Result statuses:

- `APPLIED`
- `DUPLICATE`
- `REJECTED`
- `CONFLICT`

## Realtime Contract

WebSocket endpoint: `/ws`

Suggested protocol: STOMP over WebSocket.

Topics:

- `/topic/workers/{workerId}/tasks`
- `/topic/dispatch/tasks`
- `/topic/dispatch/workers`

Event envelope:

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
    "assigneeId": "user_worker_123"
  }
}
```

Clients must persist the latest received `eventId` and call `/sync?since=<eventId>` after reconnect.
