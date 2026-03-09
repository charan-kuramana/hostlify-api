package com.hostlify.module.complaint.repository;

import com.hostlify.module.complaint.entity.ComplaintMaintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintMaintenanceRepository extends JpaRepository<ComplaintMaintenance, Long> {
    List<ComplaintMaintenance> findByHostelIdAndStatus(Long hostelId, String status);
    List<ComplaintMaintenance> findByHostelIdOrderByCreatedAtDesc(Long hostelId);
}
