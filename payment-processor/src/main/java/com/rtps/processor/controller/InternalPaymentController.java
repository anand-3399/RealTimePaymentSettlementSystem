package com.rtps.processor.controller;

import com.rtps.processor.dto.PaymentResponse;
import com.rtps.processor.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payment-processor/internal")
public class InternalPaymentController {

    @Autowired
    private PaymentService paymentService;

    @Value("${rtps.internal-secret}")
    private String internalSecret;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InternalPaymentController.class);

    @GetMapping("/payments")
    public ResponseEntity<PaymentResponse> getInternalPayment(
            @RequestParam UUID orderId,
            @RequestHeader("X-Internal-Secret") String secret) {
        
        logger.debug("Received internal payment request for orderId: {}", orderId);
        
        if (!internalSecret.equals(secret)) {
            logger.warn("Unauthorized internal access attempt for orderId: {} | Received secret mismatch", orderId);
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        return paymentService.getPaymentByOrderId(orderId)
                .map(response -> {
                    logger.debug("Found payment for orderId: {} | Status: {}", orderId, response.getStatus());
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    logger.warn("No payment found for orderId: {}", orderId);
                    return ResponseEntity.notFound().build();
                });
    }
}
