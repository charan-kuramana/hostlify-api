package com.hostlify.module.review.repository;

import com.hostlify.module.review.entity.HostelReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HostelReviewRepository extends JpaRepository<HostelReview, Long> {
    List<HostelReview> findByHostelIdAndIsVisibleTrue(Long hostelId);

    java.util.Optional<HostelReview> findFirstByHostellerIdAndBookingIdOrderByCreatedAtDesc(Long hostellerId, Long bookingId);

    @Query("SELECT AVG(r.rating) FROM HostelReview r WHERE r.hostelId = :hostelId AND r.isVisible = true")
    Double findAverageRatingByHostelId(Long hostelId);
}
