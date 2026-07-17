package com.rtps.bank1.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.rtps.bank1.dto.TransferRequest;
import com.rtps.bank1.dto.TransferResponse;
import com.rtps.bank1.entity.AccountingEntry;
import com.rtps.bank1.repository.AccountingEntryRepository;
import com.rtps.bank1.service.TransferService;
import com.rtps.bank1.service.WebhookEmitterService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AccountingEntryRecoveryScheduler {

    @Autowired
    private AccountingEntryRepository accountingEntryRepository;

    @Autowired
    private TransferService transferService;

    @Autowired
    private WebhookEmitterService webhookEmitterService;

    @Scheduled(fixedDelayString = "#{@configService.getConfigAsString('BANK1_RECOVERY_SCHEDULER_DELAY')}")
    public void recoverPendingEntries() {
        // Find entries stuck in PENDING for more than 1 minute
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(1);
        List<AccountingEntry> stuckEntries = accountingEntryRepository.findByEntryStatusAndCreatedAtBefore("PENDING", threshold);

        if (stuckEntries.isEmpty()) {
            return;
        }

        log.info("Found {} stuck PENDING accounting entries to recover", stuckEntries.size());

        for (AccountingEntry entry : stuckEntries) {
            String correlationId = "recovery-" + entry.getEntryId().toString();
            try {
                TransferRequest request = new TransferRequest();
                request.setSenderAccount(entry.getSenderAccountId());
                request.setRecipientAccount(entry.getRecipientAccountId());
                request.setAmount(entry.getAmount());
                request.setCurrency("INR"); 
                request.setPaymentGatewayId(entry.getPaymentId());
                
                MDC.put("correlationId", correlationId);

                log.info("Recovering stuck entry: {}", entry.getEntryId());
                TransferResponse finalResponse = transferService.executeTransferInternal(request, correlationId, entry.getEntryId());
                
                // Fire webhook
                webhookEmitterService.sendWebhook(finalResponse);

            } catch (Exception e) {
                log.error("Failed to recover entry {}: {}", entry.getEntryId(), e);
            } finally {
                MDC.remove("correlationId");
            }
        }
    }
}

