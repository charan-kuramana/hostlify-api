package com.hostlify.module.platform;

import com.hostlify.module.admin.entity.Subscription;
import com.hostlify.module.admin.repository.SubscriptionRepository;
import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.entity.HostelOwner;
import com.hostlify.module.hostel.repository.BedRepository;
import com.hostlify.module.hostel.repository.HostelOwnerRepository;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.shared.util.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Plans: every owner gets the free tier (up to N beds). Above it, owners pay per bed per month (self-serve via
 * Razorpay, or recorded by the super admin). Plans assigned by the super admin (Starter/Professional/…) keep working
 * until they expire. Limits are enforced when beds are added; existing beds are never removed.
 */
@Service
public class PlanService {

    public static final String PAY_PER_BED = "PRO";
    public static final List<Integer> MONTH_OPTIONS = List.of(1, 3, 6, 12);
    private static final long RAZORPAY_TIMEOUT_SECONDS = 10;

    private final PlatformRepos.Settings settingsRepo;
    private final PlatformRepos.Payments paymentRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final HostelOwnerRepository ownerRepo;
    private final HostelRepository hostelRepo;
    private final BedRepository bedRepo;

    @Value("${app.razorpay.key-id:test_key}")
    private String razorpayKeyId;

    @Value("${app.razorpay.key-secret:test_secret}")
    private String razorpayKeySecret;

    public PlanService(PlatformRepos.Settings settingsRepo, PlatformRepos.Payments paymentRepo, SubscriptionRepository subscriptionRepo,
                       HostelOwnerRepository ownerRepo, HostelRepository hostelRepo, BedRepository bedRepo) {
        this.settingsRepo = settingsRepo;
        this.paymentRepo = paymentRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.ownerRepo = ownerRepo;
        this.hostelRepo = hostelRepo;
        this.bedRepo = bedRepo;
    }

    // ═══ Settings (super admin) ════════════════════════════════════════════

    public PlatformSettings settings() {
        return settingsRepo.findById(1).orElseGet(PlatformSettings::new);
    }

    @Transactional
    public PlatformSettings saveSettings(Map<String, Object> body) {
        PlatformSettings s = settings();
        if (body.get("freeBedLimit") instanceof Number n) {
            if (n.intValue() < 0 || n.intValue() > 10000) throw bad("Free bed limit must be 0 to 10000");
            s.setFreeBedLimit(n.intValue());
        }
        if (body.get("pricePerBed") != null) {
            BigDecimal p = new BigDecimal(String.valueOf(body.get("pricePerBed")));
            if (p.signum() < 0 || p.compareTo(new BigDecimal("10000")) > 0) throw bad("Price per bed must be 0 to 10000");
            s.setPricePerBed(p.setScale(2, RoundingMode.HALF_UP));
        }
        if (body.get("graceDays") instanceof Number n) {
            if (n.intValue() < 0 || n.intValue() > 60) throw bad("Grace period must be 0 to 60 days");
            s.setGraceDays(n.intValue());
        }
        if (body.containsKey("premiumBadge")) s.setPremiumBadge(Boolean.TRUE.equals(body.get("premiumBadge")));
        s.setUpdatedAt(LocalDateTime.now());
        return settingsRepo.save(s);
    }

    // ═══ Limits & usage ════════════════════════════════════════════════════

