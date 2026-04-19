package com.example.fieldservice.audit.domain;

import com.example.fieldservice.identity.domain.UserAccount;
import com.example.fieldservice.shifts.domain.Shift;
import com.example.fieldservice.tasks.domain.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "task_events")
public class TaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskEventType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private UserAccount actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;

    private Long entityVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(nullable = false)
    private Instant occurredAt;

    protected TaskEvent() {
    }

    public TaskEvent(
            Task task,
            TaskEventType type,
            UserAccount actor,
            Long entityVersion,
            Map<String, Object> payload,
            Instant occurredAt
    ) {
        this.task = task;
        this.type = type;
        this.actor = actor;
        this.entityVersion = entityVersion;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public Long eventId() {
        return eventId;
    }

    public Task task() {
        return task;
    }

    public TaskEventType type() {
        return type;
    }

    public UserAccount actor() {
        return actor;
    }

    public Long entityVersion() {
        return entityVersion;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
