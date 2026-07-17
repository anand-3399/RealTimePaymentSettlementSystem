package com.payment.order.event;

import org.springframework.context.ApplicationEvent;

public class OutboxEventCreated extends ApplicationEvent {
    
    private static final long serialVersionUID = 1L;

	public OutboxEventCreated(Object source) {
        super(source);
    }
}
