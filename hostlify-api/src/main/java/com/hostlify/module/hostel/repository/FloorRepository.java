package com.hostlify.module.hostel.repository;

import com.hostlify.module.hostel.entity.Floor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FloorRepository extends JpaRepository<Floor, Long> {
    List<Floor> findByHostelIdOrderByFloorNumberAsc(Long hostelId);
}
