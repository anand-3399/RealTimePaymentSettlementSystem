package com.rtps.bank1.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.rtps.bank1.entity.ConfigEntry;
import com.rtps.bank1.repository.ConfigRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {
    private final ConfigRepository configRepository;
    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    public void refreshCache() {
        log.info("Refreshing configuration cache from database...");
        List<ConfigEntry> configs = configRepository.findByIsActiveTrue();
        configs.forEach(config -> configCache.put(config.getConfigKey(), config.getConfigValue()));
        log.info("Configuration cache loaded with {} entries.", configCache.size());
    }

    public String getConfigAsString(String key) {
        String val = configCache.get(key);
        if (val == null) throw new IllegalArgumentException("Missing configuration for key: " + key);
        return val;
    }

    public int getConfigAsInt(String key) {
        String val = getConfigAsString(key);
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            log.error("Invalid integer config value for key {}: {}", key, val);
            throw new IllegalArgumentException("Invalid integer config for key: " + key, e);
        }
    }

    public long getConfigAsLong(String key) {
        String val = getConfigAsString(key);
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            log.error("Invalid long config value for key {}: {}", key, val);
            throw new IllegalArgumentException("Invalid long config for key: " + key, e);
        }
    }

    public boolean getConfigAsBoolean(String key) {
        String val = getConfigAsString(key);
        return Boolean.parseBoolean(val);
    }
}

