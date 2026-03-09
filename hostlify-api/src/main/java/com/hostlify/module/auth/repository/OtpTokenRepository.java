package com.hostlify.module.auth.repository;

import com.hostlify.module.auth.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
    Optional<OtpToken> findTopByUserIdAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(Long userId, String purpose);
}
