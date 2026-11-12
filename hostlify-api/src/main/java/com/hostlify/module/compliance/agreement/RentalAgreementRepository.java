package com.hostlify.module.compliance.agreement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RentalAgreementRepository extends JpaRepository<RentalAgreement, Long> {
    List<RentalAgreement> findByHostellerIdOrderBySentAtDesc(Long hostellerId);
    List<RentalAgreement> findByHostelIdOrderBySentAtDesc(Long hostelId);
    boolean existsByHostellerIdAndStatusIn(Long hostellerId, List<String> statuses);
}
