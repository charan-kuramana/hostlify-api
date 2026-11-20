package com.hostlify.module.payment.gateway;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Talks to Razorpay, Stripe and PayPal with the owner's own credentials (plain REST, no SDK state),
 * so every owner's money goes to their own account.
 */
@Component
public class GatewayClients {

    private static final Logger log = LoggerFactory.getLogger(GatewayClients.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** A failure the owner or resident can act on; message is safe to show. */
    public static class GatewayError extends RuntimeException {
        public GatewayError(String msg) { super(msg); }
    }

    public record Created(String providerOrderId, String redirectUrl) {}

    /** paid=true with the provider's payment id and the amount actually paid (paise). */
    public record Status(boolean paid, String paymentId, long amountPaise, String failure) {
        static Status pending() { return new Status(false, null, 0, null); }
        static Status failed(String why) { return new Status(false, null, 0, why); }
    }

    public record CheckoutContext(String description, String businessName, String payerName, String payerEmail,
                                  String returnUrl, String cancelUrl) {}

    // ═══ Connection test ═══════════════════════════════════════════════════

    public String test(OwnerPaymentGateway g, String secret) {
        switch (g.getProvider()) {
            case OwnerPaymentGateway.RAZORPAY -> {
                String key = g.getPublicKey() == null ? "" : g.getPublicKey();
                if (!key.startsWith(g.isLive() ? "rzp_live_" : "rzp_test_")) {
                    throw new GatewayError("This Key ID is not a " + (g.isLive() ? "live (rzp_live_…)" : "test (rzp_test_…)") + " key. Check the mode.");
                }
                Resp r = send(get("https://api.razorpay.com/v1/orders?count=1").header("Authorization", basic(key, secret)));
                if (r.status == 401) throw new GatewayError("Razorpay rejected the Key ID / Key Secret.");
                if (!r.ok()) throw new GatewayError("Razorpay answered " + r.status + ": " + razorpayError(r.body));
                return "Connected to Razorpay (" + g.getMode().toLowerCase() + " mode).";
            }
            case OwnerPaymentGateway.STRIPE -> {
                boolean live = secret.startsWith("sk_live_") || secret.startsWith("rk_live_");
                boolean test = secret.startsWith("sk_test_") || secret.startsWith("rk_test_");
                if (g.isLive() ? !live : !test) {
                    throw new GatewayError("This secret key is not a " + (g.isLive() ? "live (sk_live_…)" : "test (sk_test_…)") + " key. Check the mode.");
                }
                Resp r = send(get("https://api.stripe.com/v1/balance").header("Authorization", "Bearer " + secret));
                if (r.status == 401) throw new GatewayError("Stripe rejected the secret key.");
                if (!r.ok()) throw new GatewayError("Stripe answered " + r.status + ": " + stripeError(r.body));
                return "Connected to Stripe (" + g.getMode().toLowerCase() + " mode).";
            }
            case OwnerPaymentGateway.PAYPAL -> {
                paypalToken(g, secret);
                return "Connected to PayPal (" + (g.isLive() ? "live" : "sandbox") + ").";
            }
            default -> throw new GatewayError("Unknown provider");
        }
    }

    // ═══ Create a checkout ═════════════════════════════════════════════════

    public Created create(OwnerPaymentGateway g, String secret, GatewayOrder o, CheckoutContext ctx) {
        switch (g.getProvider()) {
            case OwnerPaymentGateway.RAZORPAY -> {
                JSONObject body = new JSONObject()
                        .put("amount", o.getAmountPaise())
                        .put("currency", o.getCurrency())
                        .put("receipt", "HLY-" + o.getId())
                        .put("payment_capture", 1)
                        .put("notes", new JSONObject().put("hostlify_order", o.getId()).put("due_id", o.getDueId()));
                Resp r = send(post("https://api.razorpay.com/v1/orders", body.toString(), "application/json")
                        .header("Authorization", basic(g.getPublicKey(), secret)));
                if (!r.ok()) throw new GatewayError("Razorpay could not start the payment: " + razorpayError(r.body));
                return new Created(new JSONObject(r.body).getString("id"), null);
            }
            case OwnerPaymentGateway.STRIPE -> {
                Map<String, String> f = new LinkedHashMap<>();
                f.put("mode", "payment");
                f.put("success_url", ctx.returnUrl());
                f.put("cancel_url", ctx.cancelUrl());
                f.put("client_reference_id", String.valueOf(o.getId()));
                f.put("line_items[0][quantity]", "1");
                f.put("line_items[0][price_data][currency]", o.getCurrency().toLowerCase());
                f.put("line_items[0][price_data][unit_amount]", String.valueOf(o.getAmountPaise()));
                f.put("line_items[0][price_data][product_data][name]", trim(ctx.description(), 200));
                f.put("metadata[hostlify_order]", String.valueOf(o.getId()));
                f.put("metadata[due_id]", String.valueOf(o.getDueId()));
                if (ctx.payerEmail() != null && !ctx.payerEmail().isBlank()) f.put("customer_email", ctx.payerEmail());
                Resp r = send(post("https://api.stripe.com/v1/checkout/sessions", form(f), "application/x-www-form-urlencoded")
                        .header("Authorization", "Bearer " + secret)
                        .header("Idempotency-Key", "hostlify-order-" + o.getId()));
                if (!r.ok()) throw new GatewayError("Stripe could not start the payment: " + stripeError(r.body));
                JSONObject s = new JSONObject(r.body);
                return new Created(s.getString("id"), s.getString("url"));
            }
            case OwnerPaymentGateway.PAYPAL -> {
                String token = paypalToken(g, secret);
                JSONObject body = new JSONObject()
                        .put("intent", "CAPTURE")
                        .put("purchase_units", new JSONArray().put(new JSONObject()
                                .put("reference_id", "HLY-" + o.getId())
                                .put("custom_id", String.valueOf(o.getId()))
                                .put("description", trim(ctx.description(), 120))
                                .put("amount", new JSONObject().put("currency_code", o.getCurrency()).put("value", major(o.getAmountPaise())))))
                        .put("application_context", new JSONObject()
                                .put("brand_name", trim(ctx.businessName(), 120))
                                .put("user_action", "PAY_NOW")
                                .put("shipping_preference", "NO_SHIPPING")
                                .put("return_url", ctx.returnUrl())
                                .put("cancel_url", ctx.cancelUrl()));
                Resp r = send(post(paypalBase(g) + "/v2/checkout/orders", body.toString(), "application/json")
                        .header("Authorization", "Bearer " + token)
                        .header("PayPal-Request-Id", "hostlify-order-" + o.getId()));
                if (!r.ok()) throw new GatewayError("PayPal could not start the payment: " + paypalError(r.body));
                JSONObject ord = new JSONObject(r.body);
                String approve = null;
                JSONArray links = ord.optJSONArray("links");
                for (int i = 0; links != null && i < links.length(); i++) {
                    String rel = links.getJSONObject(i).optString("rel");
                    if ("approve".equals(rel) || "payer-action".equals(rel)) approve = links.getJSONObject(i).getString("href");
                }
                if (approve == null) throw new GatewayError("PayPal did not return an approval link.");
                return new Created(ord.getString("id"), approve);
            }
            default -> throw new GatewayError("Unknown provider");
        }
    }

    // ═══ Check / complete a payment ════════════════════════════════════════

    /**
     * Asks the provider whether the order is paid, capturing it when it is authorised/approved but not yet captured.
     * For Razorpay, a checkout callback (payment id + signature) is verified first when given.
     */
    public Status status(OwnerPaymentGateway g, String secret, GatewayOrder o, Map<String, String> callback) {
        switch (g.getProvider()) {
            case OwnerPaymentGateway.RAZORPAY -> {
                String auth = basic(g.getPublicKey(), secret);
                String paymentId = callback == null ? null : callback.get("razorpayPaymentId");
                if (paymentId != null && !paymentId.isBlank()) {
                    String sig = callback.get("razorpaySignature");
                    if (sig == null || !hmacEquals(secret, o.getProviderOrderId() + "|" + paymentId, sig)) {
                        throw new GatewayError("Payment signature verification failed");
                    }
                    Resp r = send(get("https://api.razorpay.com/v1/payments/" + enc(paymentId)).header("Authorization", auth));
                    if (!r.ok()) throw new GatewayError("Could not confirm the payment with Razorpay: " + razorpayError(r.body));
                    return razorpayPayment(auth, o, new JSONObject(r.body));
                }
                Resp r = send(get("https://api.razorpay.com/v1/orders/" + enc(o.getProviderOrderId()) + "/payments").header("Authorization", auth));
                if (!r.ok()) throw new GatewayError("Could not check the payment with Razorpay: " + razorpayError(r.body));
                JSONArray items = new JSONObject(r.body).optJSONArray("items");
                for (int i = 0; items != null && i < items.length(); i++) {
                    Status s = razorpayPayment(auth, o, items.getJSONObject(i));
                    if (s.paid()) return s;
                }
                return Status.pending();
            }
            case OwnerPaymentGateway.STRIPE -> {
                Resp r = send(get("https://api.stripe.com/v1/checkout/sessions/" + enc(o.getProviderOrderId()))
                        .header("Authorization", "Bearer " + secret));
                if (!r.ok()) throw new GatewayError("Could not check the payment with Stripe: " + stripeError(r.body));
                JSONObject s = new JSONObject(r.body);
                if ("paid".equals(s.optString("payment_status"))) {
                    return new Status(true, s.optString("payment_intent", s.getString("id")), s.optLong("amount_total"), null);
                }
                if ("expired".equals(s.optString("status"))) return Status.failed("Checkout expired");
                return Status.pending();
            }
            case OwnerPaymentGateway.PAYPAL -> {
                String token = paypalToken(g, secret);
                String base = paypalBase(g) + "/v2/checkout/orders/" + enc(o.getProviderOrderId());
                Resp r = send(get(base).header("Authorization", "Bearer " + token));
                if (!r.ok()) throw new GatewayError("Could not check the payment with PayPal: " + paypalError(r.body));
                JSONObject ord = new JSONObject(r.body);
                String st = ord.optString("status");
                if ("APPROVED".equals(st)) {
                    Resp c = send(post(base + "/capture", "{}", "application/json")
                            .header("Authorization", "Bearer " + token)
                            .header("PayPal-Request-Id", "hostlify-capture-" + o.getId()));
                    if (!c.ok() && !c.body.contains("ORDER_ALREADY_CAPTURED")) {
                        throw new GatewayError("PayPal could not complete the payment: " + paypalError(c.body));
                    }
                    if (c.ok()) ord = new JSONObject(c.body);
                    else ord = new JSONObject(send(get(base).header("Authorization", "Bearer " + token)).body);
                    st = ord.optString("status");
                }
                if ("COMPLETED".equals(st)) {
                    JSONObject cap = ord.getJSONArray("purchase_units").getJSONObject(0)
                            .getJSONObject("payments").getJSONArray("captures").getJSONObject(0);
                    if (!"COMPLETED".equals(cap.optString("status"))) return Status.pending(); // e.g. PENDING review
                    long paise = new BigDecimal(cap.getJSONObject("amount").getString("value")).movePointRight(2).longValueExact();
                    return new Status(true, cap.getString("id"), paise, null);
                }
                if ("VOIDED".equals(st)) return Status.failed("Payment was cancelled");
                return Status.pending();
            }
            default -> throw new GatewayError("Unknown provider");
        }
    }

    private Status razorpayPayment(String auth, GatewayOrder o, JSONObject p) {
        String st = p.optString("status");
        if (!o.getProviderOrderId().equals(p.optString("order_id"))) return Status.failed("Payment belongs to another order");
        if ("authorized".equals(st)) {
            JSONObject body = new JSONObject().put("amount", p.getLong("amount")).put("currency", p.optString("currency", "INR"));
            Resp c = send(post("https://api.razorpay.com/v1/payments/" + enc(p.getString("id")) + "/capture", body.toString(), "application/json")
                    .header("Authorization", auth));
            if (!c.ok()) throw new GatewayError("Razorpay could not capture the payment: " + razorpayError(c.body));
            st = new JSONObject(c.body).optString("status");
        }
        if ("captured".equals(st)) return new Status(true, p.getString("id"), p.getLong("amount"), null);
        if ("failed".equals(st)) return Status.failed(p.optString("error_description", "Payment failed"));
        return Status.pending();
    }

    // ═══ Webhooks ══════════════════════════════════════════════════════════

    public boolean razorpayWebhookValid(String webhookSecret, String body, String signature) {
        return signature != null && hmacEquals(webhookSecret, body, signature);
    }

    /** Stripe-Signature: t=timestamp,v1=hex(HMAC_SHA256(secret, t + "." + body)); 5-minute tolerance. */
    public boolean stripeWebhookValid(String webhookSecret, String body, String header) {
        if (header == null) return false;
        String t = null;
        java.util.List<String> sigs = new java.util.ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if ("t".equals(kv[0])) t = kv[1];
            if ("v1".equals(kv[0])) sigs.add(kv[1]);
        }
        if (t == null || sigs.isEmpty()) return false;
        try {
            if (Math.abs(System.currentTimeMillis() / 1000 - Long.parseLong(t)) > 300) return false;
        } catch (NumberFormatException e) {
            return false;
        }
        String payload = t + "." + body;
        return sigs.stream().anyMatch(s -> hmacEquals(webhookSecret, payload, s));
    }

