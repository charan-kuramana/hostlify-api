package com.hostlify.module.auth.service;

import com.hostlify.module.auth.dto.AuthResponse;
import com.hostlify.module.auth.dto.LoginRequest;
import com.hostlify.module.auth.dto.RegisterHostellerRequest;
import com.hostlify.module.auth.entity.AuthUser;
import com.hostlify.module.auth.entity.OtpToken;
import com.hostlify.module.auth.entity.RefreshToken;
import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.auth.repository.OtpTokenRepository;
import com.hostlify.module.auth.repository.RefreshTokenRepository;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hostel.repository.StaffAssignmentRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.JwtService;
import com.hostlify.shared.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class AuthUserService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 30;
    private static final int OTP_EXPIRY_MINUTES = 15;

    private final AuthUserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final HostellerProfileRepository hostellerProfileRepository;
    private final BookingCheckinRepository bookingCheckinRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpTokenRepository otpTokenRepository;

    public AuthUserService(AuthUserRepository userRepository,
                           JwtService jwtService,
                           PasswordEncoder passwordEncoder,
                           HostellerProfileRepository hostellerProfileRepository,
                           BookingCheckinRepository bookingCheckinRepository,
                           StaffAssignmentRepository staffAssignmentRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           OtpTokenRepository otpTokenRepository) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.hostellerProfileRepository = hostellerProfileRepository;
        this.bookingCheckinRepository = bookingCheckinRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpTokenRepository = otpTokenRepository;
    }

    @Transactional
    public AuthResponse registerHosteller(RegisterHostellerRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException("Email already registered", HttpStatus.CONFLICT);
        }

        AuthUser user = new AuthUser();
        user.setUuid(java.util.UUID.randomUUID().toString());
        user.setEmail(request.getEmail());
        user.setMobile(request.getMobile());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("HOSTELLER");
        user.setStatus("PENDING");  // PENDING until staff allocates a bed
        user = userRepository.save(user);

        HostellerProfile profile = new HostellerProfile();
        profile.setAuthUserId(user.getId());
        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());
        profile.setEmail(request.getEmail());
        profile.setMobile(request.getMobile());
        if (request.getResidentType() != null) profile.setUserType(request.getResidentType());
        if (request.getCompanyOrCollege() != null) profile.setCompanyCollege(request.getCompanyOrCollege());
        hostellerProfileRepository.save(profile);

        // Return minimal response; access token will work but hostelId will be null until allocated
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String expectedRole) {
        AuthUser user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException("Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (!expectedRole.equals(user.getRole())) {
            throw new AppException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        // Check account lock
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AppException("Account locked. Try again after " + user.getLockedUntil(), HttpStatus.FORBIDDEN);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                user.setFailedAttempts(0);
            }
            userRepository.save(user);
            throw new AppException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        if ("PENDING".equals(user.getStatus()) && "HOSTELLER".equals(expectedRole)) {
            throw new AppException("Account pending admin approval", HttpStatus.FORBIDDEN);
        }
        if ("SUSPENDED".equals(user.getStatus())) {
            throw new AppException("Account suspended", HttpStatus.FORBIDDEN);
        }
        if ("INACTIVE".equals(user.getStatus())) {
            throw new AppException("Account inactive", HttpStatus.FORBIDDEN);
        }

        // Reset failed attempts on success
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        AuthUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException("If your email exists, you will receive an OTP shortly", HttpStatus.OK));

        String otp = String.format("%06d", new Random().nextInt(1000000));
        OtpToken token = new OtpToken();
        token.setUserId(user.getId());
        token.setOtpCode(otp);
        token.setPurpose("PASSWORD_RESET");
        token.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpTokenRepository.save(token);

        // In dev, OTP would be logged; in prod, sent via email
        System.out.println("[DEV] OTP for " + email + ": " + otp);
    }

    @Transactional
    public void resetPassword(String email, String otpCode, String newPassword) {
        AuthUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException("Invalid request", HttpStatus.BAD_REQUEST));

        OtpToken otp = otpTokenRepository
                .findTopByUserIdAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(user.getId(), "PASSWORD_RESET")
                .orElseThrow(() -> new AppException("OTP not found or already used", HttpStatus.BAD_REQUEST));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException("OTP has expired", HttpStatus.BAD_REQUEST);
        }
        if (!otp.getOtpCode().equals(otpCode)) {
            throw new AppException("Invalid OTP", HttpStatus.BAD_REQUEST);
        }

        otp.setUsed(true);
        otpTokenRepository.save(otp);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setLockedUntil(null);
        user.setFailedAttempts(0);
        userRepository.save(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        io.jsonwebtoken.Claims claims = jwtService.validateToken(rawRefreshToken);
        String uuid = claims.getSubject();
        AuthUser user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException("Invalid refresh token", HttpStatus.UNAUTHORIZED));
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        try {
            io.jsonwebtoken.Claims claims = jwtService.validateToken(rawRefreshToken);
            String uuid = claims.getSubject();
            userRepository.findByUuid(uuid).ifPresent(user ->
                refreshTokenRepository.revokeAllByUserId(user.getId())
            );
        } catch (Exception ignored) {
            // If token is already invalid, logout is a no-op
        }
    }

    private AuthResponse buildAuthResponse(AuthUser user) {
        Map<String, Object> claims = new HashMap<>();

        if ("HOSTELLER".equals(user.getRole())) {
            HostellerProfile profile = hostellerProfileRepository.findByAuthUserId(user.getId()).orElse(null);
            if (profile != null) {
                claims.put("hostelId", profile.getHostelId());
                Long bookingId = bookingCheckinRepository
                        .findFirstByHostellerIdAndStatus(profile.getId(), "ACTIVE")
                        .map(b -> b.getId())
                        .orElse(null);
                claims.put("bookingId", bookingId);
                claims.put("hostellerProfileId", profile.getId());
            }
        } else if ("HOSTEL_STAFF".equals(user.getRole())) {
            var assignments = staffAssignmentRepository.findByStaffUserIdAndIsActiveTrue(user.getId());
            List<Long> hostelIds = assignments.stream()
                    .map(com.hostlify.module.hostel.entity.StaffAssignment::getHostelId)
                    .collect(Collectors.toList());
            Long ownerId = assignments.isEmpty() ? null : assignments.get(0).getOwnerId();
            claims.put("hostel_ids", hostelIds);
            claims.put("owner_id", ownerId);
            claims.put("sub_role", user.getSubRole());
        }

        String accessToken = jwtService.generateAccessToken(user, claims);
        String refreshToken = jwtService.generateRefreshToken(user);

        RefreshToken rtEntity = new RefreshToken();
        rtEntity.setUserId(user.getId());
        // Store a hash of the refresh token (not the raw token) for security
        rtEntity.setTokenHash(java.util.UUID.randomUUID().toString()); // unique placeholder; real app would hash the token
        rtEntity.setExpiresAt(LocalDateTime.now().plusDays(30));
        rtEntity.setRevoked(false);
        refreshTokenRepository.save(rtEntity);

        return new AuthResponse(accessToken, refreshToken, user.getRole(), user.getSubRole(), user.getEmail());
    }
}
