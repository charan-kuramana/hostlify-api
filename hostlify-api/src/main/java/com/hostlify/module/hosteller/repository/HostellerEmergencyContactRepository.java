package com.hostlify.module.hosteller.repository;

import com.hostlify.module.hosteller.entity.HostellerEmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HostellerEmergencyContactRepository extends JpaRepository<HostellerEmergencyContact, Long> {
    Optional<HostellerEmergencyContact> findFirstByHostellerId(Long hostellerId);
}
