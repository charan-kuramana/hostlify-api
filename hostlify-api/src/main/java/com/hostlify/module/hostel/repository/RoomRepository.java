package com.hostlify.module.hostel.repository;

import com.hostlify.module.hostel.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByFloorIdOrderByRoomNameAsc(Long floorId);
    List<Room> findByHostelId(Long hostelId);
}
