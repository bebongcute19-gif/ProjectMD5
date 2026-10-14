package com.omnichannel.payment.repository;

import com.omnichannel.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByTransactionId(String transactionId);
    Optional<PaymentTransaction> findByOrderId(Long orderId);
    List<PaymentTransaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<PaymentTransaction> findAllByOrderByCreatedAtDesc();
}
