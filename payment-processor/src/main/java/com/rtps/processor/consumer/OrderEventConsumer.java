package com.rtps.processor.consumer;

import com.rtps.processor.dto.OrderCreatedEvent;
import com.rtps.processor.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);

    @Autowired
    private PaymentService paymentService;

    @KafkaListener(topics = "order-events", groupId = "payment-processor-group")
    public void consume(OrderCreatedEvent event) {
        // Set Correlation ID for logging
        MDC.put("correlationId", event.getCorrelationId());
        
        try {
            logger.info("Received OrderCreatedEvent | orderId: {} | userId: {}", 
                    event.getOrderId(), event.getUserId());
            
            paymentService.processPayment(event);
            
        } catch (Exception e) {
            logger.error("Error processing OrderCreatedEvent | orderId: {} | error: {}", 
                    event.getOrderId(), e.getMessage());
        } finally {
            MDC.remove("correlationId");
        }
    }
}
