package com.rtps.gateway.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "config_table")
@Data
public class ConfigEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long configId;

    private String configKey;
    private String configValue;
    private String description;
    private boolean isActive;
    private LocalDateTime lastUpdated;
    private LocalDateTime createdAt;
}
