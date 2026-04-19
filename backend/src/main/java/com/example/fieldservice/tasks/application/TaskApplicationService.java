package com.example.fieldservice.tasks.application;

import com.example.fieldservice.audit.api.EventEnvelopeDto;
import com.example.fieldservice.audit.domain.TaskEvent;
import com.example.fieldservice.audit.domain.TaskEventType;
import com.example.fieldservice.audit.persistence.TaskEventRepository;
import com.example.fieldservice.assignments.domain.TaskAssignment;
import com.example.fieldservice.assignments.persistence.TaskAssignmentRepository;
import com.example.fieldservice.common.api.GeoPointDto;
import com.example.fieldservice.common.api.PageResponse;
import com.example.fieldservice.common.errors.ApiException;
import com.example.fieldservice.common.security.FieldServicePrincipal;
import com.example.fieldservice.identity.domain.Role;
import com.example.fieldservice.identity.domain.UserAccount;
import com.example.fieldservice.identity.persistence.UserAccountRepository;
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
import com.example.fieldservice.tasks.domain.Task;
import com.example.fieldservice.tasks.domain.TaskComment;
import com.example.fieldservice.tasks.domain.TaskPriority;
import com.example.fieldservice.tasks.domain.TaskStatus;
import com.example.fieldservice.tasks.domain.TaskType;
import com.example.fieldservice.tasks.persistence.TaskCommentRepository;
import com.example.fieldservice.tasks.persistence.TaskRepository;
import com.example.fieldservice.vehicles.domain.Vehicle;
import com.example.fieldservice.vehicles.persistence.VehicleRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskApplicationService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    private final TaskAssignmentRepository assignments;
    private final TaskEventRepository events;
    private final UserAccountRepository users;
    private final VehicleRepository vehicles;

    public TaskApplicationService(
            TaskRepository tasks,
            TaskCommentRepository comments,
            TaskAssignmentRepository assignments,
            TaskEventRepository events,
            UserAccountRepository users,
            VehicleRepository vehicles
    ) {
        this.tasks = tasks;
        this.comments = comments;
        this.assignments = assignments;
        this.events = events;
        this.users = users;
        this.vehicles = vehicles;
    }

    @Transactional
    public TaskDetailResponse create(FieldServicePrincipal principal, CreateTaskRequest request) {
        UserAccount actor = requireUser(principal.id());
        Vehicle vehicle = request.vehicleId() == null
                ? null
                : vehicles.findById(request.vehicleId()).orElseThrow(() -> notFound("Vehicle not found."));

        Task task = new Task(
                vehicle,
                parseEnum(TaskType.class, request.type(), "type"),
                parseEnum(TaskPriority.class, request.priority(), "priority"),
                request.title(),
                request.description(),
                request.requirements(),
                point(request.location()),
                request.address(),
                request.dueAt(),
                actor
        );
        tasks.saveAndFlush(task);
        recordEvent(task, TaskEventType.TASK_CREATED, actor, Map.of("title", task.title()));
        return detail(task);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskListItemResponse> list(
            FieldServicePrincipal principal,
            String status,
            UUID assigneeId,
            String priority,
            String type,
            Instant dueBefore,
            int page,
            int size,
            String sort
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), parseSort(sort));
        Specification<Task> spec = filter(status, assigneeId, priority, type, dueBefore);
        if (isWorker(principal)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("assignee").get("id"), principal.id()));
        }
        var result = tasks.findAll(spec, pageable).map(this::listItem);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse get(FieldServicePrincipal principal, UUID taskId) {
        Task task = visibleTask(principal, taskId);
        return detail(task);
    }

    @Transactional
    public TaskDetailResponse update(FieldServicePrincipal principal, UUID taskId, long expectedVersion, UpdateTaskRequest request) {
        UserAccount actor = requireUser(principal.id());
        Task task = tasks.findWithLockById(taskId).orElseThrow(() -> notFound("Task not found."));
        assertVersion(task, expectedVersion);
        task.updateDetails(
                request.priority() == null ? null : parseEnum(TaskPriority.class, request.priority(), "priority"),
                request.title(),
                request.description(),
                request.requirements(),
                request.location() == null ? null : point(request.location()),
                request.address(),
                request.dueAt()
        );
        tasks.saveAndFlush(task);
        recordEvent(task, TaskEventType.TASK_UPDATED, actor, Map.of("expectedVersion", expectedVersion));
        return detail(task);
    }

    @Transactional
    public TaskDetailResponse assign(FieldServicePrincipal principal, UUID taskId, AssignTaskRequest request) {
        UserAccount actor = requireUser(principal.id());
        UserAccount worker = requireUser(request.workerId());
        if (!worker.roles().contains(Role.FIELD_WORKER)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TASK_ASSIGNEE_INVALID", "Assignee must be a field worker.");
        }

        Task task = tasks.findWithLockById(taskId).orElseThrow(() -> notFound("Task not found."));
        if (!List.of(TaskStatus.OPEN, TaskStatus.ASSIGNED, TaskStatus.BLOCKED).contains(task.status())) {
            throw invalidState("Task cannot be assigned from status " + task.status() + ".");
        }
        if (task.assignee() != null) {
            if (task.assignee().id().equals(worker.id())) {
                return detail(task);
            }
            throw new ApiException(HttpStatus.CONFLICT, "TASK_ALREADY_ASSIGNED", "Task is already assigned to another worker.");
        }

        Instant now = Instant.now();
        task.assignTo(worker, now);
        assignments.save(new TaskAssignment(task, worker, actor, now));
        tasks.saveAndFlush(task);
        recordEvent(task, TaskEventType.TASK_ASSIGNED, actor, Map.of("assigneeId", worker.id().toString()));
        return detail(task);
    }

    @Transactional
    public TaskDetailResponse unassign(FieldServicePrincipal principal, UUID taskId, UnassignTaskRequest request) {
        UserAccount actor = requireUser(principal.id());
        Task task = tasks.findWithLockById(taskId).orElseThrow(() -> notFound("Task not found."));
        if (TaskStatus.COMPLETED.equals(task.status())) {
            throw invalidState("Completed tasks cannot be unassigned.");
        }
        if (TaskStatus.IN_PROGRESS.equals(task.status()) && isBlank(request.reason())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TASK_UNASSIGN_REASON_REQUIRED", "A reason is required to unassign an in-progress task.");
        }
        if (task.assignee() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "TASK_NOT_ASSIGNED", "Task is not assigned.");
        }

        Instant now = Instant.now();
        assignments.findByTaskIdAndUnassignedAtIsNull(task.id())
                .ifPresent(assignment -> assignment.close(actor, request.reason(), now));
        task.unassign(now);
        tasks.saveAndFlush(task);
        recordEvent(task, TaskEventType.TASK_UNASSIGNED, actor, Map.of("reason", request.reason() == null ? "" : request.reason()));
        return detail(task);
    }

    @Transactional
    public TaskDetailResponse start(FieldServicePrincipal principal, UUID taskId) {
        UserAccount actor = requireUser(principal.id());
        Task task = assignedTaskForUpdate(principal, taskId);
        if (TaskStatus.IN_PROGRESS.equals(task.status())) {
            return detail(task);
        }
        if (!TaskStatus.ASSIGNED.equals(task.status())) {
            throw invalidState("Only assigned tasks can be started.");
        }

        task.start(Instant.now());
        tasks.saveAndFlush(task);
        recordEvent(task, TaskEventType.TASK_STARTED, actor, Map.of());
        return detail(task);
    }

    @Transactional
    public TaskDetailResponse complete(FieldServicePrincipal principal, UUID taskId, CompleteTaskRequest request) {
        UserAccount actor = requireUser(principal.id());
        Task task = assignedTaskForUpdate(principal, taskId);
        if (TaskStatus.COMPLETED.equals(task.status())) {
            return detail(task);
        }
        if (!TaskStatus.IN_PROGRESS.equals(task.status())) {
            throw invalidState("Only in-progress tasks can be completed.");
        }

        task.complete(Instant.now());
        tasks.saveAndFlush(task);
        recordEvent(task, TaskEventType.TASK_COMPLETED, actor, Map.of("completionNotes", request.completionNotes() == null ? "" : request.completionNotes()));
        return detail(task);
    }

    @Transactional
    public TaskDetailResponse block(FieldServicePrincipal principal, UUID taskId, BlockTaskRequest request) {
        UserAccount actor = requireUser(principal.id());
        Task task = assignedTaskForUpdate(principal, taskId);
        if (!List.of(TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS).contains(task.status())) {
            throw invalidState("Only assigned or in-progress tasks can be blocked.");
        }

        task.block(Instant.now());
        tasks.saveAndFlush(task);
        recordEvent(task, TaskEventType.TASK_BLOCKED, actor, Map.of("reason", request.reason()));
        return detail(task);
    }

    @Transactional
    public TaskCommentResponse comment(FieldServicePrincipal principal, UUID taskId, AddCommentRequest request) {
        UserAccount actor = requireUser(principal.id());
        Task task = visibleTask(principal, taskId);
        TaskComment comment = comments.save(new TaskComment(task, actor, request.body(), Instant.now()));
        recordEvent(task, TaskEventType.TASK_COMMENTED, actor, Map.of("commentId", comment.id().toString()));
        return new TaskCommentResponse(comment.id(), actor.id(), comment.body(), comment.createdAt());
    }

    @Transactional(readOnly = true)
    public List<EventEnvelopeDto> events(FieldServicePrincipal principal, UUID taskId) {
        Task task = visibleTask(principal, taskId);
        return events.findByTaskIdOrderByEventIdAsc(task.id()).stream()
                .map(event -> new EventEnvelopeDto(
                        event.eventId(),
                        event.type().name(),
                        "TASK",
                        task.id(),
                        event.entityVersion() == null ? 0L : event.entityVersion(),
                        event.occurredAt(),
                        event.payload()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskListItemResponse> assignedTasks(FieldServicePrincipal principal, UUID workerId) {
        if (isWorker(principal) && !principal.id().equals(workerId)) {
            throw notFound("Tasks not found.");
        }
        return tasks.findByAssigneeIdAndCompletedAtIsNullOrderByDueAtAsc(workerId).stream()
                .map(this::listItem)
                .toList();
    }

    private Task visibleTask(FieldServicePrincipal principal, UUID taskId) {
        Task task = tasks.findById(taskId).orElseThrow(() -> notFound("Task not found."));
        if (isWorker(principal) && (task.assignee() == null || !principal.id().equals(task.assignee().id()))) {
            throw notFound("Task not found.");
        }
        return task;
    }

    private Task assignedTaskForUpdate(FieldServicePrincipal principal, UUID taskId) {
        Task task = tasks.findWithLockById(taskId).orElseThrow(() -> notFound("Task not found."));
        if (task.assignee() == null || !principal.id().equals(task.assignee().id())) {
            throw notFound("Task not found.");
        }
        return task;
    }

    private void assertVersion(Task task, long expectedVersion) {
        if (task.version() != expectedVersion) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TASK_VERSION_CONFLICT",
                    "Task was changed by another user.",
                    Map.of("taskId", task.id().toString(), "currentVersion", task.version())
            );
        }
    }

    private void recordEvent(Task task, TaskEventType type, UserAccount actor, Map<String, Object> payload) {
        events.save(new TaskEvent(task, type, actor, task.version(), payload, Instant.now()));
    }

    private UserAccount requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> notFound("User not found."));
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    private ApiException invalidState(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TASK_INVALID_STATE", message);
    }

    private boolean isWorker(FieldServicePrincipal principal) {
        return principal.roles().contains(Role.FIELD_WORKER)
                && !principal.roles().contains(Role.DISPATCHER)
                && !principal.roles().contains(Role.ADMIN);
    }

    private Specification<Task> filter(String status, UUID assigneeId, String priority, String type, Instant dueBefore) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!isBlank(status)) {
                predicates.add(cb.equal(root.get("status"), parseEnum(TaskStatus.class, status, "status")));
            }
            if (assigneeId != null) {
                predicates.add(cb.equal(root.get("assignee").get("id"), assigneeId));
            }
            if (!isBlank(priority)) {
                predicates.add(cb.equal(root.get("priority"), parseEnum(TaskPriority.class, priority, "priority")));
            }
            if (!isBlank(type)) {
                predicates.add(cb.equal(root.get("type"), parseEnum(TaskType.class, type, "type")));
            }
            if (dueBefore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueAt"), dueBefore));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Sort parseSort(String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",", 2);
        String property = parts.length > 0 && !isBlank(parts[0]) ? parts[0] : "dueAt";
        if (!List.of("dueAt", "priority", "status", "createdAt", "updatedAt").contains(property)) {
            property = "dueAt";
        }
        Sort.Direction direction = parts.length == 2 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }

    private Point point(GeoPointDto dto) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(dto.longitude(), dto.latitude()));
        point.setSRID(4326);
        return point;
    }

    private GeoPointDto geo(Point point) {
        return new GeoPointDto(point.getY(), point.getX());
    }

    private TaskListItemResponse listItem(Task task) {
        return new TaskListItemResponse(
                task.id(),
                task.type().name(),
                task.status().name(),
                task.priority().name(),
                task.title(),
                geo(task.location()),
                task.address(),
                task.dueAt(),
                task.assignee() == null ? null : task.assignee().id(),
                task.version()
        );
    }

    private TaskDetailResponse detail(Task task) {
        return new TaskDetailResponse(
                task.id(),
                task.vehicle() == null ? null : task.vehicle().id(),
                task.type().name(),
                task.status().name(),
                task.priority().name(),
                task.title(),
                task.description(),
                task.requirements(),
                geo(task.location()),
                task.address(),
                task.dueAt(),
                task.assignee() == null ? null : task.assignee().id(),
                task.assignedAt(),
                task.startedAt(),
                task.completedAt(),
                task.blockedAt(),
                task.cancelledAt(),
                task.version()
        );
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, String field) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_" + field.toUpperCase(Locale.ROOT), "Invalid " + field + ".");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
