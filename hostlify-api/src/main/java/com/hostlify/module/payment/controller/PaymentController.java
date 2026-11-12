package com.hostlify.module.payment.controller;

import com.hostlify.module.notification.service.ScheduledNotificationService;
import com.hostlify.module.payment.entity.HostelPaymentSetting;
import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.entity.PaymentRazorpayOrder;
import com.hostlify.module.payment.entity.PaymentRecord;
import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.module.payment.service.PaymentSettingsService;
import com.hostlify.module.payment.service.ReceiptPdfService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.UserPrincipal;
import com.hostlify.shared.security.HostelAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/payment")
@Tag(name = "Payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentSettingsService settingsService;
    private final ReceiptPdfService receiptPdfService;
    private final ScheduledNotificationService reminderService;

    public PaymentController(PaymentService paymentService, PaymentSettingsService settingsService,
                             ReceiptPdfService receiptPdfService, ScheduledNotificationService reminderService) {
        this.paymentService = paymentService;
        this.settingsService = settingsService;
        this.receiptPdfService = receiptPdfService;
        this.reminderService = reminderService;
    }

    // ─── Hosteller: own dues ─────────────────────────────────────

    @GetMapping("/dues/current")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: get current month's due")
    public ApiResponse<PaymentDue> getCurrentDue() {
        UserPrincipal principal = getPrincipal();
        return ApiResponse.ok(paymentService.getCurrentDue(principal.getUserId()));
    }

    @GetMapping("/dues/history")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: get all dues history")
    public ApiResponse<List<PaymentDue>> getDueHistory() {
        UserPrincipal principal = getPrincipal();
        // Hosteller's profile id from principal or lookup
        return ApiResponse.ok(paymentService.getDuesByHosteller(
                principal.getHostellerProfileId() != null ? principal.getHostellerProfileId() : principal.getUserId()));
    }

    // ─── Razorpay ────────────────────────────────────────────────

    @PostMapping("/razorpay/initiate")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: initiate Razorpay order for a due (returns checkout info)")
    public ApiResponse<Map<String, Object>> initiateRazorpay(@RequestBody Map<String, Object> body) {
        UserPrincipal principal = getPrincipal();
        Long dueId = ((Number) body.get("dueId")).longValue();
        return ApiResponse.ok(paymentService.initiateRazorpayCheckout(principal.getUserId(), dueId),
                "Razorpay order created");
    }

    @PostMapping("/razorpay/verify")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: verify Razorpay checkout signature and capture payment")
    public ApiResponse<?> verifyRazorpay(@RequestBody Map<String, Object> body) {
        String paymentId = (String) body.get("razorpayPaymentId");
        String orderId = (String) body.get("razorpayOrderId");
        String signature = (String) body.get("razorpaySignature");
        Map<String, Object> details = paymentService.verifyAndCaptureRazorpay(paymentId, orderId, signature);
        return ApiResponse.ok(details, "Payment successful");
    }

    @GetMapping("/records/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: own payment transactions (incl. UPI payments awaiting confirmation)")
    public ApiResponse<List<Map<String, Object>>> getMyRecords() {
        UserPrincipal principal = getPrincipal();
        return ApiResponse.ok(paymentService.getRecordsByHostellerAuthUser(principal.getUserId()));
    }

    // ─── Payment settings (due day, reminders, late fee, UPI, landlord details) ───

    @GetMapping("/settings/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: payment settings of a hostel")
    public ApiResponse<HostelPaymentSetting> getSettings(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.denySupervisor();
        return ApiResponse.ok(settingsService.getOrDefault(hostelId));
    }

    @PutMapping("/settings/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Owner/Admin/Manager: update payment settings (late fee on/off, reminders, UPI, landlord)")
    public ApiResponse<HostelPaymentSetting> updateSettings(@PathVariable Long hostelId, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.requireSubRole("OWNER", "ADMIN", "MANAGER");
        return ApiResponse.ok(settingsService.update(hostelId, body), "Payment settings saved");
    }

    @GetMapping("/settings/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: how to pay (UPI) and the hostel's late fee policy")
    public ApiResponse<Map<String, Object>> mySettings() {
        return ApiResponse.ok(settingsService.residentView(paymentService.hostelOfResident(getPrincipal().getUserId())));
    }

    // ─── Charges other than rent ─────────────────────────────────────────

    @PostMapping("/dues/charge")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: add a charge (DEPOSIT, MESS, ELECTRICITY, FINE, OTHER) for one resident or all active residents")
    public ApiResponse<List<PaymentDue>> addCharge(@RequestBody Map<String, Object> body) {
        HostelAccess.denySupervisor();
        Long hostelId = body.get("hostelId") != null ? ((Number) body.get("hostelId")).longValue() : null;
        HostelAccess.requireStaffHostel(hostelId);
        Long hostellerId = body.get("hostellerId") != null ? ((Number) body.get("hostellerId")).longValue() : null;
        if (hostellerId == null && !Boolean.TRUE.equals(body.get("applyToAll"))) {
            throw new AppException("Choose a resident, or apply the charge to all residents", HttpStatus.BAD_REQUEST);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(body.get("amount")));
        } catch (Exception e) {
            throw new AppException("Amount must be a number", HttpStatus.BAD_REQUEST);
        }
        String dateStr = (String) body.get("dueDate");
        LocalDate dueDate = dateStr != null && !dateStr.isBlank() ? LocalDate.parse(dateStr) : null;
        List<PaymentDue> created = paymentService.createCharges(hostelId, hostellerId,
                (String) body.get("dueType"), amount, (String) body.get("description"), dueDate);
        return ApiResponse.ok(created, created.size() == 1 ? "Charge added" : created.size() + " charges added");
    }

    @DeleteMapping("/dues/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: waive (cancel) an unpaid due, e.g. a late fee or fine")
    public ApiResponse<Void> waiveDue(@PathVariable Long id) {
        HostelAccess.denySupervisor();
        HostelAccess.requireStaffHostel(paymentService.getDue(id).getHostelId());
        HostelAccess.requireSubRole("OWNER", "ADMIN", "MANAGER");
        paymentService.waiveDue(id);
        return ApiResponse.ok(null, "Due waived");
    }

    @PostMapping("/dues/{id}/remind")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: send an email + push reminder to the resident now")
    public ApiResponse<Map<String, Object>> remindNow(@PathVariable Long id) {
        HostelAccess.denySupervisor();
        HostelAccess.requireStaffHostel(paymentService.getDue(id).getHostelId());
        Map<String, Object> res = reminderService.sendReminderNow(id);
        return ApiResponse.ok(res, "Reminder sent");
    }

    @PostMapping("/admin/run-daily-job")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Super admin: run today's late-fee + reminder job now (normally 9 AM IST)")
    public ApiResponse<Map<String, Integer>> runDailyJob() {
        LocalDate today = com.hostlify.shared.util.DateUtils.todayKolkata();
        int fees = reminderService.applyLateFees(today);
        int reminders = reminderService.sendDueReminders(today);
        return ApiResponse.ok(Map.of("lateFees", fees, "reminders", reminders));
    }

    // ─── Direct UPI to the owner (no gateway fee) ────────────────────────

    @PostMapping("/upi/claim")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: report a UPI payment made to the hostel's UPI ID (staff will confirm)")
    public ApiResponse<PaymentRecord> upiClaim(@RequestBody Map<String, Object> body) {
        Long dueId = ((Number) body.get("dueId")).longValue();
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(body.get("amount")));
        } catch (Exception e) {
            throw new AppException("Amount must be a number", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(paymentService.submitUpiClaim(getPrincipal().getUserId(), dueId, amount, (String) body.get("utr")),
                "Submitted. The hostel will confirm your payment shortly.");
    }

    @GetMapping("/upi/pending/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: UPI payments waiting for confirmation")
    public ApiResponse<List<Map<String, Object>>> pendingUpi(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.denySupervisor();
        return ApiResponse.ok(paymentService.getPendingUpiClaims(hostelId));
    }

    @PostMapping("/upi/{recordId}/confirm")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: confirm a resident's UPI payment (money seen in the owner's UPI app)")
    public ApiResponse<PaymentRecord> confirmUpi(@PathVariable Long recordId) {
        HostelAccess.denySupervisor();
        HostelAccess.requireStaffHostel(paymentService.getRecord(recordId).getHostelId());
        return ApiResponse.ok(paymentService.verifyUpiClaim(getPrincipal().getUserId(), recordId, true, null), "Payment confirmed");
    }

    @PostMapping("/upi/{recordId}/reject")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: reject a resident's UPI payment (not received)")
    public ApiResponse<PaymentRecord> rejectUpi(@PathVariable Long recordId, @RequestBody(required = false) Map<String, Object> body) {
        HostelAccess.denySupervisor();
        HostelAccess.requireStaffHostel(paymentService.getRecord(recordId).getHostelId());
        String reason = body != null ? (String) body.get("reason") : null;
        return ApiResponse.ok(paymentService.verifyUpiClaim(getPrincipal().getUserId(), recordId, false, reason), "Payment rejected");
    }

    // ─── PDF receipts ────────────────────────────────────────────────────

    @GetMapping(value = "/records/{recordId}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    @Operation(summary = "Hosteller / Staff: PDF receipt for one payment")
    public ResponseEntity<byte[]> receiptPdf(@PathVariable Long recordId) {
        PaymentRecord rec = paymentService.getRecord(recordId);
        UserPrincipal p = getPrincipal();
        if (p.isHosteller()) {
            if (!Objects.equals(rec.getHostellerId(), paymentService.profileIdOf(p.getUserId()))) {
                throw new AppException("Access denied", HttpStatus.FORBIDDEN);
            }
        } else {
            HostelAccess.requireStaffHostel(rec.getHostelId());
        }
        return pdf(receiptPdfService.paymentReceipt(recordId), "Receipt-" + ReceiptPdfService.receiptNumber(rec) + ".pdf");
    }

    @GetMapping(value = "/receipts/hra", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    @Operation(summary = "HRA rent receipt for a financial year (fy=2026 → Apr 2026–Mar 2027). Staff pass hostellerId.")
    public ResponseEntity<byte[]> hraReceipt(@RequestParam int fy, @RequestParam(required = false) Long hostellerId) {
        if (fy < 2000 || fy > 2100) throw new AppException("Invalid financial year", HttpStatus.BAD_REQUEST);
        UserPrincipal p = getPrincipal();
        Long target;
        if (p.isHosteller()) {
            target = paymentService.profileIdOf(p.getUserId());
        } else {
            if (hostellerId == null) throw new AppException("hostellerId is required", HttpStatus.BAD_REQUEST);
            HostelAccess.requireStaffHostel(paymentService.hostelOfHosteller(hostellerId));
            target = hostellerId;
        }
        return pdf(receiptPdfService.hraStatement(target, fy), "HRA-Rent-Receipt-FY" + fy + "-" + String.valueOf(fy + 1).substring(2) + ".pdf");
    }

    private static ResponseEntity<byte[]> pdf(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(bytes);
    }

    // ─── Staff: cash payment ─────────────────────────────────────

    @PostMapping("/cash")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
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
        HostelAccess.denySupervisor();
        HostelAccess.requireStaffHostel(paymentService.getDue(dueId).getHostelId());
        return ApiResponse.ok(paymentService.recordCashPayment(
                principal.getUserId(), hostellerId, dueId, amount, method, txRef, date, notes));
    }

    // ─── Staff: generate dues ────────────────────────────────────

    @PostMapping("/dues/generate")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: generate monthly dues for all active bookings in a hostel")
    public ApiResponse<Map<String, Integer>> generateDues(@RequestParam Long hostelId,
                                                           @RequestParam int month,
                                                           @RequestParam int year) {
        HostelAccess.denySupervisor();
        HostelAccess.requireStaffHostel(hostelId);
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            throw new com.hostlify.shared.exception.AppException("Invalid month/year",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        int count = paymentService.generateMonthlyDues(hostelId, month, year);
        return ApiResponse.ok(Map.of("generatedCount", count),
                "Dues generated: " + count);
    }

    @GetMapping("/dues/hostel/{hostelId}/pending")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: list pending dues for a hostel")
    public ApiResponse<List<PaymentDue>> getPendingByHostel(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(paymentService.getPendingDuesByHostel(hostelId));
    }

    @GetMapping("/dues/hostel/{hostelId}/all")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: all dues (paid + unpaid) for a hostel")
    public ApiResponse<List<PaymentDue>> getAllDuesByHostel(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(paymentService.getAllDuesByHostel(hostelId));
    }

    @GetMapping("/records/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: all payments received for a hostel")
    public ApiResponse<List<Map<String, Object>>> getRecordsByHostel(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(paymentService.getRecordsByHostel(hostelId));
    }

    // ─── Due adjustment & receipt ────────────────────────────────

    @PutMapping("/dues/{id}/adjust")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: apply a discount or late fee to a payment due")
    public ApiResponse<?> adjustDue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        HostelAccess.denySupervisor();
        HostelAccess.requireStaffHostel(paymentService.getDue(id).getHostelId());
        BigDecimal amount = new BigDecimal(body.get("adjustmentAmount").toString());
        String type = (String) body.get("adjustmentType"); // DISCOUNT | LATE_FEE
        String notes = (String) body.getOrDefault("notes", null);
        return ApiResponse.ok(paymentService.adjustDue(id, amount, type, notes), "Due adjusted");
    }

    @GetMapping("/receipt/{dueId}")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    @Operation(summary = "Hosteller / Staff: get payment receipt for a due")
    public ApiResponse<?> getReceipt(@PathVariable Long dueId) {
        UserPrincipal p = getPrincipal();
        if (p.isHosteller() && !java.util.Objects.equals(paymentService.getDue(dueId).getHostellerId(), p.getHostellerProfileId())) {
            throw new com.hostlify.shared.exception.AppException("Access denied",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (p.isStaff()) HostelAccess.requireStaffHostel(paymentService.getDue(dueId).getHostelId());
        return ApiResponse.ok(paymentService.generateReceipt(dueId));
    }

    // ─── Legacy endpoints ────────────────────────────────────────
    // Staff only: these had no @PreAuthorize, so any hosteller could read others' dues
    // or even mark their own due as paid.

    @GetMapping("/dues/hosteller/{hostellerId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<PaymentDue>> getDuesByHosteller(@PathVariable Long hostellerId) {
        UserPrincipal p = getPrincipal();
        return ApiResponse.ok(paymentService.getDuesByHosteller(hostellerId).stream()
                .filter(d -> p.isSuperAdmin() || (p.getHostelIds() != null && p.getHostelIds().contains(d.getHostelId())))
                .toList());
    }

    @GetMapping("/dues/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<PaymentDue> getDue(@PathVariable Long id) {
        PaymentDue due = paymentService.getDue(id);
        HostelAccess.requireStaffHostel(due.getHostelId());
        return ApiResponse.ok(due);
    }

    @PostMapping("/dues")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<PaymentDue> createDue(@RequestBody PaymentDue due) {
        HostelAccess.denySupervisor();
        HostelAccess.requireStaffHostel(due.getHostelId());
        return ApiResponse.ok(paymentService.createDue(due));
    }

    @PutMapping("/dues/{id}/mark-paid")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<PaymentDue> markPaid(@PathVariable Long id) {
        HostelAccess.denySupervisor();
        HostelAccess.requireStaffHostel(paymentService.getDue(id).getHostelId());
        return ApiResponse.ok(paymentService.markPaid(id));
    }

    @GetMapping("/orders/hosteller/{hostellerId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
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
