package com.rtps.bank1.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bank_accounts_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalBankAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String accountHolderName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Long accountVersion;

    private LocalDateTime lastSyncedAt;

    @Column(nullable = false)
    private String syncStatus;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
