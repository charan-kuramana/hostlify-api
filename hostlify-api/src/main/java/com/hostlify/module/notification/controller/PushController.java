package com.hostlify.module.notification.controller;

import com.hostlify.module.notification.service.PushService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications/push")
@Tag(name = "Notifications")
public class PushController {

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    @GetMapping("/config")
    @Operation(summary = "Web push: is it available, the VAPID public key, and whether this user has a device subscribed")
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", pushService.isEnabled());
        m.put("publicKey", pushService.getPublicKey());
        m.put("subscribed", pushService.hasSubscription(principal().getUserId()));
        return ApiResponse.ok(m);
    }

    @PostMapping("/subscribe")
    @Operation(summary = "Web push: register this browser for notifications")
    @SuppressWarnings("unchecked")
    public ApiResponse<Void> subscribe(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String endpoint = (String) body.get("endpoint");
        Map<String, Object> keys = body.get("keys") instanceof Map ? (Map<String, Object>) body.get("keys") : Map.of();
        String p256dh = (String) keys.get("p256dh");
        String auth = (String) keys.get("auth");
        if (endpoint == null || !endpoint.startsWith("https://") || p256dh == null || auth == null) {
            throw new AppException("Invalid push subscription", HttpStatus.BAD_REQUEST);
        }
        pushService.subscribe(principal().getUserId(), endpoint, p256dh, auth, req.getHeader("User-Agent"));
        return ApiResponse.ok(null, "Notifications enabled on this device");
    }

    @PostMapping("/unsubscribe")
    @Operation(summary = "Web push: stop notifications on this browser")
    public ApiResponse<Void> unsubscribe(@RequestBody Map<String, Object> body) {
        String endpoint = (String) body.get("endpoint");
        if (endpoint != null) pushService.unsubscribe(principal().getUserId(), endpoint);
        return ApiResponse.ok(null, "Notifications turned off on this device");
    }

    private UserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            throw new AppException("Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        return p;
    }
}
