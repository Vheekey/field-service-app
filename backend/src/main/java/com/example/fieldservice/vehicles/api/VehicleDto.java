package com.example.fieldservice.vehicles.api;

import com.example.fieldservice.common.api.GeoPointDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;

public final class VehicleDto {

    private VehicleDto() {
    }

    public record VehicleResponse(
            UUID id,
            String externalCode,
            String vehicleType,
            String status,
            Integer batteryLevel,
            GeoPointDto lastKnownLocation,
            Instant lastSeenAt,
            long version
    ) {
    }

    public record UpdateVehicleRequest(String status, @Min(0) @Max(100) Integer batteryLevel, GeoPointDto location) {
    }
}
