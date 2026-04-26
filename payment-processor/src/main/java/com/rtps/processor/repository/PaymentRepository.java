package com.rtps.processor.repository;

import com.rtps.processor.entity.Payment;
import com.rtps.processor.entity.Payment.PaymentStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);
    List<Payment> findByStatus(Payment.PaymentStatus status);
    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);
	List<Payment> findByStatusIn(List<PaymentStatus> statuses);
}
