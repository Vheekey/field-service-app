package com.example.fieldservice.workers.api;

import com.example.fieldservice.common.api.GeoPointDto;
import com.example.fieldservice.common.security.FieldServicePrincipal;
import com.example.fieldservice.tasks.application.TaskApplicationService;
import com.example.fieldservice.tasks.api.TaskDto.TaskListItemResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/workers")
public class WorkersController {

    private final TaskApplicationService tasks;

    public WorkersController(TaskApplicationService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/{workerId}/tasks")
    ResponseEntity<List<TaskListItemResponse>> tasks(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @PathVariable UUID workerId
    ) {
        return ResponseEntity.ok(tasks.assignedTasks(principal, workerId));
    }

    @GetMapping("/{workerId}/route")
    ResponseEntity<List<TaskListItemResponse>> route(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @PathVariable UUID workerId
    ) {
        return ResponseEntity.ok(tasks.assignedTasks(principal, workerId));
    }

    @PostMapping("/me/location")
    ResponseEntity<Void> recordLocation(@Valid @RequestBody LocationPingRequest request) {
        return ResponseEntity.noContent().build();
    }

    public record LocationPingRequest(@Valid @NotNull GeoPointDto location, Integer accuracyM) {
    }
}
