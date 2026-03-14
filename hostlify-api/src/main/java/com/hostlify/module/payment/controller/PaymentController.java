package com.hostlify.module.payment.controller;

import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.entity.PaymentRazorpayOrder;
import com.hostlify.module.payment.entity.PaymentRecord;
import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
@Tag(name = "Payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // ─── Hosteller: own dues ─────────────────────────────────────

    @GetMapping("/dues/current")
    @Operation(summary = "Hosteller: get current month's due")
    public ApiResponse<PaymentDue> getCurrentDue() {
        UserPrincipal principal = getPrincipal();
        return ApiResponse.ok(paymentService.getCurrentDue(principal.getUserId()));
    }

    @GetMapping("/dues/history")
    @Operation(summary = "Hosteller: get all dues history")
    public ApiResponse<List<PaymentDue>> getDueHistory() {
        UserPrincipal principal = getPrincipal();
        // Hosteller's profile id from principal or lookup
        return ApiResponse.ok(paymentService.getDuesByHosteller(
                principal.getHostellerProfileId() != null ? principal.getHostellerProfileId() : principal.getUserId()));
    }

    // ─── Razorpay ────────────────────────────────────────────────

    @PostMapping("/razorpay/initiate")
    @Operation(summary = "Hosteller: initiate Razorpay order for a due")
    public ApiResponse<PaymentRazorpayOrder> initiateRazorpay(@RequestBody Map<String, Object> body) {
        UserPrincipal principal = getPrincipal();
        Long dueId = ((Number) body.get("dueId")).longValue();
        return ApiResponse.ok(paymentService.initiateRazorpayPayment(principal.getUserId(), dueId),
                "Razorpay order created");
    }

    // ─── Staff: cash payment ─────────────────────────────────────

    @PostMapping("/cash")
    @Operation(summary = "Staff: record a cash/manual payment")
    public ApiResponse<PaymentRecord> recordCash(@RequestBody Map<String, Object> body) {
        UserPrincipal principal = getPrincipal();
        Long hostellerId = ((Number) body.get("hostellerId")).longValue();
        Long dueId = ((Number) body.get("dueId")).longValue();
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String method = (String) body.getOrDefault("paymentMethod", "CASH");
        String txRef = (String) body.get("transactionRef");
        String dateStr = (String) body.get("paymentDate");
        LocalDate date = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now();
        String notes = (String) body.get("notes");
        return ApiResponse.ok(paymentService.recordCashPayment(
                principal.getUserId(), hostellerId, dueId, amount, method, txRef, date, notes));
    }

    // ─── Staff: generate dues ────────────────────────────────────

    @PostMapping("/dues/generate")
    @Operation(summary = "Staff: generate monthly dues for all active bookings in a hostel")
    public ApiResponse<Map<String, Integer>> generateDues(@RequestParam Long hostelId,
                                                           @RequestParam int month,
                                                           @RequestParam int year) {
        int count = paymentService.generateMonthlyDues(hostelId, month, year);
        return ApiResponse.ok(Map.of("generatedCount", count),
                "Dues generated: " + count);
    }

    @GetMapping("/dues/hostel/{hostelId}/pending")
    @Operation(summary = "Staff: list pending dues for a hostel")
    public ApiResponse<List<PaymentDue>> getPendingByHostel(@PathVariable Long hostelId) {
        return ApiResponse.ok(paymentService.getPendingDuesByHostel(hostelId));
    }

    // ─── Due adjustment & receipt ────────────────────────────────

    @PutMapping("/dues/{id}/adjust")
    @Operation(summary = "Staff: apply a discount or late fee to a payment due")
    public ApiResponse<?> adjustDue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("adjustmentAmount").toString());
        String type = (String) body.get("adjustmentType"); // DISCOUNT | LATE_FEE
        String notes = (String) body.getOrDefault("notes", null);
        return ApiResponse.ok(paymentService.adjustDue(id, amount, type, notes), "Due adjusted");
    }

    @GetMapping("/receipt/{dueId}")
    @Operation(summary = "Hosteller / Staff: get payment receipt for a due")
    public ApiResponse<?> getReceipt(@PathVariable Long dueId) {
        return ApiResponse.ok(paymentService.generateReceipt(dueId));
    }

    // ─── Legacy endpoints ────────────────────────────────────────

    @GetMapping("/dues/hosteller/{hostellerId}")
    public ApiResponse<List<PaymentDue>> getDuesByHosteller(@PathVariable Long hostellerId) {
        return ApiResponse.ok(paymentService.getDuesByHosteller(hostellerId));
    }

    @GetMapping("/dues/{id}")
    public ApiResponse<PaymentDue> getDue(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.getDue(id));
    }

    @PostMapping("/dues")
    public ApiResponse<PaymentDue> createDue(@RequestBody PaymentDue due) {
        return ApiResponse.ok(paymentService.createDue(due));
    }

    @PutMapping("/dues/{id}/mark-paid")
    public ApiResponse<PaymentDue> markPaid(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.markPaid(id));
    }

    @GetMapping("/orders/hosteller/{hostellerId}")
    public ApiResponse<List<PaymentRazorpayOrder>> getOrders(@PathVariable Long hostellerId) {
        return ApiResponse.ok(paymentService.getOrdersByHosteller(hostellerId));
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new com.hostlify.shared.exception.AppException("Not authenticated",
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return (UserPrincipal) auth.getPrincipal();
    }
}
