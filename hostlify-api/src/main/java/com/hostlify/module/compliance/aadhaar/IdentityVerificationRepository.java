package com.hostlify.module.compliance.aadhaar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, Long> {
    Optional<IdentityVerification> findFirstByHostellerIdAndStatusOrderByCreatedAtDesc(Long hostellerId, String status);
    List<IdentityVerification> findByHostellerIdOrderByCreatedAtDesc(Long hostellerId);
    List<IdentityVerification> findByHostelIdAndStatus(Long hostelId, String status);
}
