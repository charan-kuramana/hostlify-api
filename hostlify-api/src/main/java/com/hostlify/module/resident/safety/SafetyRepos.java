package com.hostlify.module.resident.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class SafetyRepos {
    private SafetyRepos() {}

    public interface Sos extends JpaRepository<SosAlert, Long> {
        List<SosAlert> findByHostelIdAndStatusInOrderByIdDesc(Long hostelId, Collection<String> statuses);
        List<SosAlert> findTop50ByHostelIdOrderByIdDesc(Long hostelId);
        Optional<SosAlert> findFirstByHostellerIdOrderByIdDesc(Long hostellerId);
    }

    public interface Wellness extends JpaRepository<WellnessFlag, Long> {
        List<WellnessFlag> findByHostelIdAndStatusAndCreatedAtAfterOrderByIdDesc(Long hostelId, String status, LocalDateTime after);
        Optional<WellnessFlag> findFirstByHostellerIdAndStatusOrderByIdDesc(Long hostellerId, String status);
    }

    public interface LateEntries extends JpaRepository<LateEntryRequest, Long> {
        List<LateEntryRequest> findTop20ByHostellerIdOrderByEntryDateDescIdDesc(Long hostellerId);
        List<LateEntryRequest> findByHostelIdAndEntryDateGreaterThanEqualOrderByEntryDateAscExpectedTimeAsc(Long hostelId, LocalDate from);
        List<LateEntryRequest> findByHostellerIdAndEntryDate(Long hostellerId, LocalDate date);
    }
}
