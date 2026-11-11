package com.hostlify.module.booking.controller;

import com.hostlify.module.booking.entity.BookingRequest;
import com.hostlify.module.booking.service.BookingRequestService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.UserPrincipal;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.module.booking.repository.BookingRequestRepository;
import com.hostlify.module.hostel.repository.BedRepository;
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
    private final BookingRequestRepository requestRepo;
    private final BedRepository bedRepo;

    public BookingRequestController(BookingRequestService requestService,
                                    BookingRequestRepository requestRepo,
                                    BedRepository bedRepo) {
        this.requestService = requestService;
        this.requestRepo = requestRepo;
        this.bedRepo = bedRepo;
    }

    /** Staff may only act on booking requests for their own hostel. */
    private void requireRequestAccess(Long requestId) {
        BookingRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new com.hostlify.shared.exception.AppException("Request not found",
                        org.springframework.http.HttpStatus.NOT_FOUND));
        HostelAccess.requireStaffHostel(req.getHostelId());
    }

    @PostMapping("/request")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: submit a bed booking request")
    public ApiResponse<BookingRequest> createRequest(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        if (!(body.get("bedId") instanceof Number) || !(body.get("hostelId") instanceof Number)) {
            throw new com.hostlify.shared.exception.AppException("hostelId and bedId are required",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        Long bedId = ((Number) body.get("bedId")).longValue();
        Long hostelId = ((Number) body.get("hostelId")).longValue();
        String dateStr = (String) body.get("checkInDate");
        LocalDate checkIn;
        try {
            checkIn = dateStr != null && !dateStr.isBlank() ? LocalDate.parse(dateStr) : LocalDate.now();
        } catch (java.time.format.DateTimeParseException e) {
            throw new com.hostlify.shared.exception.AppException("Invalid check-in date",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
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
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(requestService.getRequestsForHostel(hostelId, status));
    }

    @PostMapping("/request/{id}/approve")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: approve a booking request")
    public ApiResponse<BookingRequest> approveRequest(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        requireRequestAccess(id);
        BigDecimal rent = body.get("monthlyRent") != null ? new BigDecimal(body.get("monthlyRent").toString()) : null;
        BigDecimal advance = body.get("advanceAmount") != null ? new BigDecimal(body.get("advanceAmount").toString()) : BigDecimal.ZERO;
        if (rent != null && rent.signum() <= 0) throw new com.hostlify.shared.exception.AppException(
                "monthlyRent must be greater than 0", org.springframework.http.HttpStatus.BAD_REQUEST);
        if (advance.signum() < 0) throw new com.hostlify.shared.exception.AppException(
                "advanceAmount cannot be negative", org.springframework.http.HttpStatus.BAD_REQUEST);
        // Older clients sent "paymentMode"
        String mode = (String) body.getOrDefault("advancePaymentMode", body.getOrDefault("paymentMode", "CASH"));
        return ApiResponse.ok(requestService.approveRequest(id, p.getUserId(), rent, advance, mode),
                "Booking approved");
    }

    @PutMapping("/request/{id}/reject")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: reject a booking request")
    public ApiResponse<BookingRequest> rejectRequest(@PathVariable Long id,
                                                      @RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        requireRequestAccess(id);
        String reason = body != null && body.get("reason") != null ? body.get("reason").toString().trim() : "";
        if (reason.isEmpty()) {
            throw new com.hostlify.shared.exception.AppException("A reason is required to reject a booking request",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (reason.length() > 500) {
            throw new com.hostlify.shared.exception.AppException("Reason must be 500 characters or fewer",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(requestService.rejectRequest(id, p.getUserId(), reason), "Request rejected");
    }

    @PostMapping("/reassign")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: reassign booking request to a different bed")
    public ApiResponse<BookingRequest> reassign(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        Long requestId = ((Number) body.get("bookingRequestId")).longValue();
        Long newBedId = ((Number) body.get("newBedId")).longValue();
        BookingRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new com.hostlify.shared.exception.AppException("Request not found",
                        org.springframework.http.HttpStatus.NOT_FOUND));
        HostelAccess.requireStaffHostel(req.getHostelId());
        // The new bed must belong to the same hostel as the request
        Long newBedHostel = bedRepo.findById(newBedId).map(b -> b.getHostelId()).orElse(null);
        if (!req.getHostelId().equals(newBedHostel)) {
            throw new com.hostlify.shared.exception.AppException("Bed does not belong to this hostel",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
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
