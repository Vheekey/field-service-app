package com.example.fieldservice.shifts.api;

import com.example.fieldservice.common.api.GeoPointDto;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;

public final class ShiftDto {

    private ShiftDto() {
    }

    public record StartShiftRequest(@Valid GeoPointDto location) {
    }

    public record EndShiftRequest(@Valid GeoPointDto location) {
    }

    public record ShiftResponse(UUID id, UUID workerId, String status, Instant startedAt, Instant endedAt) {
    }
}
