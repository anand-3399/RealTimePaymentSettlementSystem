package com.payment.order.service;

import com.payment.order.entity.IdempotencyKey;
import com.payment.order.repository.IdempotencyKeyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    @Autowired
    private IdempotencyKeyRepository repository;

    public Optional<UUID> getOrderId(String key) {
        return repository.findById(key).map(IdempotencyKey::getOrderId);
    }

    public void saveKey(String key, UUID orderId) {
        IdempotencyKey idempotencyKey = IdempotencyKey.builder()
                .key(key)
                .orderId(orderId)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        repository.save(idempotencyKey);
    }
}
