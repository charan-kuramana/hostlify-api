package com.hostlify.module.compliance.aadhaar;

import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.shared.security.SimpleRateLimiter;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/compliance/aadhaar")
@Tag(name = "Aadhaar verification")
public class AadhaarController {

    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final AadhaarVerificationService service;
    private final HostellerProfileRepository profileRepo;
    private final SimpleRateLimiter limiter;

    public AadhaarController(AadhaarVerificationService service, HostellerProfileRepository profileRepo, SimpleRateLimiter limiter) {
        this.service = service;
        this.profileRepo = profileRepo;
        this.limiter = limiter;
    }

    @GetMapping("/setup")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','SUPER_ADMIN','HOSTELLER')")
    @Operation(summary = "Is Aadhaar QR verification ready (UIDAI certificate loaded)?")
    public ApiResponse<Map<String, Object>> setup() {
        Map<String, Object> s = service.setupStatus();
        if (HostelAccess.principal().isHosteller()) s.remove("certificates");
        return ApiResponse.ok(s);
    }

    /**
     * Upload a photo/screenshot of the Aadhaar QR, or the e-Aadhaar PDF (+ its password).
     * Residents verify themselves; staff pass hostellerId. Nothing uploaded is stored.
     */
    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    @Operation(summary = "Verify an Aadhaar Secure QR (photo, screenshot or e-Aadhaar PDF). Requires consent=true.")
    public ApiResponse<Map<String, Object>> verify(@RequestParam(value = "file", required = false) MultipartFile file,
                                                   @RequestParam(value = "qrData", required = false) String qrData,
                                                   @RequestParam(value = "password", required = false) String password,
                                                   @RequestParam(value = "hostellerId", required = false) Long hostellerId,
                                                   @RequestParam(value = "consent", defaultValue = "false") boolean consent) {
        UserPrincipal p = HostelAccess.principal();
        Long target;
        if (p.isHosteller()) {
            target = profileRepo.findByAuthUserId(p.getUserId())
                    .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND)).getId();
        } else {
            if (hostellerId == null) throw new AppException("hostellerId is required", HttpStatus.BAD_REQUEST);
            HostellerProfile prof = profileRepo.findById(hostellerId)
                    .orElseThrow(() -> new AppException("Resident not found", HttpStatus.NOT_FOUND));
            HostelAccess.requireHostellerRecord(prof.getId(), prof.getHostelId(), false);
            target = hostellerId;
        }
        if (!limiter.allow("aadhaar:" + p.getUserId(), 30, 86400)) {
            throw new AppException("Too many attempts today. Please try again tomorrow.", HttpStatus.TOO_MANY_REQUESTS);
        }

        String text;
        if (qrData != null && !qrData.isBlank()) {
            text = qrData;
        } else {
            if (file == null || file.isEmpty()) throw new AppException("Upload a photo of the Aadhaar QR code or the e-Aadhaar PDF", HttpStatus.BAD_REQUEST);
            if (file.getSize() > MAX_BYTES) throw new AppException("File is larger than 8 MB", HttpStatus.PAYLOAD_TOO_LARGE);
            String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            try {
                byte[] bytes = file.getBytes();
                boolean pdf = ct.equals("application/pdf") || name.endsWith(".pdf");
                if (!pdf && !Set.of("image/jpeg", "image/png", "image/webp", "image/bmp").contains(ct)) {
                    throw new AppException("Upload a JPG/PNG image or the e-Aadhaar PDF", HttpStatus.BAD_REQUEST);
                }
                text = pdf ? QrImageDecoder.fromPdf(bytes, password) : QrImageDecoder.fromImage(bytes);
            } catch (AadhaarSecureQr.QrException e) {
                throw new AppException(e.getMessage(), HttpStatus.BAD_REQUEST);
            } catch (java.io.IOException e) {
                throw new AppException("Could not read the file", HttpStatus.BAD_REQUEST);
            }
        }
        Map<String, Object> res = service.verify(target, text, consent, p.getUserId(), p.isHosteller() ? "HOSTELLER" : "STAFF");
        return ApiResponse.ok(res, Boolean.TRUE.equals(res.get("verified")) ? "Aadhaar verified" : "Aadhaar not verified");
    }

    @GetMapping("/status/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<Map<String, Object>> myStatus() {
        HostellerProfile prof = profileRepo.findByAuthUserId(HostelAccess.principal().getUserId())
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
        return ApiResponse.ok(service.status(prof.getId()));
    }

    @GetMapping("/status/{hostellerId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Map<String, Object>> status(@PathVariable Long hostellerId) {
        HostellerProfile prof = profileRepo.findById(hostellerId)
                .orElseThrow(() -> new AppException("Resident not found", HttpStatus.NOT_FOUND));
        HostelAccess.requireHostellerRecord(prof.getId(), prof.getHostelId(), false);
        return ApiResponse.ok(service.status(hostellerId));
    }

    @PostMapping("/{verificationId}/confirm-photo")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: confirm the photo in the Aadhaar QR matches the person")
    public ApiResponse<Map<String, Object>> confirmPhoto(@PathVariable Long verificationId) {
        IdentityVerification v = service.get(verificationId);
        HostellerProfile prof = profileRepo.findById(v.getHostellerId())
                .orElseThrow(() -> new AppException("Resident not found", HttpStatus.NOT_FOUND));
        HostelAccess.requireHostellerRecord(prof.getId(), prof.getHostelId(), false);
        return ApiResponse.ok(service.confirmPhoto(verificationId, HostelAccess.principal().getUserId()), "Photo match confirmed");
    }

    @GetMapping("/hostel/{hostelId}/verified")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: ids of residents with a verified Aadhaar (for badges)")
    public ApiResponse<List<Long>> verifiedIds(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(List.copyOf(service.verifiedResidents(hostelId)));
    }
}
