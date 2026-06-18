package com.hostlify.module.booking.controller;

import com.hostlify.module.booking.entity.BookingRequest;
import com.hostlify.module.booking.service.BookingRequestService;
import com.hostlify.shared.dto.ApiResponse;
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
@Tag(name = "Booking Requests")
public class BookingRequestController {

    private final BookingRequestService requestService;

    public BookingRequestController(BookingRequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping("/request")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: submit a bed booking request")
    public ApiResponse<BookingRequest> createRequest(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        Long bedId = ((Number) body.get("bedId")).longValue();
        Long hostelId = ((Number) body.get("hostelId")).longValue();
        String dateStr = (String) body.get("checkInDate");
        LocalDate checkIn = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now();
        String notes = (String) body.get("preferenceNotes");
        BookingRequest req = requestService.createRequest(
                p.getUserId(), p.getHostellerProfileId(), hostelId, bedId, checkIn, notes);
        return ApiResponse.ok(req, "Booking request submitted");
    }

    @GetMapping("/request/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: get my current booking request")
    public ApiResponse<BookingRequest> getMyRequest() {
        UserPrincipal p = getPrincipal();
        return ApiResponse.ok(requestService.getMyRequest(p.getUserId()));
    }

    @DeleteMapping("/request/{id}")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: cancel booking request")
    public ApiResponse<BookingRequest> cancelRequest(@PathVariable Long id) {
        UserPrincipal p = getPrincipal();
        return ApiResponse.ok(requestService.cancelRequest(id, p.getUserId()), "Request cancelled");
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: list all booking requests for a hostel")
    public ApiResponse<List<Map<String, Object>>> getRequests(@RequestParam Long hostelId,
                                                          @RequestParam(required = false) String status) {
        return ApiResponse.ok(requestService.getRequestsForHostel(hostelId, status));
    }

    @PostMapping("/request/{id}/approve")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: approve a booking request")
    public ApiResponse<BookingRequest> approveRequest(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        BigDecimal rent = body.get("monthlyRent") != null ? new BigDecimal(body.get("monthlyRent").toString()) : null;
        BigDecimal advance = body.get("advanceAmount") != null ? new BigDecimal(body.get("advanceAmount").toString()) : BigDecimal.ZERO;
        String mode = (String) body.getOrDefault("advancePaymentMode", "CASH");
        return ApiResponse.ok(requestService.approveRequest(id, p.getUserId(), rent, advance, mode),
                "Booking approved");
    }

    @PutMapping("/request/{id}/reject")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: reject a booking request")
    public ApiResponse<BookingRequest> rejectRequest(@PathVariable Long id,
                                                      @RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        String reason = (String) body.get("reason");
        return ApiResponse.ok(requestService.rejectRequest(id, p.getUserId(), reason), "Request rejected");
    }

    @PostMapping("/reassign")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: reassign booking request to a different bed")
    public ApiResponse<BookingRequest> reassign(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        Long requestId = ((Number) body.get("bookingRequestId")).longValue();
        Long newBedId = ((Number) body.get("newBedId")).longValue();
        return ApiResponse.ok(requestService.reassignBed(requestId, p.getUserId(), newBedId),
                "Reassigned successfully");
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
