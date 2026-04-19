CREATE TABLE tasks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id uuid REFERENCES vehicles(id),
    type text NOT NULL,
    status text NOT NULL,
    priority text NOT NULL,
    title text NOT NULL,
    description text,
    requirements jsonb,
    location geography(Point, 4326) NOT NULL,
    address text,
    due_at timestamptz,
    assignee_id uuid REFERENCES users(id),
    assigned_at timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    blocked_at timestamptz,
    cancelled_at timestamptz,
    version bigint NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX ix_tasks_status ON tasks(status);
CREATE INDEX ix_tasks_assignee ON tasks(assignee_id);
CREATE INDEX ix_tasks_assignee_status ON tasks(assignee_id, status);
CREATE INDEX ix_tasks_priority_due_at ON tasks(priority, due_at);
CREATE INDEX ix_tasks_location ON tasks USING gist(location);
CREATE INDEX ix_tasks_active_work ON tasks(priority, due_at)
    WHERE status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'BLOCKED');
