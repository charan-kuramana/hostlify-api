package com.hostlify.module.platform;

import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
@Tag(name = "Plans & billing")
public class PlanController {

    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    // ═══ Owner ═════════════════════════════════════════════════════════════

    @GetMapping("/plan")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Owner/admin: current plan, bed usage and limits")
    public ApiResponse<Map<String, Object>> plan() {
        HostelAccess.requireSubRole("OWNER", "ADMIN");
        return ApiResponse.ok(plans.usage(ownerId()));
    }

    @GetMapping("/plan/payments")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<PlatformPayment>> myPayments() {
        HostelAccess.requireSubRole("OWNER", "ADMIN");
        return ApiResponse.ok(plans.payments(ownerId()));
    }

    @GetMapping("/plan/quote")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Map<String, Object>> quote(@RequestParam int beds, @RequestParam int months) {
        HostelAccess.requireSubRole("OWNER");
        return ApiResponse.ok(plans.quote(ownerId(), beds, months));
    }

    @PostMapping("/plan/checkout")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Owner: start a Razorpay payment for a per-bed plan")
    public ApiResponse<Map<String, Object>> checkout(@RequestBody Map<String, Object> body) {
        HostelAccess.requireSubRole("OWNER");
        return ApiResponse.ok(plans.checkout(ownerId(), num(body.get("beds")), num(body.get("months")), HostelAccess.principal().getUserId()));
    }

    @PostMapping("/plan/verify")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Owner: confirm the Razorpay payment and activate the plan")
    public ApiResponse<Map<String, Object>> verify(@RequestBody Map<String, Object> body) {
        HostelAccess.requireSubRole("OWNER");
        return ApiResponse.ok(plans.verify(ownerId(), str(body.get("razorpayOrderId")), str(body.get("razorpayPaymentId")),
                str(body.get("razorpaySignature"))), "Plan activated");
    }

    // ═══ Super admin ═══════════════════════════════════════════════════════

    @GetMapping("/settings")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<PlatformSettings> settings() {
        return ApiResponse.ok(plans.settings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Super admin: free bed limit, price per bed, grace days, premium badge")
    public ApiResponse<PlatformSettings> saveSettings(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(plans.saveSettings(body), "Pricing saved");
    }

    @GetMapping("/payments")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<PlatformPayment>> payments() {
        return ApiResponse.ok(plans.recentPaid());
    }

    @GetMapping("/owners/{ownerId}/plan")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> ownerPlan(@PathVariable Long ownerId) {
        return ApiResponse.ok(Map.of("usage", plans.usage(ownerId), "payments", plans.payments(ownerId)));
    }

    @PostMapping("/owners/{ownerId}/manual-payment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Super admin: record a bank transfer / UPI payment from an owner and activate their per-bed plan")
    public ApiResponse<PlatformPayment> manualPayment(@PathVariable Long ownerId, @RequestBody Map<String, Object> body) {
        BigDecimal amount = body.get("amount") == null ? null : new BigDecimal(String.valueOf(body.get("amount")));
        return ApiResponse.ok(plans.recordManual(ownerId, num(body.get("beds")), num(body.get("months")), amount,
                str(body.get("reference")), HostelAccess.principal().getUserId()), "Payment recorded and plan activated");
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private static Long ownerId() {
        UserPrincipal p = HostelAccess.principal();
        if (p.getOwnerId() == null) throw new AppException("No owner account linked to this login", HttpStatus.FORBIDDEN);
        return p.getOwnerId();
    }

    private static int num(Object v) {
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            throw new AppException("Beds and months must be numbers", HttpStatus.BAD_REQUEST);
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
