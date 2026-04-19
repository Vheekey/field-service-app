package com.example.fieldservice.workers.domain;

import com.example.fieldservice.common.domain.UuidEntity;
import com.example.fieldservice.identity.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "worker_profiles")
public class WorkerProfile extends UuidEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount user;

    @Column(nullable = false)
    private boolean active;

    private String homeBaseName;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point lastKnownLocation;

    private Instant lastSeenAt;

    protected WorkerProfile() {
    }

    public UUID id() {
        return super.id();
    }

    public UserAccount user() {
        return user;
    }

    public boolean active() {
        return active;
    }

    public String homeBaseName() {
        return homeBaseName;
    }

    public Point lastKnownLocation() {
        return lastKnownLocation;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }
}
