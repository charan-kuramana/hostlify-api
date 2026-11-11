package com.hostlify.module.referral.controller;

import com.hostlify.module.referral.entity.ReferralCode;
import com.hostlify.module.referral.entity.ReferralRecord;
import com.hostlify.module.referral.service.ReferralService;
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
@RequestMapping("/api/v1/referral")
@Tag(name = "Referral")
public class ReferralController {

    private final ReferralService referralService;

    public ReferralController(ReferralService referralService) {
        this.referralService = referralService;
    }

    @GetMapping("/my-code")
    @Operation(summary = "Hosteller: get or generate my referral code")
    public ApiResponse<ReferralCode> getMyCode() {
        UserPrincipal p = getPrincipal();
        // Use name from JWT; firstName will at least be the full name
        String firstName = p.getName() != null ? p.getName().split(" ")[0] : "HOST";
        return ApiResponse.ok(referralService.getOrCreateCode(p.getHostellerProfileId(), firstName));
    }

    @GetMapping("/my-referrals")
    @Operation(summary = "Hosteller: list people I referred")
    public ApiResponse<List<ReferralRecord>> getMyReferrals() {
        UserPrincipal p = getPrincipal();
        return ApiResponse.ok(referralService.getMyReferrals(p.getHostellerProfileId()));
    }

    @PostMapping("/validate")
    @Operation(summary = "Apply a referral code for a hosteller (called during registration)")
    public ApiResponse<String> validateCode(@RequestBody Map<String, String> body) {
        UserPrincipal p = getPrincipal();
        String code = body.get("code");
        Long hostellerId;
        if (p.isHosteller()) {
            // a hosteller can only apply a code to their own (new) account
            hostellerId = p.getHostellerProfileId();
        } else if (p.isStaff() || p.isSuperAdmin()) {
            try {
                hostellerId = Long.parseLong(String.valueOf(body.get("hostellerId")));
            } catch (NumberFormatException e) {
                throw new AppException("hostellerId is required", HttpStatus.BAD_REQUEST);
            }
        } else {
            throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (hostellerId == null) throw new AppException("Hosteller profile not found", HttpStatus.BAD_REQUEST);
        referralService.applyReferral(code, hostellerId);
        return ApiResponse.ok("Referral applied successfully");
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AppException("Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        return (UserPrincipal) auth.getPrincipal();
    }
}
