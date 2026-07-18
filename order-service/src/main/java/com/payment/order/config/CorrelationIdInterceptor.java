package com.payment.order.config;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CorrelationIdInterceptor implements HandlerInterceptor {

	private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
	private static final String CORRELATION_ID_LOG_VAR = "correlationId";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String correlationId = request.getHeader(CORRELATION_ID_HEADER);
		if (correlationId == null || correlationId.isEmpty()) {
			correlationId = UUID.randomUUID().toString();
		}

		MDC.put(CORRELATION_ID_LOG_VAR, correlationId);
		response.setHeader(CORRELATION_ID_HEADER, correlationId);
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		MDC.remove(CORRELATION_ID_LOG_VAR);
	}
}
