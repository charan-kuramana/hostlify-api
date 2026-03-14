package com.hostlify.module.auth.controller;

import com.hostlify.module.auth.dto.AuthResponse;
import com.hostlify.module.auth.dto.LoginRequest;
import com.hostlify.module.auth.dto.RegisterHostellerRequest;
import com.hostlify.module.auth.service.AuthUserService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.CurrentUser;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthUserService authUserService;

    public AuthController(AuthUserService authUserService) {
        this.authUserService = authUserService;
    }

    @PostMapping("/hosteller/register")
    @Operation(summary = "Register a new hosteller (status=PENDING until bed allocated)")
    public ApiResponse<AuthResponse> registerHosteller(@Valid @RequestBody RegisterHostellerRequest request) {
        return ApiResponse.ok(authUserService.registerHosteller(request), "Registration successful. Awaiting admin approval.");
    }

    // legacy path kept for backward compat
    @PostMapping("/register/hosteller")
    public ApiResponse<AuthResponse> registerHostellerLegacy(@Valid @RequestBody RegisterHostellerRequest request) {
        return ApiResponse.ok(authUserService.registerHosteller(request), "Registration successful. Awaiting admin approval.");
    }

    @PostMapping("/hosteller/login")
    @Operation(summary = "Login as hosteller")
    public ApiResponse<AuthResponse> loginHosteller(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authUserService.login(request, "HOSTELLER"));
    }

    @PostMapping("/staff/login")
    @Operation(summary = "Login as hostel staff")
    public ApiResponse<AuthResponse> loginStaff(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authUserService.login(request, "HOSTEL_STAFF"));
    }

    @PostMapping("/superadmin/login")
    @Operation(summary = "Login as super admin")
    public ApiResponse<AuthResponse> loginSuperAdmin(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authUserService.login(request, "SUPER_ADMIN"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ApiResponse<AuthResponse> refresh(@RequestParam String refreshToken) {
        return ApiResponse.ok(authUserService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke refresh token")
    public ApiResponse<Void> logout(@RequestParam String refreshToken) {
        authUserService.logout(refreshToken);
        return ApiResponse.ok(null, "Logged out successfully");
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request OTP for password reset")
    public ApiResponse<Void> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new com.hostlify.shared.exception.AppException("Email is required", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        authUserService.forgotPassword(email);
        return ApiResponse.ok(null, "If your email exists, an OTP has been sent");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using OTP")
    public ApiResponse<Void> resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String otp = body.get("otp");
        String newPassword = body.get("newPassword");
        if (email == null || otp == null || newPassword == null) {
            throw new com.hostlify.shared.exception.AppException("email, otp, and newPassword are required", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        authUserService.resetPassword(email, otp, newPassword);
        return ApiResponse.ok(null, "Password reset successfully");
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user info from JWT")
    public ApiResponse<UserPrincipal> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new com.hostlify.shared.exception.AppException("Not authenticated", org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return ApiResponse.ok(principal);
    }
}
