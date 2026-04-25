package com.rtps.processor.service;

import com.rtps.processor.dto.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PaymentService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PROCESSED_TOPIC = "payment-processed";

    public void processPayment(PaymentEvent event) {
        log.info("Processing payment for Order ID: {}...", event.getOrderId());

        // Simulate core payment processing logic (e.g. anti-fraud, fund verification)
        try {
            Thread.sleep(500); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("Payment successfully processed for Order ID: {}", event.getOrderId());

        // Update status and send to settlement
        event.setStatus("PROCESSED");
        kafkaTemplate.send(PROCESSED_TOPIC, event.getOrderId(), event);
        log.info("Sent event to Kafka topic: {}", PROCESSED_TOPIC);
    }
}
