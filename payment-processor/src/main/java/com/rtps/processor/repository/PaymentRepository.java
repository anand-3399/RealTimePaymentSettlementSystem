package com.rtps.processor.repository;

import com.rtps.processor.entity.Payment;
import com.rtps.processor.entity.Payment.PaymentStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);
    List<Payment> findByStatus(Payment.PaymentStatus status);
    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);
    Optional<Payment> findTopByCorrelationIdOrderByCreatedAtDesc(String correlationId);
	List<Payment> findByStatusIn(List<PaymentStatus> statuses);

    @Query("SELECT p FROM Payment p WHERE p.status IN :statuses AND (p.nextRetryAt IS NULL OR p.nextRetryAt <= :now)")
    List<Payment> findPaymentsDueForRetry(List<PaymentStatus> statuses, LocalDateTime now);
}
