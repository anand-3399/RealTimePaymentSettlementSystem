package com.payment.order.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreatedEvent {
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String recipientAccount;
    private String senderAccount;
    private LocalDateTime timestamp;
    private String correlationId;
    private String idempotencyKey;
}
