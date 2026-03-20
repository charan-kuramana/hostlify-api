package com.hostlify.module.complaint.controller;

import com.hostlify.module.complaint.entity.ComplaintMaintenance;
import com.hostlify.module.complaint.entity.ComplaintRecord;
import com.hostlify.module.complaint.service.ComplaintService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) return null;
        return (UserPrincipal) auth.getPrincipal();
    }

    @GetMapping("/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<ComplaintRecord>> getByHostel(@PathVariable Long hostelId) {
        return ApiResponse.ok(complaintService.getComplaintsByHostel(hostelId));
    }

    @GetMapping("/hosteller/{hostellerId}")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    public ApiResponse<List<ComplaintRecord>> getByHosteller(@PathVariable Long hostellerId) {
        return ApiResponse.ok(complaintService.getComplaintsByHosteller(hostellerId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<List<ComplaintRecord>> getMyComplaints() {
        UserPrincipal p = getPrincipal();
        Long hostellerId = (p != null) ? p.getHostellerProfileId() : null;
        return ApiResponse.ok(complaintService.getComplaintsByHosteller(hostellerId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    public ApiResponse<ComplaintRecord> getById(@PathVariable Long id) {
        return ApiResponse.ok(complaintService.getComplaint(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<ComplaintRecord> create(@RequestBody ComplaintRecord complaint) {
        UserPrincipal p = getPrincipal();
        if (p != null) {
            if (complaint.getHostellerId() == null) {
                complaint.setHostellerId(p.getHostellerProfileId());
            }
            if (complaint.getHostelId() == null) {
                complaint.setHostelId(p.getHostelId());
            }
        }
        return ApiResponse.ok(complaintService.createComplaint(complaint));
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<ComplaintRecord> resolve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        Long resolvedBy = p != null ? p.getUserId() : null;
        String status = (String) body.getOrDefault("status", "RESOLVED");
        String resolution = (String) body.get("resolution");
        return ApiResponse.ok(complaintService.updateComplaintStatus(id, status, resolvedBy, resolution));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<ComplaintRecord> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        Long resolvedBy = p != null ? p.getUserId() : null;
        String status = (String) body.getOrDefault("status", "OPEN");
        String resolution = (String) body.get("resolution");
        return ApiResponse.ok(complaintService.updateComplaintStatus(id, status, resolvedBy, resolution));
    }

    @GetMapping("/maintenance/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<ComplaintMaintenance>> getMaintenance(@PathVariable Long hostelId) {
        return ApiResponse.ok(complaintService.getMaintenanceByHostel(hostelId));
    }

    @PostMapping("/maintenance")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<ComplaintMaintenance> createMaintenance(@RequestBody ComplaintMaintenance maintenance) {
        UserPrincipal p = getPrincipal();
        if (p != null) {
            if (maintenance.getReportedBy() == null) {
                maintenance.setReportedBy(p.getUserId());
            }
            if (maintenance.getHostelId() == null && p.getHostelIds() != null && !p.getHostelIds().isEmpty()) {
                maintenance.setHostelId(p.getHostelIds().get(0));
            }
        }
        return ApiResponse.ok(complaintService.createMaintenance(maintenance));
    }

    @PutMapping("/maintenance/{id}/resolve")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<ComplaintMaintenance> resolveMaintenance(@PathVariable Long id) {
        return ApiResponse.ok(complaintService.resolveMaintenance(id));
    }

    @PatchMapping("/maintenance/{id}/status")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<ComplaintMaintenance> updateMaintenanceStatus(@PathVariable Long id,
                                                                      @RequestBody Map<String, Object> body) {
        String status = (String) body.getOrDefault("status", "OPEN");
        return ApiResponse.ok(complaintService.updateMaintenanceStatus(id, status));
    }
}
