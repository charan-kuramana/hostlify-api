package com.hostlify.module.service.controller;

import com.hostlify.module.service.entity.ParkingRecord;
import com.hostlify.module.service.service.ParkingService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/parking")
@Tag(name = "Parking")
public class ParkingController {

    private final ParkingService parkingService;
    private final com.hostlify.module.service.repository.ParkingRecordRepository parkingRepo;

    public ParkingController(ParkingService parkingService,
                             com.hostlify.module.service.repository.ParkingRecordRepository parkingRepo) {
        this.parkingService = parkingService;
        this.parkingRepo = parkingRepo;
    }

    private ParkingRecord loadRecord(Long recordId) {
        return parkingRepo.findById(recordId)
                .orElseThrow(() -> new AppException("Parking record not found", HttpStatus.NOT_FOUND));
    }

    @GetMapping("/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: list all active parking registrations for a hostel")
    public ApiResponse<List<ParkingRecord>> getActiveParking(@PathVariable Long hostelId) {
        com.hostlify.shared.security.HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(parkingService.getActiveParking(hostelId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: get my parking records")
    public ApiResponse<List<ParkingRecord>> getMyParking() {
        UserPrincipal p = getPrincipal();
        return ApiResponse.ok(parkingService.getMyParking(p.getHostellerProfileId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: register a vehicle for parking")
    public ApiResponse<ParkingRecord> register(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        String vehicleNumber = (String) body.get("vehicleNumber");
        String vehicleType = (String) body.get("vehicleType");
        String slotNumber = null; // slots are assigned by staff, not chosen by the hosteller

        if (vehicleNumber == null || vehicleType == null) {
            throw new AppException("vehicleNumber and vehicleType are required", HttpStatus.BAD_REQUEST);
        }

        ParkingRecord record = parkingService.registerParking(
                p.getHostellerProfileId(), p.getHostelId(), vehicleNumber, vehicleType, slotNumber);
        return ApiResponse.ok(record, "Parking registered successfully");
    }

    @DeleteMapping("/{recordId}")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','HOSTELLER')")
    @Operation(summary = "Staff or Hosteller: deregister a parking record")
    public ApiResponse<ParkingRecord> deregister(@PathVariable Long recordId) {
        UserPrincipal p = getPrincipal();
        // hostellers may only remove their own vehicle
        Long owner = p.isHosteller() ? (p.getHostellerProfileId() != null ? p.getHostellerProfileId() : -1L) : null;
        if (p.isStaff()) com.hostlify.shared.security.HostelAccess.requireStaffHostel(loadRecord(recordId).getHostelId());
        return ApiResponse.ok(parkingService.deregisterParking(recordId, owner), "Parking record removed");
    }

    @PatchMapping("/{recordId}/slot")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: update the slot number for a parking record")
    public ApiResponse<ParkingRecord> updateSlot(@PathVariable Long recordId,
                                                   @RequestParam String slotNumber) {
        ParkingRecord record = loadRecord(recordId);
        com.hostlify.shared.security.HostelAccess.requireStaffHostel(record.getHostelId());
        String slot = slotNumber == null ? "" : slotNumber.trim().toUpperCase();
        if (slot.isEmpty() || slot.length() > 20) {
            throw new AppException("Enter a valid slot number", HttpStatus.BAD_REQUEST);
        }
        // One vehicle per slot within a hostel
        boolean taken = parkingRepo.findByHostelIdAndStatus(record.getHostelId(), "ACTIVE").stream()
                .anyMatch(r -> !r.getId().equals(recordId) && slot.equalsIgnoreCase(r.getSlotNumber()));
        if (taken) throw new AppException("Slot " + slot + " is already assigned to another vehicle", HttpStatus.CONFLICT);
        return ApiResponse.ok(parkingService.updateSlot(recordId, slot), "Slot updated");
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AppException("Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        return (UserPrincipal) auth.getPrincipal();
    }
}
