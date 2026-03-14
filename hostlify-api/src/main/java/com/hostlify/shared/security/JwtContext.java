package com.hostlify.shared.security;

import lombok.Getter;

@Getter
public class JwtContext {
    private final String userUuid;
    private final String role;

    public JwtContext(String userUuid, String role) {
        this.userUuid = userUuid;
        this.role = role;
    }

    @Override
    public String toString() {
        return "JwtContext{uuid='" + userUuid + "', role='" + role + "'}";
    }
}
