package com.payment.order.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.payment.order.security.BankAccountEncryptor;

@Entity
@Table(name = "payment_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID orderId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    @jakarta.persistence.Convert(converter = BankAccountEncryptor.class)
    private String recipientBankAccount;

    @Column(nullable = false)
    @jakarta.persistence.Convert(converter = BankAccountEncryptor.class)
    private String senderBankAccount;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private UUID paymentId;
    private String gatewayTransactionId;
    private LocalDateTime processedAt;

    public enum OrderStatus {
        PENDING, SUCCESS, FAILED, COMPLETED
    }
}
