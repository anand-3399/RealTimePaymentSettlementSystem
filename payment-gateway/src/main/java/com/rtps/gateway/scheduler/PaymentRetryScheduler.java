package com.rtps.gateway.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.rtps.gateway.entity.Payment;
import com.rtps.gateway.repository.PaymentRepository;
import com.rtps.gateway.service.PaymentService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PaymentRetryScheduler {

	private final PaymentRepository paymentRepository;

	private final PaymentService paymentService;

	PaymentRetryScheduler(PaymentRepository paymentRepository, PaymentService paymentService) {
		this.paymentRepository = paymentRepository;
		this.paymentService = paymentService;
	}

	@Scheduled(fixedDelayString = "#{@configService.getConfigAsString('PAYMENT_RETRY_SCHEDULER_DELAY_MS')}")
	public void retryPendingPayments() {
		log.debug("Checking for payments due for retry...");

		List<Payment.PaymentStatus> retriableStatuses = List.of(Payment.PaymentStatus.PENDING_RETRY,
				Payment.PaymentStatus.LOCKED_PENDING_RETRY);

		List<Payment> duePayments = paymentRepository.findPaymentsDueForRetry(retriableStatuses, LocalDateTime.now());

		if (duePayments.isEmpty()) {
			return;
		}

		log.info("Found {} payments due for retry", duePayments.size());

		for (Payment payment : duePayments) {
			try {
				paymentService.retryPaymentExecution(payment);
			} catch (Exception e) {
				log.error("Error during scheduled retry for payment {}: {}", payment.getId(), e);
			}
		}
	}
}
