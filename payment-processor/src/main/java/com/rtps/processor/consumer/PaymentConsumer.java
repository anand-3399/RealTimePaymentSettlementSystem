package com.rtps.processor.consumer;

import com.rtps.processor.dto.OrderCreatedEvent;
import com.rtps.processor.dto.PaymentEvent;
import com.rtps.processor.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentConsumer {

    @Autowired
    private PaymentService paymentService;

    @KafkaListener(topics = "payment-initiated", groupId = "payment-processor-group")
    public void consume(OrderCreatedEvent event) {
        log.info("Consumed payment event for Order ID: {}", event.getOrderId());
        paymentService.processPayment(event);
    }
}
