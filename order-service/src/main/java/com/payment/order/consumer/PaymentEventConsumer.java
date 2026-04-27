package com.payment.order.consumer;

import com.payment.order.entity.Order;
import com.payment.order.event.PaymentProcessedEvent;
import com.payment.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PaymentEventConsumer.class);

    @Autowired
    private OrderRepository orderRepository;

    @KafkaListener(topics = "payment-events", groupId = "order-service-group")
    @Transactional
    public void consume(PaymentProcessedEvent event) {
        MDC.put("correlationId", event.getCorrelationId());
        try {
            logger.info("Received PaymentProcessedEvent | orderId: {} | status: {}", 
                    event.getOrderId(), event.getStatus());

            orderRepository.findById(event.getOrderId()).ifPresentOrElse(order -> {
                if (order.getStatus() == Order.OrderStatus.PENDING) {
                    if ("COMPLETED".equalsIgnoreCase(event.getStatus())) {
                        order.setStatus(Order.OrderStatus.COMPLETED);
                    } else if ("FAILED".equalsIgnoreCase(event.getStatus())) {
                        order.setStatus(Order.OrderStatus.FAILED);
                    }
                    
                    order.setPaymentId(event.getPaymentId());
                    order.setGatewayTransactionId(event.getGatewayTransactionId());
                    order.setProcessedAt(event.getTimestamp());
                    order.setReason(event.getMessage());
                    
                    orderRepository.save(order);
                    logger.info("Successfully updated order {} status to {}", order.getOrderId(), order.getStatus());
                } else {
                    logger.debug("Order {} already in status {}. Skipping update.", order.getOrderId(), order.getStatus());
                }
            }, () -> {
                logger.error("Order not found for PaymentProcessedEvent | orderId: {}", event.getOrderId());
            });

        } catch (Exception e) {
            logger.error("Error processing PaymentProcessedEvent | orderId: {} | error: {}", 
                    event.getOrderId(), e.getMessage());
        } finally {
            MDC.remove("correlationId");
        }
    }
}
