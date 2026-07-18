package com.rtps.bank1.repository;

import com.rtps.bank1.entity.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConfigRepository extends JpaRepository<ConfigEntry, Long> {
    List<ConfigEntry> findByIsActiveTrue();
}
