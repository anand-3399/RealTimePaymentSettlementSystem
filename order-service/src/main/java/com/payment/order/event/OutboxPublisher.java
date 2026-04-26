package com.payment.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.order.entity.OutboxEvent;
import com.payment.order.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int MAX_RETRIES = 5;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private KafkaProducer kafkaProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 10000) // Poll every 10 seconds
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findReadyToPublish(MAX_RETRIES);

        if (pendingEvents.isEmpty()) {
            return;
        }

        logger.info("OutboxPublisher: Found {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                if ("OrderCreatedEvent".equals(event.getEventType())) {
                    OrderCreatedEvent kafkaEvent = objectMapper.readValue(event.getPayload(), OrderCreatedEvent.class);
                    
                    // Asynchronous send - we wait for the result here to update the outbox reliably
                    kafkaProducer.sendOrderCreatedEventAsync(kafkaEvent).get(5, java.util.concurrent.TimeUnit.SECONDS);
                    
                    event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                    event.setPublishedAt(LocalDateTime.now());
                } else {
                    logger.warn("Unknown event type in outbox: {}", event.getEventType());
                    event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                }
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                
                // Exponential backoff logic: 1s, 2s, 4s, 8s... max 5 mins (300000ms)
                long delayMs = Math.min(300000, (long) Math.pow(2, event.getRetryCount() - 1) * 1000);
                event.setNextRetryAt(LocalDateTime.now().plusNanos(delayMs * 1_000_000));

                logger.error("Failed to publish outbox event {}. Retry count: {} | Next retry in {}ms | Error: {}", 
                        event.getId(), event.getRetryCount(), delayMs, e.getMessage());
                
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                }
            }
            outboxEventRepository.save(event);
        }
    }
}
