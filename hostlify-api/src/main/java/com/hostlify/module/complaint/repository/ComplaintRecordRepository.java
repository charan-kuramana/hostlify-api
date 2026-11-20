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
    List<ComplaintRecord> findByStatusInAndDueAtBeforeAndOverdueAlertedFalse(List<String> statuses, java.time.LocalDateTime before);
    List<ComplaintRecord> findByHostelIdAndCreatedAtAfter(Long hostelId, java.time.LocalDateTime after);

    interface SlaTargets extends JpaRepository<com.hostlify.module.complaint.entity.ComplaintSlaTarget, Long> {
        List<com.hostlify.module.complaint.entity.ComplaintSlaTarget> findByHostelId(Long hostelId);
    }
}
