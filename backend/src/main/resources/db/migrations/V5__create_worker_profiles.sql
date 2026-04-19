CREATE TABLE worker_profiles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid UNIQUE NOT NULL REFERENCES users(id),
    active boolean NOT NULL,
    home_base_name text,
    last_known_location geography(Point, 4326),
    last_seen_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
