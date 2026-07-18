package com.payment.order.event;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.order.entity.OutboxEvent;
import com.payment.order.repository.OutboxEventRepository;
import com.payment.order.service.ConfigService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OutboxPublisher {
	private final ConfigService configService;

	private final OutboxEventRepository outboxEventRepository;

	private final KafkaProducer kafkaProducer;

	private final ObjectMapper objectMapper;

	OutboxPublisher(ConfigService configService, OutboxEventRepository outboxEventRepository,
			KafkaProducer kafkaProducer, ObjectMapper objectMapper) {
		this.configService = configService;
		this.outboxEventRepository = outboxEventRepository;
		this.kafkaProducer = kafkaProducer;
		this.objectMapper = objectMapper;
	}

	@Scheduled(fixedDelayString = "#{@configService.getConfigAsString('ORDER_OUTBOX_POLL_DELAY')}")
	@Transactional
	public void publishPendingEvents() {
		int maxRetries = configService.getConfigAsInt("ORDER_OUTBOX_MAX_RETRIES");
		List<OutboxEvent> pendingEvents = outboxEventRepository.findReadyToPublish(maxRetries);

		if (pendingEvents.isEmpty()) {
			return;
		}

		log.info("OutboxPublisher: Found {} pending events", pendingEvents.size());

		for (OutboxEvent event : pendingEvents) {
			try {
				if ("OrderCreatedEvent".equals(event.getEventType())) {
					OrderCreatedEvent kafkaEvent = objectMapper.readValue(event.getPayload(), OrderCreatedEvent.class);

					// Asynchronous send - we wait for the result here to update the outbox reliably
					kafkaProducer.sendOrderCreatedEventAsync(kafkaEvent).get(5, java.util.concurrent.TimeUnit.SECONDS);

					event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
					event.setPublishedAt(LocalDateTime.now());
				} else {
					log.warn("Unknown event type in outbox: {}", event.getEventType());
					event.setStatus(OutboxEvent.OutboxStatus.FAILED);
				}
			} catch (Exception e) {
				event.setRetryCount(event.getRetryCount() + 1);

				// Exponential backoff logic: 1s, 2s, 4s, 8s... max 5 mins (300000ms)
				long delayMs = Math.min(300000, (long) Math.pow(2, event.getRetryCount() - 1) * 1000);
				event.setNextRetryAt(LocalDateTime.now().plusNanos(delayMs * 1_000_000));

				log.error("Failed to publish outbox event {}. Retry count: {} | Next retry in {}ms | Error: {}",
						event.getId(), event.getRetryCount(), delayMs, e.getMessage());

				if (event.getRetryCount() >= maxRetries) {
					event.setStatus(OutboxEvent.OutboxStatus.FAILED);
				}
			}
			outboxEventRepository.save(event);
		}
	}
}
