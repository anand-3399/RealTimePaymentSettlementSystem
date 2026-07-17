package com.rtps.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResponse {
    private UUID paymentGatewayId;
    private UUID orderId;
    private String userId;
    private String senderAccount;
    private String recipientAccount;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String bankReferenceId;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private String correlationId;
    private String message;
}
