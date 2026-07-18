package com.rtps.gateway.client;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtps.gateway.dto.Bank1Request;
import com.rtps.gateway.dto.Bank1Response;
import com.rtps.gateway.dto.AccountBalance;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class Bank1Client {

    private final RestTemplate restTemplate;
    private final String ajBankUrl;
    private final String internalSecret;

    public Bank1Client(
            @Value("${rtps.bank1.url}") String ajBankUrl,
            @Value("${rtps.bank1.secret}") String internalSecret) {
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.restTemplate = new RestTemplate(factory);
        this.ajBankUrl = ajBankUrl;
        this.internalSecret = internalSecret;
    }

    public Optional<AccountBalance> getAccountBalance(String accountNumber) {
        String url = ajBankUrl + "/api/accounts/" + accountNumber + "/balance";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        
        try {
            ResponseEntity<AccountBalance> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), AccountBalance.class
            );
            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to fetch balance for account {}: {}", accountNumber, e);
            return Optional.empty();
        }
    }

    @Retry(name = "bank1")
    @CircuitBreaker(name = "bank1", fallbackMethod = "fallbackTransfer")
    public Bank1Response transferMoney(Bank1Request request) {
        String url = ajBankUrl + "/api/transfer";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Secret", internalSecret);
        headers.set("X-Correlation-ID", request.getCorrelationId());

        HttpEntity<Bank1Request> entity = new HttpEntity<>(request, headers);

        log.info("Calling Bank1 for transfer | sender: {} | amount: {} | correlationId: {}", 
                request.getSenderAccount(), request.getAmount(), request.getCorrelationId());

        try {
            ResponseEntity<Bank1Response> response = restTemplate.postForEntity(url, entity, Bank1Response.class);
            
            if (response.getStatusCode().value() != 201) {
                log.error("Bank1 handshake returned unexpected success status: {}", response.getStatusCode());
                return Bank1Response.builder()
                        .status("FAILED")
                        .message("Unexpected success status from Bank1: " + response.getStatusCode())
                        .correlationId(request.getCorrelationId())
                        .build();
            }
            
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 409) {
                log.warn("Bank1 returned 409 Conflict (Account Locked) | correlationId: {}", request.getCorrelationId());
                return Bank1Response.builder()
                        .status("LOCKED_PENDING_RETRY")
                        .message("Account is currently busy. Payment will be retried.")
                        .retryReason("ACCOUNT_LOCKED")
                        .correlationId(request.getCorrelationId())
                        .build();
            }

            try {
                // Try to parse the structured error response from Bank1 (e.g. 400 Bad Request for insufficient funds)
                ObjectMapper mapper = new ObjectMapper();
                Bank1Response errorResponse = mapper.readValue(e.getResponseBodyAsString(), Bank1Response.class);
                log.warn("Bank1 returned business error {} | correlationId: {} | message: {}", 
                        e.getStatusCode(), request.getCorrelationId(), errorResponse.getMessage());
                return errorResponse;
            } catch (Exception parseException) {
                log.error("Failed to parse error response from Bank1: {}", e.getResponseBodyAsString());
                throw e; // rethrow to trigger circuit breaker/retry for unknown errors
            }
        }
    }

    // Fallback method when Circuit Breaker is OPEN or Retries are exhausted
    public Bank1Response fallbackTransfer(Bank1Request request, Throwable t) {
        log.error("Bank1 Circuit Breaker OPEN or Retries Exhausted | correlationId: {} | error: {}", 
                request.getCorrelationId(), t.getMessage());
        
        return Bank1Response.builder()
                .status("PENDING_RETRY")
                .message("Bank1 is currently unavailable. Payment is queued for background retry.")
                .retryReason("BANK_UNAVAILABLE")
                .correlationId(request.getCorrelationId())
                .build();
    }
}

