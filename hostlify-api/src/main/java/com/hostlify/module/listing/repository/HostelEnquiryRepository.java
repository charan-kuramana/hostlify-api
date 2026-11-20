package com.hostlify.module.listing.repository;

import com.hostlify.module.listing.entity.HostelEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HostelEnquiryRepository extends JpaRepository<HostelEnquiry, Long> {
    List<HostelEnquiry> findByHostelIdOrderByCreatedAtDesc(Long hostelId);
    List<HostelEnquiry> findByHostelIdAndStatusOrderByCreatedAtDesc(Long hostelId, String status);
    List<HostelEnquiry> findByStatus(String status);
    boolean existsByHostelIdAndMobileAndCreatedAtAfter(Long hostelId, String mobile, LocalDateTime after);
    long countByHostelIdAndStatus(Long hostelId, String status);
}
