package com.example.fieldservice.assignments.domain;

import com.example.fieldservice.identity.domain.UserAccount;
import com.example.fieldservice.tasks.domain.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_assignments")
public class TaskAssignment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private UserAccount worker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private UserAccount assignedBy;

    @Column(nullable = false)
    private Instant assignedAt;

    private Instant unassignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unassigned_by")
    private UserAccount unassignedBy;

    private String unassignReason;

    protected TaskAssignment() {
    }

    public TaskAssignment(Task task, UserAccount worker, UserAccount assignedBy, Instant assignedAt) {
        this.task = task;
        this.worker = worker;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public void close(UserAccount unassignedBy, String reason, Instant unassignedAt) {
        this.unassignedBy = unassignedBy;
        this.unassignReason = reason;
        this.unassignedAt = unassignedAt;
    }

    public UUID id() {
        return id;
    }

    public Task task() {
        return task;
    }

    public UserAccount worker() {
        return worker;
    }

    public Instant unassignedAt() {
        return unassignedAt;
    }
}
