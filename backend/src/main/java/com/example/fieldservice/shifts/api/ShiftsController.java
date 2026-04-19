package com.example.fieldservice.shifts.api;

import com.example.fieldservice.shifts.api.ShiftDto.EndShiftRequest;
import com.example.fieldservice.shifts.api.ShiftDto.ShiftResponse;
import com.example.fieldservice.shifts.api.ShiftDto.StartShiftRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/shifts")
public class ShiftsController {

    @PostMapping("/start")
    ResponseEntity<ShiftResponse> start(@Valid @RequestBody StartShiftRequest request) {
        return ResponseEntity.status(501).build();
    }

    @PostMapping("/{shiftId}/end")
    ResponseEntity<ShiftResponse> end(@PathVariable UUID shiftId, @Valid @RequestBody EndShiftRequest request) {
        return ResponseEntity.status(501).build();
    }

    @GetMapping("/current")
    ResponseEntity<ShiftResponse> current() {
        return ResponseEntity.status(501).build();
    }
}
