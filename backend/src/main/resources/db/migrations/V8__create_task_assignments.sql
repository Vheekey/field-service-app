CREATE TABLE task_assignments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id uuid NOT NULL REFERENCES tasks(id),
    worker_id uuid NOT NULL REFERENCES users(id),
    assigned_by uuid NOT NULL REFERENCES users(id),
    assigned_at timestamptz NOT NULL,
    unassigned_at timestamptz,
    unassigned_by uuid REFERENCES users(id),
    unassign_reason text
);

CREATE INDEX ix_task_assignments_task ON task_assignments(task_id);
CREATE INDEX ix_task_assignments_worker_time ON task_assignments(worker_id, assigned_at);
CREATE UNIQUE INDEX ux_task_assignments_active ON task_assignments(task_id)
    WHERE unassigned_at IS NULL;
