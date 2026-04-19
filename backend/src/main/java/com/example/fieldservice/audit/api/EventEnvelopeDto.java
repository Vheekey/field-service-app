package com.example.fieldservice.audit.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelopeDto(
        long eventId,
        String type,
        String entityType,
        UUID entityId,
        long version,
        Instant occurredAt,
        Map<String, Object> payload
) {
}
