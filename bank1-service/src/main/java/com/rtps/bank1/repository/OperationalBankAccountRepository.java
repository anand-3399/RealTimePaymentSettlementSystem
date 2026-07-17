package com.rtps.bank1.repository;

import com.rtps.bank1.entity.OperationalBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OperationalBankAccountRepository extends JpaRepository<OperationalBankAccount, UUID> {
    Optional<OperationalBankAccount> findByAccountNumber(String accountNumber);
}
 