package com.hostlify.module.service.repository;

import com.hostlify.module.service.entity.CleaningRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CleaningRequestRepository extends JpaRepository<CleaningRequest, Long> {
    List<CleaningRequest> findByHostelIdAndStatusOrderByCreatedAtDesc(Long hostelId, String status);
    List<CleaningRequest> findByHostellerIdOrderByCreatedAtDesc(Long hostellerId);
    List<CleaningRequest> findByHostelIdOrderByCreatedAtDesc(Long hostelId);
}
