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
 
	@Scheduled(fixedDelay = 10000) // Every 10 seconds for fast recovery
	public void retryPendingPayments() {
		logger.debug("Checking for payments due for retry...");

        List<Payment.PaymentStatus> retriableStatuses = List.of(
            Payment.PaymentStatus.PENDING_RETRY, 
            Payment.PaymentStatus.LOCKED_PENDING_RETRY
        );

        List<Payment> duePayments = paymentRepository.findPaymentsDueForRetry(retriableStatuses, LocalDateTime.now());
 
		if (duePayments.isEmpty()) {
			return;
		}

		logger.info("Found {} payments due for retry", duePayments.size());

		for (Payment payment : duePayments) {
			try {
				paymentService.retryPaymentExecution(payment);
			} catch (Exception e) {
				logger.error("Error during scheduled retry for payment {}: {}", payment.getId(), e.getMessage());
			}
		}
	}
}
