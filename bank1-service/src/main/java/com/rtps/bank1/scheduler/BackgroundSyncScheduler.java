package com.rtps.bank1.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.rtps.bank1.entity.OperationalBankAccount;
import com.rtps.bank1.repository.OperationalBankAccountRepository;
import com.rtps.bank1.secure.entity.SecureBankAccount;
import com.rtps.bank1.secure.repository.SecureBankAccountRepository;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BackgroundSyncScheduler {

    @Autowired
    private OperationalBankAccountRepository accountRepository;

    @Autowired
    private SecureBankAccountRepository secureAccountRepository;

    // Use SpEL to read the delay dynamically from ConfigService
    @Scheduled(fixedDelayString = "#{@configService.getConfigAsString('BANK1_BACKGROUND_SYNC_DELAY')}")
    public void syncAccountsFromSecureDb() {
        log.debug("Running background account synchronization...");
        
        List<OperationalBankAccount> accounts = accountRepository.findAll();
        
        int syncCount = 0;
        for (OperationalBankAccount localAccount : accounts) {
            SecureBankAccount secureAccount = secureAccountRepository.findByAccountNumber(localAccount.getAccountNumber()).orElse(null);
            
            if (secureAccount != null && localAccount.getAccountVersion() < secureAccount.getAccountVersion()) {
                log.info("Background Sync: Updating account {} from version {} to {}", 
                        localAccount.getAccountNumber(), localAccount.getAccountVersion(), secureAccount.getAccountVersion());
                
                localAccount.setBalance(secureAccount.getBalance());
                localAccount.setAccountVersion(secureAccount.getAccountVersion());
                localAccount.setLastSyncedAt(LocalDateTime.now());
                localAccount.setSyncStatus("IN_SYNC");
                
                accountRepository.save(localAccount);
                syncCount++;
            }
        }
        
        if (syncCount > 0) {
            log.info("Background Sync completed. Synced {} accounts.", syncCount);
        }
    }
}

