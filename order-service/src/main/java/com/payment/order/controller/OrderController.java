package com.payment.order.controller;

import com.payment.order.dto.CreateOrderRequest;
import com.payment.order.dto.OrderResponse;
import com.payment.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    @RateLimiter(name = "orderService")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        return new ResponseEntity<>(orderService.createOrder(request, userId, idempotencyKey), HttpStatus.CREATED);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrderById(orderId));
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getOrdersByUserId(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        String userId = authentication.getName();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(orderService.getOrdersByUserIdPaged(userId, pageable));
    }
}
