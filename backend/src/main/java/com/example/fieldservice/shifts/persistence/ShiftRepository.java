package com.example.fieldservice.shifts.persistence;

import com.example.fieldservice.shifts.domain.Shift;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {
}
