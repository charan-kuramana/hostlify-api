package com.hostlify.module.booking.controller;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.service.BookingService;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.CurrentUser;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.module.hostel.service.HostelService;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/booking")
@Tag(name = "Booking")
public class BookingController {

    private final BookingService bookingService;
    private final HostelService hostelService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hostlify.module.booking.service.ApplicantAccess applicants;
    @org.springframework.beans.factory.annotation.Autowired
    private com.hostlify.module.hosteller.repository.HostellerProfileRepository profileRepo;

    public BookingController(BookingService bookingService, HostelService hostelService) {
        this.bookingService = bookingService;
        this.hostelService = hostelService;
    }

    /** Staff: list pending allocations for a hostel */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: list pending hosteliers awaiting bed allocation")
    public ApiResponse<List<HostellerProfile>> getPending(@RequestParam(required = false) Long hostelId,
                                                         @RequestParam(required = false) String find) {
        // Only applicants who asked this hostel for a bed, plus an exact mobile/email lookup for walk-ins
        if (hostelId == null) hostelId = HostelAccess.defaultStaffHostel();
        if (hostelId == null) throw new com.hostlify.shared.exception.AppException("hostelId is required", org.springframework.http.HttpStatus.BAD_REQUEST);
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(applicants.pendingFor(hostelId, find));
    }

