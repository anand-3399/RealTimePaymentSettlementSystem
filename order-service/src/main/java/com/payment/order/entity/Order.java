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

    @Column(nullable = false, columnDefinition = "TIMESTAMP")
    private LocalDateTime createdAt;

    private UUID paymentGatewayId;
    private String bankReferenceId;
    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime processedAt;
    
    @Column(length = 2000, name = "reason")
    private String reason;

    public enum OrderStatus {
        PENDING, SUCCESS, FAILED, COMPLETED
    }
}
 