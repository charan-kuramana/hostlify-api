package com.hostlify.module.payment.repository;

import com.hostlify.module.payment.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    List<PaymentRecord> findByHostellerIdOrderByCreatedAtDesc(Long hostellerId);
    List<PaymentRecord> findByDueId(Long dueId);
    Optional<PaymentRecord> findByRazorpayPaymentId(String razorpayPaymentId);
    List<PaymentRecord> findByHostelIdOrderByCreatedAtDesc(Long hostelId);
    List<PaymentRecord> findByHostelIdAndStatusOrderByCreatedAtDesc(Long hostelId, String status);
    List<PaymentRecord> findByDueIdAndStatus(Long dueId, String status);
}
