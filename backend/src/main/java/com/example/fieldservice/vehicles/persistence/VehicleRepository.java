package com.example.fieldservice.vehicles.persistence;

import com.example.fieldservice.vehicles.domain.Vehicle;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
}
