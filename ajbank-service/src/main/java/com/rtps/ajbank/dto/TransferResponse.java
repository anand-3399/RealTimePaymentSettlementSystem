package com.rtps.ajbank.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {
    private UUID transactionId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String senderAccount;
    private String recipientAccount;
    private LocalDateTime completedAt;
    private String correlationId;
    private String message;
}
