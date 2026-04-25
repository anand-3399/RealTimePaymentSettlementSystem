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

    public void validateOrderRequest(CreateOrderRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException("Amount must be greater than 0");
        }

        if (!userRepository.findByUsername(request.getUserId()).isPresent()) {
            throw new UserNotFoundException("User '" + request.getUserId() + "' does not exist");
        }

        // Add more validations like bank account format if needed
    }
}
