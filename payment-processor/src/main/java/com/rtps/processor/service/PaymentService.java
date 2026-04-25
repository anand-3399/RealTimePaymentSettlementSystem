package com.rtps.processor.service;

import com.rtps.processor.client.AJBankClient;
import com.rtps.processor.dto.OrderCreatedEvent;
import com.rtps.processor.entity.Payment;
import com.rtps.processor.entity.PaymentTransactionLog;
import com.rtps.processor.repository.PaymentRepository;
import com.rtps.processor.repository.PaymentTransactionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionLogRepository logRepository;

    @Autowired
    private AJBankClient ajBankClient;

    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        UUID orderId = UUID.fromString(event.getOrderId());
        
        // 1. Create initial Payment record
        Payment payment = Payment.builder()
                .orderId(orderId)
                .userId(event.getUserId())
                .senderAccount(event.getSenderAccount())
                .recipientAccount(event.getRecipientAccount())
                .amount(event.getAmount())
                .currency("INR")
                .status(Payment.PaymentStatus.PENDING)
                .correlationId(event.getCorrelationId())
                .build();

        payment = paymentRepository.save(payment);
        
        logTransaction(payment, "INITIATED", null, "PENDING", "Payment flow started for order " + orderId);

        try {
            // 2. Call AJBank
            AJBankClient.AJBankResponse response = ajBankClient.transferMoney(
                    event.getSenderAccount(),
                    event.getRecipientAccount(),
                    event.getAmount(),
                    event.getCorrelationId()
            );

            // 3. Update Payment based on response
            payment.setGatewayTransactionId(response.getTransactionId());
            payment.setGatewayResponse(response.getMessage());
            
            if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                payment.setStatus(Payment.PaymentStatus.COMPLETED);
                payment.setProcessedAt(LocalDateTime.now());
                logTransaction(payment, "COMPLETED", "PENDING", "COMPLETED", "Bank confirmed success: " + response.getTransactionId());
            } else {
                payment.setStatus(Payment.PaymentStatus.FAILED);
                payment.setFailedAt(LocalDateTime.now());
                logTransaction(payment, "FAILED", "PENDING", "FAILED", "Bank rejected: " + response.getMessage());
            }

        } catch (Exception e) {
            logger.error("Payment processing failed for order {}: {}", orderId, e.getMessage());
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setFailedAt(LocalDateTime.now());
            logTransaction(payment, "FAILED", "PENDING", "FAILED", "System Error: " + e.getMessage());
        }

        paymentRepository.save(payment);
        
        // TODO: Publish PaymentProcessedEvent to Kafka
    }

    private void logTransaction(Payment payment, String action, String fromStatus, String toStatus, String message) {
        PaymentTransactionLog log = PaymentTransactionLog.builder()
                .payment(payment)
                .action(action)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .message(message)
                .correlationId(payment.getCorrelationId())
                .build();
        logRepository.save(log);
    }
}
