# Data Model — FieldOps

> PostgreSQL schema. All tables include `created_at` and `updated_at` (managed by Spring Data Auditing).
> Soft deletes via `deleted_at` — no hard deletes on operational data.

---

## Entity Relationship Diagram (text)

```
Worker ─────────< Shift >─────────< ShiftTask >───── Task
                                                       │
Vehicle ────────────────────────────────────────────── ┘
TaskType ──────────────────────────────────────────────┘
                                                       │
                                             TaskStatusHistory
```

---

## Tables

### `workers`

Represents a field worker or dispatcher.

```sql
CREATE TABLE workers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id   VARCHAR(64) UNIQUE,                  -- HR system reference
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20) NOT NULL                  -- WORKER | DISPATCHER | ADMIN
                  CHECK (role IN ('WORKER','DISPATCHER','ADMIN')),
    phone         VARCHAR(30),
    avatar_url    VARCHAR(500),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at  TIMESTAMPTZ,
    last_lat      DECIMAL(9,6),
    last_lng      DECIMAL(9,6),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ
);

CREATE INDEX idx_workers_email ON workers(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_workers_role  ON workers(role)  WHERE deleted_at IS NULL;
```

---

### `refresh_tokens`

```sql
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id   UUID NOT NULL REFERENCES workers(id),
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_worker ON refresh_tokens(worker_id);
```

---

### `task_types`

Enumeration of what kind of work can be done.

```sql
CREATE TABLE task_types (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(50) NOT NULL UNIQUE,   -- BATTERY_SWAP | QC_CHECK | RELOCATE | RETRIEVE
    label       VARCHAR(100) NOT NULL,
    description TEXT,
    icon_name   VARCHAR(50),                   -- maps to frontend icon set
    avg_duration_minutes INT,                  -- used for shift planning estimates
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO task_types (code, label, avg_duration_minutes) VALUES
    ('BATTERY_SWAP', 'Battery Swap',      15),
    ('QC_CHECK',     'Quality Check',     10),
    ('RELOCATE',     'Relocate Vehicle',  20),
    ('RETRIEVE',     'Retrieve to Depot', 30);
```

---

### `vehicles`

```sql
CREATE TABLE vehicles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id     VARCHAR(64) UNIQUE,          -- fleet management system ID
    plate           VARCHAR(30),
    type            VARCHAR(50),                 -- SCOOTER | EBIKE | CARGO_BIKE
    model           VARCHAR(100),
    battery_level   INT CHECK (battery_level BETWEEN 0 AND 100),
    lat             DECIMAL(9,6),
    lng             DECIMAL(9,6),
    address_hint    VARCHAR(255),                -- human readable last known location
    status          VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE'
                    CHECK (status IN ('AVAILABLE','IN_TASK','AT_DEPOT','OUT_OF_SERVICE')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_vehicles_status ON vehicles(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_vehicles_location ON vehicles USING gist (
    ll_to_earth(lat::float8, lng::float8)        -- requires earthdistance + cube extension
) WHERE deleted_at IS NULL;
```

---

### `shifts`

A worker's working day.

```sql
CREATE TABLE shifts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id       UUID NOT NULL REFERENCES workers(id),
    date            DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','ACTIVE','COMPLETED','ABANDONED')),
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    notes           TEXT,
    created_by      UUID NOT NULL REFERENCES workers(id),  -- dispatcher who created it
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_shifts_worker_date
    ON shifts(worker_id, date)
    WHERE status NOT IN ('COMPLETED','ABANDONED');     -- one active shift per worker per day

CREATE INDEX idx_shifts_worker ON shifts(worker_id);
CREATE INDEX idx_shifts_date   ON shifts(date);
```

---

### `tasks`

The core operational entity.

