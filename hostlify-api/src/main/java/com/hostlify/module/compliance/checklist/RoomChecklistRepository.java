package com.hostlify.module.compliance.checklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomChecklistRepository extends JpaRepository<RoomChecklist, Long> {
    List<RoomChecklist> findByHostellerIdOrderByCreatedAtDesc(Long hostellerId);
    Optional<RoomChecklist> findFirstByBookingIdAndKind(Long bookingId, String kind);
}