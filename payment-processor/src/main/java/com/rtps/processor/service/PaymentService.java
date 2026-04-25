package com.rtps.processor.service;

import com.rtps.processor.client.AJBankClient;
import com.rtps.processor.dto.AnalyticsResponse;
import com.rtps.processor.dto.OrderCreatedEvent;
import com.rtps.processor.dto.PaymentProcessedEvent;
import com.rtps.processor.dto.PaymentResponse;
import com.rtps.processor.entity.Payment;
import com.rtps.processor.entity.PaymentTransactionLog;
import com.rtps.processor.repository.PaymentRepository;
import com.rtps.processor.repository.PaymentTransactionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionLogRepository logRepository;

    @Autowired
    private AJBankClient ajBankClient;

    @Autowired
    private com.rtps.processor.producer.KafkaProducer kafkaProducer;

    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        UUID orderId = UUID.fromString(event.getOrderId());
        
        // Check if payment already exists (idempotency)
        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            logger.warn("Payment already exists for orderId: {}", orderId);
            return;
        }

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
                .retryCount(0)
                .maxRetries(3)
                .build();

        payment = paymentRepository.save(payment);
        logTransaction(payment, "INITIATED", null, "PENDING", "Payment flow started for order " + orderId);

        executeBankTransfer(payment, event.getCorrelationId());
    }

    private void executeBankTransfer(Payment payment, String correlationId) {
        try {
            // 2. Call AJBank
            AJBankClient.AJBankResponse response = ajBankClient.transferMoney(
                    payment.getSenderAccount(),
                    payment.getRecipientAccount(),
                    payment.getAmount(),
                    correlationId
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
            logger.error("Payment processing failed for order {}: {}", payment.getOrderId(), e.getMessage());
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setFailedAt(LocalDateTime.now());
            logTransaction(payment, "FAILED", "PENDING", "FAILED", "System Error: " + e.getMessage());
        }

        paymentRepository.save(payment);
        
        // 4. Publish Event
        publishProcessedEvent(payment);
    }

    public PaymentResponse getPaymentDetails(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
        return mapToResponse(payment);
    }

    public java.util.Optional<PaymentResponse> getPaymentByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(this::mapToResponse);
    }

    public List<PaymentResponse> listPayments(UUID orderId, String status) {
        // Simple manual filtering for now
        return paymentRepository.findAll().stream()
                .filter(p -> orderId == null || p.getOrderId().equals(orderId))
                .filter(p -> status == null || p.getStatus().name().equalsIgnoreCase(status))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PaymentResponse retryPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        
        if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
            throw new RuntimeException("Cannot retry a completed payment");
        }

        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setRetryCount(payment.getRetryCount() + 1);
        paymentRepository.save(payment);
        
        logTransaction(payment, "RETRY", "FAILED", "PENDING", "Manual retry initiated. Count: " + payment.getRetryCount());
        
        executeBankTransfer(payment, payment.getCorrelationId());
        
        return mapToResponse(payment);
    }

    public AnalyticsResponse getDailyAnalytics(LocalDate date) {
        List<Payment> dailyPayments = paymentRepository.findAll().stream()
                .filter(p -> p.getCreatedAt().toLocalDate().equals(date))
                .collect(Collectors.toList());

        long total = dailyPayments.size();
        long success = dailyPayments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED).count();
        long failure = dailyPayments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.FAILED).count();
        
        BigDecimal totalAmount = dailyPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AnalyticsResponse.builder()
                .date(date.toString())
                .metrics(AnalyticsResponse.Metrics.builder()
                        .totalTransactions(total)
                        .successCount(success)
                        .failureCount(failure)
                        .totalAmount(totalAmount)
                        .successRate(total > 0 ? (double) success / total * 100 : 0.0)
                        .averageAmount(total > 0 ? totalAmount.divide(BigDecimal.valueOf(total), RoundingMode.HALF_UP) : BigDecimal.ZERO)
                        .build())
                .statusBreakdown(dailyPayments.stream()
                        .collect(Collectors.groupingBy(p -> p.getStatus().name(), Collectors.counting())))
                .build();
    }

    @Transactional
    public void handleWebhook(String payload, String signature) {
        // 1. Verify signature (Mock)
        if (signature == null || signature.isEmpty()) {
            throw new RuntimeException("Invalid webhook signature");
        }
        
        logger.info("Handling AJBank Webhook | payload: {}", payload);
        
        // TODO: Parse payload and update payment status
        // This would involve finding the payment by gatewayTransactionId or orderId from the payload
    }

    private void publishProcessedEvent(Payment payment) {
        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .gatewayTransactionId(payment.getGatewayTransactionId())
                .correlationId(payment.getCorrelationId())
                .timestamp(LocalDateTime.now())
                .build();
        kafkaProducer.publishPaymentProcessed(event);
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .senderAccount(maskAccount(payment.getSenderAccount()))
                .recipientAccount(maskAccount(payment.getRecipientAccount()))
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .gatewayTransactionId(payment.getGatewayTransactionId())
                .processedAt(payment.getProcessedAt())
                .createdAt(payment.getCreatedAt())
                .correlationId(payment.getCorrelationId())
                .build();
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 4) return "****";
        return "****" + account.substring(account.length() - 4);
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
