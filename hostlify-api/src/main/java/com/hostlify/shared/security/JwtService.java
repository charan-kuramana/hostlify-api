package com.hostlify.shared.security;

import com.hostlify.module.auth.entity.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.hosteller-expiry-ms:86400000}")
    private long hostellerExpiry;

    @Value("${app.jwt.staff-expiry-ms:86400000}")
    private long staffExpiry;

    @Value("${app.jwt.superadmin-expiry-ms:28800000}")
    private long superadminExpiry;

    @Value("${app.jwt.refresh-expiry-ms:2592000000}")
    private long refreshExpiry;

    private SecretKey getSignInKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateHostellerToken(AuthUser user, Map<String, Object> claims) {
        claims.put("role", user.getRole());
        claims.put("name", user.getEmail());
        claims.put("userId", user.getId());
        claims.put("status", user.getStatus());
        return buildToken(claims, user.getUuid(), hostellerExpiry);
    }

    public String generateStaffToken(AuthUser user, Map<String, Object> claims) {
        claims.put("role", user.getRole());
        claims.put("sub_role", user.getSubRole());
        claims.put("name", user.getEmail());
        claims.put("userId", user.getId());
        claims.put("status", user.getStatus());
        return buildToken(claims, user.getUuid(), staffExpiry);
    }

    public String generateSuperAdminToken(AuthUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole());
        claims.put("name", user.getEmail());
        claims.put("userId", user.getId());
        claims.put("status", user.getStatus());
        claims.put("permissions", java.util.List.of("ALL"));
        return buildToken(claims, user.getUuid(), superadminExpiry);
    }

    // Keep for backward compatibility
    public String generateAccessToken(AuthUser user, Map<String, Object> claims) {
        claims.put("role", user.getRole());
        claims.put("userId", user.getId());
        claims.put("status", user.getStatus());
        long expiry = "SUPER_ADMIN".equals(user.getRole()) ? superadminExpiry :
                      "HOSTEL_STAFF".equals(user.getRole()) ? staffExpiry : hostellerExpiry;
        return buildToken(claims, user.getUuid(), expiry);
    }

    public String generateRefreshToken(AuthUser user) {
        return buildToken(new HashMap<>(), user.getUuid(), refreshExpiry);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
