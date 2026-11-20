package com.hostlify.module.resident.mess;

import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.shared.security.UserPrincipal;
import com.hostlify.shared.util.DateUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mess")
@Tag(name = "Mess")
public class MessController {

    private static final String[] MANAGERS = {"OWNER", "ADMIN", "MANAGER"};

    private final MessService mess;

    public MessController(MessService mess) {
        this.mess = mess;
    }

    // ═══ Resident ══════════════════════════════════════════════════════════

    @GetMapping("/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Resident: next 7 days of meals (skip/eat) and recent meals to rate")
    public ApiResponse<Map<String, Object>> myMeals() {
        return ApiResponse.ok(mess.myMeals(me()));
    }

    @PutMapping("/my/skip")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Resident: skip or eat a meal (before the cut-off)")
    public ApiResponse<Map<String, Object>> skip(@RequestBody Map<String, Object> body) {
        mess.setSkip(me(), date(body.get("date")), str(body.get("meal")), Boolean.TRUE.equals(body.get("skip")));
        return ApiResponse.ok(mess.myMeals(me()));
    }

    @PutMapping("/my/rating")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Resident: rate a meal served in the last 3 days")
    public ApiResponse<Void> rate(@RequestBody Map<String, Object> body) {
        if (!(body.get("rating") instanceof Number n)) throw new AppException("Rating is required", HttpStatus.BAD_REQUEST);
        mess.rate(me(), date(body.get("date")), str(body.get("meal")), n.intValue(), str(body.get("comment")));
        return ApiResponse.ok(null, "Thanks for rating");
    }

    @GetMapping("/suggestions/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<List<Map<String, Object>>> mySuggestions() {
        UserPrincipal p = HostelAccess.principal();
        if (p.getHostelId() == null) return ApiResponse.ok(List.of());
        return ApiResponse.ok(mess.suggestions(p.getHostelId(), p.getHostellerProfileId()));
    }

    @PostMapping("/suggestions")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Resident: suggest a dish")
    public ApiResponse<DishSuggestion> suggest(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(mess.suggest(me(), str(body.get("dish"))), "Suggestion added");
    }

    @PutMapping("/suggestions/{id}/vote")
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<Void> vote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        mess.vote(me(), id, !Boolean.FALSE.equals(body.get("up")));
        return ApiResponse.ok(null);
    }

    // ═══ Staff ═════════════════════════════════════════════════════════════

    @GetMapping("/hostel/{hostelId}/settings")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<MessSettings> settings(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(mess.settings(hostelId));
    }

    @PutMapping("/hostel/{hostelId}/settings")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Owner/admin/manager: meal times, cut-off and per-meal rates")
    public ApiResponse<MessSettings> saveSettings(@PathVariable Long hostelId, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.requireSubRole(MANAGERS);
        return ApiResponse.ok(mess.saveSettings(hostelId, body), "Mess settings saved");
    }

    @GetMapping("/hostel/{hostelId}/headcount")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: kitchen headcount per meal for a date (default today)")
    public ApiResponse<Map<String, Object>> headcount(@PathVariable Long hostelId, @RequestParam(required = false) String date) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(mess.headcount(hostelId, date == null ? DateUtils.todayKolkata() : date(date)));
    }

    @GetMapping("/hostel/{hostelId}/bill")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: preview the mess bill for a month")
    public ApiResponse<Map<String, Object>> billPreview(@PathVariable Long hostelId, @RequestParam int year, @RequestParam int month) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.denySupervisor();
        checkPeriod(year, month);
        return ApiResponse.ok(mess.billPreview(hostelId, year, month));
    }

    @PostMapping("/hostel/{hostelId}/bill")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: add the month's mess bill as a Mess charge for each resident")
    public ApiResponse<Map<String, Object>> runBill(@PathVariable Long hostelId, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.requireSubRole(MANAGERS);
        int year = ((Number) body.get("year")).intValue(), month = ((Number) body.get("month")).intValue();
        checkPeriod(year, month);
        return ApiResponse.ok(mess.runBill(hostelId, year, month, HostelAccess.principal().getUserId()), "Mess bill added to dues");
    }

    @GetMapping("/hostel/{hostelId}/bills")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<MessBill>> bills(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.denySupervisor();
        return ApiResponse.ok(mess.bills(hostelId));
    }

    @GetMapping("/hostel/{hostelId}/ratings")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Map<String, Object>> ratings(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(mess.ratingSummary(hostelId));
    }

    @GetMapping("/hostel/{hostelId}/suggestions")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<Map<String, Object>>> suggestions(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(mess.suggestions(hostelId, null));
    }

    @PutMapping("/suggestions/{id}/status")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: mark a suggested dish as added to the menu, or dismiss it")
    public ApiResponse<DishSuggestion> closeSuggestion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(mess.suggestion(id).getHostelId());
        return ApiResponse.ok(mess.closeSuggestion(id, str(body.get("status"))));
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private static Long me() {
        Long id = HostelAccess.principal().getHostellerProfileId();
        if (id == null) throw new AppException("No resident profile", HttpStatus.FORBIDDEN);
        return id;
    }

    private static LocalDate date(Object v) {
        try {
            return LocalDate.parse(String.valueOf(v));
        } catch (Exception e) {
            throw new AppException("Date must look like 2026-11-20", HttpStatus.BAD_REQUEST);
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static void checkPeriod(int year, int month) {
        if (month < 1 || month > 12 || year < 2020 || year > 2100) throw new AppException("Invalid month", HttpStatus.BAD_REQUEST);
    }
}
