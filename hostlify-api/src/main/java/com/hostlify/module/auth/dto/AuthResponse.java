package com.hostlify.module.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String role;
    private String subRole;
    private String email;

    // Hosteller-specific context (populated when role=HOSTELLER)
    private Long hostelId;
    private Long hostellerProfileId;
    private Long bookingId;

    // Staff-specific context (populated when role=HOSTEL_STAFF)
    private java.util.List<Long> hostelIds;
    private Long ownerId;

    public AuthResponse() {}

    public AuthResponse(String accessToken, String refreshToken, String role, String subRole, String email) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.role = role;
        this.subRole = subRole;
        this.email = email;
    }
}
