package com.hostlify.module.auth.service;

import com.hostlify.module.auth.dto.AuthResponse;
import com.hostlify.module.auth.dto.LoginRequest;
import com.hostlify.module.auth.entity.AuthUser;
import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.auth.repository.RefreshTokenRepository;
import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hostel.entity.StaffAssignment;
import com.hostlify.module.hostel.repository.StaffAssignmentRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

@Service
public class AuthUserService {

    private final AuthUserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final HostellerProfileRepository hostellerProfileRepository;
    private final BookingCheckinRepository bookingCheckinRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthUserService(AuthUserRepository userRepository,
                           JwtService jwtService,
                           PasswordEncoder passwordEncoder,
                           HostellerProfileRepository hostellerProfileRepository,
                           BookingCheckinRepository bookingCheckinRepository,
                           StaffAssignmentRepository staffAssignmentRepository,
                           RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.hostellerProfileRepository = hostellerProfileRepository;
        this.bookingCheckinRepository = bookingCheckinRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public AuthResponse registerHosteller(com.hostlify.module.auth.dto.RegisterHostellerRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException("Email already registered", HttpStatus.CONFLICT);
        }

        AuthUser user = new AuthUser();
        user.setUuid(java.util.UUID.randomUUID().toString());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("HOSTELLER");
        user.setStatus("ACTIVE");
        user = userRepository.save(user);

        HostellerProfile profile = new HostellerProfile();
        profile.setAuthUserId(user.getId());
        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());
        profile.setEmail(request.getEmail());
        profile.setMobile(request.getMobile());
        hostellerProfileRepository.save(profile);

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String expectedRole) {
        AuthUser user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException("Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (!user.getRole().equals(expectedRole)) {
            throw new AppException("Access denied for this role", HttpStatus.FORBIDDEN);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AppException("Account is " + user.getStatus().toLowerCase(), HttpStatus.FORBIDDEN);
        }

        return generateAuthResponse(user);
    }

    private AuthResponse generateAuthResponse(AuthUser user) {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        
        if ("HOSTELLER".equals(user.getRole())) {
            HostellerProfile profile = hostellerProfileRepository.findByAuthUserId(user.getId()).orElse(null);
            if (profile != null) {
                claims.put("hostelId", profile.getHostelId());
                Long bookingId = bookingCheckinRepository
                        .findFirstByHostellerIdAndStatus(profile.getId(), "ACTIVE")
                        .map(com.hostlify.module.booking.entity.BookingCheckin::getId)
                        .orElse(null);
                claims.put("bookingId", bookingId);
            }
        } else if ("HOSTEL_STAFF".equals(user.getRole())) {
            List<StaffAssignment> assignments = staffAssignmentRepository.findByStaffUserIdAndIsActiveTrue(user.getId());
            List<Long> hostelIds = assignments.stream().map(StaffAssignment::getHostelId).collect(Collectors.toList());
            Long ownerId = assignments.isEmpty() ? null : assignments.get(0).getOwnerId();
            claims.put("hostel_ids", hostelIds);
            claims.put("owner_id", ownerId);
            claims.put("sub_role", user.getSubRole());
        }

        String accessToken = jwtService.generateAccessToken(user, claims);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Save refresh token to DB
        com.hostlify.module.auth.entity.RefreshToken rtEntity = new com.hostlify.module.auth.entity.RefreshToken();
        rtEntity.setUserId(user.getId());
        rtEntity.setTokenHash(passwordEncoder.encode(refreshToken)); // Storing hash for security
        rtEntity.setExpiresAt(java.time.LocalDateTime.now().plusWeeks(1));
        refreshTokenRepository.save(rtEntity);

        return new AuthResponse(accessToken, refreshToken, user.getRole());
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        // Simple implementation: find user from token and regenerate
        io.jsonwebtoken.Claims claims = jwtService.validateToken(refreshToken);
        String uuid = claims.getSubject();
        AuthUser user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException("Invalid refresh token", HttpStatus.UNAUTHORIZED));
        
        // In a real app, you'd verify the refresh token exists in DB and is not revoked
        return generateAuthResponse(user);
    }
}
