package com.rtps.bank1.repository;

import com.rtps.bank1.entity.AccountingEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AccountingEntryRepository extends JpaRepository<AccountingEntry, UUID> {
    List<AccountingEntry> findByEntryStatusAndCreatedAtBefore(String entryStatus, LocalDateTime createdAt);
}