    /** Staff: allocate bed to a pending hosteller */
    @PostMapping("/allocate")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: allocate a bed to a PENDING hosteller (atomic, pessimistic lock)")
    public ApiResponse<BookingCheckin> allocate(@RequestBody Map<String, Object> body) {
        Long hostellerAuthUserId = getLong(body, "hostellerAuthUserId");
        Long bedId = getLong(body, "bedId");
        // Staff may only allocate beds in their own hostel
        Long bedHostel = hostelService.getBed(bedId).getHostelId();
        HostelAccess.requireStaffHostel(bedHostel);
        // The applicant must have asked this hostel for a bed, or the desk must know their mobile/email (walk-in)
        HostellerProfile applicant = profileRepo.findByAuthUserId(hostellerAuthUserId)
                .orElseThrow(() -> new com.hostlify.shared.exception.AppException("Applicant not found", org.springframework.http.HttpStatus.NOT_FOUND));
        boolean mine = ("PENDING".equals(applicant.getStatus()) && bedHostel.equals(applicant.getHostelId()))
                || applicants.linkedToMyHostels(hostellerAuthUserId)
                || com.hostlify.module.booking.service.ApplicantAccess.contactMatches(applicant, (String) body.get("applicantContact"));
        if (!mine && !getPrincipal().isSuperAdmin()) {
            throw new com.hostlify.shared.exception.AppException("This applicant has not applied to your hostel. Search them by mobile number or email first.", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        String dateStr = (String) body.get("checkInDate");
        LocalDate checkIn = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now();
        BigDecimal rent = body.get("monthlyRent") != null ? new BigDecimal(body.get("monthlyRent").toString()) : null;
        BigDecimal advance = body.get("advanceAmount") != null ? new BigDecimal(body.get("advanceAmount").toString()) : BigDecimal.ZERO;
        if (rent != null && rent.signum() <= 0) throw new com.hostlify.shared.exception.AppException(
                "monthlyRent must be greater than 0", org.springframework.http.HttpStatus.BAD_REQUEST);
        if (advance.signum() < 0) throw new com.hostlify.shared.exception.AppException(
                "advanceAmount cannot be negative", org.springframework.http.HttpStatus.BAD_REQUEST);
        String paymentMode = (String) body.getOrDefault("advancePaymentMode", "CASH");
        return ApiResponse.ok(bookingService.allocateBed(hostellerAuthUserId, bedId, checkIn, rent, advance, paymentMode),
                "Bed allocated successfully");
    }

    /** Hosteller: get my active booking */
    @GetMapping("/me")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: get my current active booking")
    public ApiResponse<BookingCheckin> myBooking() {
        UserPrincipal principal = getPrincipal();
        return ApiResponse.ok(bookingService.getMyBooking(principal.getUserId()));
    }

    /** Staff: get booking by id */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: get booking by id")
    public ApiResponse<BookingCheckin> getById(@PathVariable Long id) {
        BookingCheckin booking = bookingService.getById(id);
        HostelAccess.requireStaffHostel(booking.getHostelId());
        return ApiResponse.ok(booking);
    }

    /** Staff: list all bookings for a hostel */
    @GetMapping("/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: list all bookings for a hostel")
    public ApiResponse<List<BookingCheckin>> getByHostel(@PathVariable Long hostelId) {
        requireHostelAccess(hostelId, getPrincipal());
        return ApiResponse.ok(bookingService.getByHostel(hostelId));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.hostlify.module.booking.service.ShortStayJobs shortStayJobs;

    /** Staff: extend a short stay (extra days are billed) */
    @PostMapping("/{id}/extend")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: extend a short stay to a later check-out date")
    public ApiResponse<BookingCheckin> extend(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(bookingService.getById(id).getHostelId());
        LocalDate end;
        try { end = LocalDate.parse(String.valueOf(body.get("checkOutDate"))); }
        catch (Exception e) { throw new com.hostlify.shared.exception.AppException("checkOutDate must be YYYY-MM-DD", org.springframework.http.HttpStatus.BAD_REQUEST); }
        return ApiResponse.ok(shortStayJobs.extend(id, end), "Stay extended");
    }

    /** Staff: checkout a hosteller */
    @PostMapping("/{id}/checkout")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: checkout a hosteller")
    public ApiResponse<BookingCheckin> checkout(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, String> body) {
        HostelAccess.requireStaffHostel(bookingService.getById(id).getHostelId());
        return ApiResponse.ok(bookingService.checkout(id, body != null ? body.get("reason") : null));
    }

    /** Staff: cancel a pending/active booking */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: cancel a booking")
    public ApiResponse<BookingCheckin> cancel(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(bookingService.getById(id).getHostelId());
        return ApiResponse.ok(bookingService.cancelAllocation(id));
    }

    // Legacy endpoints — staff only (previously open to any authenticated user, incl. hostellers)
    @GetMapping("/hosteller/{hostellerId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<BookingCheckin>> getByHosteller(@PathVariable Long hostellerId) {
        UserPrincipal p = getPrincipal();
        // Staff: only bookings in their own hostels
        return ApiResponse.ok(bookingService.getByHosteller(hostellerId).stream()
                .filter(b -> p.isSuperAdmin() || (p.getHostelIds() != null && p.getHostelIds().contains(b.getHostelId())))
                .toList());
    }

    @PostMapping("/checkin")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<BookingCheckin> checkin(@RequestBody Map<String, Object> body) {
        // Bound straight to the entity it could overwrite another hostel's booking; POST /booking/allocate replaces it
        throw new com.hostlify.shared.exception.AppException("Use POST /booking/allocate to check a resident in", org.springframework.http.HttpStatus.GONE);
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new com.hostlify.shared.exception.AppException("Not authenticated",
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return (UserPrincipal) auth.getPrincipal();
    }

    private void requireHostelAccess(Long hostelId, UserPrincipal principal) {
        // SUPER_ADMIN can access any hostel; staff must have hostelId in their JWT hostelIds
        if (principal.isSuperAdmin()) return;
        List<Long> hostelIds = principal.getHostelIds();
        if (hostelIds == null || !hostelIds.contains(hostelId)) {
            throw new com.hostlify.shared.exception.AppException("Access denied to hostel " + hostelId,
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
    }

    private Long getLong(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null) throw new com.hostlify.shared.exception.AppException(key + " is required",
                org.springframework.http.HttpStatus.BAD_REQUEST);
        return ((Number) val).longValue();
    }
}
