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
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING, MAX_RETRIES);

        if (pendingEvents.isEmpty()) {
            return;
        }

        logger.info("OutboxPublisher: Found {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                if ("OrderCreatedEvent".equals(event.getEventType())) {
                    OrderCreatedEvent kafkaEvent = objectMapper.readValue(event.getPayload(), OrderCreatedEvent.class);
                    
                    // Synchronous send to ensure we know it reached Kafka
                    kafkaProducer.sendOrderCreatedEventSync(kafkaEvent);
                    
                    event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                    event.setPublishedAt(LocalDateTime.now());
                } else {
                    logger.warn("Unknown event type in outbox: {}", event.getEventType());
                    event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                }
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                logger.error("Failed to publish outbox event {}. Retry count: {} | Error: {}", 
                        event.getId(), event.getRetryCount(), e.getMessage());
                
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                }
            }
            outboxEventRepository.save(event);
        }
    }
}
