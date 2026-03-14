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
import com.hostlify.module.hostel.entity.HostelOwner;
import com.hostlify.module.hostel.repository.BedRepository;
import com.hostlify.module.hostel.repository.HostelOwnerRepository;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.payment.entity.PaymentRecord;
import com.hostlify.module.payment.repository.PaymentRecordRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SuperAdminService {

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

    public Page<AuditLog> getAuditLogsByActor(Long actorUserId, Pageable pageable) {
        return auditRepo.findByActorUserIdOrderByCreatedAtDesc(actorUserId, pageable);
    }

    public Page<AuditLog> getAuditLogsByHostel(Long hostelId, Pageable pageable) {
        return auditRepo.findByHostelIdOrderByCreatedAtDesc(hostelId, pageable);
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
        Subscription sub = subscriptionRepo.findByOwnerId(ownerId)
                .orElse(new Subscription());
        sub.setOwnerId(ownerId);
        sub.setPlanName(planName);
        sub.setMaxHostels(maxHostels);
        sub.setMaxBeds(maxBeds);
        sub.setPriceMonthly(priceMonthly);
        return subscriptionRepo.save(sub);
    }

    // ── Feature Flags ────────────────────────────────────────────────────────

    public List<FeatureFlag> getEnabledFlags(Long ownerId) {
        return featureFlagRepo.findByOwnerIdAndIsEnabledTrue(ownerId);
    }

    @Transactional
    public FeatureFlag setFlag(Long ownerId, String flagName, boolean enabled,
                                Long setBy, String reason) {
        FeatureFlag flag = featureFlagRepo.findByOwnerIdAndFlagName(ownerId, flagName)
                .orElse(new FeatureFlag());
        flag.setOwnerId(ownerId);
        flag.setFlagName(flagName);
        flag.setEnabled(enabled);
        flag.setSetBy(setBy);
        flag.setReason(reason);
        return featureFlagRepo.save(flag);
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

    public HostelOwner getOwnerById(Long id) {
        return ownerRepo.findById(id)
                .orElseThrow(() -> new AppException("Owner not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public HostelOwner createOwner(String email, String password, String businessName,
                                    String ownerName, String mobile, String planName) {
        if (authUserRepo.existsByEmail(email)) {
            throw new AppException("Email already registered", HttpStatus.CONFLICT);
        }
        AuthUser user = new AuthUser();
        user.setUuid(UUID.randomUUID().toString());
        user.setEmail(email);
        user.setMobile(mobile);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("HOSTEL_STAFF");
        user.setSubRole("ADMIN");
        user.setStatus("ACTIVE");
        AuthUser savedUser = authUserRepo.save(user);

        HostelOwner owner = new HostelOwner();
        owner.setAuthUserId(savedUser.getId());
        owner.setBusinessName(businessName);
        owner.setOwnerName(ownerName);
        owner.setMobile(mobile);
        owner.setEmail(email);
        owner.setStatus("ACTIVE");
        owner.setSubscriptionPlan(planName != null ? planName : "FREE");
        HostelOwner savedOwner = ownerRepo.save(owner);

        Subscription sub = new Subscription();
        sub.setOwnerId(savedOwner.getId());
        sub.setPlanName(planName != null ? planName : "FREE");
        sub.setMaxHostels(1);
        sub.setMaxBeds(50);
        sub.setPriceMonthly(BigDecimal.ZERO);
        subscriptionRepo.save(sub);

        return savedOwner;
    }

    @Transactional
    public HostelOwner suspendOwner(Long id) {
        HostelOwner owner = getOwnerById(id);
        owner.setStatus("SUSPENDED");
        authUserRepo.findById(owner.getAuthUserId()).ifPresent(user -> {
            user.setStatus("SUSPENDED");
            authUserRepo.save(user);
        });
        return ownerRepo.save(owner);
    }

    @Transactional
    public void resetOwnerPassword(Long id, String newPassword) {
        HostelOwner owner = getOwnerById(id);
        AuthUser user = authUserRepo.findById(owner.getAuthUserId())
                .orElseThrow(() -> new AppException("Auth user not found", HttpStatus.NOT_FOUND));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        authUserRepo.save(user);
    }

    // ── Dashboard & Reports ──────────────────────────────────────────────────

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
        return stats;
    }

    public Map<String, Object> getRevenueReport(int month, int year) {
        List<PaymentRecord> records = paymentRecordRepo.findAll().stream()
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
        return report;
    }
}
