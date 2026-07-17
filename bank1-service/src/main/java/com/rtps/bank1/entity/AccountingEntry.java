package com.rtps.bank1.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounting_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID entryId;

    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private String senderAccountId;

    @Column(nullable = false)
    private String recipientAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String pgName;

    @Column(nullable = false)
    private String entryType;

    @Column(nullable = false)
    private String entryStatus;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime committedAt;

    @Column(length = 500)
    private String entryHash;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
