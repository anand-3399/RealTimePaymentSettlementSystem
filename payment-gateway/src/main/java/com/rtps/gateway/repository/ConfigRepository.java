package com.rtps.gateway.repository;

import com.rtps.gateway.entity.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfigRepository extends JpaRepository<ConfigEntry, Long> {
    List<ConfigEntry> findByIsActiveTrue();
}
