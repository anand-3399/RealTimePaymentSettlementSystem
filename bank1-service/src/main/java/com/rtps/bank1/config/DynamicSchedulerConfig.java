package com.rtps.bank1.config;

import java.time.Instant;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import com.rtps.bank1.service.ConfigService;

@Configuration
@EnableScheduling
public class DynamicSchedulerConfig implements SchedulingConfigurer {

	private final ConfigService configService;

	DynamicSchedulerConfig(ConfigService configService) {
		this.configService = configService;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		// 1. Config Cache Refresh Task
		taskRegistrar.addTriggerTask(() -> configService.refreshCache(), triggerContext -> {
			long delay = configService.getConfigAsLong("AJBANK_CONFIG_REFRESH_RATE");
			Instant lastCompletion = triggerContext.lastCompletion();
			if (lastCompletion == null) {
				return Instant.now().plusMillis(delay);
			}
			return lastCompletion.plusMillis(delay);
		});
	}
}
