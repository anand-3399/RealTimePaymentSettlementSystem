package com.rtps.ajbank.repository;

import com.rtps.ajbank.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);
}
