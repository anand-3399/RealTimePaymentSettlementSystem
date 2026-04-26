package com.rtps.ajbank.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private UUID paymentProcessorId;
    private String idempotencyKey;

    @Column(nullable = false)
    private String senderAccountNumber;

    @Column(nullable = false)
    private String recipientAccountNumber;

    @Column(nullable = false)
    private BigDecimal amount;

    private String currency;

    @Column(nullable = false)
    private String status;

    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, ROLLED_BACK
    }
}
