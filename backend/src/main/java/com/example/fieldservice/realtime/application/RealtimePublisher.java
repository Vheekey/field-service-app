package com.example.fieldservice.realtime.application;

import com.example.fieldservice.audit.api.EventEnvelopeDto;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RealtimePublisher {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(EventEnvelopeDto event) {
        // Wire to STOMP or SSE once the transport choice is finalized.
    }
}
