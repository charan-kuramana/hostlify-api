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
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"INVALID_TOKEN\",\"message\":\"Invalid or expired token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
