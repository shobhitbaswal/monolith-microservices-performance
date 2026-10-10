package com.ecommerce.order.repository;

import com.ecommerce.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for OrderItem entity
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}

