package com.rtps.bank1.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    private String senderAccount;
    private String recipientAccount;
    private BigDecimal amount;
    private String currency;
    private String idempotencyKey;
    private UUID paymentGatewayId;
    private String correlationId;
}
