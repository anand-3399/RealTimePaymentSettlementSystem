package com.rtps.processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtps.processor.client.AJBankClient;
import com.rtps.processor.dto.AJBankRequest;
import com.rtps.processor.dto.AJBankResponse;
import com.rtps.processor.dto.AnalyticsResponse;
import com.rtps.processor.dto.OrderCreatedEvent;
import com.rtps.processor.dto.PaymentProcessedEvent;
import com.rtps.processor.dto.PaymentResponse;
import com.rtps.processor.entity.Payment;
import com.rtps.processor.entity.PaymentTransactionLog;
import com.rtps.processor.repository.PaymentRepository;
import com.rtps.processor.repository.PaymentTransactionLogRepository;
import com.rtps.processor.entity.RetryReason;
import com.rtps.processor.producer.KafkaProducer;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
import java.util.Optional;
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
    private KafkaProducer kafkaProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional
    public void processPayment(UUID orderId, String userId, BigDecimal amount, String currency, 
                               String senderAccount, String recipientAccount, String correlationId, 
                               String idempotencyKey) {
        
        // 1. Idempotency Check
        Optional<Payment> existingPayment = paymentRepository.findByOrderId(orderId);
        if (existingPayment.isPresent()) {
            logger.warn("Payment already exists for orderId: {} | currentStatus: {}", 
                    orderId, existingPayment.get().getStatus());
            return;
        }

        // 2. Initial Save as PENDING
        Payment payment = Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .senderAccount(senderAccount)
                .recipientAccount(recipientAccount)
                .status(Payment.PaymentStatus.PENDING)
                .correlationId(correlationId)
                .idempotencyKey(idempotencyKey)
                .createdAt(LocalDateTime.now())
                .retryCount(0)
                .maxRetries(10) // Allow more retries for high volume
                .build();

        payment = paymentRepository.save(payment);

        // 3. Bank Handshake
        executeHandshake(payment);
    }

    private void executeHandshake(Payment payment) {
        try {
            AJBankRequest ajRequest = AJBankRequest.builder()
                    .senderAccount(payment.getSenderAccount())
                    .recipientAccount(payment.getRecipientAccount())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .idempotencyKey(payment.getIdempotencyKey())
                    .paymentProcessorId(payment.getId())
                    .correlationId(payment.getCorrelationId())
                    .build();

            AJBankResponse ajResponse = ajBankClient.transferMoney(ajRequest);

            // 4. Handle Response based on Implementation Plan
            if ("LOCKED_PENDING_RETRY".equals(ajResponse.getStatus())) {
                handleLockContention(payment, ajResponse.getMessage());
            } else if ("PENDING_RETRY".equals(ajResponse.getStatus())) {
                handleInfrastructureFailure(payment, ajResponse.getMessage());
            } else {
                // Handshake OK (200) -> Move to SENT_AWAITING_RESPONSE
                payment.setStatus(Payment.PaymentStatus.SENT_AWAITING_RESPONSE);
                payment.setGatewayResponse(ajResponse.getMessage());
                payment.setProcessedAt(LocalDateTime.now());
                paymentRepository.save(payment);
                
                logger.info("Handshake successful for payment {}. Status: SENT_AWAITING_RESPONSE. Waiting for webhook.", payment.getId());
                logTransaction(payment, "AJBANK_HANDSHAKE", "PENDING", "SENT_AWAITING_RESPONSE", "Handshake Accepted: " + ajResponse.getMessage());
            }

        } catch (Exception e) {
            logger.error("AJBank handshake exception for payment {}: {}", payment.getId(), e.getMessage());
            handleInfrastructureFailure(payment, e.getMessage());
        }
    }

    private void handleLockContention(Payment payment, String message) {
        String oldStatus = payment.getStatus().name();
        payment.setStatus(Payment.PaymentStatus.LOCKED_PENDING_RETRY);
        payment.setRetryReason(RetryReason.ACCOUNT_LOCKED);
        payment.setLockedContentionCount(payment.getLockedContentionCount() + 1);
        
        // Exponential backoff: 5s, 10s, 20s... (capped at 30 min)
        long delaySeconds = Math.min(5L * (long) Math.pow(2, payment.getRetryCount()), 1800L);
        payment.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
        payment.setGatewayResponse(message);
        paymentRepository.save(payment);

        logger.warn("Account Locked for payment {}. Retrying in {}s | attempt: {}", payment.getId(), delaySeconds, payment.getRetryCount());
        logTransaction(payment, "LOCK_CONTENTION", oldStatus, "LOCKED_PENDING_RETRY", message);
    }

    private void handleInfrastructureFailure(Payment payment, String message) {
        String oldStatus = payment.getStatus().name();
        payment.setStatus(Payment.PaymentStatus.PENDING_RETRY);
        payment.setRetryReason(RetryReason.BANK_UNAVAILABLE);
        
        // Exponential backoff: 30s, 60s, 120s... (capped at 30 min)
        // Development override: 20s
        long initialDelay = 30L; 
        long delaySeconds = Math.min(initialDelay * (long) Math.pow(2, payment.getRetryCount()), 1800L);
        
        payment.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
        payment.setGatewayResponse(message);
        paymentRepository.save(payment);

        logger.warn("Bank Unavailable for payment {}. Retrying in {}s | attempt: {}", payment.getId(), delaySeconds, payment.getRetryCount());
        logTransaction(payment, "INFRA_FAILURE", oldStatus, "PENDING_RETRY", message);
    }

    @Transactional
    public void retryPaymentExecution(Payment payment) {
        logger.info("Executing retry for payment {} | status: {} | attempt: {}", 
                payment.getId(), payment.getStatus(), payment.getRetryCount());
        
        payment.setRetryCount(payment.getRetryCount() + 1);
        payment.setLastFailedAt(LocalDateTime.now());
        
        if (payment.getRetryCount() > payment.getMaxRetries()) {
            logger.error("Max retries exceeded for payment {}. Marking as FAILURE.", payment.getId());
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setGatewayResponse("Max retries exceeded");
            paymentRepository.save(payment);
            publishProcessedEvent(payment);
            logTransaction(payment, "MAX_RETRIES_EXCEEDED", payment.getStatus().name(), "FAILURE", "Maximum retry attempts reached");
            return;
        }

        executeHandshake(payment);
    }
    
    

    @Autowired
    private com.rtps.processor.util.WebhookSignatureVerifier signatureVerifier;

    @Transactional
    public void handleWebhook(String payload, String signature) {
        logger.info("Received Webhook | signature: {}", signature);
        
        // 1. Verify Signature
        if (!signatureVerifier.verifySignature(payload, signature)) {
            logger.error("Webhook Signature Mismatch!");
            throw new RuntimeException("Webhook Signature Validation Failed");
        }

        try {
            // 2. Parse Payload
            AJBankResponse ajResponse;
            try {
                ajResponse = objectMapper.readValue(payload, AJBankResponse.class);
            } catch (Exception parseEx) {
                logger.error("JSON Parsing failed for webhook payload: {}", parseEx.getMessage());
                throw new RuntimeException("Webhook JSON Parsing Failed: " + parseEx.getMessage());
            }

            String correlationId = ajResponse.getCorrelationId();
            logger.info("Processing webhook for correlationId: {} | status: {}", correlationId, ajResponse.getStatus());

            // 3. Find and Update the latest Payment attempt (with retry for race conditions)
            Optional<Payment> paymentOpt = paymentRepository.findTopByCorrelationIdOrderByCreatedAtDesc(correlationId);
            
            if (paymentOpt.isEmpty()) {
                logger.info("Payment not found for correlationId: {}. Waiting 500ms for race condition...", correlationId);
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                paymentOpt = paymentRepository.findTopByCorrelationIdOrderByCreatedAtDesc(correlationId);
            }

            paymentOpt.ifPresentOrElse(payment -> {
                logger.info("Found payment {} for webhook. Current status: {}", payment.getId(), payment.getStatus());
                
                // Log receipt in a SEPARATE transaction so it's never rolled back
                logTransactionInternal(payment, "AJBANK_WEBHOOK_RECEIVED", payment.getStatus().name(), 
                        payment.getStatus().name(), "Webhook received: " + ajResponse.getStatus());

                if (payment.getStatus() == Payment.PaymentStatus.SENT_AWAITING_RESPONSE || 
                    payment.getStatus() == Payment.PaymentStatus.PENDING || 
                    payment.getStatus() == Payment.PaymentStatus.PENDING_RETRY ||
                    payment.getStatus() == Payment.PaymentStatus.LOCKED_PENDING_RETRY ||
                    payment.getStatus() == Payment.PaymentStatus.FAILED) { // FAILURE -> COMPLETED allowed
                    
                    String oldStatus = payment.getStatus().name();
                    if ("COMPLETED".equals(ajResponse.getStatus())) {
                        payment.setStatus(Payment.PaymentStatus.COMPLETED);
                        payment.setGatewayTransactionId(ajResponse.getTransactionId().toString());
                    } else if ("FAILED".equals(ajResponse.getStatus())) {
                        payment.setStatus(Payment.PaymentStatus.FAILED);
                    }
                    
                    payment.setGatewayResponse(ajResponse.getMessage());
                    payment.setProcessedAt(LocalDateTime.now());
                    paymentRepository.save(payment);
                    
                    logger.info("Payment {} updated to {} via webhook", payment.getId(), payment.getStatus());
                    
                    // Log the state change
                    logTransactionInternal(payment, "AJBANK_WEBHOOK_UPDATE", oldStatus, 
                            payment.getStatus().name(), "Status updated via webhook: " + ajResponse.getMessage());

                    try {
                        publishProcessedEvent(payment);
                    } catch (Exception kafkaEx) {
                        logger.error("Failed to notify Kafka after webhook update: {}", kafkaEx.getMessage());
                    }
                } else {
                    logger.info("Payment {} already in final status {}. No update needed.", payment.getId(), payment.getStatus());
                }
            }, () -> logger.warn("No payment found for correlationId: {} after retry.", correlationId));

        } catch (Exception e) {
            logger.error("Critical error in handleWebhook: {}", e.getMessage(), e);
            throw new RuntimeException("Webhook processing failed", e);
        }
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

        return mapToResponse(payment);
    }

    public AnalyticsResponse getDailyAnalytics(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<Payment> dailyPayments = paymentRepository.findAll().stream()
                .filter(p -> p.getCreatedAt().isAfter(startOfDay) && p.getCreatedAt().isBefore(endOfDay))
                .collect(Collectors.toList());

        long total = dailyPayments.size();
        long success = dailyPayments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED).count();
        long failure = dailyPayments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.FAILED).count();

        BigDecimal totalAmount = dailyPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
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

    private void publishProcessedEvent(Payment payment) {
        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .gatewayTransactionId(payment.getGatewayTransactionId())
                .correlationId(payment.getCorrelationId())
                .message(payment.getGatewayResponse())
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
                .message(payment.getGatewayResponse())
                .build();
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logTransactionInternal(Payment payment, String action, String fromStatus, String toStatus, String message) {
        try {
            PaymentTransactionLog log = PaymentTransactionLog.builder()
                    .payment(payment)
                    .action(action)
                    .fromStatus(fromStatus)
                    .toStatus(toStatus)
                    .message(message)
                    .correlationId(payment.getCorrelationId())
                    .createdAt(LocalDateTime.now())
                    .build();
            logRepository.save(log);
        } catch (Exception e) {
            logger.error("Failed to persist transaction log: {}", e.getMessage());
        }
    }

    private void logTransaction(Payment payment, String action, String fromStatus, String toStatus, String message) {
        logTransactionInternal(payment, action, fromStatus, toStatus, message);
    }

}
