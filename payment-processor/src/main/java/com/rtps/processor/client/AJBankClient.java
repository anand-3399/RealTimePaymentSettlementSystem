package com.rtps.processor.client;

import com.rtps.processor.dto.AJBankRequest;
import com.rtps.processor.dto.AJBankResponse;
import com.rtps.processor.dto.AccountBalance;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;

@Component
public class AJBankClient {

    private static final Logger logger = LoggerFactory.getLogger(AJBankClient.class);

    private final RestTemplate restTemplate;
    private final String ajBankUrl;
    private final String internalSecret;

    public AJBankClient(
            RestTemplateBuilder builder,
            @Value("${rtps.ajbank.url}") String ajBankUrl,
            @Value("${rtps.ajbank.secret}") String internalSecret) {
        
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.ajBankUrl = ajBankUrl;
        this.internalSecret = internalSecret;
    }

    public Optional<AccountBalance> getAccountBalance(String accountNumber) {
        String url = ajBankUrl + "/api/accounts/" + accountNumber + "/balance";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        
        try {
            ResponseEntity<AccountBalance> response = restTemplate.exchange(
                url, org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), AccountBalance.class
            );
            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            logger.warn("Failed to fetch balance for account {}: {}", accountNumber, e.getMessage());
            return Optional.empty();
        }
    }

    @Retry(name = "ajbank")
    @CircuitBreaker(name = "ajbank", fallbackMethod = "fallbackTransfer")
    public AJBankResponse transferMoney(AJBankRequest request) {
        String url = ajBankUrl + "/api/transfer";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Secret", internalSecret);
        headers.set("X-Correlation-ID", request.getCorrelationId());

        HttpEntity<AJBankRequest> entity = new HttpEntity<>(request, headers);

        logger.info("Calling AJBank for transfer | sender: {} | amount: {} | correlationId: {}", 
                request.getSenderAccount(), request.getAmount(), request.getCorrelationId());

        try {
            ResponseEntity<AJBankResponse> response = restTemplate.postForEntity(url, entity, AJBankResponse.class);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 409) {
                logger.warn("AJBank returned 409 Conflict (Account Locked) | correlationId: {}", request.getCorrelationId());
                return AJBankResponse.builder()
                        .status("LOCKED_PENDING_RETRY")
                        .message("Account is currently busy. Payment will be retried.")
                        .retryReason("ACCOUNT_LOCKED")
                        .correlationId(request.getCorrelationId())
                        .build();
            }

            try {
                // Try to parse the structured error response from AJBank (e.g. 400 Bad Request for insufficient funds)
                ObjectMapper mapper = new ObjectMapper();
                AJBankResponse errorResponse = mapper.readValue(e.getResponseBodyAsString(), AJBankResponse.class);
                logger.warn("AJBank returned business error {} | correlationId: {} | message: {}", 
                        e.getStatusCode(), request.getCorrelationId(), errorResponse.getMessage());
                return errorResponse;
            } catch (Exception parseException) {
                logger.error("Failed to parse error response from AJBank: {}", e.getResponseBodyAsString());
                throw e; // rethrow to trigger circuit breaker/retry for unknown errors
            }
        }
    }

    // Fallback method when Circuit Breaker is OPEN or Retries are exhausted
    public AJBankResponse fallbackTransfer(AJBankRequest request, Throwable t) {
        logger.error("AJBank Circuit Breaker OPEN or Retries Exhausted | correlationId: {} | error: {}", 
                request.getCorrelationId(), t.getMessage());
        
        return AJBankResponse.builder()
                .status("PENDING_RETRY")
                .message("AJBank is currently unavailable. Payment is queued for background retry.")
                .retryReason("BANK_UNAVAILABLE")
                .correlationId(request.getCorrelationId())
                .build();
    }
}
