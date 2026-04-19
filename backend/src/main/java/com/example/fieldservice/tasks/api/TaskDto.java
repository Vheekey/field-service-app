package com.example.fieldservice.tasks.api;

import com.example.fieldservice.common.api.GeoPointDto;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class TaskDto {

    private TaskDto() {
    }

    public record TaskListItemResponse(
            UUID id,
            String type,
            String status,
            String priority,
            String title,
            GeoPointDto location,
            String address,
            Instant dueAt,
            UUID assigneeId,
            long version
    ) {
    }

    public record TaskDetailResponse(
            UUID id,
            UUID vehicleId,
            String type,
            String status,
            String priority,
            String title,
            String description,
            Map<String, Object> requirements,
            GeoPointDto location,
            String address,
            Instant dueAt,
            UUID assigneeId,
            Instant assignedAt,
            Instant startedAt,
            Instant completedAt,
            Instant blockedAt,
            Instant cancelledAt,
            long version
    ) {
    }

    public record CreateTaskRequest(
            UUID vehicleId,
            @NotBlank String type,
            @NotBlank String priority,
            @NotBlank String title,
            String description,
            Map<String, Object> requirements,
            @Valid @NotNull GeoPointDto location,
            String address,
            Instant dueAt
    ) {
    }

    public record UpdateTaskRequest(
            String priority,
            String title,
            String description,
            Map<String, Object> requirements,
            @Valid GeoPointDto location,
            String address,
            Instant dueAt
    ) {
    }

    public record AssignTaskRequest(@JsonAlias("assigneeId") @NotNull UUID workerId) {
    }

    public record UnassignTaskRequest(String reason) {
    }

    public record CompleteTaskRequest(String completionNotes) {
    }

    public record BlockTaskRequest(@NotBlank String reason) {
    }

    public record AddCommentRequest(@NotBlank String body) {
    }

    public record TaskCommentResponse(UUID id, UUID authorId, String body, Instant createdAt) {
    }
}
