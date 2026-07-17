package com.rtps.bank1.repository;

import com.rtps.bank1.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);
}
