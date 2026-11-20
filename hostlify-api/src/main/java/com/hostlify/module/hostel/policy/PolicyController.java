package com.hostlify.module.hostel.policy;

import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.HostelAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/hostels/{hostelId}/policies")
@Tag(name = "Hostel policies")
public class PolicyController {

    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','HOSTELLER')")
    @Operation(summary = "Cost of stay, notice / lock-in, house rules and safety facilities")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long hostelId) {
        if (HostelAccess.principal().isHosteller()) return ApiResponse.ok(service.publicView(hostelId));
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(service.staffView(hostelId));
    }

    @PutMapping
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Owner/Admin/Manager: save the hostel's fine print, house rules and safety facilities")
    public ApiResponse<Map<String, Object>> save(@PathVariable Long hostelId, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.requireSubRole("OWNER", "ADMIN", "MANAGER");
        return ApiResponse.ok(service.save(hostelId, body, HostelAccess.principal().getUserId()), "Saved");
    }
}
