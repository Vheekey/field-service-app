# API Contract — FieldOps

> Base URL: `https://api.fieldops.io/api/v1`  
> All requests: `Content-Type: application/json`  
> All responses: `Content-Type: application/json`  
> Authentication: `Authorization: Bearer <access_token>` (except `/auth/*`)  
> Versioning: URI prefix `/api/v1/`  
> Pagination: cursor-free offset — `?page=0&size=20` on list endpoints

---

## Global Response Envelope

### Success
```json
{
  "data": { ... },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 147,
    "timestamp": "2025-03-15T08:30:00Z"
  }
}
```

### Error
```json
{
  "error": {
    "code": "TASK_CONFLICT",
    "message": "Task was modified by another user. Please reload.",
    "details": {},
    "timestamp": "2025-03-15T08:30:00Z",
    "traceId": "abc123"
  }
}
```

### Standard Error Codes

| HTTP | code | Meaning |
|---|---|---|
| 400 | `VALIDATION_ERROR` | DTO validation failed |
| 401 | `UNAUTHORIZED` | Missing or expired token |
| 403 | `FORBIDDEN` | Valid token, wrong role |
| 404 | `NOT_FOUND` | Resource does not exist |
| 409 | `TASK_CONFLICT` | Optimistic lock version mismatch |
| 409 | `ALREADY_ASSIGNED` | Task already assigned to another worker |
| 422 | `INVALID_STATUS_TRANSITION` | e.g. COMPLETED → IN_PROGRESS |
| 429 | `RATE_LIMITED` | Too many requests |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

---

## Auth

### POST `/auth/login`

**Role**: Public

**Request**
```json
{
  "email": "worker@fieldops.io",
  "password": "secret"
}
```

**Response 200**
```json
{
  "data": {
    "accessToken": "eyJ...",
    "expiresIn": 900,
    "worker": {
      "id": "uuid",
      "firstName": "Sara",
      "lastName": "Lindqvist",
      "email": "worker@fieldops.io",
      "role": "WORKER",
      "avatarUrl": null
    }
  }
}
```
Refresh token set as `Set-Cookie: refreshToken=<token>; HttpOnly; SameSite=Strict; Secure; Path=/api/v1/auth/refresh`

---

### POST `/auth/refresh`

**Role**: Public (cookie required)

**Response 200**: Same shape as login — new access token + rotated refresh cookie.

**Response 401**: Refresh token expired or revoked.

---

### POST `/auth/logout`

**Role**: WORKER | DISPATCHER | ADMIN

Revokes the refresh token. Clears cookie.

**Response 204**: No content.

---

## Workers

### GET `/workers/me`

**Role**: WORKER | DISPATCHER | ADMIN

**Response 200**
```json
{
  "data": {
    "id": "uuid",
    "firstName": "Sara",
    "lastName": "Lindqvist",
    "email": "worker@fieldops.io",
    "phone": "+46701234567",
    "role": "WORKER",
    "avatarUrl": null,
    "lastSeenAt": "2025-03-15T07:45:00Z"
  }
}
```

---

### GET `/workers`

**Role**: DISPATCHER | ADMIN  
**Query params**: `?role=WORKER&isActive=true&page=0&size=20`

**Response 200** — paginated list of worker summaries with current shift status.

---

### GET `/workers/{id}`

**Role**: DISPATCHER | ADMIN

---

### POST `/workers`

**Role**: ADMIN

**Request**
```json
{
  "email": "new@fieldops.io",
  "firstName": "Björn",
  "lastName": "Ek",
  "phone": "+46709999999",
  "role": "WORKER"
}
```
Sends a welcome email with a password-set link.

---

### POST `/workers/me/location`

**Role**: WORKER  
Batched GPS ping. Called every 30 seconds from the app.

**Request**
```json
{
  "lat": 59.3340,
  "lng": 18.0686,
  "accuracyM": 12,
  "pinnedAt": "2025-03-15T08:30:00Z"
}
```

**Response 204**: No content.

---

## Shifts

### GET `/shifts/current`

**Role**: WORKER  
Returns the worker's active shift or 404 if none.

**Response 200**
```json
{
  "data": {
    "id": "uuid",
    "date": "2025-03-15",
    "status": "ACTIVE",
    "startedAt": "2025-03-15T07:00:00Z",
    "endedAt": null,
    "summary": {
      "total": 12,
      "pending": 3,
      "inProgress": 1,
      "completed": 8,
      "issues": 0
    }
  }
}
```

---

### GET `/shifts`

**Role**: DISPATCHER | ADMIN  
**Query params**: `?workerId=&date=2025-03-15&status=ACTIVE&page=0&size=20`

---

### POST `/shifts`

**Role**: DISPATCHER | ADMIN

**Request**
```json
{
  "workerId": "uuid",
  "date": "2025-03-15",
  "notes": "Focus on north-side battery swaps first"
}
```

