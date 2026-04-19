package com.example.fieldservice.shifts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.fieldservice.common.api.GeoPointDto;
import com.example.fieldservice.common.errors.ApiException;
import com.example.fieldservice.common.security.FieldServicePrincipal;
import com.example.fieldservice.identity.domain.Role;
import com.example.fieldservice.identity.domain.UserAccount;
import com.example.fieldservice.identity.domain.UserStatus;
import com.example.fieldservice.identity.persistence.UserAccountRepository;
import com.example.fieldservice.shifts.api.ShiftDto.EndShiftRequest;
import com.example.fieldservice.shifts.api.ShiftDto.StartShiftRequest;
import com.example.fieldservice.shifts.domain.Shift;
import com.example.fieldservice.shifts.domain.ShiftStatus;
import com.example.fieldservice.shifts.persistence.ShiftRepository;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ShiftApplicationServiceTest {

    @Mock
    private ShiftRepository shifts;

    @Mock
    private UserAccountRepository users;

    private ShiftApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ShiftApplicationService(shifts, users);
    }

    @Test
    void startCreatesActiveShiftWhenWorkerHasNone() {
        UserAccount worker = user("worker@example.com", Role.FIELD_WORKER);

        when(shifts.findFirstByWorkerIdAndStatusOrderByStartedAtDesc(worker.id(), ShiftStatus.ACTIVE)).thenReturn(Optional.empty());
        when(users.findById(worker.id())).thenReturn(Optional.of(worker));
        when(shifts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.start(principal(worker), new StartShiftRequest(new GeoPointDto(59.3293, 18.0686)));

        assertThat(response.workerId()).isEqualTo(worker.id());
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.endedAt()).isNull();
        verify(shifts).saveAndFlush(any(Shift.class));
    }

    @Test
    void startLoadsExistingActiveShift() {
        UserAccount worker = user("worker@example.com", Role.FIELD_WORKER);
        Shift activeShift = new Shift(worker, Instant.parse("2026-04-19T08:00:00Z"), null);

        when(shifts.findFirstByWorkerIdAndStatusOrderByStartedAtDesc(worker.id(), ShiftStatus.ACTIVE)).thenReturn(Optional.of(activeShift));

        var response = service.start(principal(worker), new StartShiftRequest(null));

        assertThat(response.id()).isEqualTo(activeShift.id());
        assertThat(response.startedAt()).isEqualTo(Instant.parse("2026-04-19T08:00:00Z"));
        verify(users, never()).findById(any());
        verify(shifts, never()).saveAndFlush(any());
    }

    @Test
    void currentReturnsActiveShiftForWorker() {
        UserAccount worker = user("worker@example.com", Role.FIELD_WORKER);
        Shift activeShift = new Shift(worker, Instant.parse("2026-04-19T08:00:00Z"), null);

        when(shifts.findFirstByWorkerIdAndStatusOrderByStartedAtDesc(worker.id(), ShiftStatus.ACTIVE)).thenReturn(Optional.of(activeShift));

        var response = service.current(principal(worker));

        assertThat(response.id()).isEqualTo(activeShift.id());
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void endRejectsShiftOwnedByAnotherWorkerAsNotFound() {
        UserAccount owner = user("owner@example.com", Role.FIELD_WORKER);
        UserAccount otherWorker = user("other@example.com", Role.FIELD_WORKER);
        Shift activeShift = new Shift(owner, Instant.parse("2026-04-19T08:00:00Z"), null);

        when(shifts.findById(activeShift.id())).thenReturn(Optional.of(activeShift));

        assertThatThrownBy(() -> service.end(principal(otherWorker), activeShift.id(), new EndShiftRequest(null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("NOT_FOUND");
                });

        verify(shifts, never()).saveAndFlush(any());
    }

    @Test
    void endClosesActiveShift() {
        UserAccount worker = user("worker@example.com", Role.FIELD_WORKER);
        Shift activeShift = new Shift(worker, Instant.parse("2026-04-19T08:00:00Z"), null);

        when(shifts.findById(activeShift.id())).thenReturn(Optional.of(activeShift));

        var response = service.end(principal(worker), activeShift.id(), new EndShiftRequest(new GeoPointDto(59.3293, 18.0686)));

        assertThat(response.status()).isEqualTo("ENDED");
        assertThat(response.endedAt()).isNotNull();
        verify(shifts).saveAndFlush(activeShift);
    }

    private static FieldServicePrincipal principal(UserAccount user) {
        return new FieldServicePrincipal(user.id(), user.email(), user.name(), user.passwordHash(), user.roles(), user.isActive());
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
