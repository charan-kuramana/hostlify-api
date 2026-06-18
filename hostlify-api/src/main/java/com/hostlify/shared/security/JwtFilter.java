package com.hostlify.shared.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Skip JWT processing for public endpoints. This ensures a stale/expired token left in the
     * browser is never inspected on login/refresh, so the user can always sign in again.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/payment/razorpay/webhook")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        try {
            Claims claims = jwtService.validateToken(jwt);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String role = claims.get("role", String.class);

                UserPrincipal principal = new UserPrincipal();
                principal.setUuid(claims.getSubject());
                principal.setRole(role);
                principal.setSubRole(claims.get("sub_role", String.class));
                principal.setName(claims.get("name", String.class));
                principal.setStatus(claims.get("status", String.class));

                Object userIdObj = claims.get("userId");
                if (userIdObj instanceof Number) {
                    principal.setUserId(((Number) userIdObj).longValue());
                }

                if ("HOSTELLER".equals(role)) {
                    Object hostelIdObj = claims.get("hostelId");
                    if (hostelIdObj instanceof Number) principal.setHostelId(((Number) hostelIdObj).longValue());
                    Object bookingIdObj = claims.get("bookingId");
                    if (bookingIdObj instanceof Number) principal.setBookingId(((Number) bookingIdObj).longValue());
                    Object hostellerProfileIdObj = claims.get("hostellerProfileId");
                    if (hostellerProfileIdObj instanceof Number) principal.setHostellerProfileId(((Number) hostellerProfileIdObj).longValue());
                } else if ("HOSTEL_STAFF".equals(role)) {
                    Object ownerIdObj = claims.get("owner_id");
                    if (ownerIdObj instanceof Number) principal.setOwnerId(((Number) ownerIdObj).longValue());
                    Object hostelIdsObj = claims.get("hostel_ids");
                    if (hostelIdsObj instanceof java.util.List<?> rawList) {
                        java.util.List<Long> ids = new java.util.ArrayList<>();
                        for (Object item : rawList) {
                            if (item instanceof Number) ids.add(((Number) item).longValue());
                        }
                        principal.setHostelIds(ids);
                    }
                }

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            // Invalid/expired token: don't authenticate. Let the request continue so Spring
            // Security's entry point returns a 401 (which the client uses to refresh the token).
            // Never hard-block here, or a stale token would also break public routes like login.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
