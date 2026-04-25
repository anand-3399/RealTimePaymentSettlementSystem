package com.payment.order.event;

import com.payment.order.entity.Order;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Internal Spring event used to decouple the Order Service from Kafka.
 * This event is published within the DB transaction and caught by a 
 * TransactionalEventListener.
 */
@Getter
public class OrderCreatedInternalEvent extends ApplicationEvent {
    private final Order order;

    public OrderCreatedInternalEvent(Order order) {
        super(order);
        this.order = order;
    }
}
