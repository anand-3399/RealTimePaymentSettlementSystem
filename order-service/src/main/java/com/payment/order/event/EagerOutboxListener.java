package com.payment.order.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class EagerOutboxListener {

    private final OutboxPublisher outboxPublisher;

    EagerOutboxListener(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxEvent(OutboxEventCreated event) {
        log.info("EagerOutboxListener: Triggering immediate outbox publication after commit");
        try {
            outboxPublisher.publishPendingEvents();
        } catch (Exception e) {
            log.error("EagerOutboxListener: Failed to publish events eagerly: {}", e);
        }
    }
}

