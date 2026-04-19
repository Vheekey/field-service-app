package com.example.fieldservice.sync.api;

import com.example.fieldservice.audit.api.EventEnvelopeDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SyncDto {

    private SyncDto() {
    }

    public record SyncResponse(Instant serverTime, long latestEventId, List<EventEnvelopeDto> events) {
    }

    public record OutboxReplayRequest(List<OfflineCommandDto> commands) {
    }

    public record OfflineCommandDto(
            @NotBlank String clientMutationId,
            @NotBlank String idempotencyKey,
            @NotBlank String type,
            @NotNull Instant createdAt,
            @NotNull Map<String, Object> payload
    ) {
    }

    public record OutboxReplayResponse(List<OutboxResultDto> results) {
    }

    public record OutboxResultDto(String clientMutationId, String status, Long serverEventId) {
    }
}
