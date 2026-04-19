package com.example.fieldservice.tasks.domain;

import com.example.fieldservice.common.domain.UuidEntity;
import com.example.fieldservice.identity.domain.UserAccount;
import com.example.fieldservice.vehicles.domain.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "tasks")
public class Task extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    @Column(nullable = false)
    private String title;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> requirements;

    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    private String address;
    private Instant dueAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private UserAccount assignee;

    private Instant assignedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant blockedAt;
    private Instant cancelledAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserAccount createdBy;

    protected Task() {
    }

    public Task(
            Vehicle vehicle,
            TaskType type,
            TaskPriority priority,
            String title,
            String description,
            Map<String, Object> requirements,
            Point location,
            String address,
            Instant dueAt,
            UserAccount createdBy
    ) {
        this.vehicle = vehicle;
        this.type = type;
        this.status = TaskStatus.OPEN;
        this.priority = priority;
        this.title = title;
        this.description = description;
        this.requirements = requirements;
        this.location = location;
        this.address = address;
        this.dueAt = dueAt;
        this.createdBy = createdBy;
    }

    public Vehicle vehicle() {
        return vehicle;
    }

    public TaskType type() {
        return type;
    }

    public TaskStatus status() {
        return status;
    }

    public TaskPriority priority() {
        return priority;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public Map<String, Object> requirements() {
        return requirements;
    }

    public Point location() {
        return location;
    }

    public String address() {
        return address;
    }

    public Instant dueAt() {
        return dueAt;
    }

    public UserAccount assignee() {
        return assignee;
    }

    public Instant assignedAt() {
        return assignedAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public Instant blockedAt() {
        return blockedAt;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }

    public long version() {
        return version == null ? 0L : version;
    }

    public UserAccount createdBy() {
        return createdBy;
    }

    public void updateDetails(
            TaskPriority priority,
            String title,
            String description,
            Map<String, Object> requirements,
            Point location,
            String address,
            Instant dueAt
    ) {
        if (priority != null) {
            this.priority = priority;
        }
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (requirements != null) {
            this.requirements = requirements;
        }
        if (location != null) {
            this.location = location;
        }
        if (address != null) {
            this.address = address;
        }
        if (dueAt != null) {
            this.dueAt = dueAt;
        }
    }

    public void assignTo(UserAccount worker, Instant assignedAt) {
        this.assignee = worker;
        this.assignedAt = assignedAt;
        this.status = TaskStatus.ASSIGNED;
        this.startedAt = null;
        this.blockedAt = null;
    }

    public void unassign(Instant unassignedAt) {
        this.assignee = null;
        this.assignedAt = null;
        this.status = TaskStatus.OPEN;
        this.startedAt = null;
        this.blockedAt = null;
    }

    public void start(Instant startedAt) {
        this.status = TaskStatus.IN_PROGRESS;
        this.startedAt = startedAt;
        this.blockedAt = null;
    }

    public void block(Instant blockedAt) {
        this.status = TaskStatus.BLOCKED;
        this.blockedAt = blockedAt;
    }

    public void complete(Instant completedAt) {
        this.status = TaskStatus.COMPLETED;
        this.completedAt = completedAt;
    }
}
