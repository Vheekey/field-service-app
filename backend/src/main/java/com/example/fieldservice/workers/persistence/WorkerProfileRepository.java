package com.example.fieldservice.workers.persistence;

import com.example.fieldservice.workers.domain.WorkerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerProfileRepository extends JpaRepository<WorkerProfile, UUID> {

    Optional<WorkerProfile> findByUserId(UUID userId);
}