```sql
CREATE TABLE tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_type_id    INT NOT NULL REFERENCES task_types(id),
    vehicle_id      UUID REFERENCES vehicles(id),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    urgency         SMALLINT NOT NULL DEFAULT 2
                    CHECK (urgency BETWEEN 1 AND 5),      -- 1=low 5=critical
    lat             DECIMAL(9,6) NOT NULL,
    lng             DECIMAL(9,6) NOT NULL,
    address_hint    VARCHAR(255),
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN (
                        'PENDING',       -- created, unassigned
                        'ASSIGNED',      -- assigned to a worker
                        'IN_PROGRESS',   -- worker has started
                        'COMPLETED',     -- done
                        'ISSUE',         -- worker flagged a problem
                        'CANCELLED'      -- dispatcher cancelled
                    )),
    assigned_to     UUID REFERENCES workers(id),
    shift_id        UUID REFERENCES shifts(id),
    due_by          TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,             -- optimistic locking
    created_by      UUID NOT NULL REFERENCES workers(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

-- Prevents two workers being assigned the same task simultaneously
CREATE UNIQUE INDEX idx_task_single_assignment
    ON tasks(id)
    WHERE status = 'ASSIGNED' OR status = 'IN_PROGRESS';

-- Common query patterns
CREATE INDEX idx_tasks_assigned_to ON tasks(assigned_to) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_shift       ON tasks(shift_id)     WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_status      ON tasks(status)       WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_urgency     ON tasks(urgency DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_location    ON tasks USING gist (
    ll_to_earth(lat::float8, lng::float8)
) WHERE deleted_at IS NULL;
```

**Optimistic Concurrency Control**: The `version` column is managed by `@Version` in JPA. Any `UPDATE tasks SET ... WHERE id = ? AND version = ?` that matches 0 rows causes a `ObjectOptimisticLockingFailureException`, translated to HTTP `409 Conflict`.

---

### `task_status_history`

Immutable audit log. Never updated, never deleted.

```sql
CREATE TABLE task_status_history (
    id              BIGSERIAL PRIMARY KEY,
    task_id         UUID NOT NULL REFERENCES tasks(id),
    from_status     VARCHAR(30),
    to_status       VARCHAR(30) NOT NULL,
    changed_by      UUID NOT NULL REFERENCES workers(id),
    note            TEXT,                              -- worker's optional comment
    lat             DECIMAL(9,6),                      -- worker GPS at moment of change
    lng             DECIMAL(9,6),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_task_history_task ON task_status_history(task_id);
```

---

### `worker_location_pings`

Lightweight GPS breadcrumb trail. Kept for 30 days then purged by a scheduled job.

```sql
CREATE TABLE worker_location_pings (
    id          BIGSERIAL PRIMARY KEY,
    worker_id   UUID NOT NULL REFERENCES workers(id),
    lat         DECIMAL(9,6) NOT NULL,
    lng         DECIMAL(9,6) NOT NULL,
    accuracy_m  SMALLINT,
    pinged_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (pinged_at);    -- monthly partitions

CREATE INDEX idx_loc_pings_worker ON worker_location_pings(worker_id, pinged_at DESC);
```

---

## Flyway Migration Order

```
V1__create_extensions.sql          -- uuid-ossp, cube, earthdistance
V2__create_workers.sql
V3__create_refresh_tokens.sql
V4__create_task_types.sql
V5__create_vehicles.sql
V6__create_shifts.sql
V7__create_tasks.sql
V8__create_task_status_history.sql
V9__create_worker_location_pings.sql
V10__seed_task_types.sql
```

---

## JPA Entity Notes

- All entities extend `BaseEntity` with `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, `@LastModifiedBy`
- `Task` has `@Version Long version` for OCC
- `Task.status` is modelled as a Java `enum TaskStatus` with `@Enumerated(EnumType.STRING)`
- Repositories use `@QueryHints` with `HINT_FETCHGRAPH` for controlled eager loading on detail fetches
- No `FetchType.EAGER` anywhere — all joins are explicit in JPQL or `@EntityGraph`
