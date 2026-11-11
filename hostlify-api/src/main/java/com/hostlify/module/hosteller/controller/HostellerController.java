package com.hostlify.module.hosteller.controller;

import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.service.HostellerService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.UserPrincipal;
import com.hostlify.shared.storage.AzureBlobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/hostellers")
@Tag(name = "Hosteller Profiles")
public class HostellerController {

    private final HostellerService hostellerService;
    private final AzureBlobService blobService;

    public HostellerController(HostellerService hostellerService, AzureBlobService blobService) {
        this.hostellerService = hostellerService;
        this.blobService = blobService;
    }

    @GetMapping("/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: list all hostellers in a hostel")
    public ApiResponse<List<HostellerProfile>> getByHostel(@PathVariable Long hostelId) {
        com.hostlify.shared.security.HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(hostellerService.getByHostel(hostelId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','HOSTELLER')")
    @Operation(summary = "Get hosteller profile by ID")
    public ApiResponse<HostellerProfile> getById(@PathVariable Long id) {
        requireSelfIfHosteller(id);
        return ApiResponse.ok(hostellerService.getById(id));
    }

    @GetMapping("/auth/{authUserId}")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','HOSTELLER')")
    @Operation(summary = "Get hosteller profile by auth user ID")
    public ApiResponse<HostellerProfile> getByAuthUser(@PathVariable Long authUserId) {
        UserPrincipal p = getPrincipal();
        if (p.isHosteller() && !java.util.Objects.equals(p.getUserId(), authUserId)) {
            throw new com.hostlify.shared.exception.AppException("Access denied",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        HostellerProfile profile = hostellerService.getByAuthUser(authUserId);
        if (p.isStaff()) com.hostlify.shared.security.HostelAccess.requireHostellerRecord(profile.getId(), profile.getHostelId(), true);
        return ApiResponse.ok(profile);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','HOSTELLER')")
    @Operation(summary = "Update hosteller profile basic info")
    public ApiResponse<HostellerProfile> update(@PathVariable Long id, @RequestBody HostellerProfile profile) {
        requireSelfIfHosteller(id);
        return ApiResponse.ok(hostellerService.update(id, profile));
    }

    @GetMapping("/{id}/emergency-contact")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    @Operation(summary = "Get the hosteller's emergency contact")
    public ApiResponse<com.hostlify.module.hosteller.entity.HostellerEmergencyContact> getEmergency(@PathVariable Long id) {
        requireSelfIfHosteller(id);
        return ApiResponse.ok(hostellerService.getEmergencyContact(id));
    }

    @PutMapping("/{id}/emergency-contact")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    @Operation(summary = "Add or update the hosteller's emergency contact")
    public ApiResponse<com.hostlify.module.hosteller.entity.HostellerEmergencyContact> upsertEmergency(
            @PathVariable Long id, @RequestBody com.hostlify.module.hosteller.entity.HostellerEmergencyContact body) {
        requireSelfIfHosteller(id);
        return ApiResponse.ok(hostellerService.upsertEmergencyContact(id, body), "Emergency contact saved");
    }

    @PostMapping("/profile/photo")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','HOSTELLER')")
    @Operation(summary = "Hosteller: upload a profile photo")
    public ApiResponse<HostellerProfile> uploadPhoto(@RequestParam Long hostellerId,
                                                      @RequestParam("file") MultipartFile file) {
        requireSelfIfHosteller(hostellerId);
        validateUpload(file, java.util.Set.of("image/jpeg", "image/png", "image/webp"), "JPG, PNG or WebP image");
        String blobPath = blobService.uploadFile(file, "photos");
        return ApiResponse.ok(hostellerService.savePhotoUrl(hostellerId, blobPath), "Photo updated");
    }

    @PostMapping("/profile/id-proof")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','HOSTELLER')")
    @Operation(summary = "Hosteller: upload ID proof document")
    public ApiResponse<HostellerProfile> uploadIdProof(@RequestParam Long hostellerId,
                                                        @RequestParam("file") MultipartFile file) {
        requireSelfIfHosteller(hostellerId);
        validateUpload(file, java.util.Set.of("image/jpeg", "image/png", "image/webp", "application/pdf"), "JPG, PNG, WebP or PDF file");
        String blobPath = blobService.uploadFile(file, "id-proofs");
        return ApiResponse.ok(hostellerService.saveIdProofUrl(hostellerId, blobPath), "ID proof updated");
    }

    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    /** Rejects empty, oversized or wrong-type uploads before anything reaches storage. */
    private void validateUpload(MultipartFile file, java.util.Set<String> allowedTypes, String allowedLabel) {
        if (file == null || file.isEmpty()) {
            throw new com.hostlify.shared.exception.AppException("Please choose a file to upload", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new com.hostlify.shared.exception.AppException("File is too large. The maximum size is 5 MB.", org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String type = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        boolean extensionOk = name.matches(".*\\.(jpe?g|png|webp" + (allowedTypes.contains("application/pdf") ? "|pdf" : "") + ")$");
        // Both the declared type and the file extension must match (blocks renamed .exe files)
        if (!allowedTypes.contains(type) || !extensionOk) {
            throw new com.hostlify.shared.exception.AppException("Only " + allowedLabel + " files are allowed", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/profile/completion")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: get profile completion percentage")
    public ApiResponse<Map<String, Object>> getProfileCompletion() {
        UserPrincipal p = getPrincipal();
        return ApiResponse.ok(hostellerService.getProfileCompletion(p.getUserId()));
    }

    /** A hosteller may only read/modify their own profile (staff are unrestricted here). */
    private void requireSelfIfHosteller(Long hostellerProfileId) {
        UserPrincipal p = getPrincipal();
        if (p.isHosteller() && !java.util.Objects.equals(p.getHostellerProfileId(), hostellerProfileId)) {
            throw new com.hostlify.shared.exception.AppException("Access denied",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        // Staff: only residents of their own hostels (or not-yet-allocated applicants they may allocate)
        if (p.isStaff()) {
            HostellerProfile profile = hostellerService.getById(hostellerProfileId);
            com.hostlify.shared.security.HostelAccess.requireHostellerRecord(profile.getId(), profile.getHostelId(), true);
        }
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
