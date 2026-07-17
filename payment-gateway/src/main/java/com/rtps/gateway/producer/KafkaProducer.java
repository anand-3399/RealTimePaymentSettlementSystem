package com.rtps.gateway.producer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.rtps.gateway.dto.PaymentProcessedEvent;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class KafkaProducer {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentProcessed(PaymentProcessedEvent event) {
        try {
            log.info("Publishing PaymentProcessedEvent | orderId: {} | status: {}", 
                    event.getOrderId(), event.getStatus());
            kafkaTemplate.send("payment-events", event.getOrderId().toString(), event);
        } catch (Exception e) {
            log.error("Failed to publish PaymentProcessedEvent | error: {}", e);
        }
    }
}

