package com.hostlify.module.booking.controller;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.service.BookingService;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.CurrentUser;
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
@RequestMapping("/api/v1/booking")
@Tag(name = "Booking")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Staff: list pending allocations for a hostel */
    @GetMapping("/pending")
    @Operation(summary = "Staff: list pending hosteliers awaiting bed allocation")
    public ApiResponse<List<HostellerProfile>> getPending(@RequestParam(required = false) Long hostelId) {
        return ApiResponse.ok(bookingService.getPendingAllocations(hostelId));
    }

    /** Staff: allocate bed to a pending hosteller */
    @PostMapping("/allocate")
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
    @Operation(summary = "Hosteller: get my current active booking")
    public ApiResponse<BookingCheckin> myBooking() {
        UserPrincipal principal = getPrincipal();
        return ApiResponse.ok(bookingService.getMyBooking(principal.getUserId()));
    }

    /** Staff: get booking by id */
    @GetMapping("/{id}")
    @Operation(summary = "Staff: get booking by id")
    public ApiResponse<BookingCheckin> getById(@PathVariable Long id) {
        return ApiResponse.ok(bookingService.getById(id));
    }

    /** Staff: list all bookings for a hostel */
    @GetMapping("/hostel/{hostelId}")
    @Operation(summary = "Staff: list all bookings for a hostel")
    public ApiResponse<List<BookingCheckin>> getByHostel(@PathVariable Long hostelId) {
        return ApiResponse.ok(bookingService.getByHostel(hostelId));
    }

    /** Staff: checkout a hosteller */
    @PostMapping("/{id}/checkout")
    @Operation(summary = "Staff: checkout a hosteller")
    public ApiResponse<BookingCheckin> checkout(@PathVariable Long id,
                                                 @RequestBody Map<String, String> body) {
        return ApiResponse.ok(bookingService.checkout(id, body.get("reason")));
    }

    /** Staff: cancel a pending/active booking */
    @PostMapping("/{id}/cancel")
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

    private Long getLong(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null) throw new com.hostlify.shared.exception.AppException(key + " is required",
                org.springframework.http.HttpStatus.BAD_REQUEST);
        return ((Number) val).longValue();
    }
}
