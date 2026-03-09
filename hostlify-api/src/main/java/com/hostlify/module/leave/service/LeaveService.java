package com.hostlify.module.leave.service;

import com.hostlify.module.leave.entity.LeaveRequest;
import com.hostlify.module.leave.repository.LeaveRequestRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LeaveService {

    private final LeaveRequestRepository leaveRepo;

    public LeaveService(LeaveRequestRepository leaveRepo) {
        this.leaveRepo = leaveRepo;
    }

    public List<LeaveRequest> getByHosteller(Long hostellerId) {
        return leaveRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId);
    }

    public List<LeaveRequest> getPending() {
        return leaveRepo.findByStatusOrderByCreatedAtDesc("PENDING");
    }

    public LeaveRequest getById(Long id) {
        return leaveRepo.findById(id)
                .orElseThrow(() -> new AppException("Leave request not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public LeaveRequest apply(LeaveRequest leave) {
        leave.setStatus("PENDING");
        return leaveRepo.save(leave);
    }

    @Transactional
    public LeaveRequest review(Long id, String status, Long reviewedBy) {
        LeaveRequest leave = getById(id);
        leave.setStatus(status);
        leave.setReviewedBy(reviewedBy);
        leave.setReviewedAt(LocalDateTime.now());
        return leaveRepo.save(leave);
    }
}
