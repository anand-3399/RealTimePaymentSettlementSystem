package com.rtps.ajbank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtps.ajbank.dto.TransferRequest;
import com.rtps.ajbank.dto.TransferResponse;
import com.rtps.ajbank.entity.BankAccount;
import com.rtps.ajbank.entity.IdempotencyKey;
import com.rtps.ajbank.entity.Transaction;
import com.rtps.ajbank.repository.BankAccountRepository;
import com.rtps.ajbank.repository.IdempotencyKeyRepository;
import com.rtps.ajbank.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.scheduling.annotation.Async;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger logger = LoggerFactory.getLogger(TransferService.class);

    @Autowired
    private BankAccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebhookEmitterService webhookEmitterService;

    @Transactional(rollbackFor = Exception.class)
    public TransferResponse processTransfer(TransferRequest request) {
        String correlationId = request.getCorrelationId();
        if (correlationId == null) correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);

        try {
            // 1. Check Idempotency
            Optional<IdempotencyKey> existingKey = idempotencyRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existingKey.isPresent()) {
                logger.info("Duplicate request detected | idempotencyKey: {}. Returning cached response.", request.getIdempotencyKey());
                return objectMapper.readValue(existingKey.get().getResponseBody(), TransferResponse.class);
            }

            // 2. Return PENDING immediately to the caller
            TransferResponse pendingResponse = TransferResponse.builder()
                    .status("PENDING")
                    .message("Accepted")
                    .correlationId(correlationId)
                    .build();

            // 3. Save initial idempotency state
            IdempotencyKey key = IdempotencyKey.builder()
                    .idempotencyKey(request.getIdempotencyKey())
                    .responseBody(objectMapper.writeValueAsString(pendingResponse))
                    .createdAt(LocalDateTime.now())
                    .build();
            idempotencyRepository.save(key);

            // 4. Trigger the actual transfer asynchronously
            executeAsyncTransfer(request, correlationId);

            logger.info("Transfer request accepted synchronously | correlationId: {}", correlationId);
            return pendingResponse;

        } catch (Exception e) {
            logger.error("Failed to accept transfer request: {}", e.getMessage());
            throw new RuntimeException("Failed to accept transfer: " + e.getMessage());
        } finally {
            MDC.remove("correlationId");
        }
    }

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void executeAsyncTransfer(TransferRequest request, String correlationId) {
        MDC.put("correlationId", correlationId);
        logger.info("Executing async balance transfer for correlationId: {}", correlationId);
        
        TransferResponse finalResponse;
        try {
            // Lock accounts to prevent concurrent updates
            BankAccount sender = accountRepository.findByAccountNumberWithLock(request.getSenderAccount()).orElse(null);
            BankAccount recipient = accountRepository.findByAccountNumberWithLock(request.getRecipientAccount()).orElse(null);

            String failureReason = null;
            if (sender == null) {
                failureReason = "Sender account not found: " + request.getSenderAccount();
            } else if (recipient == null) {
                failureReason = "Recipient account not found: " + request.getRecipientAccount();
            } else if (!"ACTIVE".equals(sender.getStatus())) {
                failureReason = "Sender account is " + sender.getStatus();
            } else if (!"ACTIVE".equals(recipient.getStatus())) {
                failureReason = "Recipient account is " + recipient.getStatus();
            } else if (sender.getBalance().compareTo(request.getAmount()) < 0) {
                failureReason = "Insufficient balance. Required: " + request.getAmount() + " | Available: " + sender.getBalance();
            }

            if (failureReason != null) {
                finalResponse = TransferResponse.builder()
                        .status("FAILED")
                        .message(failureReason)
                        .correlationId(correlationId)
                        .build();
            } else {
                // Perform Debit/Credit
                sender.setBalance(sender.getBalance().subtract(request.getAmount()));
                recipient.setBalance(recipient.getBalance().add(request.getAmount()));
                accountRepository.save(sender);
                accountRepository.save(recipient);

                Transaction transaction = Transaction.builder()
                        .paymentProcessorId(request.getPaymentProcessorId())
                        .idempotencyKey(request.getIdempotencyKey())
                        .senderAccountNumber(request.getSenderAccount())
                        .recipientAccountNumber(request.getRecipientAccount())
                        .amount(request.getAmount())
                        .currency(request.getCurrency())
                        .status("COMPLETED")
                        .createdAt(LocalDateTime.now())
                        .completedAt(LocalDateTime.now())
                        .build();
                transactionRepository.save(transaction);

                finalResponse = TransferResponse.builder()
                        .status("COMPLETED")
                        .message("Transfer completed successfully")
                        .transactionId(transaction.getId())
                        .amount(request.getAmount())
                        .currency(request.getCurrency())
                        .senderAccount(request.getSenderAccount())
                        .recipientAccount(request.getRecipientAccount())
                        .completedAt(transaction.getCompletedAt())
                        .correlationId(correlationId)
                        .build();
            }
        } catch (Exception e) {
            logger.error("Error during async transfer processing: {}", e.getMessage());
            finalResponse = TransferResponse.builder()
                    .status("FAILED")
                    .message("System error: " + e.getMessage())
                    .correlationId(correlationId)
                    .build();
        }

        // Fire the webhook with final result
        webhookEmitterService.sendWebhook(finalResponse);
        MDC.remove("correlationId");
    }
}
