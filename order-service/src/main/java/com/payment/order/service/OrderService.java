package com.payment.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.order.client.PaymentProcessorClient;
import com.payment.order.dto.CreateOrderRequest;
import com.payment.order.dto.OrderResponse;
import com.payment.order.dto.PaymentStatusResponse;
import com.payment.order.entity.Order;
import com.payment.order.entity.OutboxEvent;
import com.payment.order.entity.User;
import com.payment.order.event.OrderCreatedEvent;
import com.payment.order.repository.OrderRepository;
import com.payment.order.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired 
    private ValidationService validationService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        String correlationId = MDC.get("correlationId");
        logger.info("Processing order creation | username: {} | amount: {} | correlationId: {}",
                request.getUserId(), request.getAmount(), correlationId);

        // 1. Check Idempotency
        Optional<UUID> existingOrderId = idempotencyService.getOrderId(idempotencyKey);
        if (existingOrderId.isPresent()) {
            logger.warn(
                    "Audit: Duplicate order attempt detected - returning cached response | idempotencyKey: {} | correlationId: {}",
                    idempotencyKey, correlationId);
            Order existingOrder = orderRepository.findById(existingOrderId.get())
                    .orElseThrow(() -> new RuntimeException("Order linked to idempotency key not found"));
            return mapToResponse(existingOrder);
        }

        // 2. Validate Request and Get User Details
        com.payment.order.entity.User user = validationService.validateOrderRequest(request, idempotencyKey);

        // 3. Create Order
        Order order = Order.builder()
                .username(request.getUserId())
                .recipientBankAccount(request.getRecipientBankAccount())
                .senderBankAccount(user.getAccountNumber())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .status(Order.OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Order savedOrder = orderRepository.save(order);

        // 4. Save Idempotency Key
        idempotencyService.saveKey(idempotencyKey, savedOrder.getOrderId());

        // 5. Save to Outbox (Atomic with Order)
        try {
            OrderCreatedEvent kafkaEvent = OrderCreatedEvent.builder()
                    .orderId(savedOrder.getOrderId().toString())
                    .userId(savedOrder.getUsername())
                    .amount(savedOrder.getAmount())
                    .currency(savedOrder.getCurrency())
                    .recipientAccount(savedOrder.getRecipientBankAccount())
                    .senderAccount(savedOrder.getSenderBankAccount())
                    .timestamp(savedOrder.getCreatedAt())
                    .correlationId(correlationId)
                    .idempotencyKey(idempotencyKey)
                    .build();

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventType("OrderCreatedEvent")
                    .payload(objectMapper.writeValueAsString(kafkaEvent))
                    .status(OutboxEvent.OutboxStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();

            outboxEventRepository.save(outboxEvent);
            
            // 6. Publish local event for eager outbox publishing
            eventPublisher.publishEvent(new com.payment.order.event.OutboxEventCreated(this));

            logger.info("Order saved and event stored in outbox | orderId: {} | correlationId: {}",
                    savedOrder.getOrderId(), correlationId);
        } catch (Exception e) {
            logger.error("Failed to serialize or save outbox event for order {}: {}", savedOrder.getOrderId(),
                    e.getMessage());
            throw new RuntimeException("Failed to prepare outbox event", e);
        }

        return mapToResponse(savedOrder);
    }

    @Autowired
    private PaymentProcessorClient paymentProcessorClient;

    @Transactional
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // If payment details are missing locally, attempt to fetch them from the
        // Payment Processor
        if (order.getPaymentId() == null) {
            Optional<PaymentStatusResponse> paymentOpt = paymentProcessorClient
                    .getPaymentStatusByOrderId(orderId);

            if (paymentOpt.isPresent()) {
                PaymentStatusResponse payment = paymentOpt.get();
                logger.info("Real-time update: Payment found for order {}. Status: {}", orderId, payment.getStatus());

                // Update local status and tracking fields
                if ("COMPLETED".equalsIgnoreCase(payment.getStatus())) {
                    order.setStatus(Order.OrderStatus.COMPLETED);
                } else if ("FAILED".equalsIgnoreCase(payment.getStatus())) {
                    order.setStatus(Order.OrderStatus.FAILED);
                }

                order.setPaymentId(payment.getPaymentId());
                order.setGatewayTransactionId(payment.getGatewayTransactionId());
                order.setProcessedAt(payment.getProcessedAt());
                order.setReason(payment.getMessage());
                orderRepository.save(order);

                return mapToResponse(order);
            }
        }

        return mapToResponse(order);
    }

    public List<OrderResponse> getOrdersByUserId(String userId) {
        return orderRepository.findByUsername(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public org.springframework.data.domain.Page<OrderResponse> getOrdersByUserIdPaged(String userId,
            org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findByUsername(userId, pageable).map(this::mapToResponse);
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse response = OrderResponse.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus().name())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .senderAccount(order.getSenderBankAccount())
                .recipientAccount(order.getRecipientBankAccount())
                .createdAt(order.getCreatedAt())
                .build();

        if (order.getPaymentId() != null) {
            response.setPayment(OrderResponse.PaymentInfo.builder()
                    .paymentId(order.getPaymentId())
                    .gatewayTransactionId(order.getGatewayTransactionId())
                    .processedAt(order.getProcessedAt())
                    .reason(order.getReason())
                    .build());
        }

        return response;
    }
}
