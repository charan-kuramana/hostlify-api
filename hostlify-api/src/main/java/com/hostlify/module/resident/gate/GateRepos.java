package com.hostlify.module.resident.gate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class GateRepos {

    private GateRepos() {}

    public interface Visitors extends JpaRepository<VisitorPass, Long> {
        List<VisitorPass> findByHostelIdAndVisitDateOrderByCreatedAtDesc(Long hostelId, LocalDate date);
        List<VisitorPass> findByHostelIdAndStatus(Long hostelId, String status);
        List<VisitorPass> findByHostelIdAndPassCodeAndStatus(Long hostelId, String code, String status);
        List<VisitorPass> findByHostellerIdAndVisitDateGreaterThanEqualOrderByVisitDateAsc(Long hostellerId, LocalDate from);
        List<VisitorPass> findTop20ByHostellerIdAndVisitDateBeforeOrderByVisitDateDesc(Long hostellerId, LocalDate before);
        long countByHostellerIdAndStatusAndVisitDateGreaterThanEqual(Long hostellerId, String status, LocalDate from);
    }

    public interface Parcels extends JpaRepository<Parcel, Long> {
        List<Parcel> findByHostelIdAndStatusOrderByReceivedAtDesc(Long hostelId, String status);
        List<Parcel> findByHostelIdAndStatusAndCollectedAtAfterOrderByCollectedAtDesc(Long hostelId, String status, LocalDateTime after);
        List<Parcel> findTop30ByHostellerIdOrderByReceivedAtDesc(Long hostellerId);
    }
}
