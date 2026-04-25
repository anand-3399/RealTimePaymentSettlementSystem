package com.payment.order.dto;

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
public class OrderResponse {
    private UUID orderId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String senderAccount;
    private String recipientAccount;
    private LocalDateTime createdAt;
    private PaymentInfo payment;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PaymentInfo {
        private UUID paymentId;
        private String gatewayTransactionId;
        private LocalDateTime processedAt;
    }
}
