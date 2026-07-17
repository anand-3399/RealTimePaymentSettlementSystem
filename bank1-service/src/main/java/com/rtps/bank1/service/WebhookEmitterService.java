package com.rtps.bank1.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtps.bank1.dto.TransferResponse;
import com.rtps.bank1.entity.WebhookDelivery;
import com.rtps.bank1.repository.WebhookDeliveryRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WebhookEmitterService {

    @Autowired
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${rtps.bank1.webhook.url}")
    private String webhookUrl;

    @Value("${rtps.bank1.webhook.secret}")
    private String webhookSecret;

    @Async
    public void sendWebhook(TransferResponse payload) {
        String correlationId = payload.getCorrelationId();
        log.info("Starting webhook emission | correlationId: {}", correlationId);

        String jsonPayload = "";
        String signature = "";
        int status = 0;

        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
            signature = generateSignature(jsonPayload, webhookSecret);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Bank1-Signature", signature);
            headers.set("X-Correlation-ID", correlationId);

            HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);
            
            try {
                var response = restTemplate.postForEntity(webhookUrl, entity, String.class);
                status = response.getStatusCode().value();
                log.info("Webhook delivered successfully | correlationId: {} | status: {}", correlationId, status);
            } catch (HttpStatusCodeException e) {
                status = e.getStatusCode().value();
                log.warn("Webhook delivery failed with status {} | correlationId: {} | Response: {}", 
                        status, correlationId, e.getResponseBodyAsString());
            } catch (Exception e) {
                status = 500;
                log.error("Webhook delivery failed due to network error | correlationId: {} | error: {}", 
                        correlationId, e.getMessage());
            }

        } catch (Exception e) {
            log.error("Failed to prepare webhook | correlationId: {} | error: {}", correlationId, e);
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

