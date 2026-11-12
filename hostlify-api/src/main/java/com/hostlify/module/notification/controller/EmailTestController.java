package com.hostlify.module.notification.controller;

import com.hostlify.module.notification.service.EmailService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lets an owner/admin check the SMTP setup by emailing themselves. */
@RestController
@RequestMapping("/api/v1/notifications/email")
@Tag(name = "Notifications")
public class EmailTestController {

    private final EmailService emailService;

    public EmailTestController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','SUPER_ADMIN')")
    @Operation(summary = "Is outgoing email configured?")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of("enabled", emailService.isEnabled()));
    }

    @PostMapping("/test")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','SUPER_ADMIN')")
    @Operation(summary = "Send a test email to the signed-in user's own address")
    public ApiResponse<Map<String, Object>> sendTest() {
        UserPrincipal p = HostelAccess.principal();
        if (p.isStaff()) HostelAccess.requireSubRole("OWNER", "ADMIN", "MANAGER");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("to", p.getEmail());
        res.put("enabled", emailService.isEnabled());
        if (!emailService.isEnabled()) {
            res.put("sent", false);
            res.put("error", "Email is not configured on the server (see .env.example).");
            return ApiResponse.ok(res, "Email is not configured");
        }
        try {
            emailService.sendEmail(p.getEmail(), "Hostlify test email",
                    "Hi,\n\nThis is a test email from Hostlify. Email delivery is working: residents will now get "
                            + "payment reminders, late-fee notices and PDF receipts by email.\n\n- Hostlify");
            res.put("sent", true);
            return ApiResponse.ok(res, "Test email sent to " + p.getEmail());
        } catch (Exception e) {
            res.put("sent", false);
            // Root cause is what helps here, e.g. "535 Authentication failed" or "Sender not verified"
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            res.put("error", root.getMessage());
            return ApiResponse.ok(res, "Test email failed");
        }
    }
}
