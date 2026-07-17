package com.payment.order.repository;

import com.payment.order.entity.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfigRepository extends JpaRepository<ConfigEntry, Long> {
    List<ConfigEntry> findByIsActiveTrue();
}
