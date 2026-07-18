package com.rtps.gateway.consumer;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.rtps.gateway.dto.OrderCreatedEvent;
import com.rtps.gateway.service.PaymentService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OrderEventConsumer {

    private final PaymentService paymentService;

    OrderEventConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = "order-events", groupId = "payment-gateway-group")
    public void consume(OrderCreatedEvent event) {
        MDC.put("correlationId", event.getCorrelationId());
        try {
            log.info("Received OrderCreatedEvent | orderId: {} | userId: {}",
                    event.getOrderId(), event.getUserId());

            paymentService.processPayment(
                UUID.fromString(event.getOrderId()),
                event.getUserId(),
                event.getAmount(),
                event.getCurrency(),
                event.getSenderAccount(),
                event.getRecipientAccount(),
                event.getCorrelationId(),
                event.getIdempotencyKey()
            );
        } catch (Exception e) {
            log.error("Error processing OrderCreatedEvent | orderId: {} | error: {}",
                    event.getOrderId(), e.getMessage());
        } finally {
            MDC.remove("correlationId");
        }
    }
}

