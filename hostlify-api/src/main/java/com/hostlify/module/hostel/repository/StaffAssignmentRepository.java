package com.hostlify.module.hostel.repository;

import com.hostlify.module.hostel.entity.StaffAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, Long> {
    List<StaffAssignment> findByStaffUserIdAndIsActiveTrue(Long staffUserId);
    List<StaffAssignment> findByHostelIdAndIsActiveTrue(Long hostelId);
}
