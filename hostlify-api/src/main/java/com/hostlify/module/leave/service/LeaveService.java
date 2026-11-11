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
    private final com.hostlify.module.hostel.repository.RoomRepository roomRepo;

    public LeaveService(LeaveRequestRepository leaveRepo,
                        HostellerProfileRepository profileRepo,
                        BookingCheckinRepository bookingRepo,
                        com.hostlify.module.hostel.repository.RoomRepository roomRepo) {
        this.leaveRepo = leaveRepo;
        this.profileRepo = profileRepo;
        this.bookingRepo = bookingRepo;
        this.roomRepo = roomRepo;
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
        if (leave.getHostellerId() == null || leave.getBookingId() == null) {
            throw new AppException("You need an active stay to apply for leave", HttpStatus.BAD_REQUEST);
        }
        if (leave.getLeaveType() == null || leave.getLeaveType().isBlank()) leave.setLeaveType("CASUAL");
        if (!java.util.Set.of("CASUAL", "SICK", "HOME", "VACATE", "OTHER").contains(leave.getLeaveType())) {
            throw new AppException("Invalid leave type", HttpStatus.BAD_REQUEST);
        }
        if (leave.getFromDate() == null || leave.getToDate() == null) {
            throw new AppException("From and to dates are required", HttpStatus.BAD_REQUEST);
        }
        if (leave.getToDate().isBefore(leave.getFromDate())) {
            throw new AppException("To date must be on or after the from date", HttpStatus.BAD_REQUEST);
        }
        if (leave.getFromDate().isBefore(java.time.LocalDate.now())) {
            throw new AppException("From date cannot be in the past", HttpStatus.BAD_REQUEST);
        }
        // No overlapping pending/approved leave for the same hosteller
        boolean overlaps = leaveRepo.findByHostellerIdOrderByCreatedAtDesc(leave.getHostellerId()).stream()
                .filter(l -> "PENDING".equals(l.getStatus()) || "APPROVED".equals(l.getStatus()))
                .anyMatch(l -> !l.getFromDate().isAfter(leave.getToDate()) && !l.getToDate().isBefore(leave.getFromDate()));
        if (overlaps) {
            throw new AppException("You already have a leave request covering these dates", HttpStatus.CONFLICT);
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
    /** Hostel the leave's hosteller belongs to (leave rows carry no hostel id). */
    public Long hostelIdOf(LeaveRequest leave) {
        return profileRepo.findById(leave.getHostellerId()).map(p -> p.getHostelId()).orElse(null);
    }

    /** Leave requests of one hostel's residents (newest first); pendingOnly limits to PENDING. */
    public List<LeaveRequest> getForHostel(Long hostelId, boolean pendingOnly) {
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        profileRepo.findByHostelId(hostelId).forEach(p ->
                names.put(p.getId(), (p.getFirstName() + " " + (p.getLastName() != null ? p.getLastName() : "")).trim()));
        List<LeaveRequest> out = new java.util.ArrayList<>();
        java.util.Map<Long, String> roomByBooking = new java.util.HashMap<>();
        for (Long hostellerId : names.keySet()) {
            for (LeaveRequest l : leaveRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId)) {
                if (pendingOnly && !"PENDING".equals(l.getStatus())) continue;
                l.setHostellerName(names.get(hostellerId));
                // Room of the stay the leave belongs to (staff list shows it)
                if (l.getBookingId() != null) {
                    l.setRoomNumber(roomByBooking.computeIfAbsent(l.getBookingId(), bid ->
                            bookingRepo.findById(bid)
                                    .flatMap(b -> roomRepo.findById(b.getRoomId()))
                                    .map(r -> r.getRoomName())
                                    .orElse(null)));
                }
                out.add(l);
            }
        }
        out.sort(java.util.Comparator.comparing(LeaveRequest::getCreatedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return out;
    }

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
