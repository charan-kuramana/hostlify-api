package com.hostlify.module.complaint.repository;

import com.hostlify.module.complaint.entity.ComplaintRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintRecordRepository extends JpaRepository<ComplaintRecord, Long> {
    List<ComplaintRecord> findByHostellerIdOrderByCreatedAtDesc(Long hostellerId);
    List<ComplaintRecord> findByHostelIdAndStatus(Long hostelId, String status);
    List<ComplaintRecord> findByHostelIdAndPriority(Long hostelId, String priority);
    List<ComplaintRecord> findByHostelIdOrderByCreatedAtDesc(Long hostelId);
}
