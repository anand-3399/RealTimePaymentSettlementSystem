package com.payment.order.repository;

import com.payment.order.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
	List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);

	List<OutboxEvent> findByStatusAndRetryCountLessThanAndNextRetryAtBeforeOrderByCreatedAtAsc(
			OutboxEvent.OutboxStatus status, Integer maxRetries, LocalDateTime nextRetryAt);

	default List<OutboxEvent> findReadyToPublish(Integer maxRetries) {
		return findByStatusAndRetryCountLessThanAndNextRetryAtBeforeOrderByCreatedAtAsc(
				OutboxEvent.OutboxStatus.PENDING, maxRetries, LocalDateTime.now().plusSeconds(1));
	}
}
