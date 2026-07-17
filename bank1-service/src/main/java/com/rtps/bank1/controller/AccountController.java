package com.rtps.bank1.controller;

import com.rtps.bank1.entity.OperationalBankAccount;
import com.rtps.bank1.repository.OperationalBankAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    @Autowired
    private OperationalBankAccountRepository accountRepository;

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .map(acc -> ResponseEntity.ok(acc))
                .orElse(ResponseEntity.notFound().build());
    }
}
