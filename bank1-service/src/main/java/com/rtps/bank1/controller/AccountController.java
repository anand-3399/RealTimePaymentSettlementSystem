package com.rtps.bank1.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rtps.bank1.repository.OperationalBankAccountRepository;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final OperationalBankAccountRepository accountRepository;

    AccountController(OperationalBankAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .map(acc -> ResponseEntity.ok(acc))
                .orElse(ResponseEntity.notFound().build());
    }
}
