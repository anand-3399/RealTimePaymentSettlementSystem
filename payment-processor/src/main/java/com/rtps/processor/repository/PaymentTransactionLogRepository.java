package com.rtps.processor.repository;

import com.rtps.processor.entity.PaymentTransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PaymentTransactionLogRepository extends JpaRepository<PaymentTransactionLog, UUID> {
    List<PaymentTransactionLog> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}
