package com.hostlify.module.admin.repository;

import com.hostlify.module.admin.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {
    Optional<FeatureFlag> findByOwnerIdAndFlagName(Long ownerId, String flagName);
    List<FeatureFlag> findByOwnerIdAndIsEnabledTrue(Long ownerId);
}
