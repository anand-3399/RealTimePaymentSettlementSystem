package com.rtps.bank1.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtps.bank1.dto.TransferRequest;
import com.rtps.bank1.dto.TransferResponse;
import com.rtps.bank1.entity.AccountingEntry;
import com.rtps.bank1.entity.IdempotencyKey;
import com.rtps.bank1.entity.OperationalBankAccount;
import com.rtps.bank1.entity.Transaction;
import com.rtps.bank1.repository.AccountingEntryRepository;
import com.rtps.bank1.repository.IdempotencyKeyRepository;
import com.rtps.bank1.repository.OperationalBankAccountRepository;
import com.rtps.bank1.repository.TransactionRepository;
import com.rtps.bank1.secure.entity.SecureBankAccount;
import com.rtps.bank1.secure.repository.SecureBankAccountRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TransferService {

	private final OperationalBankAccountRepository accountRepository;

	private final SecureBankAccountRepository secureAccountRepository;

	private final TransactionRepository transactionRepository;

	private final IdempotencyKeyRepository idempotencyRepository;

	private final AccountingEntryRepository accountingEntryRepository;

	private final ObjectMapper objectMapper;

	private final AsyncTransferService asyncTransferService;

	public TransferService(OperationalBankAccountRepository accountRepository,
			SecureBankAccountRepository secureAccountRepository, TransactionRepository transactionRepository,
			IdempotencyKeyRepository idempotencyRepository, AccountingEntryRepository accountingEntryRepository,
			ObjectMapper objectMapper, AsyncTransferService asyncTransferService) {
		this.accountRepository = accountRepository;
		this.secureAccountRepository = secureAccountRepository;
		this.transactionRepository = transactionRepository;
		this.idempotencyRepository = idempotencyRepository;
		this.accountingEntryRepository = accountingEntryRepository;
		this.objectMapper = objectMapper;
		this.asyncTransferService = asyncTransferService;
	}

	@Transactional(rollbackFor = Exception.class)
	public TransferResponse processTransfer(TransferRequest request) {
		String correlationId = request.getCorrelationId();
		if (correlationId == null)
			correlationId = UUID.randomUUID().toString();
		final String finalCorrelationId = correlationId;
		MDC.put("correlationId", finalCorrelationId);

		try {
			// 1. Check Idempotency
			Optional<IdempotencyKey> existingKey = idempotencyRepository
					.findByIdempotencyKey(request.getIdempotencyKey());
			if (existingKey.isPresent()) {
				log.info("Duplicate request detected | idempotencyKey: {}. Returning cached response.",
						request.getIdempotencyKey());
				return objectMapper.readValue(existingKey.get().getResponseBody(), TransferResponse.class);
			}

			// 2. Create Accounting Entry (PENDING) before transfer
			AccountingEntry entry = AccountingEntry.builder().paymentId(request.getPaymentGatewayId())
					.senderAccountId(request.getSenderAccount()).recipientAccountId(request.getRecipientAccount())
					.amount(request.getAmount()).pgName("RTPS-Gateway").entryType("DEBIT") // representing the primary
																							// action on sender
					.entryStatus("PENDING").build();
			accountingEntryRepository.save(entry);

			// 3. Return PENDING immediately to the caller
			TransferResponse pendingResponse = TransferResponse.builder().status("PENDING").message("Accepted")
					.correlationId(correlationId).build();

			// 4. Save initial idempotency state
			IdempotencyKey key = IdempotencyKey.builder().idempotencyKey(request.getIdempotencyKey())
					.responseBody(objectMapper.writeValueAsString(pendingResponse)).createdAt(LocalDateTime.now())
					.build();
			idempotencyRepository.save(key);

			// 5. Trigger the actual transfer asynchronously via self-reference to proxy,
			// after transaction commits
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					asyncTransferService.executeAsyncTransfer(request, finalCorrelationId, entry.getEntryId());
				}
			});

			log.info("Transfer request accepted synchronously | correlationId: {}", correlationId);
			return pendingResponse;

		} catch (Exception e) {
			log.error("Failed to accept transfer request: {}", e);
			throw new RuntimeException("Failed to accept transfer: " + e.getMessage());
		} finally {
			MDC.remove("correlationId");
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public TransferResponse executeTransferInternal(TransferRequest request, String correlationId, UUID entryId) {
		AccountingEntry entry = accountingEntryRepository.findById(entryId).orElseThrow();

		try {
			// Step 1: Load sender & recipient from Operational DB
			OperationalBankAccount sender = accountRepository.findByAccountNumber(request.getSenderAccount())
					.orElse(null);
			OperationalBankAccount recipient = accountRepository.findByAccountNumber(request.getRecipientAccount())
					.orElse(null);
			log.info("Accounts fetched from cache | sender: {} | recipient: {}", request.getSenderAccount(),
					request.getRecipientAccount());

			String failureReason = null;
			if (sender == null)
				failureReason = "Sender account not found: " + request.getSenderAccount();
			else if (recipient == null)
				failureReason = "Recipient account not found: " + request.getRecipientAccount();
			else if (!"ACTIVE".equals(sender.getStatus()))
				failureReason = "Sender account is " + sender.getStatus();
			else if (!"ACTIVE".equals(recipient.getStatus()))
				failureReason = "Recipient account is " + recipient.getStatus();

			if (failureReason != null) {
				return failTransfer(entry, correlationId, failureReason);
			}

			if (sender.getBalance().compareTo(request.getAmount()) < 0) {
				// Lazy Sync: Only check Secure DB if we have insufficient balance
				log.info("Insufficient local balance ({} < {}). Checking Secure DB for fresh funds... | sender: {}",
						sender.getBalance(), request.getAmount(), request.getSenderAccount());
				SecureBankAccount secureSender = secureAccountRepository.findByAccountNumber(request.getSenderAccount())
						.orElse(null);

				if (secureSender != null && sender.getAccountVersion() < secureSender.getAccountVersion()) {
					log.info(
							"Account version mismatch detected (Local: {}, Secure: {}). Syncing local balance and version... | sender: {}",
							sender.getAccountVersion(), secureSender.getAccountVersion(), request.getSenderAccount());
					sender.setBalance(secureSender.getBalance());
					sender.setAccountVersion(secureSender.getAccountVersion());
					sender.setLastSyncedAt(LocalDateTime.now());
					sender.setSyncStatus("IN_SYNC");
					accountRepository.save(sender);
				}

				// Re-evaluate balance after potential sync
				if (sender.getBalance().compareTo(request.getAmount()) < 0) {
					return failTransfer(entry, correlationId, "Insufficient balance. Required: " + request.getAmount()
							+ " | Available: " + sender.getBalance());
				}
			}

			// Step 3 & 4: Debit sender, Credit recipient & Save on OPERATIONAL DB ONLY (per
			// user rules)
			// Note: Optimistic locking cannot truly function securely here as Bank1 is
			// restricted to read-only on Secure DB.
			// Balances are strictly updated locally in the cache.
			sender.setBalance(sender.getBalance().subtract(request.getAmount()));
			recipient.setBalance(recipient.getBalance().add(request.getAmount()));

			// Sync Operational DB with new balances, but DO NOT increment version
			accountRepository.save(sender);
			accountRepository.save(recipient);

			Transaction transaction = Transaction.builder().paymentGatewayId(request.getPaymentGatewayId())
					.idempotencyKey(request.getIdempotencyKey()).senderAccountNumber(request.getSenderAccount())
					.recipientAccountNumber(request.getRecipientAccount()).amount(request.getAmount())
					.currency(request.getCurrency()).status("COMPLETED").createdAt(LocalDateTime.now())
					.completedAt(LocalDateTime.now()).build();
			transactionRepository.save(transaction);

			// Commit Accounting Entry
			entry.setEntryStatus("COMMITTED");
			entry.setCommittedAt(LocalDateTime.now());
			accountingEntryRepository.save(entry);

			log.info("Transfer completed successfully | sender: {} | recipient: {} | amount: {}",
					request.getSenderAccount(), request.getRecipientAccount(), request.getAmount());

			return TransferResponse.builder().status("COMPLETED").message("Transfer completed successfully")
					.transactionId(transaction.getId()).amount(request.getAmount()).currency(request.getCurrency())
					.senderAccount(request.getSenderAccount()).recipientAccount(request.getRecipientAccount())
					.completedAt(transaction.getCompletedAt()).correlationId(correlationId).build();

		} catch (ObjectOptimisticLockingFailureException e) {
			// Step 5: Version conflict
			log.warn("Version conflict detected during transfer. Returning LOCKED_PENDING_RETRY.");
			return failTransfer(entry, correlationId, "LOCKED_PENDING_RETRY");
		} catch (Exception e) {
			log.error("Error during async transfer processing: {}", e);
			return failTransfer(entry, correlationId, "System error: " + e.getMessage());
		}
	}

	private TransferResponse failTransfer(AccountingEntry entry, String correlationId, String reason) {
		entry.setEntryStatus("FAILED");
		entry.setCommittedAt(LocalDateTime.now());
		accountingEntryRepository.save(entry);

		return TransferResponse.builder().status("FAILED").message(reason).correlationId(correlationId).build();
	}
}
