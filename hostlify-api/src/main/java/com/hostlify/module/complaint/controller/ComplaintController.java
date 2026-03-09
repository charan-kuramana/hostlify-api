package com.hostlify.module.complaint.controller;

import com.hostlify.module.complaint.entity.ComplaintMaintenance;
import com.hostlify.module.complaint.entity.ComplaintRecord;
import com.hostlify.module.complaint.service.ComplaintService;
import com.hostlify.shared.dto.ApiResponse;
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

    @GetMapping("/hostel/{hostelId}")
    public ApiResponse<List<ComplaintRecord>> getByHostel(@PathVariable Long hostelId) {
        return ApiResponse.ok(complaintService.getComplaintsByHostel(hostelId));
    }

    @GetMapping("/hosteller/{hostellerId}")
    public ApiResponse<List<ComplaintRecord>> getByHosteller(@PathVariable Long hostellerId) {
        return ApiResponse.ok(complaintService.getComplaintsByHosteller(hostellerId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ComplaintRecord> getById(@PathVariable Long id) {
        return ApiResponse.ok(complaintService.getComplaint(id));
    }

    @PostMapping
    public ApiResponse<ComplaintRecord> create(@RequestBody ComplaintRecord complaint) {
        return ApiResponse.ok(complaintService.createComplaint(complaint));
    }

    @PutMapping("/{id}/resolve")
    public ApiResponse<ComplaintRecord> resolve(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return ApiResponse.ok(complaintService.resolveComplaint(id, body.get("resolvedBy")));
    }

    @GetMapping("/maintenance/hostel/{hostelId}")
    public ApiResponse<List<ComplaintMaintenance>> getMaintenance(@PathVariable Long hostelId) {
        return ApiResponse.ok(complaintService.getMaintenanceByHostel(hostelId));
    }

    @PostMapping("/maintenance")
    public ApiResponse<ComplaintMaintenance> createMaintenance(@RequestBody ComplaintMaintenance maintenance) {
        return ApiResponse.ok(complaintService.createMaintenance(maintenance));
    }

    @PutMapping("/maintenance/{id}/resolve")
    public ApiResponse<ComplaintMaintenance> resolveMaintenance(@PathVariable Long id) {
        return ApiResponse.ok(complaintService.resolveMaintenance(id));
    }
}
