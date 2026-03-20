package com.hostlify.module.hostel.repository;

import com.hostlify.module.hostel.entity.HostelBookingSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface HostelBookingSettingRepository extends JpaRepository<HostelBookingSetting, Long> {
    Optional<HostelBookingSetting> findByHostelId(Long hostelId);
}
