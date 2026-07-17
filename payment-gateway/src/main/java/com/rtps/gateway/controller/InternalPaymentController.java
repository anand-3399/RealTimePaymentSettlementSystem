package com.rtps.gateway.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rtps.gateway.dto.PaymentResponse;
import com.rtps.gateway.service.PaymentService;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/payment-gateway/internal")
@Slf4j
public class InternalPaymentController {

    @Autowired
    private PaymentService paymentService;

    @Value("${rtps.inbound-secret}")
    private String internalSecret;

    @GetMapping("/payments")
    public ResponseEntity<PaymentResponse> getInternalPayment(
            @RequestParam UUID orderId,
            @RequestHeader("X-Internal-Secret") String secret) {
        
        log.debug("Received internal payment request for orderId: {}", orderId);
        
        if (!internalSecret.equals(secret)) {
            log.warn("Unauthorized internal access attempt for orderId: {} | Received secret mismatch", orderId);
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        return paymentService.getPaymentByOrderId(orderId)
                .map(response -> {
                    log.debug("Found payment for orderId: {} | Status: {}", orderId, response.getStatus());
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    log.warn("No payment found for orderId: {}", orderId);
                    return ResponseEntity.notFound().build();
                });
    }
}

