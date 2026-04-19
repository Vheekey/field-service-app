CREATE TABLE shifts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id uuid NOT NULL REFERENCES users(id),
    status text NOT NULL,
    started_at timestamptz NOT NULL,
    ended_at timestamptz,
    start_location geography(Point, 4326),
    end_location geography(Point, 4326),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX ux_shifts_worker_active ON shifts(worker_id)
    WHERE status = 'ACTIVE';
