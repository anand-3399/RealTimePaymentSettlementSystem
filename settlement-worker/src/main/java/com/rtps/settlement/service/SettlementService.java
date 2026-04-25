package com.rtps.settlement.service;

import com.rtps.settlement.dto.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@Slf4j
public class SettlementService {

    private final ConcurrentLinkedQueue<PaymentEvent> pendingSettlements = new ConcurrentLinkedQueue<>();

    public void queueForSettlement(PaymentEvent event) {
        log.info("Queuing Order ID: {} for batch settlement", event.getOrderId());
        pendingSettlements.add(event);
    }

    @Scheduled(fixedRate = 60000) // Every 1 minute
    public void processBatchSettlement() {
        if (pendingSettlements.isEmpty()) {
            return;
        }

        log.info("Starting batch settlement for {} orders", pendingSettlements.size());
        
        List<PaymentEvent> batch = new ArrayList<>();
        PaymentEvent event;
        while ((event = pendingSettlements.poll()) != null) {
            batch.add(event);
        }

        // Simulate batch communication with clearing house / central bank
        try {
            Thread.sleep(2000); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        batch.forEach(e -> {
            log.info("Order ID: {} has been SETTLED in batch", e.getOrderId());
            // In a real app, we would update the status in the central DB here
        });

        log.info("Batch settlement completed.");
    }
}
