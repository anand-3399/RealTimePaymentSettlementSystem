package com.rtps.gateway.repository;

import com.rtps.gateway.entity.PaymentTransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PaymentTransactionLogRepository extends JpaRepository<PaymentTransactionLog, UUID> {
    List<PaymentTransactionLog> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}
