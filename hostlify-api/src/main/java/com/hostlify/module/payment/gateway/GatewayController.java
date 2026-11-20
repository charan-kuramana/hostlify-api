package com.hostlify.module.payment.gateway;

import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Owner payment gateways")
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);
    private final GatewayService service;
    private final PaymentService paymentService;

    public GatewayController(GatewayService service, PaymentService paymentService) {
        this.service = service;
        this.paymentService = paymentService;
    }

    // ═══ Owner ═════════════════════════════════════════════════════════════

    @GetMapping("/api/v1/payment/gateways")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Owner: my connected payment gateways (secrets masked)")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list(ownerId()));
    }

    @PostMapping("/api/v1/payment/gateways")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Owner: connect a gateway (Razorpay / Stripe / PayPal) for all hostels or one hostel")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.save(ownerId(), HostelAccess.principal().getUserId(), null, body), "Gateway saved");
    }

    @PutMapping("/api/v1/payment/gateways/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Owner: update a gateway (leave secret blank to keep the saved one)")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.save(ownerId(), HostelAccess.principal().getUserId(), id, body), "Gateway saved");
    }

    @DeleteMapping("/api/v1/payment/gateways/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Owner: disconnect a gateway")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(ownerId(), id);
        return ApiResponse.ok(null, "Gateway removed");
    }

    @PostMapping("/api/v1/payment/gateways/{id}/test")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Owner: check the saved keys with the provider")
    public ApiResponse<Map<String, Object>> test(@PathVariable Long id) {
        return ApiResponse.ok(service.test(ownerId(), id));
    }

    // ═══ Resident ══════════════════════════════════════════════════════════

    @GetMapping("/api/v1/payment/online/options")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: online payment methods my hostel accepts")
    public ApiResponse<List<Map<String, Object>>> options() {
        UserPrincipal p = HostelAccess.principal();
        Long hostelId;
        try { hostelId = paymentService.hostelOfResident(p.getUserId()); } catch (AppException e) { return ApiResponse.ok(List.of()); }
        return ApiResponse.ok(service.availableFor(hostelId));
    }

    @PostMapping("/api/v1/payment/online/initiate")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: start paying a due through the hostel owner's gateway")
    public ApiResponse<Map<String, Object>> initiate(@RequestBody Map<String, Object> body) {
        Object due = body.get("dueId");
        if (due == null) throw new AppException("dueId is required", HttpStatus.BAD_REQUEST);
        return ApiResponse.ok(service.initiate(HostelAccess.principal().getUserId(), Long.valueOf(String.valueOf(due)),
                body.get("provider") == null ? null : String.valueOf(body.get("provider"))));
    }

    @PostMapping("/api/v1/payment/online/verify")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: confirm a payment after checkout (Razorpay callback or return from Stripe / PayPal)")
    public ApiResponse<Map<String, Object>> verify(@RequestBody Map<String, Object> body) {
        Object ref = body.get("orderRef");
        if (ref == null) throw new AppException("orderRef is required", HttpStatus.BAD_REQUEST);
        Map<String, String> cb = new HashMap<>();
        for (String k : List.of("razorpayPaymentId", "razorpayOrderId", "razorpaySignature")) {
            if (body.get(k) != null) cb.put(k, String.valueOf(body.get(k)));
        }
        Long profileId = paymentService.profileIdOf(HostelAccess.principal().getUserId());
        return ApiResponse.ok(service.verify(profileId, Long.valueOf(String.valueOf(ref)), cb));
    }

    // ═══ Webhooks (public, signature-checked per gateway) ══════════════════

    @PostMapping("/api/payment/gateway/webhook/{token}")
    public ResponseEntity<String> webhook(@PathVariable String token, @RequestBody String body,
                                          @RequestHeader Map<String, String> headers) {
        Map<String, String> h = new HashMap<>();
        headers.forEach((k, v) -> h.put(k.toLowerCase(), v));
        try {
            service.webhook(token, body, h);
            return ResponseEntity.ok("OK");
        } catch (AppException e) {
            log.warn("Gateway webhook rejected: {}", e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(e.getMessage());
        } catch (Exception e) {
            // Provider retries on 5xx; the reconcile job also catches anything missed
            log.error("Gateway webhook failed: {}", e.getMessage());
            return ResponseEntity.status(500).body("error");
        }
    }

    /** Payment accounts are the owner's own bank money: only the OWNER login manages them. */
    private static Long ownerId() {
        UserPrincipal p = HostelAccess.principal();
        if (!p.isStaff() || !"OWNER".equals(p.getSubRole()) || p.getOwnerId() == null) {
            throw new AppException("Only the hostel owner can manage payment gateways", HttpStatus.FORBIDDEN);
        }
        return p.getOwnerId();
    }
}
