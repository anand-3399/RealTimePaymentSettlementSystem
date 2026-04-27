package com.rtps.processor.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class WebhookSignatureVerifier {

    private static final Logger logger = LoggerFactory.getLogger(WebhookSignatureVerifier.class);

    @Value("${rtps.ajbank.webhook.secret:RTPS_WEBHOOK_SECRET_2026}")
    private String webhookSecret;

    public boolean verifySignature(String payload, String incomingSignature) {
        try {
            String calculatedSignature = generateSignature(payload, webhookSecret);
            boolean isValid = calculatedSignature.equals(incomingSignature);
            if (!isValid) {
                logger.warn("Webhook signature verification failed! Calculated: {} | Received: {}", 
                        calculatedSignature, incomingSignature);
            }
            return isValid;
        } catch (Exception e) {
            logger.error("Error during signature verification: {}", e.getMessage());
            return false;
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
