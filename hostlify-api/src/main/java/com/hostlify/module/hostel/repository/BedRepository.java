package com.hostlify.module.hostel.repository;

import com.hostlify.module.hostel.entity.Bed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BedRepository extends JpaRepository<Bed, Long> {
    List<Bed> findByRoomIdOrderByBedNameAsc(Long roomId);
    List<Bed> findByStatusAndHostelId(String status, Long hostelId);
    List<Bed> findByReservedForHostellerId(Long hostellerId);
}
