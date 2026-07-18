package com.rtps.bank1.service;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.rtps.bank1.dto.TransferRequest;
import com.rtps.bank1.dto.TransferResponse;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AsyncTransferService {

	private final TransferService transferService;
	private final WebhookEmitterService webhookEmitterService;

	public AsyncTransferService(@Lazy TransferService transferService, WebhookEmitterService webhookEmitterService) {
		this.transferService = transferService;
		this.webhookEmitterService = webhookEmitterService;
	}

	@Async
	public void executeAsyncTransfer(TransferRequest request, String correlationId, UUID entryId) {
		MDC.put("correlationId", correlationId);
		try {
			log.info("Executing async balance transfer for correlationId: {}", correlationId);

			TransferResponse response = transferService.executeTransferInternal(request, correlationId, entryId);

			webhookEmitterService.sendWebhook(response);
		} finally {
			MDC.remove("correlationId");
		}
	}
}
