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

    long countByHostelIdAndStatus(Long hostelId, String status);
    long countByRoomIdAndStatus(Long roomId, String status);

    /** Count beds on a specific floor via JOIN through hostel_rooms */
    @Query(value = "SELECT COUNT(*) FROM hostel_beds b " +
            "JOIN hostel_rooms r ON b.room_id = r.id " +
            "WHERE b.hostel_id = :hostelId AND r.floor_id = :floorId " +
            "AND b.status = :status AND b.is_deleted = 0 AND r.is_deleted = 0",
            nativeQuery = true)
    long countByHostelIdAndFloorIdAndStatus(@Param("hostelId") Long hostelId,
                                             @Param("floorId") Long floorId,
                                             @Param("status") String status);

    @Query(value = "SELECT COUNT(*) FROM hostel_beds b " +
            "JOIN hostel_rooms r ON b.room_id = r.id " +
            "WHERE b.hostel_id = :hostelId AND r.floor_id = :floorId " +
            "AND b.is_deleted = 0 AND r.is_deleted = 0",
            nativeQuery = true)
    long countByHostelIdAndFloorId(@Param("hostelId") Long hostelId,
                                    @Param("floorId") Long floorId);

    @Query("SELECT b FROM Bed b WHERE b.roomId = :roomId")
    List<Bed> findAllByRoomId(@Param("roomId") Long roomId);
}
