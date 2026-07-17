package com.rtps.gateway.repository;

import com.rtps.gateway.entity.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WebhookLogRepository extends JpaRepository<WebhookLog, UUID> {
}
