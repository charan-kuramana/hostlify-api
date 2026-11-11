package com.hostlify.module.referral.service;

import com.hostlify.module.referral.entity.ReferralCode;
import com.hostlify.module.referral.entity.ReferralRecord;
import com.hostlify.module.referral.repository.ReferralCodeRepository;
import com.hostlify.module.referral.repository.ReferralRecordRepository;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.SlugUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReferralService {

    private final ReferralCodeRepository codeRepo;
    private final ReferralRecordRepository recordRepo;
    private final com.hostlify.module.hosteller.repository.HostellerProfileRepository profileRepo;

    public ReferralService(ReferralCodeRepository codeRepo, ReferralRecordRepository recordRepo,
                           com.hostlify.module.hosteller.repository.HostellerProfileRepository profileRepo) {
        this.codeRepo = codeRepo;
        this.recordRepo = recordRepo;
        this.profileRepo = profileRepo;
    }

    /**
     * Get or create a referral code for a hosteller.
     * Called at registration time (firstName is available) and from GET /referral/my-code.
     */
    @Transactional
    public ReferralCode getOrCreateCode(Long hostellerId, String firstName) {
        if (hostellerId == null) {
            throw new AppException("Hosteller profile not found", HttpStatus.NOT_FOUND);
        }
        return codeRepo.findByHostellerId(hostellerId).orElseGet(() -> {
            // JWTs carry no name, so prefer the profile's first name over the "HOST" fallback
            String name = profileRepo.findById(hostellerId).map(p -> p.getFirstName()).orElse(firstName);
            ReferralCode rc = new ReferralCode();
            rc.setHostellerId(hostellerId);
            rc.setCode(generateUniqueCode(name));
            return codeRepo.save(rc);
        });
    }

    /**
     * Validate a referral code and record the referral.
     * Returns the referrer's hostellerId so the caller can apply any discount logic.
     * Safe to call with null/blank code — simply returns null.
     */
    @Transactional
    public Long applyReferral(String code, Long referredHostellerId) {
        if (code == null || code.isBlank()) return null;

        ReferralCode rc = codeRepo.findByCode(code.toUpperCase())
                .orElseThrow(() -> new AppException("Invalid referral code", HttpStatus.BAD_REQUEST));

        if (rc.getHostellerId().equals(referredHostellerId)) {
            throw new AppException("You cannot use your own referral code", HttpStatus.BAD_REQUEST);
        }
        if (recordRepo.existsByReferredId(referredHostellerId)) {
            throw new AppException("A referral code has already been applied to this account", HttpStatus.CONFLICT);
        }

        ReferralRecord record = new ReferralRecord();
        record.setReferrerId(rc.getHostellerId());
        record.setReferredId(referredHostellerId);
        record.setStatus("PENDING");
        recordRepo.save(record);

        rc.setTotalReferrals(rc.getTotalReferrals() + 1);
        codeRepo.save(rc);

        return rc.getHostellerId();
    }

    public ReferralCode getMyCode(Long hostellerId) {
        return codeRepo.findByHostellerId(hostellerId)
                .orElseThrow(() -> new AppException("No referral code found. Use /referral/my-code to generate one.", HttpStatus.NOT_FOUND));
    }

    public List<ReferralRecord> getMyReferrals(Long hostellerId) {
        List<ReferralRecord> list = recordRepo.findByReferrerId(hostellerId);
        list.forEach(r -> profileRepo.findById(r.getReferredId()).ifPresent(p ->
                r.setReferredName((p.getFirstName() + " " + (p.getLastName() != null ? p.getLastName() : "")).trim())));
        return list;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String generateUniqueCode(String firstName) {
        String candidate = SlugUtils.generateReferralCode(firstName != null ? firstName : "HOST");
        // Retry on collision (extremely rare)
        while (codeRepo.findByCode(candidate).isPresent()) {
            candidate = SlugUtils.generateReferralCode(firstName != null ? firstName : "HOST");
        }
        return candidate;
    }
}
