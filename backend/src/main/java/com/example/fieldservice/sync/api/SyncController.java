package com.example.fieldservice.sync.api;

import com.example.fieldservice.sync.api.SyncDto.OutboxReplayRequest;
import com.example.fieldservice.sync.api.SyncDto.OutboxReplayResponse;
import com.example.fieldservice.sync.api.SyncDto.SyncResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/sync")
public class SyncController {

    @GetMapping
    ResponseEntity<SyncResponse> sync(@RequestParam long since) {
        return ResponseEntity.status(501).build();
    }

    @PostMapping("/outbox")
    ResponseEntity<OutboxReplayResponse> replay(@Valid @RequestBody OutboxReplayRequest request) {
        return ResponseEntity.status(501).build();
    }
}
