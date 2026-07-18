package com.payment.order.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.payment.order.dto.CreateOrderRequest;
import com.payment.order.dto.OrderResponse;
import com.payment.order.service.OrderService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

	private final OrderService orderService;

	OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	@RateLimiter(name = "orderService")
	public ResponseEntity<OrderResponse> createOrder(@RequestHeader(value = "Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
		String userId = authentication.getName();
		return new ResponseEntity<>(orderService.createOrder(request, userId, idempotencyKey), HttpStatus.CREATED);
	}

	@GetMapping("/{orderId}")
	public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID orderId) {
		return ResponseEntity.ok(orderService.getOrderById(orderId));
	}

	@GetMapping
	public ResponseEntity<com.payment.order.dto.OrderPageResponse> getOrdersByUserId(Authentication authentication,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
			@RequestParam(required = false) String status, @RequestParam(required = false) String account) {

		String userId = authentication.getName();
		Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

		LocalDateTime startLocal = startDate != null ? LocalDateTime.ofInstant(startDate, ZoneId.systemDefault())
				: null;
		LocalDateTime endLocal = endDate != null ? LocalDateTime.ofInstant(endDate, ZoneId.systemDefault()) : null;

		return ResponseEntity.ok(
				orderService.getOrdersByUserIdPagedWithDate(userId, startLocal, endLocal, status, account, pageable));
	}
}
