package com.payment.order.client;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.payment.order.dto.PaymentStatusResponse;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PaymentGatewayClient {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${rtps.payment-gateway.url}")
    private String processorUrl;

    @Value("${rtps.payment-gateway.path}")
    private String processorPath;

    @Value("${rtps.payment-gateway.secret}")
    private String internalSecret;

    public Optional<PaymentStatusResponse> getPaymentStatusByOrderId(UUID orderId) {
        try {
            String url = processorUrl + processorPath + "?orderId=" + orderId;
            log.info("Querying internal payment status for orderId: {} | URL: {}", orderId, url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", internalSecret);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<PaymentStatusResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    PaymentStatusResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                return Optional.ofNullable(response.getBody());
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Payment not found yet for orderId: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to query internal payment processor for orderId: {} | Error: {}", orderId, e);
        }
        return Optional.empty();
    }
}

