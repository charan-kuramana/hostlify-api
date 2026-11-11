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
    private static final int MAX_OTP_ATTEMPTS = 5;

    /** Wrong-guess counter per OTP id (in-memory; an OTP lives 15 minutes at most). */
    private final Map<Long, Integer> otpFailures = new java.util.concurrent.ConcurrentHashMap<>();

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String lockedMessage(LocalDateTime until) {
        return "Too many failed login attempts. Your account is locked until "
                + until.format(java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a", java.util.Locale.ENGLISH))
                + ". Try again later or reset your password.";
    }

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
        request.setEmail(normalizeEmail(request.getEmail()));
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

    // noRollbackFor: the failed-attempt counter / lock must be persisted even though we throw
    @Transactional(noRollbackFor = AppException.class)
    public AuthResponse login(LoginRequest request, String expectedRole) {
        AuthUser user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new AppException("Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (!expectedRole.equals(user.getRole())) {
            throw new AppException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        // Check account lock
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AppException(lockedMessage(user.getLockedUntil()), HttpStatus.FORBIDDEN);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                user.setFailedAttempts(0);
                userRepository.save(user);
                throw new AppException(lockedMessage(user.getLockedUntil()), HttpStatus.FORBIDDEN);
            }
            userRepository.save(user);
            int left = MAX_FAILED_ATTEMPTS - attempts;
            throw new AppException(left <= 2
                    ? "Invalid credentials. " + left + " attempt" + (left == 1 ? "" : "s") + " left before your account is locked."
                    : "Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        // PENDING hostellers may log in: they need access to Discover / Book / Booking Status
        // to request a bed. Their token simply carries no hostelId until a bed is allocated.
        if ("PENDING".equals(user.getStatus()) && !"HOSTELLER".equals(expectedRole)) {
            throw new AppException("Account pending approval", HttpStatus.FORBIDDEN);
        }
        if ("SUSPENDED".equals(user.getStatus())) {
            throw new AppException("Your account has been suspended. Please contact Hostlify support.", HttpStatus.FORBIDDEN);
        }
        if ("INACTIVE".equals(user.getStatus())) {
            throw new AppException("Your account is inactive. Please contact your hostel or Hostlify support.", HttpStatus.FORBIDDEN);
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
        email = normalizeEmail(email);
        // Unknown email: respond exactly like a known one so accounts can't be enumerated
        AuthUser user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return;

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

    // noRollbackFor: burning an OTP after too many wrong guesses must be persisted
    @Transactional(noRollbackFor = AppException.class)
    public void resetPassword(String email, String otpCode, String newPassword) {
        if (newPassword.length() < 8) {
            throw new AppException("Password must be at least 8 characters", HttpStatus.BAD_REQUEST);
        }
        AuthUser user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new AppException("Invalid or expired OTP", HttpStatus.BAD_REQUEST));

        OtpToken otp = otpTokenRepository
                .findTopByUserIdAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(user.getId(), "PASSWORD_RESET")
                .orElseThrow(() -> new AppException("OTP not found or already used. Please request a new OTP.", HttpStatus.BAD_REQUEST));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException("OTP has expired. Please request a new OTP.", HttpStatus.BAD_REQUEST);
        }
        if (!otp.getOtpCode().equals(otpCode == null ? null : otpCode.trim())) {
            // Limit guesses per OTP so the 6-digit code can't be brute-forced
            int wrong = otpFailures.merge(otp.getId(), 1, Integer::sum);
            if (wrong >= MAX_OTP_ATTEMPTS) {
                otpFailures.remove(otp.getId());
                otp.setUsed(true);
                otpTokenRepository.save(otp);
                throw new AppException("Too many invalid attempts. Please request a new OTP.", HttpStatus.BAD_REQUEST);
            }
            throw new AppException("Invalid OTP", HttpStatus.BAD_REQUEST);
        }

        otpFailures.remove(otp.getId());
        otp.setUsed(true);
        otpTokenRepository.save(otp);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setLockedUntil(null);
        user.setFailedAttempts(0);
        userRepository.save(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtService.validateToken(rawRefreshToken);
        } catch (Exception e) {
            throw new AppException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
        }
        // Only genuine refresh tokens (not access tokens) that were issued by us and not revoked by logout
        if (!"refresh".equals(claims.get("typ", String.class))
                || refreshTokenRepository.findByTokenHashAndIsRevokedFalse(JwtService.hash(rawRefreshToken)).isEmpty()) {
            throw new AppException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
        }
        String uuid = claims.getSubject();
        AuthUser user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException("Invalid refresh token", HttpStatus.UNAUTHORIZED));
        if ("SUSPENDED".equals(user.getStatus()) || "INACTIVE".equals(user.getStatus())) {
            throw new AppException("Account " + user.getStatus().toLowerCase(), HttpStatus.UNAUTHORIZED);
        }
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
        AuthResponse response = new AuthResponse(null, null, user.getRole(), user.getSubRole(), user.getEmail());

        if ("HOSTELLER".equals(user.getRole())) {
            HostellerProfile profile = hostellerProfileRepository.findByAuthUserId(user.getId()).orElse(null);
            if (profile != null) {
                claims.put("hostelId", profile.getHostelId());
                claims.put("hostellerProfileId", profile.getId());
                Long bookingId = bookingCheckinRepository
                        .findFirstByHostellerIdAndStatus(profile.getId(), "ACTIVE")
                        .map(b -> b.getId())
                        .orElse(null);
                claims.put("bookingId", bookingId);
                response.setHostelId(profile.getHostelId());
                response.setHostellerProfileId(profile.getId());
                response.setBookingId(bookingId);
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
            response.setHostelIds(hostelIds);
            response.setOwnerId(ownerId);
        }

        String accessToken = jwtService.generateAccessToken(user, claims);
        String refreshToken = jwtService.generateRefreshToken(user);
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);

        RefreshToken rtEntity = new RefreshToken();
        rtEntity.setUserId(user.getId());
        rtEntity.setTokenHash(JwtService.hash(refreshToken));
        rtEntity.setExpiresAt(LocalDateTime.now().plusDays(30));
        rtEntity.setRevoked(false);
        refreshTokenRepository.save(rtEntity);

        return response;
    }
}
