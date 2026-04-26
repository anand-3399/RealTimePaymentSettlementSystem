package com.rtps.ajbank.controller;

import com.rtps.ajbank.entity.BankAccount;
import com.rtps.ajbank.repository.BankAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    @Autowired
    private BankAccountRepository accountRepository;

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .map(acc -> ResponseEntity.ok(acc))
                .orElse(ResponseEntity.notFound().build());
    }
}
