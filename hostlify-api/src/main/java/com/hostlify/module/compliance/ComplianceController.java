package com.hostlify.module.compliance;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.compliance.aadhaar.AadhaarVerificationService;
import com.hostlify.module.compliance.agreement.AgreementService;
import com.hostlify.module.compliance.agreement.AgreementTemplate;
import com.hostlify.module.compliance.agreement.RentalAgreement;
import com.hostlify.module.compliance.checklist.ChecklistService;
import com.hostlify.module.compliance.checklist.RoomChecklist;
import com.hostlify.module.compliance.settlement.DepositSettlement;
import com.hostlify.module.compliance.settlement.SettlementService;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.shared.security.SimpleRateLimiter;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/** Rental agreements, move-in/out checklists and deposit settlements (staff + resident). */
@RestController
@RequestMapping("/api/v1/compliance")
@Tag(name = "Compliance")
public class ComplianceController {

    private static final String[] MANAGERS = {"OWNER", "ADMIN", "MANAGER"};

    private final AgreementService agreements;
    private final ChecklistService checklists;
    private final SettlementService settlements;
    private final AadhaarVerificationService aadhaar;
    private final CompliancePdfService pdf;
    private final HostellerProfileRepository profileRepo;
    private final BookingCheckinRepository bookingRepo;
    private final SimpleRateLimiter limiter;

    public ComplianceController(AgreementService agreements, ChecklistService checklists, SettlementService settlements,
                                AadhaarVerificationService aadhaar, CompliancePdfService pdf, HostellerProfileRepository profileRepo,
                                BookingCheckinRepository bookingRepo, SimpleRateLimiter limiter) {
        this.agreements = agreements;
        this.checklists = checklists;
        this.settlements = settlements;
        this.aadhaar = aadhaar;
        this.pdf = pdf;
        this.profileRepo = profileRepo;
        this.bookingRepo = bookingRepo;
        this.limiter = limiter;
    }

    // ═══ Staff: one resident's documents ═══════════════════════════════════

