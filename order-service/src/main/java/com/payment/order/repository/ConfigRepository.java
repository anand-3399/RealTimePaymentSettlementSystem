package com.payment.order.repository;

import com.payment.order.entity.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConfigRepository extends JpaRepository<ConfigEntry, Long> {
    List<ConfigEntry> findByIsActiveTrue();
}
