package com.rtps.bank1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@SpringBootApplication(excludeName = { "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
		"org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration",
		"org.springframework.boot.sql.init.autoconfigure.SqlInitializationAutoConfiguration" })
@EnableAsync
@EnableScheduling
public class Bank1Application {
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(Bank1Application.class);
		app.setAdditionalProfiles("local");
		app.run(args);
	}

	@Bean
	public ObjectMapper legacyObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}
}