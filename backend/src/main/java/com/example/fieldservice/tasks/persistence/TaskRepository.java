package com.example.fieldservice.tasks.persistence;

import com.example.fieldservice.tasks.domain.Task;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Task> findWithLockById(UUID id);

    List<Task> findByAssigneeIdAndCompletedAtIsNullOrderByDueAtAsc(UUID assigneeId);
}
