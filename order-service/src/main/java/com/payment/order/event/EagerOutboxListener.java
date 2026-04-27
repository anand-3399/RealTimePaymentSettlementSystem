package com.payment.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EagerOutboxListener {

    private static final Logger logger = LoggerFactory.getLogger(EagerOutboxListener.class);

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxEvent(OutboxEventCreated event) {
        logger.info("EagerOutboxListener: Triggering immediate outbox publication after commit");
        try {
            outboxPublisher.publishPendingEvents();
        } catch (Exception e) {
            logger.error("EagerOutboxListener: Failed to publish events eagerly: {}", e.getMessage());
        }
    }
}