---

### POST `/shifts/{id}/start`

**Role**: WORKER  
Transitions shift from `PENDING` → `ACTIVE`. Sets `startedAt`.

**Response 200** — updated shift object.

---

### POST `/shifts/{id}/end`

**Role**: WORKER | DISPATCHER  
Transitions shift to `COMPLETED`. Only allowed if no tasks are `IN_PROGRESS`.

**Response 200** — updated shift object.

---

### GET `/shifts/{id}/report`

**Role**: DISPATCHER | ADMIN  
Returns shift completion stats + per-task status breakdown.

---

## Tasks

### GET `/tasks`

**Role**: WORKER (own tasks only) | DISPATCHER (all)  
**Query params**:

| param | type | description |
|---|---|---|
| `shiftId` | UUID | filter by shift |
| `assignedTo` | UUID | filter by worker (dispatcher only) |
| `status` | enum | PENDING,ASSIGNED,IN_PROGRESS,COMPLETED,ISSUE,CANCELLED |
| `urgency` | 1-5 | minimum urgency |
| `updatedSince` | ISO8601 | for incremental sync on reconnect |
| `lat` / `lng` / `radiusM` | decimal/int | spatial filter |
| `page` | int | default 0 |
| `size` | int | default 20, max 100 |
| `sort` | string | `urgency,desc` or `distance,asc` |

Workers only receive tasks assigned to themselves. The `updatedSince` param is critical for offline reconnect — the client stores the last successful sync timestamp and fetches the diff.

**Response 200**
```json
{
  "data": [
    {
      "id": "uuid",
      "title": "Swap battery — Gamla Stan",
      "taskType": {
        "code": "BATTERY_SWAP",
        "label": "Battery Swap",
        "iconName": "battery-swap"
      },
      "vehicle": {
        "id": "uuid",
        "externalId": "SC-1042",
        "type": "SCOOTER",
        "batteryLevel": 8
      },
      "urgency": 5,
      "status": "ASSIGNED",
      "lat": 59.3234,
      "lng": 18.0712,
      "addressHint": "Outside Stortorget, near the fountain",
      "dueBy": "2025-03-15T10:00:00Z",
      "assignedTo": "uuid",
      "version": 3,
      "createdAt": "2025-03-15T06:00:00Z",
      "updatedAt": "2025-03-15T07:30:00Z"
    }
  ],
  "meta": {
    "page": 0,
    "size": 20,
    "total": 12
  }
}
```

---

### GET `/tasks/{id}`

**Role**: WORKER (own tasks) | DISPATCHER | ADMIN  
Returns full task detail including status history.

**Response 200**
```json
{
  "data": {
    "id": "uuid",
    "title": "Swap battery — Gamla Stan",
    "description": "Vehicle is parked between two parked cars, might need to be moved first. Charger port is on the right side.",
    "taskType": { "code": "BATTERY_SWAP", "label": "Battery Swap" },
    "vehicle": {
      "id": "uuid",
      "externalId": "SC-1042",
      "type": "SCOOTER",
      "model": "Segway Max G2",
      "batteryLevel": 8,
      "status": "IN_TASK"
    },
    "urgency": 5,
    "status": "IN_PROGRESS",
    "lat": 59.3234,
    "lng": 18.0712,
    "addressHint": "Outside Stortorget",
    "dueBy": "2025-03-15T10:00:00Z",
    "assignedTo": {
      "id": "uuid",
      "firstName": "Sara",
      "lastName": "Lindqvist"
    },
    "shift": { "id": "uuid", "date": "2025-03-15" },
    "version": 3,
    "history": [
      {
        "fromStatus": null,
        "toStatus": "PENDING",
        "changedBy": "Dispatcher",
        "changedAt": "2025-03-15T06:00:00Z",
        "note": null
      },
      {
        "fromStatus": "PENDING",
        "toStatus": "ASSIGNED",
        "changedBy": "Dispatcher",
        "changedAt": "2025-03-15T06:05:00Z",
        "note": null
      },
      {
        "fromStatus": "ASSIGNED",
        "toStatus": "IN_PROGRESS",
        "changedBy": "Sara Lindqvist",
        "changedAt": "2025-03-15T08:12:00Z",
        "note": null
      }
    ],
    "createdAt": "2025-03-15T06:00:00Z",
    "updatedAt": "2025-03-15T08:12:00Z"
  }
}
```

---

### POST `/tasks`

**Role**: DISPATCHER | ADMIN

**Request**
```json
{
  "title": "Quality check — Södermalm cluster",
  "description": "Three scooters reported with brake issues.",
  "taskTypeCode": "QC_CHECK",
  "vehicleId": "uuid",
  "urgency": 3,
  "lat": 59.3150,
  "lng": 18.0700,
  "addressHint": "Hornsgatan 42",
  "dueBy": "2025-03-15T14:00:00Z",
  "assignedTo": "uuid",
  "shiftId": "uuid"
}
```

