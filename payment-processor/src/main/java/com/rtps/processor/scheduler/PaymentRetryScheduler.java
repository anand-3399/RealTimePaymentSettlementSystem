package com.rtps.processor.scheduler;

import com.rtps.processor.entity.Payment;
import com.rtps.processor.repository.PaymentRepository;
import com.rtps.processor.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PaymentRetryScheduler {

	private static final Logger logger = LoggerFactory.getLogger(PaymentRetryScheduler.class);

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentService paymentService;
 
	@Scheduled(fixedDelay = 6000) // Every 1 minute
	public void retryPendingPayments() {
		logger.debug("Running background payment retry job...");

        List<Payment> retriablePayments = paymentRepository.findByStatus(Payment.PaymentStatus.PENDING_RETRY); 
//		List<Payment> retriablePayments = paymentRepository
//				.findByStatusIn(List.of(Payment.PaymentStatus.PENDING_RETRY, Payment.PaymentStatus.FAILED));
 
		if (retriablePayments.isEmpty()) {
			return;
		}

		logger.info("Found {} payments pending retry", retriablePayments.size());

		for (Payment payment : retriablePayments) {
			try {
				// If nextRetryAt is set, respect it
				if (payment.getNextRetryAt() != null && payment.getNextRetryAt().isAfter(LocalDateTime.now())) {
					continue;
				}

				if (payment.getRetryCount() >= payment.getMaxRetries()) {
					logger.warn("Payment {} exceeded max retries. Marking as FAILED.", payment.getId());
					payment.setStatus(Payment.PaymentStatus.FAILED);
					payment.setFailedAt(LocalDateTime.now());
					paymentRepository.save(payment);
					continue;
				}

				logger.info("Retrying payment {} | Attempt {}/{}", payment.getId(), payment.getRetryCount() + 1,
						payment.getMaxRetries());

				payment.setRetryCount(payment.getRetryCount() + 1);
				payment.setLastRetryAt(LocalDateTime.now());
				paymentRepository.save(payment);

				// Re-trigger the processing logic
				// Since processPayment handles its own transaction, we call it directly
				// Note: We need to pass the event data, but we can reconstruct it from the
				// payment entity
				paymentService.retryPaymentExecution(payment);

			} catch (Exception e) {
				logger.error("Failed to retry payment {}: {}", payment.getId(), e.getMessage());
			}
		}
	}
}
