package com.hostlify.module.hosteller.repository;

import com.hostlify.module.hosteller.entity.HostellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HostellerProfileRepository extends JpaRepository<HostellerProfile, Long> {
    Optional<HostellerProfile> findByAuthUserId(Long authUserId);
    Optional<HostellerProfile> findByEmail(String email);
    Optional<HostellerProfile> findByMobile(String mobile);
    List<HostellerProfile> findByHostelId(Long hostelId);
    List<HostellerProfile> findByStatusOrderByCreatedAtDesc(String status);
    List<HostellerProfile> findByHostelIdAndStatusOrderByCreatedAtDesc(Long hostelId, String status);
}
