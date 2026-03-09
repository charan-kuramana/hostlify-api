package com.hostlify.module.hostel.repository;

import com.hostlify.module.hostel.entity.HostelOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HostelOwnerRepository extends JpaRepository<HostelOwner, Long> {
    Optional<HostelOwner> findByAuthUserId(Long authUserId);
    Optional<HostelOwner> findByEmail(String email);
}
