package com.rtps.processor.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AJBankClient {

    private static final Logger logger = LoggerFactory.getLogger(AJBankClient.class);

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;

    public AJBankClient(@Value("${ajbank.api.url}") String apiUrl, 
                        @Value("${ajbank.api.key}") String apiKey) {
        this.restTemplate = new RestTemplate();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    public AJBankResponse transferMoney(String sender, String recipient, BigDecimal amount, String correlationId) {
        String url = apiUrl + "/api/transfer";
        
        AJBankRequest request = AJBankRequest.builder()
                .senderAccount(sender)
                .recipientAccount(recipient)
                .amount(amount)
                .idempotencyKey(UUID.randomUUID().toString())
                .correlationId(correlationId)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("X-Correlation-ID", correlationId);

        HttpEntity<AJBankRequest> entity = new HttpEntity<>(request, headers);

        try {
            logger.info("Calling AJBank API | amount: {} | sender: {}", amount, sender);
            ResponseEntity<AJBankResponse> response = restTemplate.postForEntity(url, entity, AJBankResponse.class);
            return response.getBody();
        } catch (Exception e) {
            logger.error("AJBank API Call Failed | error: {}", e.getMessage());
            throw new RuntimeException("AJBank communication failure: " + e.getMessage());
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AJBankRequest {
        private String senderAccount;
        private String recipientAccount;
        private BigDecimal amount;
        private String idempotencyKey;
        private String correlationId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AJBankResponse {
        private String transactionId;
        private String status; // SUCCESS, REJECTED, PENDING
        private String message;
    }
}
