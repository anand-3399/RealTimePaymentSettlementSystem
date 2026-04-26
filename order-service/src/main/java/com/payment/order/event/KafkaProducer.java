package com.payment.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class KafkaProducer {
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducer.class);
    private static final String TOPIC = "order-events";

    @Autowired
    private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public void sendOrderCreatedEventSync(OrderCreatedEvent event) throws Exception {
        logger.info("Publishing OrderCreatedEvent (Synchronous): {}", event.getOrderId());
        kafkaTemplate.send(TOPIC, event.getOrderId(), event).get(5, TimeUnit.SECONDS);
    }

    public CompletableFuture<?> sendOrderCreatedEventAsync(OrderCreatedEvent event) {
        logger.info("Publishing OrderCreatedEvent (Asynchronous): {} | correlationId: {}", 
                event.getOrderId(), event.getCorrelationId());
        // Returns the future so we can handle success/failure
        return kafkaTemplate.send(TOPIC, event.getOrderId(), event);
    }
}
