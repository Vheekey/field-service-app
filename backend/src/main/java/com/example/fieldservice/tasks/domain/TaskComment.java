package com.example.fieldservice.tasks.domain;

import com.example.fieldservice.identity.domain.UserAccount;
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
@Table(name = "task_comments")
public class TaskComment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserAccount author;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskComment() {
    }

    public TaskComment(Task task, UserAccount author, String body, Instant createdAt) {
        this.task = task;
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public Task task() {
        return task;
    }

    public UserAccount author() {
        return author;
    }

    public String body() {
        return body;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
