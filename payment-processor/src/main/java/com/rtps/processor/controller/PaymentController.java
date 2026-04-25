package com.rtps.processor.controller;

import com.rtps.processor.dto.AnalyticsResponse;
import com.rtps.processor.dto.PaymentResponse;
import com.rtps.processor.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.getPaymentDetails(paymentId));
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> listPayments(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(paymentService.listPayments(orderId, status));
    }

    @PostMapping("/{paymentId}/retry")
    public ResponseEntity<PaymentResponse> retryPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.retryPayment(paymentId));
    }

    @GetMapping("/analytics/daily")
    public ResponseEntity<AnalyticsResponse> getDailyAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(paymentService.getDailyAnalytics(date));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload, 
                                               @RequestHeader("X-AJBank-Signature") String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok("Webhook processed successfully");
    }
}
