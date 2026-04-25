package com.payment.order.repository;

import com.payment.order.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM IdempotencyKey i WHERE i.expiresAt < :now")
    int deleteExpiredBefore(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
}
