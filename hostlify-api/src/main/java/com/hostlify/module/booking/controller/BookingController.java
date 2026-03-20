package com.hostlify.module.booking.controller;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.service.BookingService;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.CurrentUser;
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

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Staff: list pending allocations for a hostel */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: list pending hosteliers awaiting bed allocation")
    public ApiResponse<List<HostellerProfile>> getPending(@RequestParam(required = false) Long hostelId) {
        if (hostelId != null) requireHostelAccess(hostelId, getPrincipal());
        return ApiResponse.ok(bookingService.getPendingAllocations(hostelId));
    }

    /** Staff: allocate bed to a pending hosteller */
    @PostMapping("/allocate")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: allocate a bed to a PENDING hosteller (atomic, pessimistic lock)")
    public ApiResponse<BookingCheckin> allocate(@RequestBody Map<String, Object> body) {
        Long hostellerAuthUserId = getLong(body, "hostellerAuthUserId");
        Long bedId = getLong(body, "bedId");
        String dateStr = (String) body.get("checkInDate");
        LocalDate checkIn = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now();
        BigDecimal rent = body.get("monthlyRent") != null ? new BigDecimal(body.get("monthlyRent").toString()) : null;
        BigDecimal advance = body.get("advanceAmount") != null ? new BigDecimal(body.get("advanceAmount").toString()) : BigDecimal.ZERO;
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
        return ApiResponse.ok(bookingService.getById(id));
    }

    /** Staff: list all bookings for a hostel */
    @GetMapping("/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: list all bookings for a hostel")
    public ApiResponse<List<BookingCheckin>> getByHostel(@PathVariable Long hostelId) {
        requireHostelAccess(hostelId, getPrincipal());
        return ApiResponse.ok(bookingService.getByHostel(hostelId));
    }

    /** Staff: checkout a hosteller */
    @PostMapping("/{id}/checkout")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: checkout a hosteller")
    public ApiResponse<BookingCheckin> checkout(@PathVariable Long id,
                                                 @RequestBody Map<String, String> body) {
        return ApiResponse.ok(bookingService.checkout(id, body.get("reason")));
    }

    /** Staff: cancel a pending/active booking */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: cancel a booking")
    public ApiResponse<BookingCheckin> cancel(@PathVariable Long id) {
        return ApiResponse.ok(bookingService.cancelAllocation(id));
    }

    // Legacy endpoints
    @GetMapping("/hosteller/{hostellerId}")
    public ApiResponse<List<BookingCheckin>> getByHosteller(@PathVariable Long hostellerId) {
        return ApiResponse.ok(bookingService.getByHosteller(hostellerId));
    }

    @PostMapping("/checkin")
    public ApiResponse<BookingCheckin> checkin(@RequestBody BookingCheckin booking) {
        return ApiResponse.ok(bookingService.checkin(booking));
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
