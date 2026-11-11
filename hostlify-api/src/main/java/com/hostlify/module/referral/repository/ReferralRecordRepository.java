package com.hostlify.module.referral.repository;

import com.hostlify.module.referral.entity.ReferralRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferralRecordRepository extends JpaRepository<ReferralRecord, Long> {
    List<ReferralRecord> findByReferrerId(Long referrerId);

    boolean existsByReferredId(Long referredId);
}
