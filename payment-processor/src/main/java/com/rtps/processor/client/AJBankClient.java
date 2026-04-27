package com.rtps.processor.client;

import com.rtps.processor.dto.AJBankRequest;
import com.rtps.processor.dto.AJBankResponse;
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
                        .status("PENDING_RETRY")
                        .message("Account is currently busy. Payment will be retried.")
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
                // Not a structured response, let Resilience4j handle it as a failure
                throw e;
            }
        }
    }
    public AJBankResponse fallbackTransfer(AJBankRequest request, Throwable t) {
        logger.error("AJBank fallback triggered for order {} | Error: {}", request.getIdempotencyKey(), t.getMessage());
        return AJBankResponse.builder()
                .status("PENDING_RETRY")
                .message("AJBank is currently unavailable. Payment will be retried.")
                .correlationId(request.getCorrelationId())
                .build();
    }
}
