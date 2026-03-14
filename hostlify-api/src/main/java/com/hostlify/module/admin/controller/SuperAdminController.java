package com.hostlify.module.admin.controller;

import com.hostlify.module.admin.entity.AuditLog;
import com.hostlify.module.admin.entity.FeatureFlag;
import com.hostlify.module.admin.entity.Subscription;
import com.hostlify.module.admin.service.SuperAdminService;
import com.hostlify.module.hostel.entity.HostelOwner;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Super Admin")
public class SuperAdminController {

    private final SuperAdminService adminService;

    public SuperAdminController(SuperAdminService adminService) {
        this.adminService = adminService;
    }

    // ── Audit Logs ────────────────────────────────────────────────────────────

    @GetMapping("/audit/hostel/{hostelId}")
    @Operation(summary = "SuperAdmin: get audit logs for a hostel (paginated)")
    public ApiResponse<Page<AuditLog>> getAuditByHostel(
            @PathVariable Long hostelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireSuperAdmin();
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(adminService.getAuditLogsByHostel(hostelId, pageable));
    }

    @GetMapping("/audit/actor/{actorUserId}")
    @Operation(summary = "SuperAdmin: get audit logs by actor user (paginated)")
    public ApiResponse<Page<AuditLog>> getAuditByActor(
            @PathVariable Long actorUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireSuperAdmin();
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(adminService.getAuditLogsByActor(actorUserId, pageable));
    }

    // ── Subscriptions ─────────────────────────────────────────────────────────

    @GetMapping("/subscription/{ownerId}")
    @Operation(summary = "SuperAdmin: get subscription for an owner")
    public ApiResponse<Subscription> getSubscription(@PathVariable Long ownerId) {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.getSubscription(ownerId));
    }

    @PostMapping("/subscription")
    @Operation(summary = "SuperAdmin: create or update a subscription plan for an owner")
    public ApiResponse<Subscription> upsertSubscription(@RequestBody Map<String, Object> body) {
        requireSuperAdmin();
        Long ownerId = ((Number) body.get("ownerId")).longValue();
        String planName = (String) body.get("planName");
        int maxHostels = ((Number) body.getOrDefault("maxHostels", 1)).intValue();
        int maxBeds = ((Number) body.getOrDefault("maxBeds", 50)).intValue();
        BigDecimal priceMonthly = new BigDecimal(body.getOrDefault("priceMonthly", "0").toString());

        return ApiResponse.ok(
                adminService.upsertSubscription(ownerId, planName, maxHostels, maxBeds, priceMonthly),
                "Subscription saved");
    }

    // ── Feature Flags ─────────────────────────────────────────────────────────

    @GetMapping("/flags/{ownerId}")
    @Operation(summary = "SuperAdmin: get enabled feature flags for an owner")
    public ApiResponse<List<FeatureFlag>> getEnabledFlags(@PathVariable Long ownerId) {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.getEnabledFlags(ownerId));
    }

    @PostMapping("/flags")
    @Operation(summary = "SuperAdmin: set a feature flag for an owner")
    public ApiResponse<FeatureFlag> setFlag(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        requireSuperAdmin(p);

        Long ownerId = ((Number) body.get("ownerId")).longValue();
        String flagName = (String) body.get("flagName");
        boolean enabled = Boolean.parseBoolean(body.getOrDefault("enabled", "true").toString());
        String reason = (String) body.getOrDefault("reason", null);

        return ApiResponse.ok(adminService.setFlag(ownerId, flagName, enabled, p.getUserId(), reason),
                "Feature flag updated");
    }

    @GetMapping("/flags/{ownerId}/{flagName}")
    @Operation(summary = "SuperAdmin: check if a feature flag is enabled")
    public ApiResponse<Boolean> isFlagEnabled(@PathVariable Long ownerId,
                                               @PathVariable String flagName) {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.isFlagEnabled(ownerId, flagName));
    }

    // ── Owner Management ──────────────────────────────────────────────────────

    @GetMapping("/owners")
    @Operation(summary = "SuperAdmin: list all hostel owners (paginated)")
    public ApiResponse<Page<HostelOwner>> getOwners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.getOwners(PageRequest.of(page, size)));
    }

    @GetMapping("/owners/{id}")
    @Operation(summary = "SuperAdmin: get owner by ID")
    public ApiResponse<HostelOwner> getOwnerById(@PathVariable Long id) {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.getOwnerById(id));
    }

    @PostMapping("/owners")
    @Operation(summary = "SuperAdmin: create a new hostel owner account")
    public ApiResponse<HostelOwner> createOwner(@RequestBody Map<String, Object> body) {
        requireSuperAdmin();
        String email = (String) body.get("email");
        String password = (String) body.get("password");
        String businessName = (String) body.get("businessName");
        String ownerName = (String) body.get("ownerName");
        String mobile = (String) body.get("mobile");
        String planName = (String) body.getOrDefault("planName", "FREE");
        return ApiResponse.ok(
                adminService.createOwner(email, password, businessName, ownerName, mobile, planName),
                "Owner account created");
    }

    @PutMapping("/owners/{id}/suspend")
    @Operation(summary = "SuperAdmin: suspend an owner and their auth account")
    public ApiResponse<HostelOwner> suspendOwner(@PathVariable Long id) {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.suspendOwner(id), "Owner suspended");
    }

    @PutMapping("/owners/{id}/reset-password")
    @Operation(summary = "SuperAdmin: reset owner's password")
    public ApiResponse<Void> resetOwnerPassword(@PathVariable Long id,
                                                  @RequestBody Map<String, String> body) {
        requireSuperAdmin();
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.isBlank()) {
            throw new AppException("newPassword is required", HttpStatus.BAD_REQUEST);
        }
        adminService.resetOwnerPassword(id, newPassword);
        return ApiResponse.ok(null, "Password reset successfully");
    }

    // ── Dashboard & Reports ───────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @Operation(summary = "SuperAdmin: cross-hostel platform statistics")
    public ApiResponse<Map<String, Object>> getDashboard() {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.getDashboard());
    }

    @GetMapping("/reports/revenue")
    @Operation(summary = "SuperAdmin: revenue report for a given month/year")
    public ApiResponse<Map<String, Object>> getRevenueReport(
            @RequestParam int month,
            @RequestParam int year) {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.getRevenueReport(month, year));
    }

    @GetMapping("/reports/occupancy")
    @Operation(summary = "SuperAdmin: occupancy report across all hostels")
    public ApiResponse<Map<String, Object>> getOccupancyReport() {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.getOccupancyReport());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void requireSuperAdmin() {
        requireSuperAdmin(getPrincipal());
    }

    private void requireSuperAdmin(UserPrincipal p) {
        if (!p.isSuperAdmin()) {
            throw new AppException("Super admin access required", HttpStatus.FORBIDDEN);
        }
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AppException("Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        return (UserPrincipal) auth.getPrincipal();
    }
}
