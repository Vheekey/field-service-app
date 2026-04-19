package com.example.fieldservice.common.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record GeoPointDto(
        @JsonAlias("lat") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @JsonAlias("lng") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
) {
}
