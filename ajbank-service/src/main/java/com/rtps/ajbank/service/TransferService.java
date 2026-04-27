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

import java.time.LocalDateTime;
import java.util.Optional;

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
        MDC.put("correlationId", correlationId);

        try {
            // 1. Check Idempotency (Permanent)
            Optional<IdempotencyKey> existingKey = idempotencyRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existingKey.isPresent()) {
                logger.info("Duplicate request detected | idempotencyKey: {}. Returning cached response.", request.getIdempotencyKey());
                TransferResponse response = objectMapper.readValue(existingKey.get().getResponseBody(), TransferResponse.class);
                
                webhookEmitterService.sendWebhook(response);
                
                return response;
            }

            // 2. ACID Transfer with Locking
            logger.info("Initiating ACID transfer | sender: {} | recipient: {} | amount: {}", 
                    request.getSenderAccount(), request.getRecipientAccount(), request.getAmount());

            // Lock accounts to prevent concurrent updates
            BankAccount sender = accountRepository.findByAccountNumberWithLock(request.getSenderAccount())
                    .orElseThrow(() -> new com.rtps.ajbank.exception.BusinessValidationException("Sender account not found: " + request.getSenderAccount()));

            BankAccount recipient = accountRepository.findByAccountNumberWithLock(request.getRecipientAccount())
                    .orElseThrow(() -> new com.rtps.ajbank.exception.BusinessValidationException("Recipient account not found: " + request.getRecipientAccount()));

            // Validation
            if (sender.getBalance().compareTo(request.getAmount()) < 0) {
                throw new com.rtps.ajbank.exception.BusinessValidationException("Insufficient balance in sender account");
            }

            if (!"ACTIVE".equals(sender.getStatus()) || !"ACTIVE".equals(recipient.getStatus())) {
                throw new com.rtps.ajbank.exception.BusinessValidationException("One or both accounts are not ACTIVE");
            }

            // Perform Debit/Credit
            sender.setBalance(sender.getBalance().subtract(request.getAmount()));
            recipient.setBalance(recipient.getBalance().add(request.getAmount()));

            accountRepository.save(sender);
            accountRepository.save(recipient);

            // Create Transaction Record
            Transaction transaction = Transaction.builder()
                    .paymentProcessorId(request.getPaymentProcessorId())
                    .idempotencyKey(request.getIdempotencyKey())
                    .senderAccountNumber(request.getSenderAccount())
                    .recipientAccountNumber(request.getRecipientAccount())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status(Transaction.TransactionStatus.COMPLETED.name())
                    .createdAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .build();

            Transaction savedTx = transactionRepository.save(transaction);

            // Create Response
            TransferResponse response = TransferResponse.builder()
                    .transactionId(savedTx.getId())
                    .status("COMPLETED")
                    .message("Transfer completed successfully")
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .senderAccount(request.getSenderAccount())
                    .recipientAccount(request.getRecipientAccount())
                    .completedAt(savedTx.getCompletedAt())
                    .correlationId(correlationId)
                    .build();

            // Cache Idempotency Key (Permanent)
            IdempotencyKey key = IdempotencyKey.builder()
                    .idempotencyKey(request.getIdempotencyKey())
                    .transactionId(savedTx.getId())
                    .responseBody(objectMapper.writeValueAsString(response))
                    .createdAt(LocalDateTime.now())
                    .build();
            
            idempotencyRepository.save(key);

            webhookEmitterService.sendWebhook(response);

            logger.info("ACID transfer completed successfully | txnId: {}", savedTx.getId());
            return response;

        } catch (com.rtps.ajbank.exception.BusinessValidationException e) {
            logger.warn("Business validation failed | error: {}", e.getMessage());
            throw e;
        } catch (org.springframework.dao.DataAccessException e) {
            logger.warn("Data access exception (e.g. Lock Timeout) | error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("ACID transfer failed | error: {}", e.getMessage());
            throw new RuntimeException("Transfer failed: " + e.getMessage(), e);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
