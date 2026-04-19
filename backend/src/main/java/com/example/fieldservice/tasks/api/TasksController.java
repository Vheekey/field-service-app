package com.example.fieldservice.tasks.api;

import com.example.fieldservice.common.api.PageResponse;
import com.example.fieldservice.tasks.api.TaskDto.AddCommentRequest;
import com.example.fieldservice.tasks.api.TaskDto.AssignTaskRequest;
import com.example.fieldservice.tasks.api.TaskDto.BlockTaskRequest;
import com.example.fieldservice.tasks.api.TaskDto.CompleteTaskRequest;
import com.example.fieldservice.tasks.api.TaskDto.CreateTaskRequest;
import com.example.fieldservice.tasks.api.TaskDto.TaskCommentResponse;
import com.example.fieldservice.tasks.api.TaskDto.TaskDetailResponse;
import com.example.fieldservice.tasks.api.TaskDto.TaskListItemResponse;
import com.example.fieldservice.tasks.api.TaskDto.UnassignTaskRequest;
import com.example.fieldservice.tasks.api.TaskDto.UpdateTaskRequest;
import com.example.fieldservice.audit.api.EventEnvelopeDto;
import com.example.fieldservice.common.security.FieldServicePrincipal;
import com.example.fieldservice.tasks.application.TaskApplicationService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/tasks")
public class TasksController {

    private final TaskApplicationService tasks;

    public TasksController(TaskApplicationService tasks) {
        this.tasks = tasks;
    }

    @PostMapping
    ResponseEntity<TaskDetailResponse> create(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        TaskDetailResponse response = tasks.create(principal, request);
        return ResponseEntity.status(201)
                .eTag(etag(response.version()))
                .body(response);
    }

    @GetMapping
    ResponseEntity<PageResponse<TaskListItemResponse>> list(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant dueBefore,
            @RequestParam(required = false) String bbox,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "dueAt,asc") String sort
    ) {
        return ResponseEntity.ok(tasks.list(principal, status, assigneeId, priority, type, dueBefore, page, size, sort));
    }

    @GetMapping("/{taskId}")
    ResponseEntity<TaskDetailResponse> get(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @PathVariable UUID taskId
    ) {
        TaskDetailResponse response = tasks.get(principal, taskId);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @PatchMapping("/{taskId}")
    ResponseEntity<TaskDetailResponse> update(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @PathVariable UUID taskId,
            @RequestHeader("If-Match") long expectedVersion,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        TaskDetailResponse response = tasks.update(principal, taskId, expectedVersion, request);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @PostMapping("/{taskId}/assign")
    ResponseEntity<TaskDetailResponse> assign(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @PathVariable UUID taskId,
            @Valid @RequestBody AssignTaskRequest request
    ) {
        TaskDetailResponse response = tasks.assign(principal, taskId, request);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @PostMapping("/{taskId}/unassign")
    ResponseEntity<TaskDetailResponse> unassign(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @PathVariable UUID taskId,
            @Valid @RequestBody UnassignTaskRequest request
    ) {
        TaskDetailResponse response = tasks.unassign(principal, taskId, request);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @PostMapping("/{taskId}/start")
    ResponseEntity<TaskDetailResponse> start(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        TaskDetailResponse response = tasks.start(principal, taskId);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @PostMapping("/{taskId}/complete")
    ResponseEntity<TaskDetailResponse> complete(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CompleteTaskRequest request
    ) {
        TaskDetailResponse response = tasks.complete(principal, taskId, request);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @PostMapping("/{taskId}/block")
    ResponseEntity<TaskDetailResponse> block(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BlockTaskRequest request
    ) {
        TaskDetailResponse response = tasks.block(principal, taskId, request);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @PostMapping("/{taskId}/comments")
    ResponseEntity<TaskCommentResponse> comment(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AddCommentRequest request
    ) {
        return ResponseEntity.ok(tasks.comment(principal, taskId, request));
    }

    @GetMapping("/{taskId}/events")
    ResponseEntity<List<EventEnvelopeDto>> events(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @PathVariable UUID taskId
    ) {
        return ResponseEntity.ok(tasks.events(principal, taskId));
    }

    private String etag(long version) {
        return "\"" + version + "\"";
    }
}
