CREATE TABLE task_events (
    event_id bigserial PRIMARY KEY,
    task_id uuid REFERENCES tasks(id),
    type text NOT NULL,
    actor_id uuid REFERENCES users(id),
    shift_id uuid REFERENCES shifts(id),
    entity_version bigint,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL
);

CREATE INDEX ix_task_events_task ON task_events(task_id);
CREATE INDEX ix_task_events_occurred_at ON task_events(occurred_at);
