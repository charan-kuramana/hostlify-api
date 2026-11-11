package com.hostlify.module.admin.service;

import com.hostlify.module.admin.entity.AuditLog;
import com.hostlify.module.admin.entity.FeatureFlag;
import com.hostlify.module.admin.entity.Subscription;
import com.hostlify.module.admin.repository.AuditLogRepository;
import com.hostlify.module.admin.repository.FeatureFlagRepository;
import com.hostlify.module.admin.repository.SubscriptionRepository;
import com.hostlify.module.auth.entity.AuthUser;
import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hostel.entity.Bed;
import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.entity.HostelOwner;
import com.hostlify.module.hostel.entity.StaffAssignment;
import com.hostlify.module.hostel.repository.BedRepository;
import com.hostlify.module.hostel.repository.HostelOwnerRepository;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hostel.repository.StaffAssignmentRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.payment.entity.PaymentRecord;
import com.hostlify.module.complaint.entity.ComplaintRecord;
import com.hostlify.module.complaint.repository.ComplaintRecordRepository;
import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.repository.PaymentDueRepository;
import com.hostlify.module.payment.repository.PaymentRecordRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SuperAdminService {

    /** Feature flags the platform knows about (same names the seeder uses). */
    public static final List<String> KNOWN_FLAGS = List.of(
            "online_payment", "referral_program", "parking_management",
            "cleaning_requests", "weekly_menu", "hostel_reviews");

    /** Plans offered on the platform with their limits (maxHostels, maxBeds, price/month). */
    private static final Map<String, Object[]> PLAN_LIMITS = Map.of(
            "FREE", new Object[]{1, 30, BigDecimal.ZERO},
            "STARTER", new Object[]{1, 75, new BigDecimal("999.00")},
            "PROFESSIONAL", new Object[]{3, 200, new BigDecimal("2999.00")},
            "ENTERPRISE", new Object[]{10, 1000, new BigDecimal("7999.00")});

    private final AuditLogRepository auditRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final FeatureFlagRepository featureFlagRepo;
    private final HostelOwnerRepository ownerRepo;
    private final AuthUserRepository authUserRepo;
    private final HostelRepository hostelRepo;
    private final BedRepository bedRepo;
    private final BookingCheckinRepository bookingRepo;
    private final HostellerProfileRepository hostellerRepo;
    private final PaymentRecordRepository paymentRecordRepo;
    private final PaymentDueRepository paymentDueRepo;
    private final ComplaintRecordRepository complaintRepo;
    private final StaffAssignmentRepository staffAssignmentRepo;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminService(AuditLogRepository auditRepo,
                              SubscriptionRepository subscriptionRepo,
                              FeatureFlagRepository featureFlagRepo,
                              HostelOwnerRepository ownerRepo,
                              AuthUserRepository authUserRepo,
                              HostelRepository hostelRepo,
                              BedRepository bedRepo,
                              BookingCheckinRepository bookingRepo,
                              HostellerProfileRepository hostellerRepo,
                              PaymentRecordRepository paymentRecordRepo,
                              PaymentDueRepository paymentDueRepo,
                              ComplaintRecordRepository complaintRepo,
                              StaffAssignmentRepository staffAssignmentRepo,
                              PasswordEncoder passwordEncoder) {
        this.auditRepo = auditRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.featureFlagRepo = featureFlagRepo;
        this.ownerRepo = ownerRepo;
        this.authUserRepo = authUserRepo;
        this.hostelRepo = hostelRepo;
        this.bedRepo = bedRepo;
        this.bookingRepo = bookingRepo;
        this.hostellerRepo = hostellerRepo;
        this.paymentRecordRepo = paymentRecordRepo;
        this.paymentDueRepo = paymentDueRepo;
        this.complaintRepo = complaintRepo;
        this.staffAssignmentRepo = staffAssignmentRepo;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Audit Logs ───────────────────────────────────────────────────────────

    public void logAction(Long actorUserId, String actorRole, String action,
                           String entityType, Long entityId, Long hostelId,
                           String beforeState, String afterState,
                           String ipAddress, String userAgent) {
        AuditLog log = new AuditLog();
        log.setActorUserId(actorUserId);
        log.setActorRole(actorRole);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setHostelId(hostelId);
        log.setBeforeState(beforeState);
        log.setAfterState(afterState);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        auditRepo.save(log);
    }

    /** Records a super-admin action performed on an owner account. */
    private void logOwnerAction(Long actorUserId, String action, Long ownerId, String before, String after) {
        if (actorUserId == null) return;
        logAction(actorUserId, "SUPER_ADMIN", action, "OWNER", ownerId, null,
                before, after, null, null);
    }

    private static String json(String key, Object value) {
        return "{\"" + key + "\":" + (value == null ? "null" : "\"" + value.toString().replace("\"", "'") + "\"") + "}";
    }

    public Page<AuditLog> getAuditLogsByActor(Long actorUserId, Pageable pageable) {
        return auditRepo.findByActorUserIdOrderByCreatedAtDesc(actorUserId, pageable);
    }

    public Page<AuditLog> getAuditLogsByHostel(Long hostelId, Pageable pageable) {
        return auditRepo.findByHostelIdOrderByCreatedAtDesc(hostelId, pageable);
    }

    public Page<AuditLog> getAuditLogsByOwner(Long ownerId, Pageable pageable) {
        return auditRepo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("OWNER", ownerId, pageable);
    }

    // ── Subscriptions ────────────────────────────────────────────────────────

    public Subscription getSubscription(Long ownerId) {
        return subscriptionRepo.findByOwnerId(ownerId)
                .orElseThrow(() -> new AppException("No subscription found for this owner", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Subscription upsertSubscription(Long ownerId, String planName,
                                            int maxHostels, int maxBeds,
                                            BigDecimal priceMonthly) {
        return upsertSubscription(ownerId, planName, maxHostels, maxBeds, priceMonthly, null);
    }

    @Transactional
    public Subscription upsertSubscription(Long ownerId, String planName,
                                            Integer maxHostels, Integer maxBeds,
                                            BigDecimal priceMonthly, Long actorUserId) {
        HostelOwner owner = getOwnerById(ownerId);
        String plan = planName == null ? null : planName.trim().toUpperCase();
        if (plan == null || !PLAN_LIMITS.containsKey(plan)) {
            throw new AppException("planName must be one of " + PLAN_LIMITS.keySet(), HttpStatus.BAD_REQUEST);
        }
        Object[] defaults = PLAN_LIMITS.get(plan);
        Subscription sub = subscriptionRepo.findByOwnerId(ownerId)
                .orElse(new Subscription());
        String previous = sub.getPlanName();
        sub.setOwnerId(ownerId);
        sub.setPlanName(plan);
        sub.setMaxHostels(maxHostels != null ? maxHostels : (Integer) defaults[0]);
        sub.setMaxBeds(maxBeds != null ? maxBeds : (Integer) defaults[1]);
        sub.setPriceMonthly(priceMonthly != null ? priceMonthly : (BigDecimal) defaults[2]);
        Subscription saved = subscriptionRepo.save(sub);

        // Keep the owner's plan (shown in owner lists/reports) in sync with the subscription
        owner.setSubscriptionPlan(plan);
        if (owner.getPlanExpiresAt() == null || owner.getPlanExpiresAt().isBefore(LocalDateTime.now())) {
            owner.setPlanExpiresAt(LocalDateTime.now().plusYears(1));
        }
        ownerRepo.save(owner);
        logOwnerAction(actorUserId, "CHANGE_PLAN", ownerId, json("plan", previous), json("plan", plan));
        return saved;
    }

    // ── Feature Flags ────────────────────────────────────────────────────────

    public List<FeatureFlag> getEnabledFlags(Long ownerId) {
        return featureFlagRepo.findByOwnerIdAndIsEnabledTrue(ownerId);
    }

    @Transactional
    public FeatureFlag setFlag(Long ownerId, String flagName, boolean enabled,
                                Long setBy, String reason) {
        if (flagName == null || flagName.isBlank()) {
            throw new AppException("flagName is required", HttpStatus.BAD_REQUEST);
        }
        getOwnerById(ownerId);
        FeatureFlag flag = featureFlagRepo.findByOwnerIdAndFlagName(ownerId, flagName)
                .orElse(new FeatureFlag());
        boolean before = flag.getId() != null && flag.isEnabled();
        flag.setOwnerId(ownerId);
        flag.setFlagName(flagName);
        flag.setEnabled(enabled);
        flag.setSetBy(setBy);
        flag.setReason(reason);
        FeatureFlag saved = featureFlagRepo.save(flag);
        logOwnerAction(setBy, "SET_FLAG", ownerId, json(flagName, before), json(flagName, enabled));
        return saved;
    }

    public boolean isFlagEnabled(Long ownerId, String flagName) {
        return featureFlagRepo.findByOwnerIdAndFlagName(ownerId, flagName)
                .map(FeatureFlag::isEnabled)
                .orElse(false);
    }

    // ── Owner Management ─────────────────────────────────────────────────────

    public Page<HostelOwner> getOwners(Pageable pageable) {
        return ownerRepo.findAll(pageable);
    }

    /** Owners page with per-owner stats (hostels, active members, lifetime collections). */
    public Page<Map<String, Object>> getOwnerViews(Pageable pageable) {
        return ownerRepo.findAll(pageable).map(this::toOwnerView);
    }

    public Map<String, Object> getOwnerView(Long id) {
        return toOwnerView(getOwnerById(id));
    }

    private Map<String, Object> toOwnerView(HostelOwner o) {
        List<Long> hostelIds = hostelRepo.findByOwnerId(o.getId()).stream().map(Hostel::getId).toList();
        long members = hostelIds.stream()
                .mapToLong(id -> bookingRepo.findByHostelIdAndStatus(id, "ACTIVE").size())
                .sum();
        BigDecimal revenue = hostelIds.stream()
                .flatMap(id -> paymentRecordRepo.findByHostelIdOrderByCreatedAtDesc(id).stream())
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("authUserId", o.getAuthUserId());
        m.put("businessName", o.getBusinessName());
        m.put("ownerName", o.getOwnerName());
        m.put("email", o.getEmail());
        m.put("mobile", o.getMobile());
        m.put("status", o.getStatus());
        m.put("subscriptionPlan", o.getSubscriptionPlan());
        m.put("planExpiresAt", o.getPlanExpiresAt());
        m.put("createdAt", o.getCreatedAt());
        m.put("hostels", hostelIds.size());
        m.put("members", members);
        m.put("revenue", revenue);
        return m;
    }

    public HostelOwner getOwnerById(Long id) {
        return ownerRepo.findById(id)
                .orElseThrow(() -> new AppException("Owner not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public HostelOwner createOwner(String email, String password, String businessName,
                                    String ownerName, String mobile, String planName) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("businessName", businessName);
        body.put("ownerName", ownerName);
        body.put("mobile", mobile);
        body.put("planName", planName);
        return createOwner(body, null);
    }

    /**
     * Creates an owner login (HOSTEL_STAFF / OWNER), the owner record, subscription and feature flags.
     * When hostel details are supplied the owner's first hostel is created too and the owner is
     * assigned to it, so they land in a working staff portal on first login.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public HostelOwner createOwner(Map<String, Object> body, Long actorUserId) {
        String email = str(body.get("email"));
        String password = (String) body.get("password");
        String ownerName = str(body.get("ownerName"));
        String businessName = str(body.get("businessName"));
        String mobile = str(body.get("mobile"));
        String planName = str(body.get("planName"));
        String plan = planName == null ? "FREE" : planName.toUpperCase();

        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new AppException("A valid email is required", HttpStatus.BAD_REQUEST);
        }
        email = email.toLowerCase();
        if (password == null || password.length() < 8) {
            throw new AppException("Password must be at least 8 characters", HttpStatus.BAD_REQUEST);
        }
        if (ownerName == null) {
            throw new AppException("Owner name is required", HttpStatus.BAD_REQUEST);
        }
        if (mobile == null || !mobile.matches("^[6-9]\\d{9}$")) {
            throw new AppException("Enter a valid 10-digit mobile number", HttpStatus.BAD_REQUEST);
        }
        if (!PLAN_LIMITS.containsKey(plan)) {
            throw new AppException("planName must be one of " + PLAN_LIMITS.keySet(), HttpStatus.BAD_REQUEST);
        }
        String hostelName = str(body.get("hostelName"));
        if (businessName == null) businessName = hostelName != null ? hostelName : ownerName;
        if (authUserRepo.existsByEmail(email)) {
            throw new AppException("Email already registered", HttpStatus.CONFLICT);
        }
        String city = str(body.get("city"));
        String pincode = str(body.get("pincode"));
        if (hostelName != null) {
            if (city == null) throw new AppException("Hostel city is required", HttpStatus.BAD_REQUEST);
            if (pincode == null || !pincode.matches("^\\d{6}$")) {
                throw new AppException("Enter a valid 6-digit pincode", HttpStatus.BAD_REQUEST);
            }
        }

        AuthUser user = new AuthUser();
        user.setUuid(UUID.randomUUID().toString());
        user.setEmail(email);
        user.setMobile(mobile);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("HOSTEL_STAFF");
        user.setSubRole("OWNER");
        user.setStatus("ACTIVE");
        AuthUser savedUser = authUserRepo.save(user);

        HostelOwner owner = new HostelOwner();
        owner.setAuthUserId(savedUser.getId());
        owner.setBusinessName(businessName);
        owner.setOwnerName(ownerName);
        owner.setMobile(mobile);
        owner.setEmail(email);
        owner.setStatus("ACTIVE");
        owner.setSubscriptionPlan(plan);
        owner.setPlanExpiresAt(LocalDateTime.now().plusYears(1));
        HostelOwner savedOwner = ownerRepo.save(owner);

        Object[] limits = PLAN_LIMITS.get(plan);
        Subscription sub = new Subscription();
        sub.setOwnerId(savedOwner.getId());
        sub.setPlanName(plan);
        sub.setMaxHostels((Integer) limits[0]);
        sub.setMaxBeds((Integer) limits[1]);
        sub.setPriceMonthly((BigDecimal) limits[2]);
        subscriptionRepo.save(sub);

        // Feature flags: explicit map from the form, otherwise all known flags off
        Map<String, Object> features = body.get("features") instanceof Map<?, ?> f
                ? (Map<String, Object>) f : Map.of();
        Long setBy = actorUserId != null ? actorUserId : savedUser.getId();
        for (String flagName : KNOWN_FLAGS) {
            FeatureFlag ff = new FeatureFlag();
            ff.setOwnerId(savedOwner.getId());
            ff.setFlagName(flagName);
            ff.setEnabled(Boolean.parseBoolean(String.valueOf(features.getOrDefault(flagName, "false"))));
            ff.setSetBy(setBy);
            ff.setReason("Set at owner creation");
            featureFlagRepo.save(ff);
        }

        if (hostelName != null) {
            Hostel hostel = new Hostel();
            hostel.setOwnerId(savedOwner.getId());
            hostel.setName(hostelName);
            String type = str(body.get("hostelType"));
            hostel.setHostelType(type != null ? type.toUpperCase() : "COED");
            hostel.setCity(city);
            hostel.setPincode(pincode);
            hostel.setAddress(str(body.get("address")));
            hostel.setContactPhone(mobile);
            Hostel savedHostel = hostelRepo.save(hostel);

            StaffAssignment sa = new StaffAssignment();
            sa.setStaffUserId(savedUser.getId());
            sa.setHostelId(savedHostel.getId());
            sa.setOwnerId(savedOwner.getId());
            sa.setActive(true);
            sa.setAssignedAt(LocalDateTime.now());
            sa.setAssignedBy(setBy);
            staffAssignmentRepo.save(sa);
        }

        logOwnerAction(actorUserId, "CREATE_OWNER", savedOwner.getId(), null, json("email", email));
        return savedOwner;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    @Transactional
    public HostelOwner suspendOwner(Long id) {
        return setOwnerStatus(id, "SUSPENDED", null);
    }

    @Transactional
    public HostelOwner setOwnerStatus(Long id, String status, Long actorUserId) {
        HostelOwner owner = getOwnerById(id);
        String before = owner.getStatus();
        owner.setStatus(status);
        authUserRepo.findById(owner.getAuthUserId()).ifPresent(user -> {
            user.setStatus(status);
            if ("ACTIVE".equals(status)) {
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
            }
            authUserRepo.save(user);
        });
        HostelOwner saved = ownerRepo.save(owner);
        logOwnerAction(actorUserId, "SUSPENDED".equals(status) ? "SUSPEND_OWNER" : "ACTIVATE_OWNER",
                id, json("status", before), json("status", status));
        return saved;
    }

    @Transactional
    public void resetOwnerPassword(Long id, String newPassword) {
        resetOwnerPassword(id, newPassword, null);
    }

    @Transactional
    public void resetOwnerPassword(Long id, String newPassword, Long actorUserId) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new AppException("Password must be at least 8 characters", HttpStatus.BAD_REQUEST);
        }
        HostelOwner owner = getOwnerById(id);
        AuthUser user = authUserRepo.findById(owner.getAuthUserId())
                .orElseThrow(() -> new AppException("Auth user not found", HttpStatus.NOT_FOUND));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        authUserRepo.save(user);
        logOwnerAction(actorUserId, "RESET_PASSWORD", id, null, null);
    }

    // ── Super admin's own account ────────────────────────────────────────────

    public Map<String, Object> getAccount(Long userId) {
        AuthUser user = authUserRepo.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", user.getEmail());
        m.put("mobile", user.getMobile());
        m.put("role", user.getRole());
        m.put("status", user.getStatus());
        m.put("createdAt", user.getCreatedAt());
        m.put("lastLoginAt", user.getLastLoginAt());
        return m;
    }

    @Transactional
    public void changeOwnPassword(Long userId, String currentPassword, String newPassword) {
        AuthUser user = authUserRepo.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AppException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new AppException("New password must be at least 8 characters", HttpStatus.BAD_REQUEST);
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new AppException("New password must be different from the current password", HttpStatus.BAD_REQUEST);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        authUserRepo.save(user);
        logAction(userId, "SUPER_ADMIN", "CHANGE_OWN_PASSWORD", "AUTH_USER", userId, null, null, null, null, null);
    }

    // ── Dashboard & Reports ──────────────────────────────────────────────────

    /** Per-hostel occupancy rows: total beds, occupied (active bookings), owner, city. */
    private List<Map<String, Object>> hostelRows() {
        Map<Long, Long> bedsByHostel = bedRepo.findAll().stream()
                .filter(b -> b.getHostelId() != null)
                .collect(Collectors.groupingBy(Bed::getHostelId, Collectors.counting()));
        Map<Long, HostelOwner> owners = new HashMap<>();
        ownerRepo.findAll().forEach(o -> owners.put(o.getId(), o));
        return hostelRepo.findAll().stream()
                .sorted(java.util.Comparator.comparing(Hostel::getId))
                .map(h -> {
                    long total = bedsByHostel.getOrDefault(h.getId(), 0L);
                    long occupied = bookingRepo.findByHostelIdAndStatus(h.getId(), "ACTIVE").size();
                    HostelOwner owner = owners.get(h.getOwnerId());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("hostelId", h.getId());
                    row.put("hostelName", h.getName());
                    row.put("ownerId", h.getOwnerId());
                    row.put("ownerName", owner != null ? owner.getOwnerName() : null);
                    row.put("city", h.getCity());
                    row.put("totalBeds", total);
                    row.put("occupiedBeds", occupied);
                    row.put("occupancyRate", total > 0 ? Math.round(occupied * 1000.0 / total) / 10.0 : 0);
                    return row;
                })
                .toList();
    }

    public Map<String, Object> getDashboard() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOwners", ownerRepo.count());
        stats.put("totalHostels", hostelRepo.count());
        stats.put("totalBeds", bedRepo.count());
        stats.put("totalHostellers", hostellerRepo.count());
        long activeBookings = bookingRepo.findAll().stream()
                .filter(b -> "ACTIVE".equals(b.getStatus()))
                .count();
        stats.put("activeBookings", activeBookings);
        stats.put("totalResidents", activeBookings);

        // Current-month collections per hostel
        YearMonth now = YearMonth.now();
        Map<Long, BigDecimal> revenueByHostel = new HashMap<>();
        paymentRecordRepo.findAll().stream()
                .filter(r -> r.getPaymentDate() != null && YearMonth.from(r.getPaymentDate()).equals(now))
                .forEach(r -> revenueByHostel.merge(r.getHostelId(), r.getAmount(), BigDecimal::add));
        BigDecimal monthlyRevenue = revenueByHostel.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("monthlyRevenue", monthlyRevenue);

        stats.put("openComplaints", complaintRepo.findAll().stream()
                .filter(c -> "OPEN".equals(c.getStatus()))
                .count());

        List<Map<String, Object>> hostels = hostelRows().stream().map(r -> {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("id", r.get("hostelId"));
            h.put("name", r.get("hostelName"));
            h.put("city", r.get("city"));
            h.put("total", r.get("totalBeds"));
            h.put("occupied", r.get("occupiedBeds"));
            h.put("revenue", revenueByHostel.getOrDefault((Long) r.get("hostelId"), BigDecimal.ZERO));
            return h;
        }).toList();
        stats.put("hostels", hostels);
        return stats;
    }

    public Map<String, Object> getRevenueReport(int month, int year) {
        if (month < 1 || month > 12) {
            throw new AppException("month must be between 1 and 12", HttpStatus.BAD_REQUEST);
        }
        List<PaymentRecord> allRecords = paymentRecordRepo.findAll();
        List<PaymentRecord> records = allRecords.stream()
                .filter(r -> r.getPaymentDate() != null
                        && r.getPaymentDate().getMonthValue() == month
                        && r.getPaymentDate().getYear() == year)
                .toList();
        BigDecimal total = records.stream()
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> report = new HashMap<>();
        report.put("month", month);
        report.put("year", year);
        report.put("totalCollected", total);
        report.put("transactionCount", records.size());

        // Per-owner breakdown for the selected month (consumed by the Super Admin Revenue page)
        List<Map<String, Object>> ownerBreakdown = new java.util.ArrayList<>();
        for (HostelOwner owner : ownerRepo.findAll()) {
            List<Long> hostelIds = hostelRepo.findByOwnerId(owner.getId()).stream()
                    .map(Hostel::getId).toList();
            long members = hostelIds.stream()
                    .mapToLong(id -> hostellerRepo.findByHostelIdAndStatusOrderByCreatedAtDesc(id, "ACTIVE").size())
                    .sum();
            BigDecimal collected = records.stream()
                    .filter(r -> hostelIds.contains(r.getHostelId()))
                    .map(PaymentRecord::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal outstanding = hostelIds.stream()
                    .flatMap(id -> paymentDueRepo.findByHostelIdAndPeriodYearAndPeriodMonth(id, year, month).stream())
                    .filter(d -> !"PAID".equals(d.getStatus()))
                    .map(PaymentDue::getRentAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal billed = collected.add(outstanding);
            int rate = billed.signum() == 0 ? 0
                    : collected.multiply(BigDecimal.valueOf(100)).divide(billed, 0, java.math.RoundingMode.HALF_UP).intValue();
            Map<String, Object> row = new HashMap<>();
            row.put("ownerId", owner.getId());
            row.put("ownerName", owner.getBusinessName() + " (" + owner.getOwnerName() + ")");
            row.put("plan", owner.getSubscriptionPlan());
            row.put("hostelCount", hostelIds.size());
            row.put("memberCount", members);
            row.put("collectedAmount", collected);
            row.put("outstandingAmount", outstanding);
            row.put("collectionRate", rate);
            ownerBreakdown.add(row);
        }
        report.put("ownerBreakdown", ownerBreakdown);

        // Platform-wide collections for the 6 months ending at the selected month
        YearMonth selected = YearMonth.of(year, month);
        List<Map<String, Object>> monthlyTrend = new java.util.ArrayList<>();
        for (int back = 5; back >= 0; back--) {
            YearMonth ym = selected.minusMonths(back);
            BigDecimal amount = allRecords.stream()
                    .filter(r -> r.getPaymentDate() != null && YearMonth.from(r.getPaymentDate()).equals(ym))
                    .map(PaymentRecord::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new HashMap<>();
            m.put("month", ym.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH));
            m.put("amount", amount);
            m.put("current", back == 0);
            monthlyTrend.add(m);
        }
        report.put("monthlyTrend", monthlyTrend);
        return report;
    }

    public Map<String, Object> getOccupancyReport() {
        long totalBeds = bedRepo.count();
        long occupiedBeds = bookingRepo.findAll().stream()
                .filter(b -> "ACTIVE".equals(b.getStatus()))
                .count();
        double occupancyRate = totalBeds > 0 ? (occupiedBeds * 100.0 / totalBeds) : 0;
        Map<String, Object> report = new HashMap<>();
        report.put("totalBeds", totalBeds);
        report.put("occupiedBeds", occupiedBeds);
        report.put("vacantBeds", totalBeds - occupiedBeds);
        report.put("occupancyRate", Math.round(occupancyRate * 100.0) / 100.0);
        // Per-hostel rows consumed by the Super Admin Occupancy page
        report.put("hostels", hostelRows());
        return report;
    }

    // ── Complaints (platform-wide monitor) ───────────────────────────────────

    public List<Map<String, Object>> getAllComplaints() {
        Map<Long, Hostel> hostels = new HashMap<>();
        hostelRepo.findAll().forEach(h -> hostels.put(h.getId(), h));
        Map<Long, HostelOwner> owners = new HashMap<>();
        ownerRepo.findAll().forEach(o -> owners.put(o.getId(), o));
        Map<Long, String> members = new HashMap<>();
        hostellerRepo.findAll().forEach(p -> members.put(p.getId(),
                (p.getFirstName() + " " + (p.getLastName() != null ? p.getLastName() : "")).trim()));

        return complaintRepo.findAll().stream()
                .sorted(java.util.Comparator.comparing(ComplaintRecord::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(c -> {
                    var hostel = hostels.get(c.getHostelId());
                    var owner = hostel != null ? owners.get(hostel.getOwnerId()) : null;
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId());
                    m.put("hostelId", c.getHostelId());
                    m.put("hostel", hostel != null ? hostel.getName() : "—");
                    m.put("owner", owner != null ? owner.getOwnerName() : "—");
                    m.put("member", members.getOrDefault(c.getHostellerId(), "—"));
                    m.put("title", c.getTitle());
                    m.put("category", c.getCategory());
                    m.put("status", c.getStatus());
                    m.put("priority", c.getPriority());
                    m.put("createdAt", c.getCreatedAt());
                    return m;
                })
                .toList();
    }
}
