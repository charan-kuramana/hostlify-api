package com.hostlify.module.payment.repository;

import com.hostlify.module.payment.entity.PaymentRazorpayOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRazorpayOrderRepository extends JpaRepository<PaymentRazorpayOrder, Long> {
    Optional<PaymentRazorpayOrder> findByRazorpayOrderId(String razorpayOrderId);
    Optional<PaymentRazorpayOrder> findByDueIdAndStatus(Long dueId, String status);
    List<PaymentRazorpayOrder> findByHostellerIdOrderByCreatedAtDesc(Long hostellerId);
}
