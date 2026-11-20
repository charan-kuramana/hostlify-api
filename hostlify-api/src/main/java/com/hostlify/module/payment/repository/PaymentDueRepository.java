package com.hostlify.module.payment.repository;

import com.hostlify.module.payment.entity.PaymentDue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentDueRepository extends JpaRepository<PaymentDue, Long> {
    List<PaymentDue> findByHostellerIdAndStatus(Long hostellerId, String status);
    List<PaymentDue> findByHostelIdAndPeriodYearAndPeriodMonth(Long hostelId, int year, int month);
    List<PaymentDue> findByBookingId(Long bookingId);
    List<PaymentDue> findByHostellerIdOrderByDueDateDesc(Long hostellerId);
    List<PaymentDue> findByHostelIdAndStatus(Long hostelId, String status);
    List<PaymentDue> findByHostelIdOrderByDueDateDesc(Long hostelId);
    List<PaymentDue> findByStatusAndDueDateBefore(String status, LocalDate date);

    /** A month can now hold several dues (rent + mess + electricity...): rent lookups must filter by type. */
    Optional<PaymentDue> findFirstByHostellerIdAndPeriodYearAndPeriodMonthAndDueType(
            Long hostellerId, int year, int month, String dueType);

    default Optional<PaymentDue> findRentDue(Long hostellerId, int year, int month) {
        return findFirstByHostellerIdAndPeriodYearAndPeriodMonthAndDueType(hostellerId, year, month, PaymentDue.TYPE_RENT);
    }

    List<PaymentDue> findByStatusIn(Collection<String> statuses);

    Optional<PaymentDue> findFirstByParentDueIdAndDueType(Long parentDueId, String dueType);

    /** Includes waived (soft-deleted) late fees, so a waived fee is never charged again. */
    @Query(value = "select count(*) > 0 from payment_dues where parent_due_id = :parentId and due_type = 'LATE_FEE'", nativeQuery = true)
    boolean lateFeeEverCharged(@org.springframework.data.repository.query.Param("parentId") Long parentId);

    @Query("select d from PaymentDue d where d.status in ('UNPAID','PARTIAL','OVERDUE') and d.dueType = 'RENT' and d.dueDate < :before")
    List<PaymentDue> findOpenRentDuesDueBefore(@org.springframework.data.repository.query.Param("before") LocalDate before);
}
