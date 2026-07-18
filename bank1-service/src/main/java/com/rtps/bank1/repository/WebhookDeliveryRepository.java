package com.rtps.bank1.repository;

import com.rtps.bank1.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    
    List<WebhookDelivery> findByResponseStatusNotAndRetryCountLessThan(Integer status, Integer maxRetries);
}
