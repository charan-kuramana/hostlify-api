package com.hostlify.module.payment.gateway;

import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.payment.entity.PaymentRecord;
import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.module.payment.service.ReceiptPdfService;
import com.hostlify.shared.exception.AppException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Owners' own payment gateways: connect / test / remove, and residents paying through them.
 * Every gateway belongs to one owner; a resident can only ever pay through a gateway of the owner of
 * the hostel the due belongs to, so money always lands in that owner's account.
 */
@Service
public class GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);
    public static final List<String> PROVIDERS = List.of(OwnerPaymentGateway.RAZORPAY, OwnerPaymentGateway.STRIPE, OwnerPaymentGateway.PAYPAL);
    private static final Map<String, String> NAMES = Map.of("RAZORPAY", "Razorpay", "STRIPE", "Stripe", "PAYPAL", "PayPal");

    private final GatewayRepos.Gateways gateways;
    private final GatewayRepos.Orders orders;
    private final GatewayClients clients;
    private final SecretBox box;
    private final HostelRepository hostelRepo;
    private final PaymentService paymentService;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public GatewayService(GatewayRepos.Gateways gateways, GatewayRepos.Orders orders, GatewayClients clients, SecretBox box,
                          HostelRepository hostelRepo, PaymentService paymentService, TransactionTemplate tx) {
        this.gateways = gateways;
        this.orders = orders;
        this.clients = clients;
        this.box = box;
        this.hostelRepo = hostelRepo;
        this.paymentService = paymentService;
        this.tx = tx;
    }

    // ═══ Owner: manage gateways ════════════════════════════════════════════

    public List<Map<String, Object>> list(Long ownerId) {
        return gateways.findByOwnerIdOrderByIdAsc(ownerId).stream().map(this::view).toList();
    }

    @Transactional
    public Map<String, Object> save(Long ownerId, Long userId, Long id, Map<String, Object> body) {
        OwnerPaymentGateway g;
        if (id != null) {
            g = owned(ownerId, id);
        } else {
            String provider = str(body.get("provider"));
            provider = provider == null ? null : provider.toUpperCase();
            if (!PROVIDERS.contains(provider)) throw bad("Choose Razorpay, Stripe or PayPal");
            Long hostelId = body.get("hostelId") == null || String.valueOf(body.get("hostelId")).isBlank()
                    ? null : Long.valueOf(String.valueOf(body.get("hostelId")));
            if (hostelId != null) requireOwnHostel(ownerId, hostelId);
            if (gateways.findScope(ownerId, hostelId, provider).isPresent()) {
                throw new AppException(NAMES.get(provider) + " is already connected for " + (hostelId == null ? "all your hostels" : "this hostel")
                        + ". Edit that one instead.", HttpStatus.CONFLICT);
            }
            g = new OwnerPaymentGateway();
            g.setOwnerId(ownerId);
            g.setHostelId(hostelId);
            g.setProvider(provider);
            g.setWebhookToken(token());
            g.setCreatedBy(userId);
        }
        if (body.containsKey("mode")) {
            String mode = String.valueOf(body.get("mode")).toUpperCase();
            if (!mode.equals("TEST") && !mode.equals("LIVE")) throw bad("Mode must be TEST or LIVE");
            g.setMode(mode);
        }
        if (body.containsKey("label")) g.setLabel(limit(str(body.get("label")), 80));
        if (body.containsKey("publicKey")) g.setPublicKey(limit(str(body.get("publicKey")), 255));
        String secret = str(body.get("secret"));
        if (secret != null) g.setSecretEnc(box.encrypt(secret));
        if (body.containsKey("webhookSecret")) {
            String ws = str(body.get("webhookSecret"));
            if (ws != null) g.setWebhookSecretEnc(box.encrypt(ws));
            else if (Boolean.TRUE.equals(body.get("clearWebhookSecret"))) g.setWebhookSecretEnc(null);
        }
        if (body.containsKey("enabled")) g.setEnabled(Boolean.TRUE.equals(body.get("enabled")));

        if (g.getSecretEnc() == null) throw bad("Enter the secret key");
        if (!OwnerPaymentGateway.STRIPE.equals(g.getProvider()) && g.getPublicKey() == null) {
            throw bad(OwnerPaymentGateway.RAZORPAY.equals(g.getProvider()) ? "Enter the Razorpay Key ID" : "Enter the PayPal Client ID");
        }
        boolean credentialsChanged = secret != null || body.containsKey("publicKey") || body.containsKey("mode");
        if (credentialsChanged) {
            g.setLastTestOk(null);
            g.setLastTestMessage(null);
            g.setLastTestedAt(null);
        }
        g.setUpdatedAt(LocalDateTime.now());
        return view(gateways.save(g));
    }

    @Transactional
    public void delete(Long ownerId, Long id) {
        gateways.delete(owned(ownerId, id));
    }

    /** Calls the provider with the saved credentials and remembers the result. */
    public Map<String, Object> test(Long ownerId, Long id) {
        OwnerPaymentGateway g = owned(ownerId, id);
        try {
            g.setLastTestMessage(clients.test(g, box.decrypt(g.getSecretEnc())));
            g.setLastTestOk(true);
        } catch (GatewayClients.GatewayError | IllegalStateException e) {
            g.setLastTestOk(false);
            g.setLastTestMessage(limit(e.getMessage(), 300));
        }
        g.setLastTestedAt(LocalDateTime.now());
        return view(gateways.save(g));
    }

    // ═══ Resident: pay a due ═══════════════════════════════════════════════

    /** Providers a resident of this hostel can pay with (a hostel-specific gateway beats the owner-wide one). */
    public List<Map<String, Object>> availableFor(Long hostelId) {
        List<Map<String, Object>> out = new ArrayList<>();
        Hostel h = hostelId == null ? null : hostelRepo.findById(hostelId).orElse(null);
        if (h == null || !h.isOnlinePaymentEnabled()) return out;
        for (OwnerPaymentGateway g : resolve(h).values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("provider", g.getProvider());
            m.put("name", NAMES.get(g.getProvider()));
            m.put("testMode", !g.isLive());
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> initiate(Long authUserId, Long dueId, String provider) {
        PaymentService.OnlineDue od = paymentService.onlineDue(authUserId, dueId);
        Hostel h = hostelRepo.findById(od.due().getHostelId())
                .orElseThrow(() -> new AppException("Hostel not found", HttpStatus.NOT_FOUND));
        if (!h.isOnlinePaymentEnabled()) throw new AppException("This hostel does not take online payments. Please pay at the reception.", HttpStatus.BAD_REQUEST);
        Map<String, OwnerPaymentGateway> available = resolve(h);
        String p = provider == null || provider.isBlank()
                ? available.keySet().stream().findFirst().orElse(null) : provider.toUpperCase();
        OwnerPaymentGateway g = p == null ? null : available.get(p);
        if (g == null) {
            throw new AppException("Online payment is not set up for this hostel yet. Please pay by UPI or at the reception.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        GatewayOrder o = new GatewayOrder();
        o.setGatewayId(g.getId());
        o.setProvider(g.getProvider());
        o.setHostelId(h.getId());
        o.setHostellerId(od.profile().getId());
        o.setDueId(dueId);
        o.setAmountPaise(od.outstanding().movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact());
        o = orders.save(o);

        String title = ReceiptPdfService.typeLabel(od.due().getDueType()) + " " + od.due().getPeriodMonth() + "/" + od.due().getPeriodYear();
        String payer = (od.profile().getFirstName() + " " + Objects.toString(od.profile().getLastName(), "")).trim();
        String base = frontendUrl.replaceAll("/+$", "") + "/app/dues?pay=" + o.getId();
        GatewayClients.Created created;
        try {
            created = clients.create(g, box.decrypt(g.getSecretEnc()), o,
                    new GatewayClients.CheckoutContext(title + " - " + h.getName(), h.getName(), payer, od.email(), base, base + "&cancelled=1"));
        } catch (GatewayClients.GatewayError | IllegalStateException e) {
            o.setStatus("FAILED");
            o.setFailureReason(limit(e.getMessage(), 300));
            orders.save(o);
            log.warn("Gateway {} (owner {}) could not create order {}: {}", g.getId(), g.getOwnerId(), o.getId(), e.getMessage());
            throw new AppException(NAMES.get(g.getProvider()) + " is not available right now. Please try another way to pay or pay at the reception.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        o.setProviderOrderId(created.providerOrderId());
        orders.save(o);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderRef", o.getId());
        m.put("provider", g.getProvider());
        m.put("amountPaise", o.getAmountPaise());
        m.put("currency", o.getCurrency());
        m.put("description", title);
        m.put("businessName", h.getName());
        m.put("payerName", payer);
        m.put("payerEmail", od.email());
        if (created.redirectUrl() != null) m.put("redirectUrl", created.redirectUrl());
        if (OwnerPaymentGateway.RAZORPAY.equals(g.getProvider())) {
            m.put("keyId", g.getPublicKey());
            m.put("orderId", created.providerOrderId());
        }
        return m;
    }

    /** Resident came back from checkout: confirm with the provider and record the payment once. */
    public Map<String, Object> verify(Long hostellerProfileId, Long orderRef, Map<String, String> callback) {
        GatewayOrder o = orders.findById(orderRef).orElseThrow(() -> new AppException("Payment not found", HttpStatus.NOT_FOUND));
        if (!Objects.equals(o.getHostellerId(), hostellerProfileId)) throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        if (!"PAID".equals(o.getStatus())) {
            OwnerPaymentGateway g = gateways.findById(o.getGatewayId())
                    .orElseThrow(() -> new AppException("This hostel's payment gateway was removed. Contact the hostel.", HttpStatus.CONFLICT));
            GatewayClients.Status s;
            try {
                s = clients.status(g, box.decrypt(g.getSecretEnc()), o, callback);
            } catch (GatewayClients.GatewayError e) {
                throw new AppException(e.getMessage(), HttpStatus.BAD_GATEWAY);
            }
            if (!s.paid()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("status", s.failure() != null ? "FAILED" : "PENDING");
                m.put("message", s.failure() != null ? s.failure() : "The payment is not complete yet. If money was deducted it will show here within a few minutes.");
                return m;
            }
            o = finish(o.getId(), s);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "PAID");
        m.put("amount", BigDecimal.valueOf(o.getAmountPaise()).movePointLeft(2));
        m.put("paymentId", o.getProviderPaymentId());
        m.put("method", o.getProvider());
        m.put("recordId", o.getPaymentRecordId());
        m.put("dueId", o.getDueId());
        m.put("paymentDate", o.getPaidAt() != null ? o.getPaidAt().toLocalDate() : null);
        return m;
    }

    /** Marks the order paid and records the payment exactly once (row lock guards against callback + webhook races). */
    private GatewayOrder finish(Long orderId, GatewayClients.Status s) {
        return tx.execute(st -> {
            GatewayOrder o = orders.lockById(orderId).orElseThrow();
            if ("PAID".equals(o.getStatus())) return o;
            if (s.amountPaise() != o.getAmountPaise()) {
                log.error("Gateway order {} paid {} paise but expected {} — not recorded automatically", o.getId(), s.amountPaise(), o.getAmountPaise());
                throw new AppException("The amount paid does not match. The hostel will check and record it.", HttpStatus.CONFLICT);
            }
            PaymentRecord rec = paymentService.recordOnlinePayment(o.getHostellerId(), o.getDueId(),
                    BigDecimal.valueOf(o.getAmountPaise()).movePointLeft(2), o.getProvider(), s.paymentId(), o.getProviderOrderId());
            o.setStatus("PAID");
            o.setProviderPaymentId(s.paymentId());
            o.setPaymentRecordId(rec.getId());
            o.setPaidAt(LocalDateTime.now());
            o.setFailureReason(null);
            return orders.save(o);
        });
    }

    // ═══ Webhooks (optional; the reconcile job below covers owners who skip them) ═══

    public void webhook(String token, String body, Map<String, String> headers) {
        OwnerPaymentGateway g = gateways.findByWebhookToken(token).orElse(null);
        if (g == null) throw new AppException("Unknown webhook", HttpStatus.NOT_FOUND);
        if (g.getWebhookSecretEnc() == null) throw new AppException("Webhook secret not configured", HttpStatus.BAD_REQUEST);
        String secret = box.decrypt(g.getWebhookSecretEnc());
        JSONObject j;
        String providerOrderId = null;
        switch (g.getProvider()) {
            case OwnerPaymentGateway.RAZORPAY -> {
                if (!clients.razorpayWebhookValid(secret, body, headers.get("x-razorpay-signature"))) throw new AppException("Invalid signature", HttpStatus.BAD_REQUEST);
                j = new JSONObject(body);
                String ev = j.optString("event");
                if (!"payment.captured".equals(ev) && !"order.paid".equals(ev)) return;
                providerOrderId = j.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity").optString("order_id", null);
            }
            case OwnerPaymentGateway.STRIPE -> {
                if (!clients.stripeWebhookValid(secret, body, headers.get("stripe-signature"))) throw new AppException("Invalid signature", HttpStatus.BAD_REQUEST);
                j = new JSONObject(body);
                String ev = j.optString("type");
                if (!"checkout.session.completed".equals(ev) && !"checkout.session.async_payment_succeeded".equals(ev)) return;
                providerOrderId = j.getJSONObject("data").getJSONObject("object").optString("id", null);
            }
            default -> { return; } // PayPal: payments are captured when the resident returns, or by the reconcile job
        }
        if (providerOrderId == null) return;
        GatewayOrder o = orders.findFirstByGatewayIdAndProviderOrderId(g.getId(), providerOrderId).orElse(null);
        if (o == null || "PAID".equals(o.getStatus())) return;
        // Never trust the webhook body for the amount: ask the provider (also captures if needed)
        GatewayClients.Status s = clients.status(g, box.decrypt(g.getSecretEnc()), o, null);
        if (s.paid()) finish(o.getId(), s);
    }

    /** Every 10 minutes: settle checkouts from the last 24 h whose resident closed the page before returning. */
    @Scheduled(cron = "0 */10 * * * *")
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        for (GatewayOrder o : orders.findByStatusAndCreatedAtBetween("CREATED", now.minusHours(24), now.minusMinutes(3))) {
            if (o.getProviderOrderId() == null) continue;
            try {
                OwnerPaymentGateway g = gateways.findById(o.getGatewayId()).orElse(null);
                if (g == null) continue;
                GatewayClients.Status s = clients.status(g, box.decrypt(g.getSecretEnc()), o, null);
                if (s.paid()) {
                    finish(o.getId(), s);
                    log.info("[GATEWAY] Reconciled order {} ({}) as paid", o.getId(), o.getProvider());
                }
            } catch (Exception e) {
                log.debug("[GATEWAY] Reconcile of order {} skipped: {}", o.getId(), e.getMessage());
            }
        }
        for (GatewayOrder o : orders.findByStatusAndCreatedAtBefore("CREATED", now.minusHours(24))) {
            o.setStatus("EXPIRED");
            orders.save(o);
        }
    }

    // ═══ helpers ═══════════════════════════════════════════════════════════

    /** Enabled gateways usable for this hostel, per provider (hostel-specific first, then the owner-wide one). */
    private Map<String, OwnerPaymentGateway> resolve(Hostel h) {
        Map<String, OwnerPaymentGateway> out = new LinkedHashMap<>();
        if (h.getOwnerId() == null) return out;
        List<OwnerPaymentGateway> all = gateways.findByOwnerIdOrderByIdAsc(h.getOwnerId());
        for (String p : PROVIDERS) {
            all.stream().filter(g -> g.isEnabled() && p.equals(g.getProvider()) && h.getId().equals(g.getHostelId())).findFirst()
                    .or(() -> all.stream().filter(g -> g.isEnabled() && p.equals(g.getProvider()) && g.getHostelId() == null).findFirst())
                    .ifPresent(g -> out.put(p, g));
        }
        return out;
    }

    private OwnerPaymentGateway owned(Long ownerId, Long id) {
        OwnerPaymentGateway g = gateways.findById(id).orElseThrow(() -> new AppException("Gateway not found", HttpStatus.NOT_FOUND));
        if (!Objects.equals(g.getOwnerId(), ownerId)) throw new AppException("Gateway not found", HttpStatus.NOT_FOUND);
        return g;
    }

    private void requireOwnHostel(Long ownerId, Long hostelId) {
        Hostel h = hostelRepo.findById(hostelId).orElseThrow(() -> new AppException("Hostel not found", HttpStatus.NOT_FOUND));
        if (!Objects.equals(h.getOwnerId(), ownerId)) throw new AppException("That hostel is not yours", HttpStatus.FORBIDDEN);
    }

    private Map<String, Object> view(OwnerPaymentGateway g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("provider", g.getProvider());
        m.put("providerName", NAMES.get(g.getProvider()));
        m.put("hostelId", g.getHostelId());
        m.put("hostelName", g.getHostelId() == null ? null : hostelRepo.findById(g.getHostelId()).map(Hostel::getName).orElse(null));
        m.put("mode", g.getMode());
        m.put("label", g.getLabel());
        m.put("publicKey", g.getPublicKey());
        String secretMask;
        try { secretMask = SecretBox.mask(box.decrypt(g.getSecretEnc())); } catch (Exception e) { secretMask = "(re-enter)"; }
        m.put("secretMask", secretMask);
        m.put("hasWebhookSecret", g.getWebhookSecretEnc() != null);
        m.put("webhookPath", "/api/payment/gateway/webhook/" + g.getWebhookToken());
        m.put("enabled", g.isEnabled());
        m.put("lastTestedAt", g.getLastTestedAt());
        m.put("lastTestOk", g.getLastTestOk());
        m.put("lastTestMessage", g.getLastTestMessage());
        m.put("updatedAt", g.getUpdatedAt());
        return m;
    }

    private String token() {
        byte[] b = new byte[18];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String limit(String s, int n) {
        return s == null ? null : s.length() > n ? s.substring(0, n) : s;
    }

    private static AppException bad(String msg) {
        return new AppException(msg, HttpStatus.BAD_REQUEST);
    }
}
