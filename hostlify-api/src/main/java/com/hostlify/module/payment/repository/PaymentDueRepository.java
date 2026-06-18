package com.hostlify.module.payment.repository;

import com.hostlify.module.payment.entity.PaymentDue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentDueRepository extends JpaRepository<PaymentDue, Long> {
    List<PaymentDue> findByHostellerIdAndStatus(Long hostellerId, String status);
    List<PaymentDue> findByHostelIdAndPeriodYearAndPeriodMonth(Long hostelId, int year, int month);
    List<PaymentDue> findByBookingId(Long bookingId);
    List<PaymentDue> findByHostellerIdOrderByDueDateDesc(Long hostellerId);
    List<PaymentDue> findByHostelIdAndStatus(Long hostelId, String status);
    List<PaymentDue> findByHostelIdOrderByDueDateDesc(Long hostelId);
    java.util.Optional<PaymentDue> findByHostellerIdAndPeriodYearAndPeriodMonth(Long hostellerId, int year, int month);
    List<PaymentDue> findByStatusAndDueDateBefore(String status, java.time.LocalDate date);
}
