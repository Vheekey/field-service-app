package com.example.fieldservice.shifts.api;

import com.example.fieldservice.common.api.GeoPointDto;
import java.time.Instant;
import java.util.UUID;

public final class ShiftDto {

    private ShiftDto() {
    }

    public record StartShiftRequest(GeoPointDto location) {
    }

    public record EndShiftRequest(GeoPointDto location) {
    }

    public record ShiftResponse(UUID id, UUID workerId, String status, Instant startedAt, Instant endedAt) {
    }
}
