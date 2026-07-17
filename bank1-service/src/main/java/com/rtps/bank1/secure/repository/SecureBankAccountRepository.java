package com.rtps.bank1.secure.repository;

import com.rtps.bank1.secure.entity.SecureBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SecureBankAccountRepository extends JpaRepository<SecureBankAccount, UUID> {
    Optional<SecureBankAccount> findByAccountNumber(String accountNumber);
}
