package com.hostlify.module.leave.repository;

import com.hostlify.module.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByHostellerIdOrderByCreatedAtDesc(Long hostellerId);
    List<LeaveRequest> findByBookingIdAndStatus(Long bookingId, String status);
    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);
}