    // ═══ helpers ═══════════════════════════════════════════════════════════

    private record Resp(int status, String body) {
        boolean ok() { return status >= 200 && status < 300; }
    }

    private Resp send(HttpRequest.Builder b) {
        try {
            HttpResponse<String> r = http.send(b.timeout(TIMEOUT).build(), HttpResponse.BodyHandlers.ofString());
            return new Resp(r.statusCode(), r.body() == null ? "" : r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayError("The payment gateway did not answer in time. Please try again.");
        } catch (Exception e) {
            log.warn("Gateway call failed: {}", e.toString());
            throw new GatewayError("Could not reach the payment gateway. Please try again.");
        }
    }

    private static HttpRequest.Builder get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).GET().header("Accept", "application/json");
    }

    private static HttpRequest.Builder post(String url, String body, String type) {
        return HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", type).header("Accept", "application/json");
    }

    private String paypalToken(OwnerPaymentGateway g, String secret) {
        Resp r = send(post(paypalBase(g) + "/v1/oauth2/token", "grant_type=client_credentials", "application/x-www-form-urlencoded")
                .header("Authorization", basic(g.getPublicKey(), secret)));
        if (r.status == 401) throw new GatewayError("PayPal rejected the Client ID / Secret (check sandbox vs live).");
        if (!r.ok()) throw new GatewayError("PayPal answered " + r.status + ": " + paypalError(r.body));
        return new JSONObject(r.body).getString("access_token");
    }

    private static String paypalBase(OwnerPaymentGateway g) {
        return g.isLive() ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";
    }

    private static String basic(String user, String pass) {
        return "Basic " + Base64.getEncoder().encodeToString(((user == null ? "" : user) + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }

    private static String form(Map<String, String> f) {
        return f.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String major(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2).setScale(2).toPlainString();
    }

    private static String trim(String s, int n) {
        if (s == null || s.isBlank()) return "Hostel payment";
        return s.length() > n ? s.substring(0, n) : s;
    }

    static boolean hmacEquals(String secret, String data, String expectedHex) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] got = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            byte[] want = HexFormat.of().parseHex(expectedHex.trim().toLowerCase());
            return MessageDigest.isEqual(got, want);
        } catch (Exception e) {
            return false;
        }
    }

    private static String razorpayError(String body) {
        try { return new JSONObject(body).getJSONObject("error").optString("description", "unknown error"); }
        catch (Exception e) { return "unknown error"; }
    }

    private static String stripeError(String body) {
        try { return new JSONObject(body).getJSONObject("error").optString("message", "unknown error"); }
        catch (Exception e) { return "unknown error"; }
    }

    private static String paypalError(String body) {
        try {
            JSONObject j = new JSONObject(body);
            JSONArray d = j.optJSONArray("details");
            String issue = d != null && d.length() > 0 ? d.getJSONObject(0).optString("description", d.getJSONObject(0).optString("issue")) : null;
            return issue != null ? issue : j.optString("message", j.optString("error_description", "unknown error"));
        } catch (Exception e) {
            return "unknown error";
        }
    }
}
