package com.hostlify.module.hostel.repository;

import com.hostlify.module.hostel.entity.Bed;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BedRepository extends JpaRepository<Bed, Long> {
    List<Bed> findByRoomIdOrderByBedNameAsc(Long roomId);
    List<Bed> findByStatusAndHostelId(String status, Long hostelId);
    List<Bed> findByReservedForHostellerId(Long hostellerId);

    /** Fetch bed with PESSIMISTIC_WRITE lock for allocation (SELECT ... FOR UPDATE) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b WHERE b.id = :bedId")
    Optional<Bed> findByIdForUpdate(@Param("bedId") Long bedId);
}
