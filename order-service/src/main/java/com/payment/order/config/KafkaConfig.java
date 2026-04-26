package com.payment.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.JsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;

@Configuration
public class KafkaConfig {

    /**
     * This bean allows @KafkaListener to automatically convert JSON strings 
     * into DTO objects using the application's ObjectMapper.
     */
    @Bean
    public RecordMessageConverter converter() {
        return new JsonMessageConverter();
    }
}
