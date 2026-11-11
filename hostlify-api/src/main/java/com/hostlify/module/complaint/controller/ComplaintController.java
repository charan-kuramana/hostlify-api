package com.hostlify.module.complaint.controller;

import com.hostlify.module.complaint.entity.ComplaintMaintenance;
import com.hostlify.module.complaint.entity.ComplaintRecord;
import com.hostlify.module.complaint.service.ComplaintService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.UserPrincipal;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.module.complaint.repository.ComplaintMaintenanceRepository;
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
    private final ComplaintMaintenanceRepository maintenanceRepo;

    public ComplaintController(ComplaintService complaintService, ComplaintMaintenanceRepository maintenanceRepo) {
        this.complaintService = complaintService;
        this.maintenanceRepo = maintenanceRepo;
    }

    /** Staff may only touch maintenance tasks of their own hostel. */
    private void requireMaintenanceAccess(Long id) {
        ComplaintMaintenance m = maintenanceRepo.findById(id)
                .orElseThrow(() -> new com.hostlify.shared.exception.AppException("Maintenance task not found",
                        org.springframework.http.HttpStatus.NOT_FOUND));
        HostelAccess.requireStaffHostel(m.getHostelId());
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) return null;
        return (UserPrincipal) auth.getPrincipal();
    }

    @GetMapping("/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<ComplaintRecord>> getByHostel(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(complaintService.getComplaintsByHostel(hostelId));
    }

    @GetMapping("/hosteller/{hostellerId}")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    public ApiResponse<List<ComplaintRecord>> getByHosteller(@PathVariable Long hostellerId) {
        UserPrincipal p = getPrincipal();
        if (p != null && p.isHosteller() && !java.util.Objects.equals(p.getHostellerProfileId(), hostellerId)) {
            throw new com.hostlify.shared.exception.AppException("Access denied", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (p != null && p.isStaff()) {
            // Staff: only complaints raised in their own hostels
            return ApiResponse.ok(complaintService.getComplaintsByHosteller(hostellerId).stream()
                    .filter(c -> p.getHostelIds() != null && p.getHostelIds().contains(c.getHostelId())).toList());
        }
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
        ComplaintRecord c = complaintService.getComplaint(id);
        UserPrincipal p = getPrincipal();
        if (p != null && p.isHosteller() && !java.util.Objects.equals(p.getHostellerProfileId(), c.getHostellerId())) {
            throw new com.hostlify.shared.exception.AppException("Access denied", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (p != null && p.isStaff()) HostelAccess.requireStaffHostel(c.getHostelId());
        return ApiResponse.ok(c);
    }

    @PostMapping
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<ComplaintRecord> create(@RequestBody ComplaintRecord complaint) {
        UserPrincipal p = getPrincipal();
        if (p != null) {
            // Never trust ids from the body: a hosteller files complaints only for themselves,
            // against the hostel of their active booking (resolved in the service).
            complaint.setHostellerId(p.getHostellerProfileId());
            complaint.setHostelId(null);
            complaint.setRoomId(null);
        }
        // Staff-only fields must not be set by the hosteller
        complaint.setId(null);
        complaint.setStatus("OPEN");
        complaint.setAssignedTo(null);
        complaint.setResolutionNotes(null);
        complaint.setResolvedAt(null);
        if (complaint.getTitle() == null || complaint.getTitle().isBlank()) {
            throw new com.hostlify.shared.exception.AppException("Title is required", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (complaint.getTitle().length() > 200) {
            throw new com.hostlify.shared.exception.AppException("Title must be 200 characters or less", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (complaint.getCategory() == null || complaint.getCategory().isBlank() || complaint.getCategory().length() > 40) {
            throw new com.hostlify.shared.exception.AppException("A valid category is required", org.springframework.http.HttpStatus.BAD_REQUEST);
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
        HostelAccess.requireStaffHostel(complaintService.getComplaint(id).getHostelId());
        if (!java.util.List.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").contains(status)) {
            throw new com.hostlify.shared.exception.AppException("Invalid status: " + status, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        requireResolutionNotes(status, resolution);
        return ApiResponse.ok(complaintService.updateComplaintStatus(id, status, resolvedBy, resolution));
    }

    /** Residents see the resolution notes, so resolving or closing a complaint needs one. */
    private void requireResolutionNotes(String status, String resolution) {
        if (("RESOLVED".equals(status) || "CLOSED".equals(status)) && (resolution == null || resolution.isBlank())) {
            throw new com.hostlify.shared.exception.AppException("Resolution notes are required to resolve a complaint",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (resolution != null && resolution.length() > 2000) {
            throw new com.hostlify.shared.exception.AppException("Resolution notes must be 2000 characters or fewer",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<ComplaintRecord> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        Long resolvedBy = p != null ? p.getUserId() : null;
        String status = (String) body.getOrDefault("status", "OPEN");
        String resolution = (String) body.get("resolution");
        HostelAccess.requireStaffHostel(complaintService.getComplaint(id).getHostelId());
        if (!java.util.List.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").contains(status)) {
            throw new com.hostlify.shared.exception.AppException("Invalid status: " + status, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        requireResolutionNotes(status, resolution);
        return ApiResponse.ok(complaintService.updateComplaintStatus(id, status, resolvedBy, resolution));
    }

    @GetMapping("/maintenance/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<ComplaintMaintenance>> getMaintenance(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
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
        HostelAccess.requireStaffHostel(maintenance.getHostelId());
        maintenance.setId(null);
        maintenance.setActualCost(null);
        maintenance.setCompletedAt(null);
        if (maintenance.getStatus() == null) maintenance.setStatus("OPEN");
        if (!java.util.List.of("OPEN", "IN_PROGRESS", "COMPLETED").contains(maintenance.getStatus())) {
            throw new com.hostlify.shared.exception.AppException("Invalid status: " + maintenance.getStatus(), org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (maintenance.getCategory() == null || maintenance.getCategory().isBlank()
                || maintenance.getDescription() == null || maintenance.getDescription().isBlank()) {
            throw new com.hostlify.shared.exception.AppException("Category and description are required",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (maintenance.getEstimatedCost() != null && maintenance.getEstimatedCost().signum() < 0) {
            throw new com.hostlify.shared.exception.AppException("Estimated cost cannot be negative",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if ("COMPLETED".equals(maintenance.getStatus())) maintenance.setCompletedAt(java.time.LocalDateTime.now());
        return ApiResponse.ok(complaintService.createMaintenance(maintenance));
    }

    @PutMapping("/maintenance/{id}/resolve")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<ComplaintMaintenance> resolveMaintenance(@PathVariable Long id) {
        requireMaintenanceAccess(id);
        return ApiResponse.ok(complaintService.resolveMaintenance(id));
    }

    @PatchMapping("/maintenance/{id}/status")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<ComplaintMaintenance> updateMaintenanceStatus(@PathVariable Long id,
                                                                      @RequestBody Map<String, Object> body) {
        String status = (String) body.getOrDefault("status", "OPEN");
        requireMaintenanceAccess(id);
        if (!java.util.List.of("OPEN", "IN_PROGRESS", "COMPLETED", "RESOLVED").contains(status)) {
            throw new com.hostlify.shared.exception.AppException("Invalid status: " + status, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        // Optional actual cost when completing the task
        java.math.BigDecimal actualCost = body.get("actualCost") != null
                ? new java.math.BigDecimal(body.get("actualCost").toString()) : null;
        if (actualCost != null && actualCost.signum() < 0) {
            throw new com.hostlify.shared.exception.AppException("Actual cost cannot be negative",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        ComplaintMaintenance updated = complaintService.updateMaintenanceStatus(id, status);
        if (actualCost != null) {
            updated.setActualCost(actualCost);
            updated = maintenanceRepo.save(updated);
        }
        return ApiResponse.ok(updated);
    }
}
