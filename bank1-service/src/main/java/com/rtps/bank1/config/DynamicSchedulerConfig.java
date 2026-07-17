package com.rtps.bank1.config;

import com.rtps.bank1.scheduler.AccountingEntryRecoveryScheduler;
import com.rtps.bank1.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Configuration
@EnableScheduling
public class DynamicSchedulerConfig implements SchedulingConfigurer {

    @Autowired
    private ConfigService configService;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // 1. Config Cache Refresh Task
        taskRegistrar.addTriggerTask(
                () -> configService.refreshCache(),
                triggerContext -> {
                    long delay = configService.getConfigAsLong("BANK1_CONFIG_REFRESH_RATE");
                    Instant lastCompletion = triggerContext.lastCompletion();
                    if (lastCompletion == null) {
                        return Instant.now().plusMillis(delay);
                    }
                    return lastCompletion.plusMillis(delay);
                }
        );
    }
}
