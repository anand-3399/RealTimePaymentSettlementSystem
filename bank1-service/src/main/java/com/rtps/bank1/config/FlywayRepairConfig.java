package com.rtps.bank1.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayRepairConfig {
    @Bean
    public FlywayMigrationStrategy repairStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
