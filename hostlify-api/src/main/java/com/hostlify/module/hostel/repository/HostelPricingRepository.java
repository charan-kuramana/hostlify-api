package com.hostlify.module.hostel.repository;

import com.hostlify.module.hostel.entity.HostelPricing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HostelPricingRepository extends JpaRepository<HostelPricing, Long> {
    List<HostelPricing> findByHostelIdAndIsActiveTrue(Long hostelId);
    Optional<HostelPricing> findByHostelIdAndRoomType(Long hostelId, String roomType);
}
