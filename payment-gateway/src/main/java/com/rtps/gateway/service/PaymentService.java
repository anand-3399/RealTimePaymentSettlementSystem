package com.rtps.gateway.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtps.gateway.client.Bank1Client;
import com.rtps.gateway.dto.AccountBalance;
import com.rtps.gateway.dto.AnalyticsResponse;
import com.rtps.gateway.dto.Bank1Request;
import com.rtps.gateway.dto.Bank1Response;
import com.rtps.gateway.dto.PaymentProcessedEvent;
import com.rtps.gateway.dto.PaymentResponse;
import com.rtps.gateway.entity.Payment;
import com.rtps.gateway.entity.PaymentTransactionLog;
import com.rtps.gateway.entity.RetryReason;
import com.rtps.gateway.entity.WebhookLog;
import com.rtps.gateway.producer.KafkaProducer;
import com.rtps.gateway.repository.PaymentRepository;
import com.rtps.gateway.repository.PaymentTransactionLogRepository;
import com.rtps.gateway.repository.WebhookLogRepository;
import com.rtps.gateway.util.WebhookSignatureVerifier;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentService {

	private final PaymentRepository paymentRepository;

	private final ConfigService configService;

	private final PaymentTransactionLogRepository logRepository;

	private final WebhookLogRepository webhookLogRepository;

	private final Bank1Client ajBankClient;

	private final KafkaProducer kafkaProducer;

	private final ObjectMapper objectMapper;

	private final WebhookSignatureVerifier signatureVerifier;

	PaymentService(PaymentRepository paymentRepository, ConfigService configService,
			PaymentTransactionLogRepository logRepository, WebhookLogRepository webhookLogRepository,
			Bank1Client ajBankClient, KafkaProducer kafkaProducer, ObjectMapper objectMapper,
			WebhookSignatureVerifier signatureVerifier) {
		this.paymentRepository = paymentRepository;
		this.configService = configService;
		this.logRepository = logRepository;
		this.webhookLogRepository = webhookLogRepository;
		this.ajBankClient = ajBankClient;
		this.kafkaProducer = kafkaProducer;
		this.objectMapper = objectMapper;
		this.signatureVerifier = signatureVerifier;
	}

	@Transactional
	public void processPayment(UUID orderId, String userId, BigDecimal amount, String currency, String senderAccount,
			String recipientAccount, String correlationId, String idempotencyKey) {

		// 1. Idempotency Check
		Optional<Payment> existingPayment = paymentRepository.findByOrderId(orderId);
		if (existingPayment.isPresent()) {
			log.warn("Payment already exists for orderId: {} | currentStatus: {}", orderId,
					existingPayment.get().getStatus());
			return;
		}

		// 2. Initial Save as PENDING
		Payment payment = Payment.builder().orderId(orderId).userId(userId).amount(amount).currency(currency)
				.senderAccount(senderAccount).recipientAccount(recipientAccount).status(Payment.PaymentStatus.PENDING)
				.correlationId(correlationId).idempotencyKey(idempotencyKey).createdAt(LocalDateTime.now())
				.retryCount(0).maxRetries(configService.getConfigAsInt("PAYMENT_MAX_RETRY_ATTEMPTS")).build();

		payment = paymentRepository.save(payment);

		// 3. GATEWAY VALIDATION: Check balance BEFORE calling bank
		Optional<AccountBalance> balanceOpt = ajBankClient.getAccountBalance(senderAccount);

		if (balanceOpt.isEmpty()) {
			// Infrastructure failure
			payment.setStatus(Payment.PaymentStatus.PENDING_RETRY);
			payment.setRetryReason(RetryReason.BANK_UNAVAILABLE);
			payment.setNextRetryAt(LocalDateTime.now().plusSeconds(2));
			paymentRepository.save(payment);
			return;
		}

		AccountBalance balance = balanceOpt.get();

		// Check 1: Sufficient balance?
		if (balance.getBalance().compareTo(amount) < 0) {
			payment.setStatus(Payment.PaymentStatus.FAILED);
			payment.setRetryReason(RetryReason.INSUFFICIENT_BALANCE);
			payment.setGatewayResponse(
					"Insufficient balance. Required: " + amount + " | Available: " + balance.getBalance());
			paymentRepository.save(payment);
			publishProcessedEvent(payment);
			return;
		}

		// Check 2: Account status?
		if (!"ACTIVE".equals(balance.getStatus())) {
			payment.setStatus(Payment.PaymentStatus.FAILED);
			payment.setRetryReason(RetryReason.ACCOUNT_FROZEN);
			payment.setGatewayResponse("Account is " + balance.getStatus());
			paymentRepository.save(payment);
			publishProcessedEvent(payment);
			return;
		}

		// 4. Bank Handshake (All checks passed)
		executeHandshake(payment);
	}

	private void executeHandshake(Payment payment) {
		try {
			Bank1Request ajRequest = Bank1Request.builder().senderAccount(payment.getSenderAccount())
					.recipientAccount(payment.getRecipientAccount()).amount(payment.getAmount())
					.currency(payment.getCurrency()).idempotencyKey(payment.getIdempotencyKey())
					.paymentGatewayId(payment.getId()).correlationId(payment.getCorrelationId()).build();

			Bank1Response ajResponse = ajBankClient.transferMoney(ajRequest);

			// 4. Handle Response based on Implementation Plan
			if ("LOCKED_PENDING_RETRY".equals(ajResponse.getStatus())) {
				handleLockContention(payment, ajResponse.getMessage());
			} else if ("PENDING_RETRY".equals(ajResponse.getStatus())) {
				handleInfrastructureFailure(payment, ajResponse.getMessage());
			} else if ("FAILED".equals(ajResponse.getStatus())) {
				// The user specifically requested that FAILED status should be picked up by the
				// retry scheduler
				// Using handleInfrastructureFailure delegates it to PENDING_RETRY with
				// exponential backoff
				log.warn("Handshake rejected for payment {}. Converting FAILED to retryable state.", payment.getId());
				handleInfrastructureFailure(payment, ajResponse.getMessage());
			} else if ("PENDING".equals(ajResponse.getStatus())) {
				// Handshake Created (201) -> Move to SENT_AWAITING_RESPONSE
				payment.setStatus(Payment.PaymentStatus.SENT_AWAITING_RESPONSE);
				payment.setGatewayResponse(ajResponse.getMessage());
				payment.setProcessedAt(LocalDateTime.now());
				paymentRepository.save(payment);

				log.info(
						"Handshake successful (201 CREATED) for payment {}. Status: SENT_AWAITING_RESPONSE. Waiting for webhook.",
						payment.getId());
				logTransaction(payment, "BANK1_HANDSHAKE", "PENDING", "SENT_AWAITING_RESPONSE",
						"Handshake Accepted (201): " + ajResponse.getMessage());
			} else {
				// Any other unexpected status code or missing body
				handleInfrastructureFailure(payment, "Unexpected status returned: " + ajResponse.getStatus());
			}

		} catch (Exception e) {
			log.error("Bank1 handshake exception for payment {}: {}", payment.getId(), e);
			handleInfrastructureFailure(payment, e.getMessage());
		}
	}

	private void handleLockContention(Payment payment, String message) {
		String oldStatus = payment.getStatus().name();
		payment.setLockedContentionCount(payment.getLockedContentionCount() + 1);

		int maxLock = configService.getConfigAsInt("PAYMENT_MAX_LOCK_CONTENTIONS");
		if (payment.getLockedContentionCount() > maxLock) {
			log.error("Payment {} exceeded lock contention threshold. Failing payment.", payment.getId());
			payment.setStatus(Payment.PaymentStatus.FAILED);
			payment.setGatewayResponse("Max lock contention retries exceeded");
			paymentRepository.save(payment);
			publishProcessedEvent(payment);
			logTransaction(payment, "LOCK_CONTENTION_EXCEEDED", oldStatus, "FAILED",
					"Maximum lock contention attempts reached");
			return;
		}

		payment.setStatus(Payment.PaymentStatus.LOCKED_PENDING_RETRY);
		payment.setRetryReason(RetryReason.ACCOUNT_LOCKED);

		// Exponential backoff
		long baseDelay = configService.getConfigAsLong("PAYMENT_RETRY_DELAY_ACCOUNT_LOCKED_SEC");
		long delaySeconds = Math.min(baseDelay * (long) Math.pow(2, payment.getRetryCount()), 1800L);
		payment.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
		payment.setGatewayResponse(message);
		paymentRepository.save(payment);

		log.warn("Account Locked for payment {}. Retrying in {}s | attempt: {}", payment.getId(), delaySeconds,
				payment.getRetryCount());
		logTransaction(payment, "LOCK_CONTENTION", oldStatus, "LOCKED_PENDING_RETRY", message);
	}

	private void handleInfrastructureFailure(Payment payment, String message) {
		String oldStatus = payment.getStatus().name();
		payment.setStatus(Payment.PaymentStatus.PENDING_RETRY);
		payment.setRetryReason(RetryReason.BANK_UNAVAILABLE);

		// Exponential backoff: 30s, 60s, 120s... (capped at 30 min)
		long initialDelay = configService.getConfigAsLong("PAYMENT_RETRY_DELAY_BANK_UNAVAILABLE_SEC");
		long delaySeconds = Math.min(initialDelay * (long) Math.pow(2, payment.getRetryCount()), 1800L);

		payment.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
		payment.setGatewayResponse(message);
		paymentRepository.save(payment);

		log.warn("Bank Unavailable for payment {}. Retrying in {}s | attempt: {}", payment.getId(), delaySeconds,
				payment.getRetryCount());
		logTransaction(payment, "INFRA_FAILURE", oldStatus, "PENDING_RETRY", message);
	}

	@Transactional
	public void retryPaymentExecution(Payment payment) {
		log.info("Executing retry for payment {} | status: {} | attempt: {}", payment.getId(), payment.getStatus(),
				payment.getRetryCount());

		payment.setRetryCount(payment.getRetryCount() + 1);
		payment.setLastFailedAt(LocalDateTime.now());

		if (payment.getRetryCount() > payment.getMaxRetries()) {
			log.error("Max retries exceeded for payment {}. Marking as FAILURE.", payment.getId());
			payment.setStatus(Payment.PaymentStatus.FAILED);
			payment.setGatewayResponse("Max retries exceeded");
			paymentRepository.save(payment);
			publishProcessedEvent(payment);
			logTransaction(payment, "MAX_RETRIES_EXCEEDED", payment.getStatus().name(), "FAILURE",
					"Maximum retry attempts reached");
			return;
		}

		executeHandshake(payment);
	}

	@Transactional
	public void handleWebhook(String payload, String signature) {
		log.info("Received Webhook | signature: {}", signature);

		WebhookLog logEntry = new WebhookLog();
		logEntry.setSignature(signature);
		logEntry.setRequestBody(payload);

		// 1. Verify Signature
		boolean isValid = signatureVerifier.verifySignature(payload, signature);
		logEntry.setSignatureValid(isValid);

		if (!isValid) {
			webhookLogRepository.save(logEntry);
			log.error("Webhook Signature Mismatch!");
			throw new RuntimeException("Webhook Signature Validation Failed");
		}

		try {
			// 2. Parse Payload
			Bank1Response ajResponse;
			try {
				ajResponse = objectMapper.readValue(payload, Bank1Response.class);
			} catch (Exception parseEx) {
				log.error("JSON Parsing failed for webhook payload: {}", parseEx);
				throw new RuntimeException("Webhook JSON Parsing Failed: " + parseEx.getMessage());
			}

			String correlationId = ajResponse.getCorrelationId();
			log.info("Processing webhook for correlationId: {} | status: {}", correlationId, ajResponse.getStatus());

			logEntry.setCorrelationId(correlationId);
			logEntry.setEventType(ajResponse.getStatus());
			logEntry.setResponseStatus(200);

			// 3. Find and Update the latest Payment attempt (with retry for race
			// conditions)
			Optional<Payment> paymentOpt = paymentRepository.findTopByCorrelationIdOrderByCreatedAtDesc(correlationId);

			if (paymentOpt.isEmpty()) {
				log.info("Payment not found for correlationId: {}. Waiting 500ms for race condition...", correlationId);
				try {
					Thread.sleep(500);
				} catch (InterruptedException ignored) {
				}
				paymentOpt = paymentRepository.findTopByCorrelationIdOrderByCreatedAtDesc(correlationId);
			}

			paymentOpt.ifPresentOrElse(payment -> {
				log.info("Found payment {} for webhook. Current status: {}", payment.getId(), payment.getStatus());

				logEntry.setPaymentGatewayId(payment.getId());
				logEntry.setProcessedAt(LocalDateTime.now());
				webhookLogRepository.save(logEntry);

				// Log receipt in a SEPARATE transaction so it's never rolled back
				logTransactionInternal(payment, "BANK1_WEBHOOK_RECEIVED", payment.getStatus().name(),
						payment.getStatus().name(), "Webhook received: " + ajResponse.getStatus());

				if (payment.getStatus() == Payment.PaymentStatus.SENT_AWAITING_RESPONSE
						|| payment.getStatus() == Payment.PaymentStatus.PENDING
						|| payment.getStatus() == Payment.PaymentStatus.PENDING_RETRY
						|| payment.getStatus() == Payment.PaymentStatus.LOCKED_PENDING_RETRY
						|| payment.getStatus() == Payment.PaymentStatus.FAILED) { // FAILURE -> COMPLETED allowed

					String oldStatus = payment.getStatus().name();
					if ("COMPLETED".equals(ajResponse.getStatus())) {
						payment.setStatus(Payment.PaymentStatus.COMPLETED);
						payment.setBankReferenceId(ajResponse.getTransactionId().toString());
					} else if ("FAILED".equals(ajResponse.getStatus())) {
						payment.setStatus(Payment.PaymentStatus.FAILED);
					}

					payment.setGatewayResponse(ajResponse.getMessage());
					payment.setProcessedAt(LocalDateTime.now());
					paymentRepository.save(payment);

					log.info("Payment {} updated to {} via webhook", payment.getId(), payment.getStatus());

					// Log the state change
					logTransactionInternal(payment, "BANK1_WEBHOOK_UPDATE", oldStatus, payment.getStatus().name(),
							"Status updated via webhook: " + ajResponse.getMessage());

					try {
						publishProcessedEvent(payment);
					} catch (Exception kafkaEx) {
						log.error("Failed to notify Kafka after webhook update: {}", kafkaEx);
					}
				} else {
					log.info("Payment {} already in final status {}. No update needed.", payment.getId(),
							payment.getStatus());
				}
			}, () -> {
				webhookLogRepository.save(logEntry);
				log.warn("No payment found for correlationId: {} after retry.", correlationId);
			});

		} catch (Exception e) {
			logEntry.setResponseStatus(500);
			webhookLogRepository.save(logEntry);
			log.error("Critical error in handleWebhook: {}", e.getMessage(), e);
			throw new RuntimeException("Webhook processing failed", e);
		}
	}

	public PaymentResponse getPaymentDetails(UUID paymentGatewayId) {
		Payment payment = paymentRepository.findById(paymentGatewayId)
				.orElseThrow(() -> new RuntimeException("Payment not found: " + paymentGatewayId));
		return mapToResponse(payment);
	}

	public java.util.Optional<PaymentResponse> getPaymentByOrderId(UUID orderId) {
		return paymentRepository.findByOrderId(orderId).map(this::mapToResponse);
	}

	public List<PaymentResponse> listPayments(UUID orderId, String status) {
		return paymentRepository.findAll().stream().filter(p -> orderId == null || p.getOrderId().equals(orderId))
				.filter(p -> status == null || p.getStatus().name().equalsIgnoreCase(status)).map(this::mapToResponse)
				.collect(Collectors.toList());
	}

	@Transactional
	public PaymentResponse retryPayment(UUID paymentGatewayId) {
		Payment payment = paymentRepository.findById(paymentGatewayId)
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

		BigDecimal totalAmount = dailyPayments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
				.map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

		return AnalyticsResponse.builder().date(date.toString())
				.metrics(AnalyticsResponse.Metrics.builder().totalTransactions(total).successCount(success)
						.failureCount(failure).totalAmount(totalAmount)
						.successRate(total > 0 ? (double) success / total * 100 : 0.0)
						.averageAmount(total > 0 ? totalAmount.divide(BigDecimal.valueOf(total), RoundingMode.HALF_UP)
								: BigDecimal.ZERO)
						.build())
				.statusBreakdown(dailyPayments.stream()
						.collect(Collectors.groupingBy(p -> p.getStatus().name(), Collectors.counting())))
				.build();
	}

	private void publishProcessedEvent(Payment payment) {
		PaymentProcessedEvent event = PaymentProcessedEvent.builder().paymentGatewayId(payment.getId())
				.orderId(payment.getOrderId()).userId(payment.getUserId()).amount(payment.getAmount())
				.status(payment.getStatus().name()).bankReferenceId(payment.getBankReferenceId())
				.correlationId(payment.getCorrelationId()).message(payment.getGatewayResponse())
				.timestamp(LocalDateTime.now()).build();
		kafkaProducer.publishPaymentProcessed(event);
	}

	private PaymentResponse mapToResponse(Payment payment) {
		return PaymentResponse.builder().paymentGatewayId(payment.getId()).orderId(payment.getOrderId())
				.userId(payment.getUserId()).senderAccount(maskAccount(payment.getSenderAccount()))
				.recipientAccount(maskAccount(payment.getRecipientAccount())).amount(payment.getAmount())
				.currency(payment.getCurrency()).status(payment.getStatus().name())
				.bankReferenceId(payment.getBankReferenceId()).processedAt(payment.getProcessedAt())
				.createdAt(payment.getCreatedAt()).correlationId(payment.getCorrelationId())
				.message(payment.getGatewayResponse()).build();
	}

	private String maskAccount(String account) {
		if (account == null || account.length() < 4)
			return "****";
		return "****" + account.substring(account.length() - 4);
	}

	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
	public void logTransactionInternal(Payment payment, String action, String fromStatus, String toStatus,
			String message) {
		try {
			PaymentTransactionLog log = PaymentTransactionLog.builder().payment(payment).action(action)
					.fromStatus(fromStatus).toStatus(toStatus).message(message)
					.correlationId(payment.getCorrelationId()).createdAt(LocalDateTime.now()).build();
			logRepository.save(log);
		} catch (Exception e) {
			log.error("Failed to persist transaction log: {}", e);
		}
	}

	private void logTransaction(Payment payment, String action, String fromStatus, String toStatus, String message) {
		logTransactionInternal(payment, action, fromStatus, toStatus, message);
	}

}
