package com.rtps.ajbank.controller;

import com.rtps.ajbank.dto.TransferRequest;
import com.rtps.ajbank.dto.TransferResponse;
import com.rtps.ajbank.service.TransferService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfer")
public class TransferController {

    @Autowired
    private TransferService transferService;

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@RequestBody TransferRequest request) {
        return ResponseEntity.ok(transferService.processTransfer(request));
    }
}
