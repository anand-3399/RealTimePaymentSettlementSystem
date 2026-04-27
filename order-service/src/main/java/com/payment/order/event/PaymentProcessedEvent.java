package com.payment.order.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentProcessedEvent {
    private UUID paymentId;
    private UUID orderId;
    private String userId;
    private BigDecimal amount;
    private String status;
    private String gatewayTransactionId;
    private LocalDateTime timestamp;
    private String correlationId;
    private String message;
}
