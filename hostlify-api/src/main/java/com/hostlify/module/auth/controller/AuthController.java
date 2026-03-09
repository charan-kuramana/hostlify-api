package com.hostlify.module.auth.controller;

import com.hostlify.module.auth.dto.AuthResponse;
import com.hostlify.module.auth.dto.LoginRequest;
import com.hostlify.module.auth.dto.RegisterHostellerRequest;
import com.hostlify.module.auth.service.AuthUserService;
import com.hostlify.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthUserService authUserService;

    public AuthController(AuthUserService authUserService) {
        this.authUserService = authUserService;
    }

    @PostMapping("/register/hosteller")
    public ApiResponse<AuthResponse> registerHosteller(@Valid @RequestBody RegisterHostellerRequest request) {
        return ApiResponse.ok(authUserService.registerHosteller(request));
    }

    @PostMapping("/hosteller/login")
    public ApiResponse<AuthResponse> loginHosteller(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authUserService.login(request, "HOSTELLER"));
    }

    @PostMapping("/staff/login")
    public ApiResponse<AuthResponse> loginStaff(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authUserService.login(request, "HOSTEL_STAFF"));
    }

    @PostMapping("/superadmin/login")
    public ApiResponse<AuthResponse> loginSuperAdmin(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authUserService.login(request, "SUPER_ADMIN"));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestParam String refreshToken) {
        return ApiResponse.ok(authUserService.refresh(refreshToken));
    }
}
