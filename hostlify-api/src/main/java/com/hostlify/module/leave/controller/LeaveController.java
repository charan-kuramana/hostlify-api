package com.hostlify.module.leave.controller;

import com.hostlify.module.leave.entity.LeaveRequest;
import com.hostlify.module.leave.service.LeaveService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) return null;
        return (UserPrincipal) auth.getPrincipal();
    }

    @GetMapping("/hosteller/{hostellerId}")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    public ApiResponse<List<LeaveRequest>> getByHosteller(@PathVariable Long hostellerId) {
        UserPrincipal p = getPrincipal();
        if (p != null && p.isHosteller() && !java.util.Objects.equals(p.getHostellerProfileId(), hostellerId)) {
            throw new com.hostlify.shared.exception.AppException("Access denied", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (p != null && p.isStaff()) {
            // Staff: only leaves of residents in their own hostels
            return ApiResponse.ok(leaveService.getByHosteller(hostellerId).stream()
                    .filter(l -> p.getHostelIds() != null && p.getHostelIds().contains(leaveService.hostelIdOf(l))).toList());
        }
        return ApiResponse.ok(leaveService.getByHosteller(hostellerId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<List<LeaveRequest>> getMyLeaves() {
        UserPrincipal p = getPrincipal();
        Long hostellerId = (p != null) ? p.getHostellerProfileId() : null;
        return ApiResponse.ok(leaveService.getByHosteller(hostellerId));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<LeaveRequest>> getPending(@RequestParam(required = false) Long hostelId) {
        // Was platform-wide: every hostel's staff saw (and could approve) every hostel's leaves
        UserPrincipal p = getPrincipal();
        if (hostelId != null) {
            com.hostlify.shared.security.HostelAccess.requireStaffHostel(hostelId);
            return ApiResponse.ok(leaveService.getForHostel(hostelId, true));
        }
        List<LeaveRequest> out = new java.util.ArrayList<>();
        if (p != null && p.getHostelIds() != null) p.getHostelIds().forEach(h -> out.addAll(leaveService.getForHostel(h, true)));
        return ApiResponse.ok(out);
    }

    /** Staff: all leave requests (pending + reviewed) for residents of a hostel */
    @GetMapping("/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<LeaveRequest>> getForHostel(@PathVariable Long hostelId) {
        com.hostlify.shared.security.HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(leaveService.getForHostel(hostelId, false));
    }

    @GetMapping("/occupancy")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Map<String, Object>> getOccupancy(@RequestParam Long hostelId,
                                                          @RequestParam String from,
                                                          @RequestParam String to) {
        com.hostlify.shared.security.HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(leaveService.getOccupancy(hostelId,
                java.time.LocalDate.parse(from), java.time.LocalDate.parse(to)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    public ApiResponse<LeaveRequest> getById(@PathVariable Long id) {
        LeaveRequest leave = leaveService.getById(id);
        UserPrincipal p = getPrincipal();
        if (p != null && p.isHosteller() && !java.util.Objects.equals(p.getHostellerProfileId(), leave.getHostellerId())) {
            throw new com.hostlify.shared.exception.AppException("Access denied", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (p != null && p.isStaff()) com.hostlify.shared.security.HostelAccess.requireStaffHostel(leaveService.hostelIdOf(leave));
        return ApiResponse.ok(leave);
    }

    @PostMapping
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<LeaveRequest> apply(@RequestBody LeaveRequest leave) {
        UserPrincipal p = getPrincipal();
        if (p != null) {
            // Never trust ids from the body — the service resolves the active booking.
            leave.setHostellerId(p.getHostellerProfileId());
            leave.setBookingId(null);
        }
        leave.setId(null);
        leave.setReviewedBy(null);
        leave.setReviewedAt(null);
        return ApiResponse.ok(leaveService.apply(leave));
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<LeaveRequest> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        UserPrincipal p = getPrincipal();
        LeaveRequest leave = leaveService.getById(id);
        com.hostlify.shared.security.HostelAccess.requireStaffHostel(leaveService.hostelIdOf(leave));
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new com.hostlify.shared.exception.AppException("status must be APPROVED or REJECTED", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (!"PENDING".equals(leave.getStatus())) {
            throw new com.hostlify.shared.exception.AppException("Leave request is already " + leave.getStatus(), org.springframework.http.HttpStatus.CONFLICT);
        }
        // Reviewer is always the caller (a client-supplied reviewedBy was trusted before)
        Long reviewedBy = p != null ? p.getUserId() : null;
        return ApiResponse.ok(leaveService.review(id, status, reviewedBy));
    }
}
