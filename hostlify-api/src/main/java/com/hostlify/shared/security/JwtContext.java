package com.hostlify.shared.security;

public class JwtContext {
    private final String userUuid;
    private final String role;

    public JwtContext(String userUuid, String role) {
        this.userUuid = userUuid;
        this.role = role;
    }

    public String getUserUuid() { return userUuid; }
    public String getRole() { return role; }

    @Override
    public String toString() {
        return "JwtContext{uuid='" + userUuid + "', role='" + role + "'}";
    }
}
