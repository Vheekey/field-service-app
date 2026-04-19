package com.example.fieldservice.shifts.domain;

import com.example.fieldservice.common.domain.UuidEntity;
import com.example.fieldservice.identity.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "shifts")
public class Shift extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private UserAccount worker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftStatus status;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant endedAt;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point startLocation;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point endLocation;

    protected Shift() {
    }

    public Shift(UserAccount worker, Instant startedAt, Point startLocation) {
        this.worker = worker;
        this.status = ShiftStatus.ACTIVE;
        this.startedAt = startedAt;
        this.startLocation = startLocation;
    }

    public UserAccount worker() {
        return worker;
    }

    public ShiftStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public Point startLocation() {
        return startLocation;
    }

    public Point endLocation() {
        return endLocation;
    }

    public void end(Instant endedAt, Point endLocation) {
        this.status = ShiftStatus.ENDED;
        this.endedAt = endedAt;
        this.endLocation = endLocation;
    }
}
