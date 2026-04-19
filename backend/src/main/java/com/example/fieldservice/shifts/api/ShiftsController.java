package com.example.fieldservice.shifts.api;

import com.example.fieldservice.common.security.FieldServicePrincipal;
import com.example.fieldservice.shifts.application.ShiftApplicationService;
import com.example.fieldservice.shifts.api.ShiftDto.EndShiftRequest;
import com.example.fieldservice.shifts.api.ShiftDto.ShiftResponse;
import com.example.fieldservice.shifts.api.ShiftDto.StartShiftRequest;
import jakarta.validation.Valid;
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
@RequestMapping("${app.api.base-path}/shifts")
public class ShiftsController {

    private final ShiftApplicationService shifts;

    public ShiftsController(ShiftApplicationService shifts) {
        this.shifts = shifts;
    }

    @PostMapping("/start")
    ResponseEntity<ShiftResponse> start(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @Valid @RequestBody StartShiftRequest request
    ) {
        return ResponseEntity.status(201).body(shifts.start(principal, request));
    }

    @PostMapping("/{shiftId}/end")
    ResponseEntity<ShiftResponse> end(
            @AuthenticationPrincipal FieldServicePrincipal principal,
            @PathVariable UUID shiftId,
            @Valid @RequestBody EndShiftRequest request
    ) {
        return ResponseEntity.ok(shifts.end(principal, shiftId, request));
    }

    @GetMapping("/current")
    ResponseEntity<ShiftResponse> current(@AuthenticationPrincipal FieldServicePrincipal principal) {
        return ResponseEntity.ok(shifts.current(principal));
    }
}
