package com.payment.order.service;

import com.payment.order.dto.CreateOrderRequest;
import com.payment.order.dto.OrderResponse;
import com.payment.order.entity.Order;
import com.payment.order.event.OrderCreatedInternalEvent;
import com.payment.order.repository.OrderRepository;
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

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        String correlationId = MDC.get("correlationId");
        logger.info("Processing order creation | username: {} | amount: {} | correlationId: {}", 
                request.getUserId(), request.getAmount(), correlationId);

        // 1. Check Idempotency
        Optional<UUID> existingOrderId = idempotencyService.getOrderId(idempotencyKey);
        if (existingOrderId.isPresent()) {
            logger.warn("Audit: Duplicate order attempt detected - returning cached response | idempotencyKey: {} | correlationId: {}", 
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
        
        // 5. Publish Internal Event
        // This will be caught by OrderEventListener AFTER the transaction commits
        eventPublisher.publishEvent(new OrderCreatedInternalEvent(savedOrder));

        logger.info("Order saved in database. Event queued for publication | orderId: {} | correlationId: {}", 
                savedOrder.getOrderId(), correlationId);

        return mapToResponse(savedOrder);
    }

    @Autowired
    private com.payment.order.client.PaymentProcessorClient paymentProcessorClient;

    @Transactional
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // If order is still PENDING locally, check the real-time status from Payment Processor
        if (order.getStatus() == Order.OrderStatus.PENDING) {
            java.util.Optional<com.payment.order.dto.PaymentStatusResponse> paymentOpt = paymentProcessorClient.getPaymentStatusByOrderId(orderId);
            
            if (paymentOpt.isPresent()) {
                com.payment.order.dto.PaymentStatusResponse payment = paymentOpt.get();
                logger.info("Real-time update: Payment found for order {}. Status: {}", orderId, payment.getStatus());
                
                // Update local status if processor says it's finished
                if ("COMPLETED".equalsIgnoreCase(payment.getStatus())) {
                    order.setStatus(Order.OrderStatus.COMPLETED);
                    orderRepository.save(order);
                } else if ("FAILED".equalsIgnoreCase(payment.getStatus())) {
                    order.setStatus(Order.OrderStatus.FAILED);
                    orderRepository.save(order);
                }
                
                OrderResponse response = mapToResponse(order);
                response.setPayment(OrderResponse.PaymentInfo.builder()
                        .paymentId(payment.getPaymentId())
                        .gatewayTransactionId(payment.getGatewayTransactionId())
                        .processedAt(payment.getProcessedAt())
                        .build());
                return response;
            }
        }

        return mapToResponse(order);
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
                .senderAccount(order.getSenderBankAccount())
                .recipientAccount(order.getRecipientBankAccount())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
