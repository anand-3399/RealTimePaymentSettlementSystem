package com.rtps.ajbank.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDelivery {

    @Id
    @GeneratedValue
    private UUID id;

    private String correlationId;

    @Lob
    private String payload;

    private String signature;

    private Integer responseStatus;

    private Integer retryCount;

    private LocalDateTime createdAt;
}
