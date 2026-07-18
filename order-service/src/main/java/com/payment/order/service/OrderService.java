package com.payment.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.order.client.PaymentGatewayClient;
import com.payment.order.dto.CreateOrderRequest;
import com.payment.order.dto.OrderPageResponse;
import com.payment.order.dto.OrderResponse;
import com.payment.order.dto.PaymentStatusResponse;
import com.payment.order.entity.Order;
import com.payment.order.entity.OutboxEvent;
import com.payment.order.entity.User;
import com.payment.order.event.OrderCreatedEvent;
import com.payment.order.event.OutboxEventCreated;
import com.payment.order.repository.OrderRepository;
import com.payment.order.repository.OutboxEventRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OrderService {

	private final OrderRepository orderRepository;

	private final ValidationService validationService;

	private final IdempotencyService idempotencyService;

	private final ApplicationEventPublisher eventPublisher;

	private final OutboxEventRepository outboxEventRepository;

	private final ObjectMapper objectMapper;

	private final PaymentGatewayClient paymentGatewayClient;

	OrderService(OrderRepository orderRepository, IdempotencyService idempotencyService,
			ApplicationEventPublisher eventPublisher, ValidationService validationService,
			OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper,
			PaymentGatewayClient paymentGatewayClient) {
		this.orderRepository = orderRepository;
		this.idempotencyService = idempotencyService;
		this.eventPublisher = eventPublisher;
		this.validationService = validationService;
		this.outboxEventRepository = outboxEventRepository;
		this.objectMapper = objectMapper;
		this.paymentGatewayClient = paymentGatewayClient;
	}

	@Transactional(rollbackFor = Exception.class)
	public OrderResponse createOrder(CreateOrderRequest request, String userId, String idempotencyKey) {
		String correlationId = MDC.get("correlationId");
		log.info("Processing order creation | username: {} | amount: {} | correlationId: {}", userId,
				request.getAmount(), correlationId);

		// 1. Check Idempotency
		Optional<UUID> existingOrderId = idempotencyService.getOrderId(idempotencyKey);
		if (existingOrderId.isPresent()) {
			log.warn(
					"Audit: Duplicate order attempt detected - returning cached response | idempotencyKey: {} | correlationId: {}",
					idempotencyKey, correlationId);
			Order existingOrder = orderRepository.findById(existingOrderId.get())
					.orElseThrow(() -> new RuntimeException("Order linked to idempotency key not found"));
			return mapToResponse(existingOrder);
		}

		// 2. Validate Request and Get User Details
		User user = validationService.validateOrderRequest(request, userId, idempotencyKey);

		// 3. Create Order
		Order order = Order.builder().username(userId).recipientBankAccount(request.getRecipientBankAccount())
				.senderBankAccount(user.getAccountNumber()).amount(request.getAmount()).currency(request.getCurrency())
				.description(request.getDescription()).status(Order.OrderStatus.PENDING).createdAt(LocalDateTime.now())
				.build();

		Order savedOrder = orderRepository.save(order);

		// 4. Save Idempotency Key
		idempotencyService.saveKey(idempotencyKey, savedOrder.getOrderId());

		// 5. Save to Outbox (Atomic with Order)
		try {
			OrderCreatedEvent kafkaEvent = OrderCreatedEvent.builder().orderId(savedOrder.getOrderId().toString())
					.userId(savedOrder.getUsername()).amount(savedOrder.getAmount()).currency(savedOrder.getCurrency())
					.recipientAccount(savedOrder.getRecipientBankAccount())
					.senderAccount(savedOrder.getSenderBankAccount()).timestamp(savedOrder.getCreatedAt())
					.correlationId(correlationId).idempotencyKey(idempotencyKey).build();

			OutboxEvent outboxEvent = OutboxEvent.builder().eventType("OrderCreatedEvent")
					.payload(objectMapper.writeValueAsString(kafkaEvent)).status(OutboxEvent.OutboxStatus.PENDING)
					.createdAt(LocalDateTime.now()).build();

			outboxEventRepository.save(outboxEvent);

			// 6. Publish local event for eager outbox publishing
			eventPublisher.publishEvent(new OutboxEventCreated(this));

			log.info("Order saved and event stored in outbox | orderId: {} | correlationId: {}",
					savedOrder.getOrderId(), correlationId);
		} catch (Exception e) {
			log.error("Failed to serialize or save outbox event for order {}: {}", savedOrder.getOrderId(),
					e.getMessage());
			throw new RuntimeException("Failed to prepare outbox event", e);
		}

		return mapToResponse(savedOrder);
	}

	@Transactional
	public OrderResponse getOrderById(UUID orderId) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

		// If payment details are missing locally, attempt to fetch them from the
		// Payment Processor
		if (order.getPaymentGatewayId() == null) {
			Optional<PaymentStatusResponse> paymentOpt = paymentGatewayClient.getPaymentStatusByOrderId(orderId);

			if (paymentOpt.isPresent()) {
				PaymentStatusResponse payment = paymentOpt.get();
				log.info("Real-time update: Payment found for order {}. Status: {}", orderId, payment.getStatus());

				// Update local status and tracking fields
				if ("COMPLETED".equalsIgnoreCase(payment.getStatus())) {
					order.setStatus(Order.OrderStatus.COMPLETED);
				} else if ("FAILED".equalsIgnoreCase(payment.getStatus())) {
					order.setStatus(Order.OrderStatus.FAILED);
				}

				order.setPaymentGatewayId(payment.getPaymentGatewayId());
				order.setBankReferenceId(payment.getBankReferenceId());
				order.setProcessedAt(payment.getProcessedAt());
				order.setReason(payment.getMessage());
				orderRepository.save(order);

				return mapToResponse(order);
			}
		}

		return mapToResponse(order);
	}

	public List<OrderResponse> getOrdersByUserId(String userId) {
		return orderRepository.findByUsername(userId).stream().map(this::mapToResponse).collect(Collectors.toList());
	}

	public Page<OrderResponse> getOrdersByUserIdPaged(String userId, Pageable pageable) {
		return orderRepository.findByUsername(userId, pageable).map(this::mapToResponse);
	}

	public OrderPageResponse getOrdersByUserIdPagedWithDate(String userId, LocalDateTime startDate,
			LocalDateTime endDate, String statusStr, String account, Pageable pageable) {
		Order.OrderStatus filterStatus = null;
		if (statusStr != null && !statusStr.trim().isEmpty() && !statusStr.equalsIgnoreCase("All")) {
			try {
				filterStatus = Order.OrderStatus.valueOf(statusStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				log.warn("Invalid status filter provided: {}", statusStr);
			}
		}

		String accountFilter = (account != null && !account.trim().isEmpty()) ? account.trim() : null;

		List<Order> orders = orderRepository.findByUsernameAndDateRange(userId, startDate, endDate, filterStatus,
				accountFilter, pageable);
		List<OrderResponse> content = orders.stream().map(this::mapToResponse).collect(Collectors.toList());

		List<Object[]> statusCounts = orderRepository.countStatusesByUsernameAndDateRange(userId, startDate, endDate,
				filterStatus, accountFilter);

		long total = 0;
		long success = 0;
		long failure = 0;

		for (Object[] row : statusCounts) {
			Order.OrderStatus status = (Order.OrderStatus) row[0];
			long count = ((Number) row[1]).longValue();
			total += count;

			if (status == Order.OrderStatus.COMPLETED || status == Order.OrderStatus.SUCCESS) {
				success += count;
			} else if (status == Order.OrderStatus.FAILED) {
				failure += count;
			}
		}

		return OrderPageResponse.builder().total(total).success(success).failure(failure).fetched(content.size())
				.content(content).build();
	}

	private OrderResponse mapToResponse(Order order) {
		OrderResponse response = OrderResponse.builder().orderId(order.getOrderId()).status(order.getStatus().name())
				.amount(order.getAmount()).currency(order.getCurrency()).senderAccount(order.getSenderBankAccount())
				.recipientAccount(order.getRecipientBankAccount()).createdAt(order.getCreatedAt()).build();

		if (order.getPaymentGatewayId() != null) {
			response.setPayment(OrderResponse.PaymentInfo.builder().paymentGatewayId(order.getPaymentGatewayId())
					.bankReferenceId(order.getBankReferenceId()).processedAt(order.getProcessedAt())
					.reason(order.getReason()).build());
		}

		return response;
	}
}
