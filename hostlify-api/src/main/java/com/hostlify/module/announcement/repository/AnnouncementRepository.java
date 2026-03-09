package com.hostlify.module.announcement.repository;

import com.hostlify.module.announcement.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByHostelIdAndValidFromLessThanEqualAndValidTillGreaterThanEqualOrValidTillIsNull(
        Long hostelId, LocalDate from, LocalDate till);
    List<Announcement> findByHostelIdOrderByCreatedAtDesc(Long hostelId);
}
