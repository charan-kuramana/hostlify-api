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
        return ApiResponse.ok(hostellerService.getByHostel(hostelId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','HOSTELLER')")
    @Operation(summary = "Get hosteller profile by ID")
    public ApiResponse<HostellerProfile> getById(@PathVariable Long id) {
        return ApiResponse.ok(hostellerService.getById(id));
    }

    @GetMapping("/auth/{authUserId}")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','HOSTELLER')")
    @Operation(summary = "Get hosteller profile by auth user ID")
    public ApiResponse<HostellerProfile> getByAuthUser(@PathVariable Long authUserId) {
        return ApiResponse.ok(hostellerService.getByAuthUser(authUserId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','HOSTELLER')")
    @Operation(summary = "Update hosteller profile basic info")
    public ApiResponse<HostellerProfile> update(@PathVariable Long id, @RequestBody HostellerProfile profile) {
        return ApiResponse.ok(hostellerService.update(id, profile));
    }

    @PostMapping("/profile/photo")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','HOSTELLER')")
    @Operation(summary = "Hosteller: upload a profile photo")
    public ApiResponse<HostellerProfile> uploadPhoto(@RequestParam Long hostellerId,
                                                      @RequestParam("file") MultipartFile file) {
        String blobPath = blobService.uploadFile(file, "photos");
        return ApiResponse.ok(hostellerService.savePhotoUrl(hostellerId, blobPath), "Photo updated");
    }

    @PostMapping("/profile/id-proof")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','HOSTELLER')")
    @Operation(summary = "Hosteller: upload ID proof document")
    public ApiResponse<HostellerProfile> uploadIdProof(@RequestParam Long hostellerId,
                                                        @RequestParam("file") MultipartFile file) {
        String blobPath = blobService.uploadFile(file, "id-proofs");
        return ApiResponse.ok(hostellerService.saveIdProofUrl(hostellerId, blobPath), "ID proof updated");
    }

    @GetMapping("/profile/completion")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: get profile completion percentage")
    public ApiResponse<Map<String, Object>> getProfileCompletion() {
        UserPrincipal p = getPrincipal();
        return ApiResponse.ok(hostellerService.getProfileCompletion(p.getUserId()));
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
