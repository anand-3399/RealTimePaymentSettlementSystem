package com.payment.order.service;

import com.payment.order.dto.CreateOrderRequest;
import com.payment.order.exception.InvalidOrderException;
import com.payment.order.exception.UserNotFoundException;
import com.payment.order.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ValidationService {

    @Autowired
    private UserRepository userRepository;

    public com.payment.order.entity.User validateOrderRequest(CreateOrderRequest request, String userId, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException("Amount must be greater than 0");
        }

        return userRepository.findByUsername(userId)
                .orElseThrow(() -> new UserNotFoundException("User '" + userId + "' does not exist"));
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new InvalidOrderException("Idempotency-Key header is required");
        }
        if (key.length() > 255) {
            throw new InvalidOrderException("Idempotency-Key is too long (max 255 characters)");
        }
    }
}
