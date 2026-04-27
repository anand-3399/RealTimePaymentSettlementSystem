package com.rtps.ajbank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtps.ajbank.dto.TransferResponse;
import com.rtps.ajbank.entity.WebhookDelivery;
import com.rtps.ajbank.repository.WebhookDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class WebhookEmitterService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookEmitterService.class);

    @Autowired
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${rtps.ajbank.webhook.url}")
    private String webhookUrl;

    @Value("${rtps.ajbank.webhook.secret}")
    private String webhookSecret;

    @Async
    public void sendWebhook(TransferResponse payload) {
        String correlationId = payload.getCorrelationId();
        logger.info("Starting webhook emission | correlationId: {}", correlationId);

        String jsonPayload = "";
        String signature = "";
        int status = 0;

        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
            signature = generateSignature(jsonPayload, webhookSecret);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-AJBank-Signature", signature);
            headers.set("X-Correlation-ID", correlationId);

            HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);
            
            try {
                var response = restTemplate.postForEntity(webhookUrl, entity, String.class);
                status = response.getStatusCode().value();
                logger.info("Webhook delivered successfully | correlationId: {} | status: {}", correlationId, status);
            } catch (org.springframework.web.client.HttpStatusCodeException e) {
                status = e.getStatusCode().value();
                logger.warn("Webhook delivery failed with status {} | correlationId: {} | Response: {}", 
                        status, correlationId, e.getResponseBodyAsString());
            } catch (Exception e) {
                status = 500;
                logger.error("Webhook delivery failed due to network error | correlationId: {} | error: {}", 
                        correlationId, e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Failed to prepare webhook | correlationId: {} | error: {}", correlationId, e.getMessage());
        } finally {
            WebhookDelivery delivery = WebhookDelivery.builder()
                    .correlationId(correlationId)
                    .payload(jsonPayload)
                    .signature(signature)
                    .responseStatus(status)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();
            webhookDeliveryRepository.save(delivery);
        }
    }

    private String generateSignature(String data, String key) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
