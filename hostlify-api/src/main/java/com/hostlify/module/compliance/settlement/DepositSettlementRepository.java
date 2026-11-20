package com.hostlify.module.compliance.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepositSettlementRepository extends JpaRepository<DepositSettlement, Long> {
    Optional<DepositSettlement> findByBookingId(Long bookingId);
    List<DepositSettlement> findByHostellerIdOrderByCreatedAtDesc(Long hostellerId);
}
