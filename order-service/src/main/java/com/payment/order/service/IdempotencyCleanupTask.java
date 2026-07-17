package com.payment.order.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.payment.order.repository.IdempotencyKeyRepository;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class IdempotencyCleanupTask {

    @Autowired
    private IdempotencyKeyRepository repository;

    @Scheduled(fixedDelayString = "#{@configService.getConfigAsString('ORDER_IDEMPOTENCY_CLEANUP_DELAY')}")
    @Transactional
    public void cleanupExpiredKeys() {
        log.info("Starting idempotency key cleanup task...");
        int deletedCount = repository.deleteExpiredBefore(LocalDateTime.now());
        log.info("Successfully deleted {} expired idempotency keys.", deletedCount);
    }
}

