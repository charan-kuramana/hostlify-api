package com.hostlify.module.resident.checkout;

import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.HostelAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/checkout-requests")
@Tag(name = "Check-out requests")
public class CheckoutController {

    private final CheckoutService service;
    private final PaymentService paymentService;

    public CheckoutController(CheckoutService service, PaymentService paymentService) {
        this.service = service;
        this.paymentService = paymentService;
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: my stay and check-out requests")
    public ApiResponse<Map<String, Object>> mine() {
        return ApiResponse.ok(service.mine(me()));
    }

    @PostMapping("/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: ask to check out on a date")
    public ApiResponse<CheckoutRequest> request(@RequestBody Map<String, Object> body) {
        LocalDate date;
        try {
            date = body.get("date") == null || String.valueOf(body.get("date")).isBlank() ? null : LocalDate.parse(String.valueOf(body.get("date")));
        } catch (java.time.format.DateTimeParseException e) {
            throw new AppException("Date must be YYYY-MM-DD", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(service.request(me(), date, (String) body.get("reason")), "Check-out requested");
    }

    @GetMapping("/my/estimate")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: notice period / lock-in / refund estimate for a last day")
    public ApiResponse<Map<String, Object>> estimate(@RequestParam(required = false) String date) {
        LocalDate d;
        try {
            d = date == null || date.isBlank() ? null : LocalDate.parse(date);
        } catch (java.time.format.DateTimeParseException e) {
            throw new AppException("Date must be YYYY-MM-DD", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(service.estimate(me(), d));
    }

    @PostMapping("/my/{id}/cancel")
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<CheckoutRequest> cancel(@PathVariable Long id) {
        return ApiResponse.ok(service.cancel(me(), id), "Request cancelled");
    }

    @GetMapping("/items")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: default items to inspect")
    public ApiResponse<List<String>> items() {
        return ApiResponse.ok(CheckoutService.DEFAULT_ITEMS);
    }

    @GetMapping("/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: check-out requests for a hostel")
    public ApiResponse<List<CheckoutRequest>> forHostel(@PathVariable Long hostelId, @RequestParam(required = false) String status) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(service.forHostel(hostelId, status));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: save the room inspection and check the resident out")
    @SuppressWarnings("unchecked")
    public ApiResponse<CheckoutRequest> approve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(service.get(id).getHostelId());
        Object items = body.get("items");
        return ApiResponse.ok(service.approve(id, HostelAccess.principal().getUserId(),
                items instanceof List<?> l ? (List<Map<String, Object>>) l : List.of(), (String) body.get("note")), "Checked out");
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: send the request back with what is pending")
    public ApiResponse<CheckoutRequest> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(service.get(id).getHostelId());
        return ApiResponse.ok(service.reject(id, HostelAccess.principal().getUserId(), (String) body.get("note")), "Sent back to the resident");
    }

    private Long me() {
        return paymentService.profileIdOf(HostelAccess.principal().getUserId());
    }
}
