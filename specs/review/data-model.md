# Reviewed Data Model

## Principles

- Use UUID/ULID public IDs.
- Use `bigserial` `event_id` for durable event ordering.
- Use PostGIS `geography(Point, 4326)` for city/map queries.
- Use `version` for optimistic locking.
- Keep assignment history separate from the current task snapshot.
- Keep immutable task events for audit, sync, and realtime.
- Use soft status changes rather than hard deletes for operational records.

## Core Tables

### `users`

- `id uuid primary key`
- `email text unique not null`
- `password_hash text not null`
- `name text not null`
- `status text not null`
- `roles text[] not null`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

### `refresh_tokens`

- `id uuid primary key`
- `user_id uuid not null references users(id)`
- `token_hash text unique not null`
- `expires_at timestamptz not null`
- `revoked_at timestamptz`
- `created_at timestamptz not null`

### `worker_profiles`

- `id uuid primary key`
- `user_id uuid unique not null references users(id)`
- `active boolean not null`
- `home_base_name text`
- `last_known_location geography(Point, 4326)`
- `last_seen_at timestamptz`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

### `vehicles`

- `id uuid primary key`
- `external_code text unique not null`
- `vehicle_type text not null`
- `status text not null`
- `battery_level integer`
- `last_known_location geography(Point, 4326)`
- `last_seen_at timestamptz`
- `version bigint not null`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

Indexes:

- unique `external_code`
- GiST `last_known_location`
- `status`

### `tasks`

- `id uuid primary key`
- `vehicle_id uuid references vehicles(id)`
- `type text not null`
- `status text not null`
- `priority text not null`
- `title text not null`
- `description text`
- `requirements jsonb`
- `location geography(Point, 4326) not null`
- `address text`
- `due_at timestamptz`
- `assignee_id uuid references users(id)`
- `assigned_at timestamptz`
- `started_at timestamptz`
- `completed_at timestamptz`
- `blocked_at timestamptz`
- `cancelled_at timestamptz`
- `version bigint not null`
- `created_by uuid not null references users(id)`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

Indexes:

- `tasks(status)`
- `tasks(assignee_id)`
- `tasks(assignee_id, status)`
- `tasks(priority, due_at)`
- GiST `tasks(location)`
- partial active-work index on `(priority, due_at)` where status is active

### `task_assignments`

Historical assignment records.

- `id uuid primary key`
- `task_id uuid not null references tasks(id)`
- `worker_id uuid not null references users(id)`
- `assigned_by uuid not null references users(id)`
- `assigned_at timestamptz not null`
- `unassigned_at timestamptz`
- `unassigned_by uuid references users(id)`
- `unassign_reason text`

Indexes:

```sql
CREATE INDEX ix_task_assignments_task
    ON task_assignments(task_id);

CREATE INDEX ix_task_assignments_worker_time
    ON task_assignments(worker_id, assigned_at);

CREATE UNIQUE INDEX ux_task_assignments_active
    ON task_assignments(task_id)
    WHERE unassigned_at IS NULL;
```

### `shifts`

- `id uuid primary key`
- `worker_id uuid not null references users(id)`
- `status text not null`
- `started_at timestamptz not null`
- `ended_at timestamptz`
- `start_location geography(Point, 4326)`
- `end_location geography(Point, 4326)`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

Constraint:

```sql
CREATE UNIQUE INDEX ux_shifts_worker_active
    ON shifts(worker_id)
    WHERE status = 'ACTIVE';
```

### `task_comments`

- `id uuid primary key`
- `task_id uuid not null references tasks(id)`
- `author_id uuid not null references users(id)`
- `body text not null`
- `created_at timestamptz not null`

### `task_events`

Durable event stream for audit, realtime, and sync.

- `event_id bigserial primary key`
- `task_id uuid references tasks(id)`
- `type text not null`
- `actor_id uuid references users(id)`
- `shift_id uuid references shifts(id)`
- `entity_version bigint`
- `payload jsonb not null`
- `occurred_at timestamptz not null`

Indexes:

- `task_events(task_id)`
- `task_events(event_id)`
- `task_events(occurred_at)`

### `idempotency_records`

- `id uuid primary key`
- `idempotency_key text not null`
- `user_id uuid not null references users(id)`
- `command_type text not null`
- `request_hash text not null`
- `response_body jsonb`
- `status_code integer`
- `created_at timestamptz not null`
- `expires_at timestamptz not null`

Constraint:

```sql
CREATE UNIQUE INDEX ux_idempotency_user_key
    ON idempotency_records(user_id, idempotency_key);
```

### `worker_location_pings`

Optional for MVP dispatcher tracking.

- `id bigserial primary key`
- `worker_id uuid not null references users(id)`
- `location geography(Point, 4326) not null`
- `accuracy_m integer`
- `pinged_at timestamptz not null`

Retain for a short window, for example 7-30 days.

## Sync Model

The client stores the latest `task_events.event_id`.

On reconnect:

1. Fetch events after last seen event ID.
2. Apply events in ascending order.
3. Replay pending outbox commands.
4. Refetch active task list.

Do not depend only on `updated_at` timestamps for sync.
