package com.omnichannel.order.repository;

import com.omnichannel.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Order> findAllByOrderByCreatedAtDesc();
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Order> findByStatusAndCreatedAtBefore(com.omnichannel.order.entity.OrderStatus status, java.time.LocalDateTime threshold);
}
