package com.rtps.processor.dto;

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
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AJBankResponse {
    private UUID transactionId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String senderAccount;
    private String recipientAccount;
    private String completedAt;
    private String correlationId;
    private String message;
}
