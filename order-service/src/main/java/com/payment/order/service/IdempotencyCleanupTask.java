package com.payment.order.service;

import com.payment.order.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class IdempotencyCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyCleanupTask.class);

    @Autowired
    private IdempotencyKeyRepository repository;

    @Scheduled(fixedDelay = 3600000) // Every 1 hour
    @Transactional
    public void cleanupExpiredKeys() {
        logger.info("Starting idempotency key cleanup task...");
        int deletedCount = repository.deleteExpiredBefore(LocalDateTime.now());
        logger.info("Successfully deleted {} expired idempotency keys.", deletedCount);
    }
}