**Response 201** — full task object.

On success, the server publishes a WebSocket event to the assigned worker.

---

### PUT `/tasks/{id}`

**Role**: DISPATCHER | ADMIN  
Full update. Must include current `version`.

**Request**
```json
{
  "title": "Updated title",
  "description": "Updated description",
  "urgency": 4,
  "dueBy": "2025-03-15T12:00:00Z",
  "version": 3
}
```

**Response 200** — updated task.  
**Response 409** — version mismatch (optimistic lock conflict).

---

### PATCH `/tasks/{id}/status`

**Role**: WORKER (own tasks only, restricted transitions) | DISPATCHER (any)

**Allowed transitions**:

| Actor | From | To |
|---|---|---|
| WORKER | ASSIGNED | IN_PROGRESS |
| WORKER | IN_PROGRESS | COMPLETED |
| WORKER | IN_PROGRESS | ISSUE |
| DISPATCHER | PENDING | ASSIGNED |
| DISPATCHER | ASSIGNED | PENDING (unassign) |
| DISPATCHER | ASSIGNED | CANCELLED |
| DISPATCHER | ISSUE | ASSIGNED (reassign) |
| DISPATCHER | any | CANCELLED |

**Request**
```json
{
  "status": "COMPLETED",
  "note": "Battery swapped, vehicle back at full charge",
  "lat": 59.3234,
  "lng": 18.0712,
  "version": 3
}
```

**Response 200** — updated task.  
**Response 409** — version mismatch.  
**Response 422** — invalid transition.

---

### POST `/tasks/bulk`

**Role**: DISPATCHER | ADMIN  
Import up to 200 tasks in one request. Atomic — all succeed or all fail.

**Request**
```json
{
  "shiftId": "uuid",
  "tasks": [
    {
      "title": "Battery swap",
      "taskTypeCode": "BATTERY_SWAP",
      "urgency": 4,
      "lat": 59.3200,
      "lng": 18.0650,
      "addressHint": "Drottninggatan 10"
    }
  ]
}
```

**Response 201**
```json
{
  "data": {
    "created": 15,
    "taskIds": ["uuid", "uuid"]
  }
}
```

---

### DELETE `/tasks/{id}`

**Role**: DISPATCHER | ADMIN  
Soft delete. Sets `deleted_at`. Only allowed if status is `PENDING` or `CANCELLED`.

**Response 204**: No content.

---

### GET `/tasks/{id}/audit`

**Role**: DISPATCHER | ADMIN  
Returns full status history for a task.

---

## WebSocket Contract

**Endpoint**: `wss://api.fieldops.io/ws`  
**Protocol**: STOMP over SockJS  
**Auth**: JWT sent as STOMP CONNECT header `Authorization: Bearer <token>`

### Subscriptions

| Topic | Subscriber | Description |
|---|---|---|
| `/user/queue/tasks` | WORKER | Personal task events (assigned, updated, cancelled) |
| `/topic/dispatch` | DISPATCHER | All task + worker events across the fleet |
| `/user/queue/errors` | ALL | Server-side error notifications |

### Event Payload Structure

```json
{
  "event": "task.assigned",
  "taskId": "uuid",
  "payload": { ...task summary object... },
  "timestamp": "2025-03-15T08:30:00Z"
}
```

### Event Types

| event | Trigger | Published to |
|---|---|---|
| `task.assigned` | Task assigned to worker | `/user/queue/tasks` for that worker |
| `task.updated` | Description/urgency changed | `/user/queue/tasks` if assigned |
| `task.status_changed` | Any status transition | `/topic/dispatch` |
| `task.unassigned` | Dispatcher removes assignment | `/user/queue/tasks` for that worker |
| `task.cancelled` | Dispatcher cancels task | `/user/queue/tasks` if assigned |
| `shift.closed` | Shift ended | `/user/queue/tasks` for that worker |
| `worker.location` | Worker GPS ping | `/topic/dispatch` |

### Client Reconnect Protocol

```
1. WS drops
2. Client attempts reconnect with exponential backoff (1s, 2s, 4s, 8s… max 30s)
3. On reconnect:
   a. Re-send STOMP CONNECT with fresh access token
   b. Re-subscribe to all topics
   c. HTTP GET /tasks?updatedSince={lastSyncTimestamp} to fetch missed changes
   d. Merge diffs into local Pinia/Vue Query cache
   e. Drain any queued offline mutations (IndexedDB) in order
```

---

## Rate Limits

| Endpoint group | Limit |
|---|---|
| `POST /auth/login` | 10 req/min per IP |
| `POST /workers/me/location` | 5 req/min per worker |
| All other endpoints | 100 req/min per worker |

Responses include:  
`X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers.  
`429` response body includes `retryAfterSeconds`.
