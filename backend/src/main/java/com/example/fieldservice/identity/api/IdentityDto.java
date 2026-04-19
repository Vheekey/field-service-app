package com.example.fieldservice.identity.api;

import com.example.fieldservice.workers.api.WorkerProfileDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class IdentityDto {

    private IdentityDto() {
    }

    public record LoginRequest(@Email String email, @NotBlank String password) {
    }

    public record AuthTokenResponse(String accessToken, Instant expiresAt) {
    }

    public record MeResponse(UUID id, String email, String name, Set<String> roles, WorkerProfileDto workerProfile) {
    }
}
