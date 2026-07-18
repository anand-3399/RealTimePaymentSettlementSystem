package com.rtps.gateway.repository;

import com.rtps.gateway.entity.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConfigRepository extends JpaRepository<ConfigEntry, Long> {
    List<ConfigEntry> findByIsActiveTrue();
}
