CREATE TABLE vehicles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    external_code text UNIQUE NOT NULL,
    vehicle_type text NOT NULL,
    status text NOT NULL,
    battery_level integer,
    last_known_location geography(Point, 4326),
    last_seen_at timestamptz,
    version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX ix_vehicles_status ON vehicles(status);
CREATE INDEX ix_vehicles_last_known_location ON vehicles USING gist(last_known_location);
