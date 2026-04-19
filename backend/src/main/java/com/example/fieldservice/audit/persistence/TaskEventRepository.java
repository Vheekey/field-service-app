package com.example.fieldservice.audit.persistence;

import com.example.fieldservice.audit.domain.TaskEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {

    List<TaskEvent> findByEventIdGreaterThanOrderByEventIdAsc(long eventId);

    List<TaskEvent> findByTaskIdOrderByEventIdAsc(UUID taskId);
}
