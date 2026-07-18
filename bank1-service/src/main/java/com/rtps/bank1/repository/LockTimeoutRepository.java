package com.rtps.bank1.repository;

import com.rtps.bank1.entity.LockTimeout;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LockTimeoutRepository extends JpaRepository<LockTimeout, UUID> {
}
