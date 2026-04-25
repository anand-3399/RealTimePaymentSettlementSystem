package com.rtps.settlement.consumer;

import com.rtps.settlement.dto.PaymentEvent;
import com.rtps.settlement.service.SettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SettlementConsumer {

    @Autowired
    private SettlementService settlementService;

    @KafkaListener(topics = "payment-processed", groupId = "settlement-worker-group")
    public void consume(PaymentEvent event) {
        log.info("Consumed processed payment event for Order ID: {}", event.getOrderId());
        settlementService.queueForSettlement(event);
    }
}
