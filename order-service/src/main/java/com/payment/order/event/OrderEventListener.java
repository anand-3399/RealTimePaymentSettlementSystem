package com.payment.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderEventListener {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventListener.class);

    @Autowired
    private KafkaProducer kafkaProducer;

    /**
     * This listener only triggers AFTER the database transaction has successfully committed.
     * This prevents blocking the DB transaction and ensures Kafka events are only sent for
     * orders that were actually saved.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedInternalEvent internalEvent) {
        var order = internalEvent.getOrder();
        String correlationId = MDC.get("correlationId");

        logger.info("Transaction committed. Publishing to Kafka | orderId: {} | correlationId: {}", 
                order.getOrderId(), correlationId);

        OrderCreatedEvent kafkaEvent = OrderCreatedEvent.builder()
                .orderId(order.getOrderId().toString())
                .userId(order.getUsername())
                .amount(order.getAmount())
                .recipientAccount(order.getRecipientBankAccount())
                .senderAccount(order.getSenderBankAccount())
                .timestamp(order.getCreatedAt())
                .correlationId(correlationId) // Trace propagation!
                .build();

        kafkaProducer.sendOrderCreatedEventAsync(kafkaEvent);
    }
}
