package com.hostlify.module.referral.repository;

import com.hostlify.module.referral.entity.ReferralCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReferralCodeRepository extends JpaRepository<ReferralCode, Long> {
    Optional<ReferralCode> findByCode(String code);
    Optional<ReferralCode> findByHostellerId(Long hostellerId);
}
