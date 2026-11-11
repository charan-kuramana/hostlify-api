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

    @GetMapping("/audit/owner/{ownerId}")
    @Operation(summary = "SuperAdmin: audit trail of super-admin actions on an owner (paginated)")
    public ApiResponse<Page<AuditLog>> getAuditByOwner(
            @PathVariable Long ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.getAuditLogsByOwner(ownerId, PageRequest.of(page, size)));
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
    @Operation(summary = "SuperAdmin: create or update a subscription plan for an owner (limits default from the plan)")
    public ApiResponse<Subscription> upsertSubscription(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        requireSuperAdmin(p);
        Long ownerId = requireLong(body, "ownerId");
        String planName = body.get("planName") != null ? body.get("planName").toString() : null;
        Integer maxHostels = body.get("maxHostels") instanceof Number n ? n.intValue() : null;
        Integer maxBeds = body.get("maxBeds") instanceof Number n ? n.intValue() : null;
        BigDecimal priceMonthly = body.get("priceMonthly") != null ? new BigDecimal(body.get("priceMonthly").toString()) : null;

        return ApiResponse.ok(
                adminService.upsertSubscription(ownerId, planName, maxHostels, maxBeds, priceMonthly, p.getUserId()),
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

        Long ownerId = requireLong(body, "ownerId");
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
    @Operation(summary = "SuperAdmin: list all hostel owners with hostel/member/revenue stats (paginated)")
    public ApiResponse<Page<Map<String, Object>>> getOwners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.getOwnerViews(PageRequest.of(page, size)));
    }

    @GetMapping("/owners/{id}")
    @Operation(summary = "SuperAdmin: get owner by ID (with hostel/member/revenue stats)")
    public ApiResponse<Map<String, Object>> getOwnerById(@PathVariable Long id) {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.getOwnerView(id));
    }

    @PostMapping("/owners")
    @Operation(summary = "SuperAdmin: create a new hostel owner account (optionally with their first hostel)")
    public ApiResponse<HostelOwner> createOwner(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        requireSuperAdmin(p);
        return ApiResponse.ok(adminService.createOwner(body, p.getUserId()), "Owner account created");
    }

    @PutMapping("/owners/{id}/suspend")
    @Operation(summary = "SuperAdmin: suspend an owner and their auth account")
    public ApiResponse<HostelOwner> suspendOwner(@PathVariable Long id) {
        UserPrincipal p = getPrincipal();
        requireSuperAdmin(p);
        return ApiResponse.ok(adminService.setOwnerStatus(id, "SUSPENDED", p.getUserId()), "Owner suspended");
    }

    @PutMapping("/owners/{id}/activate")
    @Operation(summary = "SuperAdmin: re-activate a suspended owner and their auth account")
    public ApiResponse<HostelOwner> activateOwner(@PathVariable Long id) {
        UserPrincipal p = getPrincipal();
        requireSuperAdmin(p);
        return ApiResponse.ok(adminService.setOwnerStatus(id, "ACTIVE", p.getUserId()), "Owner activated");
    }

    @PutMapping("/owners/{id}/reset-password")
    @Operation(summary = "SuperAdmin: reset owner's password")
    public ApiResponse<Void> resetOwnerPassword(@PathVariable Long id,
                                                  @RequestBody Map<String, String> body) {
        UserPrincipal p = getPrincipal();
        requireSuperAdmin(p);
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.isBlank()) {
            throw new AppException("newPassword is required", HttpStatus.BAD_REQUEST);
        }
        adminService.resetOwnerPassword(id, newPassword, p.getUserId());
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
    @Operation(summary = "SuperAdmin: revenue report for a given month/year (defaults to the current month)")
    public ApiResponse<Map<String, Object>> getRevenueReport(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        requireSuperAdmin();
        java.time.YearMonth now = java.time.YearMonth.now();
        return ApiResponse.ok(adminService.getRevenueReport(
                month != null ? month : now.getMonthValue(),
                year != null ? year : now.getYear()));
    }

    @GetMapping("/complaints")
    @Operation(summary = "SuperAdmin: platform-wide complaint monitor")
    public ApiResponse<List<Map<String, Object>>> getAllComplaints() {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.getAllComplaints());
    }

    @GetMapping("/reports/occupancy")
    @Operation(summary = "SuperAdmin: occupancy report across all hostels (totals + per-hostel rows)")
    public ApiResponse<Map<String, Object>> getOccupancyReport() {
        requireSuperAdmin();
        return ApiResponse.ok(adminService.getOccupancyReport());
    }

    // ── Own account ───────────────────────────────────────────────────────────

    @GetMapping("/account")
    @Operation(summary = "SuperAdmin: own account details")
    public ApiResponse<Map<String, Object>> getAccount() {
        UserPrincipal p = getPrincipal();
        requireSuperAdmin(p);
        return ApiResponse.ok(adminService.getAccount(p.getUserId()));
    }

    @PutMapping("/account/password")
    @Operation(summary = "SuperAdmin: change own password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body) {
        UserPrincipal p = getPrincipal();
        requireSuperAdmin(p);
        adminService.changeOwnPassword(p.getUserId(), body.get("currentPassword"), body.get("newPassword"));
        return ApiResponse.ok(null, "Password updated");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Long requireLong(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new AppException(key + " is required", HttpStatus.BAD_REQUEST);
        }
    }

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
