package com.example.fieldservice.shifts.application;

import com.example.fieldservice.common.api.GeoPointDto;
import com.example.fieldservice.common.errors.ApiException;
import com.example.fieldservice.common.security.FieldServicePrincipal;
import com.example.fieldservice.identity.domain.UserAccount;
import com.example.fieldservice.identity.persistence.UserAccountRepository;
import com.example.fieldservice.shifts.api.ShiftDto.EndShiftRequest;
import com.example.fieldservice.shifts.api.ShiftDto.ShiftResponse;
import com.example.fieldservice.shifts.api.ShiftDto.StartShiftRequest;
import com.example.fieldservice.shifts.domain.Shift;
import com.example.fieldservice.shifts.domain.ShiftStatus;
import com.example.fieldservice.shifts.persistence.ShiftRepository;
import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftApplicationService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final ShiftRepository shifts;
    private final UserAccountRepository users;

    public ShiftApplicationService(ShiftRepository shifts, UserAccountRepository users) {
        this.shifts = shifts;
        this.users = users;
    }

    @Transactional
    public ShiftResponse start(FieldServicePrincipal principal, StartShiftRequest request) {
        return shifts.findFirstByWorkerIdAndStatusOrderByStartedAtDesc(principal.id(), ShiftStatus.ACTIVE)
                .map(this::response)
                .orElseGet(() -> {
                    UserAccount worker = requireUser(principal.id());
                    Shift shift = shifts.saveAndFlush(new Shift(worker, Instant.now(), point(request.location())));
                    return response(shift);
                });
    }

    @Transactional
    public ShiftResponse end(FieldServicePrincipal principal, UUID shiftId, EndShiftRequest request) {
        Shift shift = shifts.findById(shiftId).orElseThrow(() -> notFound("Shift not found."));
        if (!shift.worker().id().equals(principal.id())) {
            throw notFound("Shift not found.");
        }
        if (ShiftStatus.ENDED.equals(shift.status())) {
            return response(shift);
        }

        shift.end(Instant.now(), point(request.location()));
        shifts.saveAndFlush(shift);
        return response(shift);
    }

    @Transactional(readOnly = true)
    public ShiftResponse current(FieldServicePrincipal principal) {
        return shifts.findFirstByWorkerIdAndStatusOrderByStartedAtDesc(principal.id(), ShiftStatus.ACTIVE)
                .map(this::response)
                .orElseThrow(() -> notFound("No active shift found."));
    }

    private UserAccount requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> notFound("User not found."));
    }

    private ShiftResponse response(Shift shift) {
        return new ShiftResponse(
                shift.id(),
                shift.worker().id(),
                shift.status().name(),
                shift.startedAt(),
                shift.endedAt()
        );
    }

    private Point point(GeoPointDto dto) {
        if (dto == null) {
            return null;
        }
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(dto.longitude(), dto.latitude()));
        point.setSRID(4326);
        return point;
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
