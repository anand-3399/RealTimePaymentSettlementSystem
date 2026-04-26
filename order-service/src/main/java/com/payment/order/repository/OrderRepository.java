package com.payment.order.repository;

import com.payment.order.entity.Order;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUsername(String username);
    Page<Order> findByUsername(String username, org.springframework.data.domain.Pageable pageable);
}
