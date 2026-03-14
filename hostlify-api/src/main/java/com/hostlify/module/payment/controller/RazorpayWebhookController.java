package com.hostlify.module.payment.controller;

import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.shared.util.HmacUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Razorpay Webhook — IMPORTANT: NO /v1 prefix. URL is /api/payment/razorpay/webhook
 * This URL must be in SecurityConfig permitAll() list.
 * Razorpay retries webhooks up to 3 times — this handler MUST be idempotent.
 */
@RestController
@RequestMapping("/api/payment/razorpay")
@Tag(name = "Razorpay Webhook — Internal")
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private final PaymentService paymentService;

    @Value("${app.razorpay.webhook-secret:test_webhook_secret}")
    private String webhookSecret;

    public RazorpayWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        // 1. Verify HMAC signature — reject unsigned webhooks immediately
        if (signature == null || signature.isBlank()) {
            log.warn("Razorpay webhook received without signature — rejected");
            return ResponseEntity.badRequest().body("Missing signature");
        }

        if (!HmacUtils.verifyHmac(webhookSecret, rawBody, signature)) {
            log.warn("Razorpay webhook signature mismatch — rejected");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        // 2. Parse event type
        try {
            org.json.JSONObject payload = new org.json.JSONObject(rawBody);
            String event = payload.optString("event");

            if (!"payment.captured".equals(event)) {
                // Other events (payment.failed etc.) — acknowledge and ignore
                log.info("Ignoring Razorpay event: {}", event);
                return ResponseEntity.ok("OK");
            }

            // 3. Extract payment details
            org.json.JSONObject paymentEntity = payload
                    .getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");

            String razorpayPaymentId = paymentEntity.optString("id");
            String razorpayOrderId = paymentEntity.optString("order_id");
            long amountPaise = paymentEntity.optLong("amount", 0L);

            log.info("Processing payment.captured: paymentId={}, orderId={}", razorpayPaymentId, razorpayOrderId);

            // 4. Handle (idempotent — service checks for duplicate payment_id)
            paymentService.handleRazorpayPaymentCaptured(razorpayPaymentId, razorpayOrderId, amountPaise);

        } catch (Exception e) {
            // ALWAYS return 200 to Razorpay — log errors but never return 5xx
            log.error("Error processing Razorpay webhook: {}", e.getMessage(), e);
        }

        // 5. Always return 200 to Razorpay
        return ResponseEntity.ok("OK");
    }
}
