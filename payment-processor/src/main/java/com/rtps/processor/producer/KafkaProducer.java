package com.rtps.processor.producer;

import com.rtps.processor.dto.PaymentProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaProducer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducer.class);

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentProcessed(PaymentProcessedEvent event) {
        try {
            logger.info("Publishing PaymentProcessedEvent | orderId: {} | status: {}", 
                    event.getOrderId(), event.getStatus());
            kafkaTemplate.send("payment-events", event.getOrderId().toString(), event);
        } catch (Exception e) {
            logger.error("Failed to publish PaymentProcessedEvent | error: {}", e.getMessage());
        }
    }
}
