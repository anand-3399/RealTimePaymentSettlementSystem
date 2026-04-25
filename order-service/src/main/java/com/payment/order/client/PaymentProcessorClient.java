package com.payment.order.client;

import com.payment.order.dto.PaymentStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentProcessorClient {

    private static final Logger logger = LoggerFactory.getLogger(PaymentProcessorClient.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${rtps.payment-processor.url}")
    private String processorUrl;

    @Value("${rtps.payment-processor.path}")
    private String processorPath;

    @Value("${rtps.payment-processor.secret}")
    private String internalSecret;

    public Optional<PaymentStatusResponse> getPaymentStatusByOrderId(UUID orderId) {
        try {
            String url = processorUrl + processorPath + "?orderId=" + orderId;
            logger.info("Querying internal payment status for orderId: {} | URL: {}", orderId, url);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("X-Internal-Secret", internalSecret);
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            ResponseEntity<PaymentStatusResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    PaymentStatusResponse.class
            );

            if (response.getStatusCode() == org.springframework.http.HttpStatus.OK) {
                return Optional.ofNullable(response.getBody());
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            logger.warn("Payment not found yet for orderId: {}", orderId);
        } catch (Exception e) {
            logger.error("Failed to query internal payment processor for orderId: {} | Error: {}", orderId, e.getMessage());
        }
        return Optional.empty();
    }
}
