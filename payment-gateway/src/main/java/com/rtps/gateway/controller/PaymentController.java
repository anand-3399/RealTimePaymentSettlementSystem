package com.rtps.gateway.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rtps.gateway.dto.AnalyticsResponse;
import com.rtps.gateway.dto.PaymentResponse;
import com.rtps.gateway.service.PaymentService;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{paymentGatewayId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentGatewayId) {
        return ResponseEntity.ok(paymentService.getPaymentDetails(paymentGatewayId));
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> listPayments(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(paymentService.listPayments(orderId, status));
    }

    @PostMapping("/{paymentGatewayId}/retry")
    public ResponseEntity<PaymentResponse> retryPayment(@PathVariable UUID paymentGatewayId) {
        return ResponseEntity.ok(paymentService.retryPayment(paymentGatewayId));
    }

    @GetMapping("/analytics/daily")
    public ResponseEntity<AnalyticsResponse> getDailyAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(paymentService.getDailyAnalytics(date));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload, 
                                               @RequestHeader("X-Bank1-Signature") String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok("Webhook processed successfully");
    }
}
