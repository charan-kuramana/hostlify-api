package com.hostlify.module.service.controller;

import com.hostlify.module.service.entity.ParkingRecord;
import com.hostlify.module.service.service.ParkingService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
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

    public ParkingController(ParkingService parkingService) {
        this.parkingService = parkingService;
    }

    @GetMapping("/hostel/{hostelId}")
    @Operation(summary = "Staff: list all active parking registrations for a hostel")
    public ApiResponse<List<ParkingRecord>> getActiveParking(@PathVariable Long hostelId) {
        return ApiResponse.ok(parkingService.getActiveParking(hostelId));
    }

    @GetMapping("/me")
    @Operation(summary = "Hosteller: get my active parking record")
    public ApiResponse<ParkingRecord> getMyParking() {
        UserPrincipal p = getPrincipal();
        return ApiResponse.ok(parkingService.getMyParking(p.getHostellerProfileId()));
    }

    @PostMapping
    @Operation(summary = "Hosteller: register a vehicle for parking")
    public ApiResponse<ParkingRecord> register(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        String vehicleNumber = (String) body.get("vehicleNumber");
        String vehicleType = (String) body.get("vehicleType");
        String slotNumber = (String) body.getOrDefault("slotNumber", null);

        if (vehicleNumber == null || vehicleType == null) {
            throw new AppException("vehicleNumber and vehicleType are required", HttpStatus.BAD_REQUEST);
        }

        ParkingRecord record = parkingService.registerParking(
                p.getHostellerProfileId(), p.getHostelId(), vehicleNumber, vehicleType, slotNumber);
        return ApiResponse.ok(record, "Parking registered successfully");
    }

    @DeleteMapping("/{recordId}")
    @Operation(summary = "Staff or Hosteller: deregister a parking record")
    public ApiResponse<ParkingRecord> deregister(@PathVariable Long recordId) {
        return ApiResponse.ok(parkingService.deregisterParking(recordId), "Parking record removed");
    }

    @PatchMapping("/{recordId}/slot")
    @Operation(summary = "Staff: update the slot number for a parking record")
    public ApiResponse<ParkingRecord> updateSlot(@PathVariable Long recordId,
                                                   @RequestParam String slotNumber) {
        return ApiResponse.ok(parkingService.updateSlot(recordId, slotNumber), "Slot updated");
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AppException("Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        return (UserPrincipal) auth.getPrincipal();
    }
}
