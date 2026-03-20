package com.hostlify.module.hostel.controller;

import com.hostlify.module.hostel.entity.*;
import com.hostlify.module.hostel.service.HostelDiscoveryService;
import com.hostlify.module.hostel.service.HostelService;
import com.hostlify.module.hostel.service.HostelSetupService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Hostel Setup & Discovery")
public class HostelSetupController {

    private final HostelSetupService setupService;
    private final HostelDiscoveryService discoveryService;
    private final HostelService hostelService;

    public HostelSetupController(HostelSetupService setupService,
                                  HostelDiscoveryService discoveryService,
                                  HostelService hostelService) {
        this.setupService = setupService;
        this.discoveryService = discoveryService;
        this.hostelService = hostelService;
    }

    // ── Amenities ─────────────────────────────────────────────────────────────

    @GetMapping("/hostels/{hostelId}/amenities")
    @Operation(summary = "Get amenities for a hostel")
    public ApiResponse<List<HostelAmenity>> getAmenities(@PathVariable Long hostelId) {
        return ApiResponse.ok(setupService.getAmenities(hostelId));
    }

    @PutMapping("/hostels/{hostelId}/amenities")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: bulk update hostel amenities")
    public ApiResponse<List<HostelAmenity>> updateAmenities(@PathVariable Long hostelId,
                                                              @RequestBody Map<String, List<Map<String, Object>>> body) {
        return ApiResponse.ok(setupService.updateAmenities(hostelId, body.get("amenities")), "Amenities updated");
    }

    // ── Pricing ───────────────────────────────────────────────────────────────

    @GetMapping("/hostels/{hostelId}/pricing")
    @Operation(summary = "Get pricing tiers for a hostel")
    public ApiResponse<List<HostelPricing>> getPricing(@PathVariable Long hostelId) {
        return ApiResponse.ok(setupService.getPricing(hostelId));
    }

    @PutMapping("/hostels/{hostelId}/pricing")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: upsert pricing tiers")
    public ApiResponse<List<HostelPricing>> upsertPricing(@PathVariable Long hostelId,
                                                            @RequestBody List<Map<String, Object>> tiers) {
        return ApiResponse.ok(setupService.upsertPricing(hostelId, tiers), "Pricing updated");
    }

    // ── Booking Settings ──────────────────────────────────────────────────────

    @GetMapping("/hostels/{hostelId}/booking-settings")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: get booking settings")
    public ApiResponse<HostelBookingSetting> getBookingSettings(@PathVariable Long hostelId) {
        return ApiResponse.ok(setupService.getBookingSettings(hostelId));
    }

    @PutMapping("/hostels/{hostelId}/booking-settings")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: update booking settings")
    public ApiResponse<HostelBookingSetting> updateBookingSettings(@PathVariable Long hostelId,
                                                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(setupService.updateBookingSettings(hostelId, body), "Settings updated");
    }

    // ── Floor/Room creation with auto-beds ────────────────────────────────────

    @PostMapping("/hostels/{hostelId}/floors/{floorId}/rooms-with-beds")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: create room and auto-generate beds")
    public ApiResponse<Room> createRoomWithBeds(@PathVariable Long hostelId,
                                                 @PathVariable Long floorId,
                                                 @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(setupService.createRoomWithBeds(floorId, body, hostelId), "Room created");
    }

    @PutMapping("/beds/{bedId}/status")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: force-update bed status")
    public ApiResponse<Bed> updateBedStatus(@PathVariable Long bedId,
                                             @RequestParam String bedStatus) {
        Bed bed = hostelService.getBed(bedId);
        bed.setStatus(bedStatus);
        return ApiResponse.ok(hostelService.createBed(bed), "Bed status updated");
    }

    // ── Availability (Hosteller) ───────────────────────────────────────────────

    @GetMapping("/hostels/{hostelId}/floors-with-availability")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    @Operation(summary = "Get floors with available bed counts")
    public ApiResponse<List<Map<String, Object>>> getFloorsWithAvailability(@PathVariable Long hostelId) {
        return ApiResponse.ok(setupService.getFloorsWithAvailability(hostelId));
    }

    @GetMapping("/floors/{floorId}/rooms-with-availability")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    @Operation(summary = "Get rooms on a floor with availability")
    public ApiResponse<List<Map<String, Object>>> getRoomsWithAvailability(@PathVariable Long floorId) {
        return ApiResponse.ok(setupService.getRoomsWithAvailability(floorId));
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    @GetMapping("/hostel/nearby")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: discover nearby hostels by GPS")
    public ApiResponse<List<Map<String, Object>>> getNearbyHostels(@RequestParam double lat,
                                                                     @RequestParam double lng,
                                                                     @RequestParam(defaultValue = "10") double radius) {
        return ApiResponse.ok(discoveryService.getNearbyHostels(lat, lng, radius));
    }

    @GetMapping("/hostel/search")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: search hostels by city/filters")
    public ApiResponse<List<Map<String, Object>>> searchHostels(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String locality,
            @RequestParam(required = false) String roomType,
            @RequestParam(required = false) BigDecimal maxRent) {
        return ApiResponse.ok(discoveryService.searchHostels(city, locality, roomType, maxRent));
    }

    @GetMapping("/hostels/{hostelId}/detail")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    @Operation(summary = "Get full hostel detail with amenities, pricing, floors")
    public ApiResponse<Map<String, Object>> getHostelDetail(@PathVariable Long hostelId) {
        return ApiResponse.ok(discoveryService.getHostelDetail(hostelId));
    }
}
