package com.hostlify.module.booking.repository;

import com.hostlify.module.booking.entity.BookingCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingCheckinRepository extends JpaRepository<BookingCheckin, Long> {
    Optional<BookingCheckin> findFirstByHostellerIdAndStatus(Long hostellerId, String status);
    List<BookingCheckin> findByHostelIdAndStatus(Long hostelId, String status);
    Optional<BookingCheckin> findByBedIdAndStatus(Long bedId, String status);
    List<BookingCheckin> findByHostelIdOrderByCreatedAtDesc(Long hostelId);
    List<BookingCheckin> findByHostellerIdOrderByCreatedAtDesc(Long hostellerId);
}
