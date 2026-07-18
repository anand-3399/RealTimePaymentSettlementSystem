package com.payment.order.event;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class KafkaProducer {
    private static final String TOPIC = "order-events";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    KafkaProducer(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrderCreatedEventSync(OrderCreatedEvent event) throws Exception {
        log.info("Publishing OrderCreatedEvent (Synchronous): {}", event.getOrderId());
        kafkaTemplate.send(TOPIC, event.getOrderId(), event).get(5, TimeUnit.SECONDS);
    }

    public CompletableFuture<?> sendOrderCreatedEventAsync(OrderCreatedEvent event) {
        log.info("Publishing OrderCreatedEvent (Asynchronous): {} | correlationId: {}", 
                event.getOrderId(), event.getCorrelationId());
        // Returns the future so we can handle success/failure
        return kafkaTemplate.send(TOPIC, event.getOrderId(), event);
    }
}

