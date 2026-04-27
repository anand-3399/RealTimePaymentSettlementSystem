package com.rtps.processor.service;

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

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Transactional
    public void processPayment(UUID orderId, String userId, BigDecimal amount, String currency, 
                               String senderAccount, String recipientAccount, String correlationId, 
                               String idempotencyKey) {
        
        // Check if payment already exists (idempotency)
        java.util.Optional<Payment> existingPayment = paymentRepository.findByOrderId(orderId);
        if (existingPayment.isPresent()) {
            logger.warn("Payment already exists for orderId: {} | currentStatus: {}", 
                    orderId, existingPayment.get().getStatus());
            return;
        }

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
                .build();

        payment = paymentRepository.save(payment);

        try {
            AJBankRequest ajRequest = AJBankRequest.builder()
                    .senderAccount(senderAccount)
                    .recipientAccount(recipientAccount)
                    .amount(amount)
                    .currency(currency)
                    .idempotencyKey(idempotencyKey)
                    .paymentProcessorId(payment.getId())
                    .correlationId(correlationId)
                    .build();

            AJBankResponse ajResponse = ajBankClient.transferMoney(ajRequest);

            if ("COMPLETED".equals(ajResponse.getStatus())) {
                payment.setStatus(Payment.PaymentStatus.COMPLETED);
                payment.setGatewayTransactionId(ajResponse.getTransactionId().toString());
            } else if ("PENDING".equals(ajResponse.getStatus()) || "PENDING_RETRY".equals(ajResponse.getStatus())) {
                payment.setStatus(Payment.PaymentStatus.PENDING);
            } else {
                payment.setStatus(Payment.PaymentStatus.FAILED);
            }

            payment.setGatewayResponse(ajResponse.getMessage());
            payment.setProcessedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Log Transaction
            logTransaction(payment, "AJBANK_TRANSFER", "PENDING", payment.getStatus().name(), "AJBank Response: " + ajResponse.getMessage());

            // ONLY notify Order Service if we have a final result (COMPLETED or FAILED)
            // If it's PENDING, we wait for the Webhook!
            if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
                publishProcessedEvent(payment);
            } else {
                logger.info("Payment {} is PENDING at AJBank. Waiting for webhook.", payment.getId());
            }

        } catch (Exception e) {
            logger.error("AJBank call failed for payment {}: {}", payment.getId(), e.getMessage());
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setGatewayResponse(e.getMessage());
            paymentRepository.save(payment);
            publishProcessedEvent(payment);
        }
    }
    
    
    @Transactional
    public void retryPayment(Payment payment) {
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

            if ("COMPLETED".equals(ajResponse.getStatus())) {
                payment.setStatus(Payment.PaymentStatus.COMPLETED);
                payment.setGatewayTransactionId(ajResponse.getTransactionId().toString());
            } else if ("PENDING".equals(ajResponse.getStatus()) || "PENDING_RETRY".equals(ajResponse.getStatus())) {
                payment.setStatus(Payment.PaymentStatus.PENDING);
            } else {
                payment.setStatus(Payment.PaymentStatus.FAILED);
            }

            payment.setProcessedAt(LocalDateTime.now());
            payment.setGatewayResponse(ajResponse.getMessage());
            paymentRepository.save(payment);

            // Log Transaction
            logTransaction(payment, "AJBANK_TRANSFER", "PENDING_RETRY", payment.getStatus().name(), "AJBank Response: " + ajResponse.getMessage());

            // ONLY notify Order Service if we have a final result
            if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
                publishProcessedEvent(payment);
            }

        } catch (Exception e) {
            logger.error("AJBank call failed for payment {}: {}", payment.getId(), e.getMessage());
            payment.setStatus(Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);
            publishProcessedEvent(payment);
        }
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
            java.util.Optional<Payment> paymentOpt = paymentRepository.findTopByCorrelationIdOrderByCreatedAtDesc(correlationId);
            
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

                if (payment.getStatus() == Payment.PaymentStatus.PENDING || 
                    payment.getStatus() == Payment.PaymentStatus.PENDING_RETRY) {
                    
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

    @Transactional
    public void retryPaymentExecution(Payment payment) {
        retryPayment(
            payment
        );
    }
}
