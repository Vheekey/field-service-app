package com.example.fieldservice.shifts.persistence;

import com.example.fieldservice.shifts.domain.Shift;
import com.example.fieldservice.shifts.domain.ShiftStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {
    Optional<Shift> findFirstByWorkerIdAndStatusOrderByStartedAtDesc(UUID workerId, ShiftStatus status);
}
