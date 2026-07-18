package com.rtps.gateway.config;

import java.time.Instant;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import com.rtps.gateway.service.ConfigService;

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
			long delay = configService.getConfigAsLong("PAYMENT_CONFIG_REFRESH_RATE_MS");
			Instant lastCompletion = triggerContext.lastCompletion();
			if (lastCompletion == null) {
				return Instant.now().plusMillis(delay);
			}
			return lastCompletion.plusMillis(delay);
		});
	}
}
