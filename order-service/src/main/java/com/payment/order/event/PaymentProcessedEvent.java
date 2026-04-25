package com.payment.order.event;

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
public class PaymentProcessedEvent {
    private UUID paymentId;
    private UUID orderId;
    private String status;
    private String gatewayTransactionId;
    private LocalDateTime timestamp;
    private String correlationId;
}
