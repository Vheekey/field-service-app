CREATE TABLE idempotency_records (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key text NOT NULL,
    user_id uuid NOT NULL REFERENCES users(id),
    command_type text NOT NULL,
    request_hash text NOT NULL,
    response_body jsonb,
    status_code integer,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX ux_idempotency_user_key ON idempotency_records(user_id, idempotency_key);