    @GetMapping("/resident/{hostellerId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: Aadhaar status, agreements, checklists and deposit settlement for a resident")
    public ApiResponse<Map<String, Object>> overview(@PathVariable Long hostellerId) {
        HostellerProfile p = staffResident(hostellerId);
        BookingCheckin b = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .orElseGet(() -> bookingRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId).stream().findFirst().orElse(null));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aadhaar", aadhaar.status(p.getId()));
        m.put("agreements", agreements.forResident(p.getId()));
        m.put("checklists", checklists.forResident(p.getId()));
        m.put("booking", b == null ? null : Map.of("id", b.getId(), "status", b.getStatus(),
                "advanceAmount", b.getAdvanceAmount(), "checkinDate", String.valueOf(b.getCheckinDate())));
        m.put("settlement", b == null ? null : settlements.forBooking(b.getId()).orElse(null));
        return ApiResponse.ok(m);
    }

    // ═══ Agreement template ════════════════════════════════════════════════

    @GetMapping("/agreement-template/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Map<String, Object>> getTemplate(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        AgreementTemplate t = agreements.template(hostelId);
        return ApiResponse.ok(Map.of("title", t.getTitle(), "body", t.getBody(), "noticeDays", t.getNoticeDays(),
                "placeholders", AgreementService.PLACEHOLDERS, "isDefault", t.getId() == null));
    }

    @PutMapping("/agreement-template/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<AgreementTemplate> saveTemplate(@PathVariable Long hostelId, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.requireSubRole(MANAGERS);
        Integer notice = body.get("noticeDays") == null ? null : ((Number) body.get("noticeDays")).intValue();
        return ApiResponse.ok(agreements.saveTemplate(hostelId, (String) body.get("title"), (String) body.get("body"), notice,
                HostelAccess.principal().getUserId()), "Template saved");
    }

    @PostMapping("/agreement-template/hostel/{hostelId}/reset")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<AgreementTemplate> resetTemplate(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.requireSubRole(MANAGERS);
        return ApiResponse.ok(agreements.resetTemplate(hostelId, HostelAccess.principal().getUserId()), "Template reset to default");
    }

    // ═══ Agreements ════════════════════════════════════════════════════════

    @GetMapping("/agreements/preview/{hostellerId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Map<String, String>> preview(@PathVariable Long hostellerId) {
        staffResident(hostellerId);
        return ApiResponse.ok(agreements.preview(hostellerId));
    }

    @PostMapping("/agreements")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: send the agreement (filled from the template) to a resident")
    public ApiResponse<RentalAgreement> createAgreement(@RequestBody Map<String, Object> body) {
        Long hostellerId = ((Number) body.get("hostellerId")).longValue();
        staffResident(hostellerId);
        HostelAccess.requireSubRole(MANAGERS);
        return ApiResponse.ok(agreements.create(hostellerId, HostelAccess.principal().getUserId()), "Agreement sent to the resident");
    }

    @PostMapping("/agreements/{id}/void")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<RentalAgreement> voidAgreement(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        HostelAccess.requireStaffHostel(agreements.get(id).getHostelId());
        HostelAccess.requireSubRole(MANAGERS);
        return ApiResponse.ok(agreements.voidAgreement(id, body == null ? null : (String) body.get("reason")), "Agreement voided");
    }

    @PostMapping("/agreements/{id}/otp")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Resident: get a code by email to accept the agreement (only when email is set up)")
    public ApiResponse<Map<String, Object>> agreementOtp(@PathVariable Long id) {
        UserPrincipal p = HostelAccess.principal();
        if (!limiter.allow("agreement-otp:" + p.getUserId(), 5, 3600)) {
            throw new AppException("Too many codes requested. Try again in an hour.", HttpStatus.TOO_MANY_REQUESTS);
        }
        return ApiResponse.ok(agreements.requestOtp(id, myProfileId()));
    }

    @PostMapping("/agreements/{id}/accept")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Resident: accept the agreement (typed name + consent [+ email code])")
    public ApiResponse<RentalAgreement> accept(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ApiResponse.ok(agreements.accept(id, myProfileId(), (String) body.get("name"), (String) body.get("otp"),
                Boolean.TRUE.equals(body.get("agree")), SimpleRateLimiter.clientIp(req), req.getHeader("User-Agent")), "Agreement accepted");
    }

    @GetMapping(value = "/agreements/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    public ResponseEntity<byte[]> agreementPdf(@PathVariable Long id) {
        RentalAgreement a = agreements.get(id);
        requireOwnerOrStaff(a.getHostellerId(), a.getHostelId());
        return pdfResponse(pdf.agreement(a), "Agreement-" + id + ".pdf");
    }

    // ═══ Checklists ════════════════════════════════════════════════════════

    @PostMapping("/checklists")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: open the move-in or move-out checklist for a resident")
    public ApiResponse<RoomChecklist> startChecklist(@RequestBody Map<String, Object> body) {
        Long hostellerId = ((Number) body.get("hostellerId")).longValue();
        staffResident(hostellerId);
        return ApiResponse.ok(checklists.start(hostellerId, (String) body.get("kind"), HostelAccess.principal().getUserId()));
    }

    @GetMapping("/checklists/{id}")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    public ApiResponse<RoomChecklist> getChecklist(@PathVariable Long id) {
        RoomChecklist c = checklists.getView(id);
        requireOwnerOrStaff(c.getHostellerId(), c.getHostelId());
        return ApiResponse.ok(c);
    }

    @PutMapping("/checklists/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @SuppressWarnings("unchecked")
    public ApiResponse<RoomChecklist> updateChecklist(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(checklists.get(id).getHostelId());
        Integer keys = body.get("keysHanded") == null ? null : ((Number) body.get("keysHanded")).intValue();
        return ApiResponse.ok(checklists.update(id, (List<Map<String, Object>>) body.get("items"), keys,
                (String) body.get("meterReading"), (String) body.get("staffNotes")), "Checklist saved");
    }

    @PostMapping("/checklists/{id}/photos")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Map<String, Object>> checklistPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                                           @RequestParam(value = "caption", required = false) String caption) {
        HostelAccess.requireStaffHostel(checklists.get(id).getHostelId());
        return ApiResponse.ok(checklists.addPhoto(id, file, caption), "Photo added");
    }

    @DeleteMapping("/checklists/photos/{photoId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Void> deleteChecklistPhoto(@PathVariable Long photoId) {
        HostelAccess.requireStaffHostel(checklists.get(checklists.getPhoto(photoId).getChecklistId()).getHostelId());
        checklists.deletePhoto(photoId);
        return ApiResponse.ok(null, "Photo deleted");
    }

    @PostMapping("/checklists/{id}/submit")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<RoomChecklist> submitChecklist(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(checklists.get(id).getHostelId());
        return ApiResponse.ok(checklists.submit(id), "Sent to the resident to confirm");
    }

    @PostMapping("/checklists/{id}/respond")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Resident: confirm the checklist, or raise a concern")
    public ApiResponse<RoomChecklist> respondChecklist(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ApiResponse.ok(checklists.respond(id, myProfileId(), (String) body.get("response"), (String) body.get("comment"),
                SimpleRateLimiter.clientIp(req)), "Thanks, your response was saved");
    }

    @GetMapping(value = "/checklists/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    public ResponseEntity<byte[]> checklistPdf(@PathVariable Long id) {
        RoomChecklist c = checklists.getView(id);
        requireOwnerOrStaff(c.getHostellerId(), c.getHostelId());
        return pdfResponse(pdf.checklist(c), ("MOVE_IN".equals(c.getKind()) ? "Move-in" : "Move-out") + "-checklist-" + id + ".pdf");
    }

    // ═══ Deposit settlement ════════════════════════════════════════════════

    @PostMapping("/settlements")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: open (or refresh) the deposit settlement for a booking")
    public ApiResponse<DepositSettlement> prepareSettlement(@RequestBody Map<String, Object> body) {
        Long bookingId = ((Number) body.get("bookingId")).longValue();
        HostelAccess.requireStaffHostel(settlements.booking(bookingId).getHostelId());
        HostelAccess.denySupervisor();
        return ApiResponse.ok(settlements.prepare(bookingId));
    }

    @PutMapping("/settlements/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @SuppressWarnings("unchecked")
    public ApiResponse<DepositSettlement> updateSettlement(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(settlements.get(id).getHostelId());
        HostelAccess.denySupervisor();
        return ApiResponse.ok(settlements.update(id, (List<Map<String, Object>>) body.get("deductions"), (String) body.get("notes")), "Saved");
    }

    @PostMapping("/settlements/{id}/finalize")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Owner/Admin/Manager: finalize: settles open dues from the deposit and notifies the resident")
    public ApiResponse<DepositSettlement> finalizeSettlement(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(settlements.get(id).getHostelId());
        HostelAccess.requireSubRole(MANAGERS);
        return ApiResponse.ok(settlements.finalizeSettlement(id, HostelAccess.principal().getUserId()), "Settlement finalized");
    }

    @PostMapping("/settlements/{id}/refunded")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<DepositSettlement> refunded(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(settlements.get(id).getHostelId());
        HostelAccess.requireSubRole(MANAGERS);
        return ApiResponse.ok(settlements.markRefunded(id, (String) body.get("method"), (String) body.get("reference")), "Refund recorded");
    }

    @GetMapping(value = "/settlements/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    public ResponseEntity<byte[]> settlementPdf(@PathVariable Long id) {
        DepositSettlement s = settlements.getView(id);
        requireOwnerOrStaff(s.getHostellerId(), s.getHostelId());
        if (HostelAccess.principal().isHosteller() && "DRAFT".equals(s.getStatus())) throw new AppException("Not ready yet", HttpStatus.NOT_FOUND);
        return pdfResponse(pdf.settlement(s), "Deposit-settlement-" + id + ".pdf");
    }

    // ═══ Resident: my documents ════════════════════════════════════════════

    @GetMapping("/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Resident: my agreements, checklists and deposit settlement")
    public ApiResponse<Map<String, Object>> mine() {
        Long id = myProfileId();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aadhaar", aadhaar.status(id));
        m.put("agreements", agreements.forResident(id).stream().filter(a -> !"VOID".equals(a.getStatus())).toList());
        m.put("checklists", checklists.forResident(id).stream().filter(c -> !"DRAFT".equals(c.getStatus())).toList());
        m.put("settlements", settlements.forResident(id));
        return ApiResponse.ok(m);
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private HostellerProfile staffResident(Long hostellerId) {
        HostellerProfile p = profileRepo.findById(hostellerId).orElseThrow(() -> new AppException("Resident not found", HttpStatus.NOT_FOUND));
        HostelAccess.requireStaffHostel(p.getHostelId());
        return p;
    }

    private Long myProfileId() {
        return profileRepo.findByAuthUserId(HostelAccess.principal().getUserId())
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND)).getId();
    }

    private void requireOwnerOrStaff(Long hostellerId, Long hostelId) {
        UserPrincipal p = HostelAccess.principal();
        if (p.isHosteller()) {
            if (!Objects.equals(hostellerId, myProfileId())) throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        } else {
            HostelAccess.requireStaffHostel(hostelId);
        }
    }

    private static ResponseEntity<byte[]> pdfResponse(byte[] bytes, String filename) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(bytes);
    }
}
