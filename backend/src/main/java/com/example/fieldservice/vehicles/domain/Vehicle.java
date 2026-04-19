package com.example.fieldservice.vehicles.domain;

import com.example.fieldservice.common.domain.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "vehicles")
public class Vehicle extends UuidEntity {

    @Column(nullable = false, unique = true)
    private String externalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleStatus status;

    private Integer batteryLevel;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point lastKnownLocation;

    private Instant lastSeenAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Vehicle() {
    }
}
