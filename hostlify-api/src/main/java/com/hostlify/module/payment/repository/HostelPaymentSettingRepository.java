package com.hostlify.module.payment.repository;

import com.hostlify.module.payment.entity.HostelPaymentSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HostelPaymentSettingRepository extends JpaRepository<HostelPaymentSetting, Long> {
    Optional<HostelPaymentSetting> findByHostelId(Long hostelId);
}
