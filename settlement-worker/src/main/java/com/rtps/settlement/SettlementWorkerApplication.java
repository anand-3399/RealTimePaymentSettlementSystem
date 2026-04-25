package com.rtps.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SettlementWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SettlementWorkerApplication.class, args);
    }
}
