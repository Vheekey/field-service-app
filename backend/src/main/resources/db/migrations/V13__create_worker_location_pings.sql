CREATE TABLE worker_location_pings (
    id bigserial PRIMARY KEY,
    worker_id uuid NOT NULL REFERENCES users(id),
    location geography(Point, 4326) NOT NULL,
    accuracy_m integer,
    pinged_at timestamptz NOT NULL
);

CREATE INDEX ix_worker_location_pings_worker_time ON worker_location_pings(worker_id, pinged_at);
