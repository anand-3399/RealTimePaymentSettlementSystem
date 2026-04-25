package com.rtps.processor.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private UUID paymentId;

    private String eventType;
    
    @Column(length = 500)
    private String signature;
    
    private Boolean signatureValid;

    @Column(length = 4000)
    private String requestBody;
    
    private Integer responseStatus;

    private String correlationId;

    @Column(nullable = false)
    private LocalDateTime receivedAt;
    
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        receivedAt = LocalDateTime.now();
    }
}
