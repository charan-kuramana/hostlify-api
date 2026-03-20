package com.hostlify.module.hostel.repository;

import com.hostlify.module.hostel.entity.Hostel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HostelRepository extends JpaRepository<Hostel, Long> {
    List<Hostel> findByOwnerId(Long ownerId);
    List<Hostel> findByCityAndIsDeletedFalse(String city);
    boolean existsByName(String name);
}
