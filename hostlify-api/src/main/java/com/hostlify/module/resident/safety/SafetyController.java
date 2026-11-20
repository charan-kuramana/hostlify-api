package com.hostlify.module.resident.safety;

import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.shared.security.SimpleRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/safety")
@Tag(name = "Safety & wellbeing")
public class SafetyController {

    private final SafetyService service;
    private final PaymentService paymentService;
    private final SimpleRateLimiter limiter;

    public SafetyController(SafetyService service, PaymentService paymentService, SimpleRateLimiter limiter) {
        this.service = service;
        this.paymentService = paymentService;
        this.limiter = limiter;
    }

    // ═══ Resident ══════════════════════════════════════════════════════════

    @GetMapping("/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: SOS status, unwell flag, late-entry requests, contacts")
    public ApiResponse<Map<String, Object>> mine() {
        return ApiResponse.ok(service.mine(me()));
    }

    @PostMapping("/sos")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: SOS — alerts hostel staff and the owner with your location")
    public ApiResponse<SosAlert> sos(@RequestBody(required = false) Map<String, Object> body) {
        if (!limiter.allow("sos:" + HostelAccess.principal().getUserId(), 10, 3600)) {
            throw new AppException("Too many SOS presses. If you're in danger, call 112.", HttpStatus.TOO_MANY_REQUESTS);
        }
        Map<String, Object> b = body == null ? Map.of() : body;
        return ApiResponse.ok(service.sos(me(), dec(b.get("latitude")), dec(b.get("longitude")),
                b.get("accuracy") instanceof Number n ? n.intValue() : null, (String) b.get("message")), "Alert sent");
    }

    @PostMapping("/sos/{id}/safe")
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<SosAlert> safe(@PathVariable Long id) {
        return ApiResponse.ok(service.cancelSos(me(), id), "Marked safe");
    }

    @PostMapping("/unwell")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: let the hostel know you're not feeling well")
    @SuppressWarnings("unchecked")
    public ApiResponse<WellnessFlag> unwell(@RequestBody Map<String, Object> body) {
        if (!limiter.allow("unwell:" + HostelAccess.principal().getUserId(), 10, 3600)) {
            throw new AppException("Please wait a little before updating again", HttpStatus.TOO_MANY_REQUESTS);
        }
        Object needs = body.get("needs");
        return ApiResponse.ok(service.flagUnwell(me(), (String) body.get("note"),
                needs instanceof List<?> l ? (List<String>) l : List.of()), "The hostel has been told");
    }

    @DeleteMapping("/unwell")
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<Void> wellAgain() {
        service.clearUnwell(me());
        return ApiResponse.ok(null, "Glad you're feeling better");
    }

    @PostMapping("/late-entry")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: ask to come in after curfew")
    public ApiResponse<LateEntryRequest> late(@RequestBody Map<String, Object> body) {
        LocalDate d;
        try {
            d = LocalDate.parse(String.valueOf(body.get("date")));
        } catch (Exception e) {
            throw new AppException("Date must be YYYY-MM-DD", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(service.requestLate(me(), d, String.valueOf(body.get("time")), (String) body.get("reason")), "Request sent");
    }

    @PostMapping("/late-entry/{id}/cancel")
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<LateEntryRequest> cancelLate(@PathVariable Long id) {
        return ApiResponse.ok(service.cancelLate(me(), id), "Cancelled");
    }

    // ═══ Staff (supervisors included: they're often the ones on duty) ══════

    @GetMapping("/hostel/{hostelId}/summary")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: live SOS alerts + counts (polled by the staff app)")
    public ApiResponse<Map<String, Object>> summary(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(service.summary(hostelId));
    }

    @GetMapping("/hostel/{hostelId}/sos")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<SosAlert>> sosHistory(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(service.sosForHostel(hostelId, false));
    }

    @PostMapping("/sos/{id}/acknowledge")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<SosAlert> ack(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(service.getSos(id).getHostelId());
        return ApiResponse.ok(service.acknowledge(id, HostelAccess.principal().getUserId()), "Resident told you're responding");
    }

    @PostMapping("/sos/{id}/resolve")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<SosAlert> resolve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(service.getSos(id).getHostelId());
        return ApiResponse.ok(service.resolve(id, HostelAccess.principal().getUserId(), (String) body.get("note")), "Alert closed");
    }

    @GetMapping("/hostel/{hostelId}/unwell")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<WellnessFlag>> unwellList(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(service.unwellForHostel(hostelId));
    }

    @PostMapping("/unwell/{id}/checked")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<WellnessFlag> checked(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        HostelAccess.requireStaffHostel(service.getFlag(id).getHostelId());
        return ApiResponse.ok(service.checkOn(id, HostelAccess.principal().getUserId(), body == null ? null : (String) body.get("note")), "Saved");
    }

    @GetMapping("/hostel/{hostelId}/late-entry")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<LateEntryRequest>> lateList(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(service.lateForHostel(hostelId));
    }

    @PostMapping("/late-entry/{id}/review")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<LateEntryRequest> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(service.getLate(id).getHostelId());
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        return ApiResponse.ok(service.reviewLate(id, approve, (String) body.get("note"), HostelAccess.principal().getUserId()),
                approve ? "Approved" : "Rejected");
    }

    @PostMapping("/late-entry/{id}/arrived")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<LateEntryRequest> arrived(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(service.getLate(id).getHostelId());
        return ApiResponse.ok(service.markArrived(id), "Marked as arrived");
    }

    private Long me() {
        return paymentService.profileIdOf(HostelAccess.principal().getUserId());
    }

    private static BigDecimal dec(Object o) {
        if (o == null) return null;
        try { return new BigDecimal(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }
}
