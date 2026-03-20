package com.hostlify.module.booking.repository;

import com.hostlify.module.booking.entity.BookingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long> {
    List<BookingRequest> findByHostelIdAndStatusOrderByCreatedAtDesc(Long hostelId, String status);
    List<BookingRequest> findByHostelIdOrderByCreatedAtDesc(Long hostelId);
    Optional<BookingRequest> findFirstByHostellerUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    Optional<BookingRequest> findFirstByHostellerUserIdAndStatusIn(Long userId, List<String> statuses);
    long countByBedIdAndStatus(Long bedId, String status);

    @Query("SELECT r FROM BookingRequest r WHERE r.status = 'PENDING_APPROVAL' AND r.expiresAt < :now")
    List<BookingRequest> findExpiredRequests(@Param("now") LocalDateTime now);

    List<BookingRequest> findByHostellerUserIdOrderByCreatedAtDesc(Long userId);
}
