package com.example.fieldservice.workers.api;

import com.example.fieldservice.common.api.GeoPointDto;
import java.time.Instant;
import java.util.UUID;

public record WorkerProfileDto(
        UUID id,
        UUID userId,
        boolean active,
        String homeBaseName,
        GeoPointDto lastKnownLocation,
        Instant lastSeenAt
) {
}
