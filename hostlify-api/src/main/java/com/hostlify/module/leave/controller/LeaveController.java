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
    public ApiResponse<List<LeaveRequest>> getPending() {
        return ApiResponse.ok(leaveService.getPending());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    public ApiResponse<LeaveRequest> getById(@PathVariable Long id) {
        return ApiResponse.ok(leaveService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<LeaveRequest> apply(@RequestBody LeaveRequest leave) {
        UserPrincipal p = getPrincipal();
        if (p != null) {
            if (leave.getHostellerId() == null) {
                leave.setHostellerId(p.getHostellerProfileId());
            }
            if (leave.getBookingId() == null) {
                leave.setBookingId(p.getBookingId());
            }
        }
        return ApiResponse.ok(leaveService.apply(leave));
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<LeaveRequest> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        UserPrincipal p = getPrincipal();
        Long reviewedBy = null;
        if (body.get("reviewedBy") != null) {
            reviewedBy = Long.parseLong(body.get("reviewedBy").toString());
        } else if (p != null) {
            reviewedBy = p.getUserId();
        }
        return ApiResponse.ok(leaveService.review(id, status, reviewedBy));
    }
}
