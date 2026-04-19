# Reviewed Security and Performance

## Security

### Authentication

- Short-lived JWT access token.
- Secure HttpOnly SameSite refresh token cookie.
- Store refresh token hashes server-side.
- Rotate refresh tokens on refresh.
- Revoke refresh tokens on logout.
- Hash passwords with Argon2id or BCrypt.

### Authorization

Roles:

- `FIELD_WORKER`
- `DISPATCHER`
- `ADMIN`

Rules:

- Workers only see and mutate assigned tasks.
- Dispatchers manage tasks/workers in their operating city or tenant.
- Admins manage users and configuration.
- WebSocket subscriptions are authorized, not only authenticated.

### API Safety

- Bean Validation on requests.
- Strict enum validation.
- Request body size limits.
- CORS allowlist.
- No wildcard WebSocket origins in production.
- Structured errors without stack traces.
- Rate limits on auth, location, sync replay, and write endpoints.
- Request IDs in logs and responses.

## Performance

### Backend Targets

- Worker task list p95 under 200 ms for 100 assigned tasks.
- Dispatcher task list p95 under 300 ms for 1,000 active city tasks.
- Map viewport query p95 under 400 ms.
- Task command p95 under 250 ms excluding media upload.
- Sync of 500 events under 1 second.
- Realtime event visible to online clients under 2 seconds.

### Backend Practices

- Paginate all list endpoints.
- Use viewport bounds for dense map queries.
- Use PostGIS indexes.
- Use DTO projections for lists.
- Use detail DTOs separately.
- Avoid N+1 with explicit fetch plans.
- Set `open-in-view=false`.
- Prefer database indexes and query optimization before Redis caching.
- Use Redis later for pub/sub fanout and rate limiting if needed.

### Frontend Practices

- Vue Query for server-state cache.
- Targeted invalidation on realtime events.
- Map marker clustering.
- Debounced viewport queries.
- IndexedDB for offline cache and outbox.
- Virtualize long lists only when needed.
- Refetch on reconnect after event sync.

## Realtime and Offline Reliability

Required:

- durable event stream
- event ID in every realtime event
- event-ID sync on reconnect
- idempotent offline commands
- per-command outbox status
- conflict preservation of locally captured worker data

Avoid:

- timestamp-only sync
- deleting the whole offline queue after partial success
- publishing realtime events before commit
- using sockets as durable delivery
