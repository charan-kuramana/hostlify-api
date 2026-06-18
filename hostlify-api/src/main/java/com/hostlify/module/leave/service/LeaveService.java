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

    /**
     * Occupancy planning: how many residents are expected to be present in [from, to],
     * based on active residents minus those with an APPROVED leave overlapping the range.
     */
    public java.util.Map<String, Object> getOccupancy(Long hostelId, java.time.LocalDate from, java.time.LocalDate to) {
        var residents = profileRepo.findByHostelIdAndStatusOrderByCreatedAtDesc(hostelId, "ACTIVE");
        java.util.Set<Long> residentIds = new java.util.HashSet<>();
        residents.forEach(r -> residentIds.add(r.getId()));

        var approved = leaveRepo.findByStatusOrderByCreatedAtDesc("APPROVED");
        java.util.List<java.util.Map<String, Object>> onLeaveList = new java.util.ArrayList<>();
        java.util.Set<Long> onLeaveIds = new java.util.HashSet<>();
        for (LeaveRequest l : approved) {
            if (!residentIds.contains(l.getHostellerId())) continue;
            // overlap: leave.from <= to && leave.to >= from
            if (!l.getFromDate().isAfter(to) && !l.getToDate().isBefore(from)) {
                if (onLeaveIds.add(l.getHostellerId())) {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("hostellerId", l.getHostellerId());
                    profileRepo.findById(l.getHostellerId()).ifPresent(p ->
                            m.put("name", (p.getFirstName() + " " + (p.getLastName() != null ? p.getLastName() : "")).trim()));
                    m.put("leaveType", l.getLeaveType());
                    m.put("fromDate", l.getFromDate());
                    m.put("toDate", l.getToDate());
                    onLeaveList.add(m);
                }
            }
        }
        int total = residents.size();
        int onLeave = onLeaveIds.size();
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("totalResidents", total);
        out.put("onLeave", onLeave);
        out.put("expectedOccupancy", total - onLeave);
        out.put("from", from);
        out.put("to", to);
        out.put("onLeaveResidents", onLeaveList);
        return out;
    }
}
