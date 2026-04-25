package com.payment.order.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateOrderRequest {
    @NotBlank
    private String userId;

    @NotBlank
    @Pattern(regexp = "^[0-9]{8,18}$", message = "Invalid bank account format. Must be 8-18 digits.")
    private String recipientBankAccount;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String currency;

    private String description;

    @NotBlank
    private String idempotencyKey;
}
