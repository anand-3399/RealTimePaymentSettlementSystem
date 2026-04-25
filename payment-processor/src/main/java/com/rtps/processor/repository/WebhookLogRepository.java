package com.rtps.processor.repository;

import com.rtps.processor.entity.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WebhookLogRepository extends JpaRepository<WebhookLog, UUID> {
}
