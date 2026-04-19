package com.example.fieldservice.tasks.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.fieldservice.audit.domain.TaskEvent;
import com.example.fieldservice.audit.domain.TaskEventType;
import com.example.fieldservice.audit.persistence.TaskEventRepository;
import com.example.fieldservice.assignments.domain.TaskAssignment;
import com.example.fieldservice.assignments.persistence.TaskAssignmentRepository;
import com.example.fieldservice.common.errors.ApiException;
import com.example.fieldservice.common.security.FieldServicePrincipal;
import com.example.fieldservice.identity.domain.Role;
import com.example.fieldservice.identity.domain.UserAccount;
import com.example.fieldservice.identity.domain.UserStatus;
import com.example.fieldservice.identity.persistence.UserAccountRepository;
import com.example.fieldservice.tasks.api.TaskDto.AssignTaskRequest;
import com.example.fieldservice.tasks.api.TaskDto.UpdateTaskRequest;
import com.example.fieldservice.tasks.domain.Task;
import com.example.fieldservice.tasks.domain.TaskPriority;
import com.example.fieldservice.tasks.domain.TaskStatus;
import com.example.fieldservice.tasks.domain.TaskType;
import com.example.fieldservice.tasks.persistence.TaskCommentRepository;
import com.example.fieldservice.tasks.persistence.TaskRepository;
import com.example.fieldservice.vehicles.persistence.VehicleRepository;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskApplicationServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    @Mock
    private TaskRepository tasks;

    @Mock
    private TaskCommentRepository comments;

    @Mock
    private TaskAssignmentRepository assignments;

    @Mock
    private TaskEventRepository events;

    @Mock
    private UserAccountRepository users;

    @Mock
    private VehicleRepository vehicles;

    private TaskApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TaskApplicationService(tasks, comments, assignments, events, users, vehicles);
    }

    @Test
    void updateRejectsStaleIfMatchVersionBeforeSaving() {
        UserAccount dispatcher = user("dispatcher@example.com", Role.DISPATCHER);
        Task task = task(dispatcher);
        ReflectionTestUtils.setField(task, "version", 8L);

        when(users.findById(dispatcher.id())).thenReturn(Optional.of(dispatcher));
        when(tasks.findWithLockById(task.id())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.update(
                principal(dispatcher),
                task.id(),
                7L,
                new UpdateTaskRequest("URGENT", null, null, null, null, null, null)
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("TASK_VERSION_CONFLICT");
                    assertThat(api.details()).containsEntry("currentVersion", 8L);
                });

        verify(tasks, never()).saveAndFlush(any());
        verify(events, never()).save(any());
    }

    @Test
    void assignRejectsTaskAlreadyAssignedToAnotherWorker() {
        UserAccount dispatcher = user("dispatcher@example.com", Role.DISPATCHER);
        UserAccount currentWorker = user("worker-one@example.com", Role.FIELD_WORKER);
        UserAccount requestedWorker = user("worker-two@example.com", Role.FIELD_WORKER);
        Task task = task(dispatcher);
        task.assignTo(currentWorker, Instant.parse("2026-04-19T08:00:00Z"));

        when(users.findById(dispatcher.id())).thenReturn(Optional.of(dispatcher));
        when(users.findById(requestedWorker.id())).thenReturn(Optional.of(requestedWorker));
        when(tasks.findWithLockById(task.id())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.assign(principal(dispatcher), task.id(), new AssignTaskRequest(requestedWorker.id())))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("TASK_ALREADY_ASSIGNED");
                });

        verify(assignments, never()).save(any());
        verify(tasks, never()).saveAndFlush(any());
        verify(events, never()).save(any());
    }

    @Test
    void workerCannotStartTaskAssignedToSomeoneElse() {
        UserAccount dispatcher = user("dispatcher@example.com", Role.DISPATCHER);
        UserAccount assignedWorker = user("worker-one@example.com", Role.FIELD_WORKER);
        UserAccount otherWorker = user("worker-two@example.com", Role.FIELD_WORKER);
        Task task = task(dispatcher);
        task.assignTo(assignedWorker, Instant.parse("2026-04-19T08:00:00Z"));

        when(users.findById(otherWorker.id())).thenReturn(Optional.of(otherWorker));
        when(tasks.findWithLockById(task.id())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.start(principal(otherWorker), task.id()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("NOT_FOUND");
                });

        verify(tasks, never()).saveAndFlush(any());
        verify(events, never()).save(any());
    }

    @Test
    void workerStartsAssignedTaskAndEmitsEvent() {
        UserAccount dispatcher = user("dispatcher@example.com", Role.DISPATCHER);
        UserAccount worker = user("worker@example.com", Role.FIELD_WORKER);
        Task task = task(dispatcher);
        task.assignTo(worker, Instant.parse("2026-04-19T08:00:00Z"));

        when(users.findById(worker.id())).thenReturn(Optional.of(worker));
        when(tasks.findWithLockById(task.id())).thenReturn(Optional.of(task));

        var response = service.start(principal(worker), task.id());

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(task.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.startedAt()).isNotNull();

        verify(tasks).saveAndFlush(task);
        ArgumentCaptor<TaskEvent> event = ArgumentCaptor.forClass(TaskEvent.class);
        verify(events).save(event.capture());
        assertThat(event.getValue().type()).isEqualTo(TaskEventType.TASK_STARTED);
        assertThat(event.getValue().task()).isSameAs(task);
        assertThat(event.getValue().actor()).isSameAs(worker);
    }

    @Test
    void fieldWorkerCanOnlyListOwnAssignedTasks() {
        UserAccount worker = user("worker@example.com", Role.FIELD_WORKER);
        UserAccount otherWorker = user("other-worker@example.com", Role.FIELD_WORKER);

        assertThatThrownBy(() -> service.assignedTasks(principal(worker), otherWorker.id()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        verify(tasks, never()).findByAssigneeIdAndCompletedAtIsNullOrderByDueAtAsc(any());
    }

    @Test
    void dispatcherCanAssignOpenTaskToFieldWorker() {
        UserAccount dispatcher = user("dispatcher@example.com", Role.DISPATCHER);
        UserAccount worker = user("worker@example.com", Role.FIELD_WORKER);
        Task task = task(dispatcher);

        when(users.findById(dispatcher.id())).thenReturn(Optional.of(dispatcher));
        when(users.findById(worker.id())).thenReturn(Optional.of(worker));
        when(tasks.findWithLockById(task.id())).thenReturn(Optional.of(task));

        var response = service.assign(principal(dispatcher), task.id(), new AssignTaskRequest(worker.id()));

        assertThat(response.status()).isEqualTo("ASSIGNED");
        assertThat(response.assigneeId()).isEqualTo(worker.id());
        assertThat(task.assignee()).isSameAs(worker);

        ArgumentCaptor<TaskAssignment> assignment = ArgumentCaptor.forClass(TaskAssignment.class);
        verify(assignments).save(assignment.capture());
        assertThat(assignment.getValue().task()).isSameAs(task);
        assertThat(assignment.getValue().worker()).isSameAs(worker);

        verify(tasks).saveAndFlush(task);
        ArgumentCaptor<TaskEvent> event = ArgumentCaptor.forClass(TaskEvent.class);
        verify(events).save(event.capture());
        assertThat(event.getValue().type()).isEqualTo(TaskEventType.TASK_ASSIGNED);
    }

    private static FieldServicePrincipal principal(UserAccount user) {
        return new FieldServicePrincipal(user.id(), user.email(), user.name(), user.passwordHash(), user.roles(), user.isActive());
    }

    private static Task task(UserAccount createdBy) {
        var point = GEOMETRY_FACTORY.createPoint(new Coordinate(18.0686, 59.3293));
        point.setSRID(4326);
        return new Task(
                null,
                TaskType.BATTERY_SWAP,
                TaskPriority.NORMAL,
                "Swap battery",
                "Battery below threshold.",
                Map.of("scanQr", true),
                point,
                "Central Stockholm",
                Instant.parse("2026-04-19T15:00:00Z"),
                createdBy
        );
    }

    private static UserAccount user(String email, Role... roles) {
        try {
            Constructor<UserAccount> constructor = UserAccount.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            UserAccount user = constructor.newInstance();
            ReflectionTestUtils.setField(user, "email", email);
            ReflectionTestUtils.setField(user, "passwordHash", "$2a$12$hash");
            ReflectionTestUtils.setField(user, "name", "Test User");
            ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);
            ReflectionTestUtils.setField(user, "roles", Set.of(roles));
            return user;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not create test user", ex);
        }
    }
}
