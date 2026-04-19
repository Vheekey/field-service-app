package com.example.fieldservice.tasks.persistence;

import com.example.fieldservice.tasks.domain.TaskComment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {
}
