package com.hostlify.module.service.repository;

import com.hostlify.module.service.entity.ParkingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingRecordRepository extends JpaRepository<ParkingRecord, Long> {
    List<ParkingRecord> findByHostelIdAndStatus(Long hostelId, String status);
    List<ParkingRecord> findByHostellerIdOrderByRegisteredAtDesc(Long hostellerId);
    Optional<ParkingRecord> findByHostellerIdAndStatus(Long hostellerId, String status);
    Optional<ParkingRecord> findByVehicleNumber(String vehicleNumber);
}
