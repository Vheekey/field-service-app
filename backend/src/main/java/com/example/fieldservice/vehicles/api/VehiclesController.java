package com.example.fieldservice.vehicles.api;

import com.example.fieldservice.vehicles.api.VehicleDto.UpdateVehicleRequest;
import com.example.fieldservice.vehicles.api.VehicleDto.VehicleResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/vehicles")
public class VehiclesController {

    @GetMapping("/{vehicleId}")
    ResponseEntity<VehicleResponse> get(@PathVariable UUID vehicleId) {
        return ResponseEntity.status(501).build();
    }

    @PatchMapping("/{vehicleId}")
    ResponseEntity<VehicleResponse> update(
            @PathVariable UUID vehicleId,
            @RequestHeader("If-Match") long expectedVersion,
            @Valid @RequestBody UpdateVehicleRequest request
    ) {
        return ResponseEntity.status(501).build();
    }
}
