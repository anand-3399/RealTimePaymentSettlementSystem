package com.payment.order.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentStatusResponse {
    private UUID paymentGatewayId;
    private UUID orderId;
    private String status;
    private String bankReferenceId;
    private LocalDateTime processedAt;
    private String correlationId;
    private String message;
}
