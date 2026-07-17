package com.rtps.bank1.repository;

import com.rtps.bank1.entity.LockTimeout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LockTimeoutRepository extends JpaRepository<LockTimeout, UUID> {
}
