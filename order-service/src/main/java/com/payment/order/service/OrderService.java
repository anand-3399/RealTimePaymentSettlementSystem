package com.payment.order.service;

import com.payment.order.dto.CreateOrderRequest;
import com.payment.order.dto.OrderResponse;
import com.payment.order.entity.Order;
import com.payment.order.event.KafkaProducer;
import com.payment.order.event.OrderCreatedEvent;
import com.payment.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
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
    private KafkaProducer kafkaProducer;

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(CreateOrderRequest request) {
        String correlationId = MDC.get("correlationId");
        logger.info("Processing order creation | username: {} | amount: {} | correlationId: {}", 
                request.getUserId(), request.getAmount(), correlationId);

        // 1. Check Idempotency
        Optional<UUID> existingOrderId = idempotencyService.getOrderId(request.getIdempotencyKey());
        if (existingOrderId.isPresent()) {
            logger.warn("Audit: Duplicate order attempt detected - returning cached response | idempotencyKey: {} | correlationId: {}", 
                    request.getIdempotencyKey(), correlationId);
            Order existingOrder = orderRepository.findById(existingOrderId.get())
                    .orElseThrow(() -> new RuntimeException("Order linked to idempotency key not found"));
            return mapToResponse(existingOrder);
        }

        // 2. Validate Request
        validationService.validateOrderRequest(request);

        // 3. Create Order
        Order order = Order.builder()
                .username(request.getUserId())
                .recipientBankAccount(request.getRecipientBankAccount())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .status(Order.OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Order savedOrder = orderRepository.save(order);

        // 4. Save Idempotency Key
        idempotencyService.saveKey(request.getIdempotencyKey(), savedOrder.getOrderId());

        // 5. Publish Event (Synchronous to ensure rollback on failure)
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(savedOrder.getOrderId().toString())
                .userId(savedOrder.getUsername())
                .amount(savedOrder.getAmount())
                .recipientAccount(savedOrder.getRecipientBankAccount())
                .timestamp(savedOrder.getCreatedAt())
                .build();
        
        try {
            kafkaProducer.sendOrderCreatedEventSync(event);
            logger.info("Audit: Order created and event published | orderId: {} | correlationId: {}", 
                    savedOrder.getOrderId(), correlationId);
        } catch (Exception e) {
            logger.error("Audit: Kafka failure - rolling back transaction | orderId: {} | error: {} | correlationId: {}", 
                    savedOrder.getOrderId(), e.getMessage(), correlationId);
            throw new RuntimeException("Reliability failure: Could not publish order event. Transaction rolled back.", e);
        }

        return mapToResponse(savedOrder);
    }

    public OrderResponse getOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
    }

    public List<OrderResponse> getOrdersByUserId(String userId) {
        return orderRepository.findByUsername(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public org.springframework.data.domain.Page<OrderResponse> getOrdersByUserIdPaged(String userId, org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findByUsername(userId, pageable).map(this::mapToResponse);
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus().name())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
