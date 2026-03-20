package com.hostlify.module.leave.service;

import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.leave.entity.LeaveRequest;
import com.hostlify.module.leave.repository.LeaveRequestRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LeaveService {

    private final LeaveRequestRepository leaveRepo;
    private final HostellerProfileRepository profileRepo;
    private final BookingCheckinRepository bookingRepo;

    public LeaveService(LeaveRequestRepository leaveRepo,
                        HostellerProfileRepository profileRepo,
                        BookingCheckinRepository bookingRepo) {
        this.leaveRepo = leaveRepo;
        this.profileRepo = profileRepo;
        this.bookingRepo = bookingRepo;
    }

    public List<LeaveRequest> getByHosteller(Long hostellerId) {
        return leaveRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId);
    }

    public List<LeaveRequest> getPending() {
        List<LeaveRequest> list = leaveRepo.findByStatusOrderByCreatedAtDesc("PENDING");
        list.forEach(item -> {
            profileRepo.findById(item.getHostellerId()).ifPresent(p ->
                item.setHostellerName(p.getFirstName() + " " + (p.getLastName() != null ? p.getLastName() : "").trim()));
        });
        return list;
    }

    public LeaveRequest getById(Long id) {
        return leaveRepo.findById(id)
                .orElseThrow(() -> new AppException("Leave request not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public LeaveRequest apply(LeaveRequest leave) {
        leave.setStatus("PENDING");
        // Auto-populate bookingId from active booking if not provided
        if (leave.getBookingId() == null && leave.getHostellerId() != null) {
            bookingRepo.findFirstByHostellerIdAndStatus(leave.getHostellerId(), "ACTIVE")
                    .ifPresent(b -> leave.setBookingId(b.getId()));
        }
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
