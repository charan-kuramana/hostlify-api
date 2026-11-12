package com.hostlify.module.compliance.agreement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgreementTemplateRepository extends JpaRepository<AgreementTemplate, Long> {
    Optional<AgreementTemplate> findByHostelId(Long hostelId);
}
