package com.hostlify.module.hostel.repository;

import com.hostlify.module.hostel.entity.HostelAmenity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HostelAmenityRepository extends JpaRepository<HostelAmenity, Long> {
    List<HostelAmenity> findByHostelId(Long hostelId);
    void deleteByHostelId(Long hostelId);
}