    /** Plan, limits, usage and expiry for an owner. */
    public Map<String, Object> usage(Long ownerId) {
        PlatformSettings s = settings();
        HostelOwner owner = owner(ownerId);
        Subscription sub = subscriptionRepo.findByOwnerId(ownerId).orElse(null);
        boolean paid = paidActive(owner, sub, s);
        boolean inGrace = paid && owner.getPlanExpiresAt().isBefore(LocalDateTime.now());
        List<Hostel> hostels = hostelRepo.findByOwnerId(ownerId);
        long usedBeds = hostels.stream().mapToLong(h -> bedRepo.countByHostelId(h.getId())).sum();
        int maxBeds = maxBeds(owner, sub, s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ownerId", ownerId);
        m.put("plan", paid ? sub.getPlanName() : "FREE");
        m.put("planLabel", paid ? label(sub.getPlanName()) : "Free");
        m.put("paidActive", paid);
        m.put("inGrace", inGrace);
        m.put("expiresAt", paid ? owner.getPlanExpiresAt() : null);
        m.put("graceEndsAt", paid ? owner.getPlanExpiresAt().plusDays(s.getGraceDays()) : null);
        m.put("maxBeds", maxBeds);
        m.put("usedBeds", usedBeds);
        m.put("bedsLeft", Math.max(0, maxBeds - usedBeds));
        m.put("hostels", hostels.size());
        m.put("freeBedLimit", s.getFreeBedLimit());
        m.put("pricePerBed", s.getPricePerBed());
        m.put("monthOptions", MONTH_OPTIONS);
        m.put("payPerBedPlan", paid && PAY_PER_BED.equals(sub.getPlanName()));
        m.put("onlinePaymentReady", onlinePaymentReady());
        m.put("premiumBadge", s.isPremiumBadge() && paid);
        return m;
    }

    /** Throws a clear upgrade message when adding beds would go over the owner's plan. Super admin is exempt. */
    public void requireBedCapacity(Long hostelId, int adding) {
        if (adding <= 0) return;
        try {
            if (HostelAccess.principal().isSuperAdmin()) return;
        } catch (AppException notAuthenticated) {
            return; // system jobs / seeding
        }
        Hostel h = hostelRepo.findById(hostelId).orElse(null);
        if (h == null) return;
        PlatformSettings s = settings();
        HostelOwner owner = ownerRepo.findById(h.getOwnerId()).orElse(null);
        if (owner == null) return;
        Subscription sub = subscriptionRepo.findByOwnerId(owner.getId()).orElse(null);
        int max = maxBeds(owner, sub, s);
        long used = hostelRepo.findByOwnerId(owner.getId()).stream().mapToLong(x -> bedRepo.countByHostelId(x.getId())).sum();
        if (used + adding > max) {
            throw new AppException("Your " + (paidActive(owner, sub, s) ? label(sub.getPlanName()) : "Free") + " plan allows "
                    + max + " beds and you have " + used + ". Upgrade under Plan & Billing to add more beds.",
                    HttpStatus.PAYMENT_REQUIRED);
        }
    }

    /** Owner has an active paid plan → premium badge and ranking boost on the public pages. */
    public boolean isPremium(Long ownerId) {
        PlatformSettings s = settings();
        if (!s.isPremiumBadge() || ownerId == null) return false;
        HostelOwner owner = ownerRepo.findById(ownerId).orElse(null);
        return owner != null && paidActive(owner, subscriptionRepo.findByOwnerId(ownerId).orElse(null), s);
    }

    // ═══ Self-serve upgrade ════════════════════════════════════════════════

    /** Price for `beds` beds for `months` months, with credit for the unused part of a current pay-per-bed plan. */
    public Map<String, Object> quote(Long ownerId, int beds, int months) {
        PlatformSettings s = settings();
        HostelOwner owner = owner(ownerId);
        Subscription sub = subscriptionRepo.findByOwnerId(ownerId).orElse(null);
        if (!MONTH_OPTIONS.contains(months)) throw bad("Choose 1, 3, 6 or 12 months");
        long used = hostelRepo.findByOwnerId(ownerId).stream().mapToLong(h -> bedRepo.countByHostelId(h.getId())).sum();
        if (beds <= s.getFreeBedLimit()) throw bad("The first " + s.getFreeBedLimit() + " beds are free. Choose more beds than that.");
        if (beds < used) throw bad("You already have " + used + " beds. Choose at least that many.");
        if (beds > 5000) throw bad("For more than 5000 beds, please contact us");
        BigDecimal perMonth = s.getPricePerBed().multiply(BigDecimal.valueOf(beds - s.getFreeBedLimit()));
        BigDecimal gross = perMonth.multiply(BigDecimal.valueOf(months));

        LocalDateTime now = LocalDateTime.now();
        boolean currentPayPerBed = sub != null && PAY_PER_BED.equals(sub.getPlanName())
                && owner.getPlanExpiresAt() != null && owner.getPlanExpiresAt().isAfter(now);
        boolean extend = currentPayPerBed && sub.getMaxBeds() == beds;
        LocalDateTime from = extend ? owner.getPlanExpiresAt() : now;
        BigDecimal credit = BigDecimal.ZERO;
        if (currentPayPerBed && !extend) {
            long minutesLeft = Duration.between(now, owner.getPlanExpiresAt()).toMinutes();
            BigDecimal oldPerMonth = s.getPricePerBed().multiply(BigDecimal.valueOf(Math.max(0, sub.getMaxBeds() - s.getFreeBedLimit())));
            credit = oldPerMonth.multiply(BigDecimal.valueOf(minutesLeft)).divide(BigDecimal.valueOf(30L * 24 * 60), 2, RoundingMode.DOWN);
        }
        BigDecimal maxCredit = gross.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
        if (credit.compareTo(maxCredit) > 0) credit = maxCredit;
        BigDecimal amount = gross.subtract(credit).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> q = new LinkedHashMap<>();
        q.put("beds", beds);
        q.put("months", months);
        q.put("paidBeds", beds - s.getFreeBedLimit());
        q.put("pricePerBed", s.getPricePerBed());
        q.put("perMonth", perMonth);
        q.put("gross", gross);
        q.put("credit", credit);
        q.put("amount", amount);
        q.put("periodFrom", from);
        q.put("periodTo", from.plusMonths(months));
        q.put("extendsCurrent", extend);
        return q;
    }

    @Transactional
    public Map<String, Object> checkout(Long ownerId, int beds, int months, Long userId) {
        Map<String, Object> q = quote(ownerId, beds, months);
        BigDecimal amount = (BigDecimal) q.get("amount");
        long paise = amount.multiply(BigDecimal.valueOf(100)).longValue();
        String orderId;
        try {
            orderId = CompletableFuture.supplyAsync(() -> {
                try {
                    com.razorpay.RazorpayClient client = new com.razorpay.RazorpayClient(razorpayKeyId, razorpayKeySecret);
                    org.json.JSONObject params = new org.json.JSONObject();
                    params.put("amount", paise);
                    params.put("currency", "INR");
                    params.put("receipt", "PLAN-" + ownerId + "-" + System.currentTimeMillis());
                    params.put("payment_capture", 1);
                    return client.orders.create(params).<String>get("id");
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }).get(RAZORPAY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw paymentsUnavailable();
        } catch (Exception e) {
            throw paymentsUnavailable();
        }
        PlatformPayment p = new PlatformPayment();
        p.setOwnerId(ownerId);
        p.setBeds(beds);
        p.setMonths(months);
        p.setAmount(amount);
        p.setCredit((BigDecimal) q.get("credit"));
        p.setMethod("RAZORPAY");
        p.setRazorpayOrderId(orderId);
        p.setCreatedBy(userId);
        paymentRepo.save(p);
        Map<String, Object> out = new LinkedHashMap<>(q);
        out.put("keyId", razorpayKeyId);
        out.put("orderId", orderId);
        out.put("amountPaise", paise);
        out.put("currency", "INR");
        return out;
    }

    @Transactional
    public Map<String, Object> verify(Long ownerId, String orderId, String paymentId, String signature) {
        PlatformPayment p = paymentRepo.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new AppException("Payment order not found", HttpStatus.NOT_FOUND));
        if (!Objects.equals(p.getOwnerId(), ownerId)) throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        if ("PAID".equals(p.getStatus())) return usage(ownerId);
        if (paymentId == null || signature == null || !HmacUtils.verifyHmac(razorpayKeySecret, orderId + "|" + paymentId, signature)) {
            p.setStatus("FAILED");
            paymentRepo.save(p);
            throw bad("Payment could not be verified. If money was deducted, contact support with payment id " + paymentId);
        }
        p.setRazorpayPaymentId(paymentId);
        apply(p);
        return usage(ownerId);
    }

    /** Super admin: record a payment received outside the app (bank transfer, UPI) and apply the plan. */
    @Transactional
    public PlatformPayment recordManual(Long ownerId, int beds, int months, BigDecimal amount, String reference, Long adminUserId) {
        owner(ownerId);
        PlatformSettings s = settings();
        if (!MONTH_OPTIONS.contains(months)) throw bad("Choose 1, 3, 6 or 12 months");
        if (beds <= s.getFreeBedLimit() || beds > 5000) throw bad("Beds must be more than the free " + s.getFreeBedLimit() + " (up to 5000)");
        if (amount == null || amount.signum() < 0) throw bad("Amount must be 0 or more");
        Map<String, Object> q = quote(ownerId, Math.max(beds, s.getFreeBedLimit() + 1), months);
        PlatformPayment p = new PlatformPayment();
        p.setOwnerId(ownerId);
        p.setBeds(beds);
        p.setMonths(months);
        p.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        p.setCredit((BigDecimal) q.get("credit"));
        p.setMethod("MANUAL");
        p.setReference(reference == null || reference.isBlank() ? null : reference.trim().substring(0, Math.min(100, reference.trim().length())));
        p.setCreatedBy(adminUserId);
        paymentRepo.save(p);
        apply(p);
        return p;
    }

    public List<PlatformPayment> payments(Long ownerId) {
        return paymentRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().filter(p -> !"CREATED".equals(p.getStatus())).toList();
    }

    public List<PlatformPayment> recentPaid() {
        List<PlatformPayment> list = paymentRepo.findTop100ByStatusOrderByCreatedAtDesc("PAID");
        list.forEach(p -> ownerRepo.findById(p.getOwnerId()).ifPresent(o -> p.setOwnerName(o.getBusinessName() != null ? o.getBusinessName() : o.getOwnerName())));
        return list;
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private void apply(PlatformPayment p) {
        HostelOwner owner = owner(p.getOwnerId());
        Subscription sub = subscriptionRepo.findByOwnerId(p.getOwnerId()).orElseGet(Subscription::new);
        LocalDateTime now = LocalDateTime.now();
        boolean extend = PAY_PER_BED.equals(sub.getPlanName()) && sub.getMaxBeds() == p.getBeds()
                && owner.getPlanExpiresAt() != null && owner.getPlanExpiresAt().isAfter(now);
        LocalDateTime from = extend ? owner.getPlanExpiresAt() : now;
        PlatformSettings s = settings();
        sub.setOwnerId(p.getOwnerId());
        sub.setPlanName(PAY_PER_BED);
        sub.setMaxBeds(p.getBeds());
        sub.setMaxHostels(Math.max(sub.getMaxHostels(), 10));
        sub.setPriceMonthly(s.getPricePerBed().multiply(BigDecimal.valueOf(p.getBeds() - s.getFreeBedLimit())));
        subscriptionRepo.save(sub);
        owner.setSubscriptionPlan(PAY_PER_BED);
        owner.setPlanExpiresAt(from.plusMonths(p.getMonths()));
        ownerRepo.save(owner);
        p.setStatus("PAID");
        p.setPaidAt(now);
        p.setPeriodFrom(from);
        p.setPeriodTo(owner.getPlanExpiresAt());
        paymentRepo.save(p);
    }

    private static boolean paidActive(HostelOwner owner, Subscription sub, PlatformSettings s) {
        return sub != null && sub.getPlanName() != null && !"FREE".equals(sub.getPlanName())
                && owner.getPlanExpiresAt() != null && owner.getPlanExpiresAt().plusDays(s.getGraceDays()).isAfter(LocalDateTime.now());
    }

    private static int maxBeds(HostelOwner owner, Subscription sub, PlatformSettings s) {
        return paidActive(owner, sub, s) ? Math.max(sub.getMaxBeds(), s.getFreeBedLimit()) : s.getFreeBedLimit();
    }

    private boolean onlinePaymentReady() {
        return razorpayKeyId != null && razorpayKeyId.startsWith("rzp_");
    }

    private HostelOwner owner(Long ownerId) {
        if (ownerId == null) throw new AppException("Owner not found", HttpStatus.NOT_FOUND);
        return ownerRepo.findById(ownerId).orElseThrow(() -> new AppException("Owner not found", HttpStatus.NOT_FOUND));
    }

    private static String label(String plan) {
        if (plan == null) return "Free";
        return PAY_PER_BED.equals(plan) ? "Pay per bed" : plan.charAt(0) + plan.substring(1).toLowerCase();
    }

    private static AppException paymentsUnavailable() {
        return new AppException("Online payment isn't available right now. You can pay by bank transfer or UPI and ask "
                + "Hostlify support to record it, or try again later.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static AppException bad(String msg) {
        return new AppException(msg, HttpStatus.BAD_REQUEST);
    }
}
