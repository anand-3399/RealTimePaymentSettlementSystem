package com.payment.order.repository;

import com.payment.order.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
    
    List<OutboxEvent> findByStatusAndRetryCountLessThanAndNextRetryAtBeforeOrderByCreatedAtAsc(
            OutboxEvent.OutboxStatus status, Integer maxRetries, java.time.LocalDateTime nextRetryAt);
            
    default List<OutboxEvent> findReadyToPublish(Integer maxRetries) {
        return findByStatusAndRetryCountLessThanAndNextRetryAtBeforeOrderByCreatedAtAsc(
                OutboxEvent.OutboxStatus.PENDING, maxRetries, java.time.LocalDateTime.now());
    }
}
