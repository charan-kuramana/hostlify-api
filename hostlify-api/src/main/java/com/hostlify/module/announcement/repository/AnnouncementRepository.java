package com.hostlify.module.announcement.repository;

import com.hostlify.module.announcement.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    // The derived-name version parsed as "(hostel AND from AND till) OR till IS NULL", so every hostel's
    // open-ended announcements leaked to all hostellers; the OR must be scoped to the hostel.
    @org.springframework.data.jpa.repository.Query("SELECT a FROM Announcement a WHERE a.hostelId = :hostelId "
            + "AND a.validFrom <= :from AND (a.validTill IS NULL OR a.validTill >= :till) ORDER BY a.createdAt DESC")
    List<Announcement> findByHostelIdAndValidFromLessThanEqualAndValidTillGreaterThanEqualOrValidTillIsNull(
        @org.springframework.data.repository.query.Param("hostelId") Long hostelId,
        @org.springframework.data.repository.query.Param("from") LocalDate from,
        @org.springframework.data.repository.query.Param("till") LocalDate till);
    List<Announcement> findByHostelIdOrderByCreatedAtDesc(Long hostelId);
}
