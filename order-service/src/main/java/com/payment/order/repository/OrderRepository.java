package com.payment.order.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payment.order.entity.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUsername(String username);
    Page<Order> findByUsername(String username, Pageable pageable);

    @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.username = :username AND (cast(:startDate as timestamp) IS NULL OR o.createdAt >= :startDate) AND (cast(:endDate as timestamp) IS NULL OR o.createdAt <= :endDate) AND (:status IS NULL OR o.status = :status) AND (:account IS NULL OR o.recipientBankAccount = :account) GROUP BY o.status")
    List<Object[]> countStatusesByUsernameAndDateRange(@Param("username") String username, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, @Param("status") Order.OrderStatus status, @Param("account") String account);

    @Query("SELECT o FROM Order o WHERE o.username = :username AND (cast(:startDate as timestamp) IS NULL OR o.createdAt >= :startDate) AND (cast(:endDate as timestamp) IS NULL OR o.createdAt <= :endDate) AND (:status IS NULL OR o.status = :status) AND (:account IS NULL OR o.recipientBankAccount = :account)")
    List<Order> findByUsernameAndDateRange(@Param("username") String username, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, @Param("status") Order.OrderStatus status, @Param("account") String account, Pageable pageable);
}
