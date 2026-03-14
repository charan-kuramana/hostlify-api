package com.hostlify.module.service.controller;

import com.hostlify.module.service.entity.CleaningRequest;
import com.hostlify.module.service.service.CleaningService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cleaning")
@Tag(name = "Cleaning Requests")
public class CleaningController {

    private final CleaningService cleaningService;

    public CleaningController(CleaningService cleaningService) {
        this.cleaningService = cleaningService;
    }

    @GetMapping("/hostel/{hostelId}")
    @Operation(summary = "Staff: list all cleaning requests for a hostel")
    public ApiResponse<List<CleaningRequest>> getByHostel(@PathVariable Long hostelId,
                                                            @RequestParam(required = false) String status) {
        if (status != null) {
            return ApiResponse.ok(cleaningService.getByHostelAndStatus(hostelId, status));
        }
        return ApiResponse.ok(cleaningService.getByHostel(hostelId));
    }

    @GetMapping("/me")
    @Operation(summary = "Hosteller: get my cleaning requests")
    public ApiResponse<List<CleaningRequest>> getMyRequests() {
        UserPrincipal p = getPrincipal();
        return ApiResponse.ok(cleaningService.getMyRequests(p.getHostellerProfileId()));
    }

    @PostMapping
    @Operation(summary = "Hosteller: submit a cleaning request")
    public ApiResponse<CleaningRequest> submit(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        String roomNumber = (String) body.getOrDefault("roomNumber", null);
        String notes = (String) body.getOrDefault("notes", null);
        return ApiResponse.ok(
                cleaningService.submitRequest(p.getHostellerProfileId(), p.getHostelId(), roomNumber, notes),
                "Cleaning request submitted");
    }

    @PatchMapping("/{requestId}/status")
    @Operation(summary = "Staff: update a cleaning request status (PENDING/IN_PROGRESS/COMPLETED/CANCELLED)")
    public ApiResponse<CleaningRequest> updateStatus(@PathVariable Long requestId,
                                                      @RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        String status = (String) body.get("status");
        return ApiResponse.ok(cleaningService.updateStatus(requestId, status, p.getUserId()), "Status updated");
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AppException("Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        return (UserPrincipal) auth.getPrincipal();
    }
}
