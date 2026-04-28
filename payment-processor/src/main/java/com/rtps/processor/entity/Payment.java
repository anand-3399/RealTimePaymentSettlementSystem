package com.rtps.processor.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String senderAccount;

    @Column(nullable = false)
    private String recipientAccount;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String gatewayTransactionId;

    @Column(length = 2000)
    private String gatewayResponse;

    private Integer retryCount = 0;
    private Integer maxRetries = 3;
    
    private LocalDateTime lastRetryAt;
    private LocalDateTime nextRetryAt;
    
    private String correlationId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private LocalDateTime lastFailedAt;
    private LocalDateTime failedAt;
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    private RetryReason retryReason;
    
    private Integer lockedContentionCount = 0;

    public enum PaymentStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, PENDING_RETRY, LOCKED_PENDING_RETRY, SENT_AWAITING_RESPONSE
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (retryCount == null) retryCount = 0;
        if (maxRetries == null) maxRetries = 3;
        if (lockedContentionCount == null) lockedContentionCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
