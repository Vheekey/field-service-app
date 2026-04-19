package com.example.fieldservice.workers.domain;

import com.example.fieldservice.identity.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "worker_location_pings")
public class WorkerLocationPing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private UserAccount worker;

    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    private Integer accuracyM;

    @Column(nullable = false)
    private Instant pingedAt;

    protected WorkerLocationPing() {
    }
}
