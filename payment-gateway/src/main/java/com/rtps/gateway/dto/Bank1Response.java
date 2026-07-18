package com.rtps.gateway.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Bank1Response {
    private UUID transactionId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String senderAccount;
    private String recipientAccount;
    private String completedAt;
    private String correlationId;
    private String message;
    private String retryReason; // Added for advanced resilience
}
