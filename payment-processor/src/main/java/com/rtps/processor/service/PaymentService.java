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

    @Transactional
    public void processPayment(UUID orderId, String userId, BigDecimal amount, String currency, 
                               String senderAccount, String recipientAccount, String correlationId, 
                               String idempotencyKey) {
        
        // Check if payment already exists (idempotency)
        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            logger.warn("Payment already exists for orderId: {}", orderId);
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
            } else if ("PENDING_RETRY".equals(ajResponse.getStatus())) {
                payment.setStatus(Payment.PaymentStatus.PENDING_RETRY);
            } else {
                payment.setStatus(Payment.PaymentStatus.FAILED);
            }

            payment.setProcessedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Log Transaction
            logTransaction(payment, "AJBANK_TRANSFER", "PENDING", payment.getStatus().name(), "AJBank Response: " + ajResponse.getMessage());

            // Notify Order Service
            publishProcessedEvent(payment);

        } catch (Exception e) {
            logger.error("AJBank call failed for payment {}: {}", payment.getId(), e.getMessage());
            payment.setStatus(Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);
            publishProcessedEvent(payment);
        }
    }
    
    
    @Transactional
    public void retryPayment(Payment payment) {

//        payment = paymentRepository.save(payment);

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
            } else if ("PENDING_RETRY".equals(ajResponse.getStatus())) {
                payment.setStatus(Payment.PaymentStatus.PENDING_RETRY);
            } else {
                payment.setStatus(Payment.PaymentStatus.FAILED);
            }

            payment.setProcessedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Log Transaction
            logTransaction(payment, "AJBANK_TRANSFER", "PENDING_RETRY", payment.getStatus().name(), "AJBank Response: " + ajResponse.getMessage());

            // Notify Order Service
            publishProcessedEvent(payment);

        } catch (Exception e) {
            logger.error("AJBank call failed for payment {}: {}", payment.getId(), e.getMessage());
            payment.setStatus(Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);
            publishProcessedEvent(payment);
        }
    }

    @Transactional
    public void handleWebhook(String payload, String signature) {
        logger.info("Handling AJBank Webhook | payload: {}", payload);
        // Signature verification and parsing logic would go here
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
                .createdAt(LocalDateTime.now())
                .build();
        logRepository.save(log);
    }

    @Transactional
    public void retryPaymentExecution(Payment payment) {
        retryPayment(
            payment
        );
    }
}
