package com.rtps.gateway.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WebhookSignatureVerifier {

	@Value("${rtps.bank1.webhook.secret:RTPS_WEBHOOK_SECRET_2026}")
	private String webhookSecret;

	public boolean verifySignature(String payload, String incomingSignature) {
		try {
			String calculatedSignature = generateSignature(payload, webhookSecret);
			boolean isValid = calculatedSignature.equals(incomingSignature);
			if (!isValid) {
				log.warn("Webhook signature mismatch! Calculated: {} | Received: {}", calculatedSignature,
						incomingSignature);
			}
			return isValid;
		} catch (Exception e) {
			log.error("Error during signature verification: {}", e);
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
