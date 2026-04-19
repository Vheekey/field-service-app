package com.example.fieldservice.assignments.persistence;

import com.example.fieldservice.assignments.domain.TaskAssignment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, UUID> {

    Optional<TaskAssignment> findByTaskIdAndUnassignedAtIsNull(UUID taskId);
}
