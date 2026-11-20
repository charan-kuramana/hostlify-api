package com.hostlify.module.resident.housekeeping;

import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.HostelAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/housekeeping")
@Tag(name = "Housekeeping")
public class HousekeepingController {

    private final HousekeepingService service;
    private final PaymentService paymentService;

    public HousekeepingController(HousekeepingService service, PaymentService paymentService) {
        this.service = service;
        this.paymentService = paymentService;
    }

    @GetMapping("/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: rooms with last cleaned date, due today, and the last week's log")
    public ApiResponse<Map<String, Object>> board(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(service.board(hostelId));
    }

    @PostMapping(value = "/hostel/{hostelId}/log", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: mark a room or area cleaned (optional photo)")
    public ApiResponse<CleaningLog> log(@PathVariable Long hostelId,
                                        @RequestParam(value = "roomId", required = false) Long roomId,
                                        @RequestParam(value = "area", required = false) String area,
                                        @RequestParam(value = "note", required = false) String note,
                                        @RequestParam(value = "photo", required = false) MultipartFile photo) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(service.log(hostelId, roomId, area, photo, note, HostelAccess.principal().getUserId()), "Marked as cleaned");
    }

    @DeleteMapping("/log/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(service.get(id).getHostelId());
        service.delete(id);
        return ApiResponse.ok(null, "Removed");
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: my room's cleaning schedule and recent cleanings")
    public ApiResponse<Map<String, Object>> mine() {
        return ApiResponse.ok(service.mine(paymentService.profileIdOf(HostelAccess.principal().getUserId())));
    }
}
