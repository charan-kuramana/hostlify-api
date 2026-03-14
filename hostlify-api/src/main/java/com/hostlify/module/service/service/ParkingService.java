package com.hostlify.module.service.service;

import com.hostlify.module.service.entity.ParkingRecord;
import com.hostlify.module.service.repository.ParkingRecordRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ParkingService {

    private final ParkingRecordRepository parkingRepo;

    public ParkingService(ParkingRecordRepository parkingRepo) {
        this.parkingRepo = parkingRepo;
    }

    public List<ParkingRecord> getActiveParking(Long hostelId) {
        return parkingRepo.findByHostelIdAndStatus(hostelId, "ACTIVE");
    }

    public ParkingRecord getMyParking(Long hostellerId) {
        return parkingRepo.findByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .orElseThrow(() -> new AppException("No active parking record found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public ParkingRecord registerParking(Long hostellerId, Long hostelId,
                                          String vehicleNumber, String vehicleType, String slotNumber) {
        // Ensure no duplicate active registration for the same vehicle
        parkingRepo.findByVehicleNumber(vehicleNumber.toUpperCase()).ifPresent(existing -> {
            if ("ACTIVE".equals(existing.getStatus())) {
                throw new AppException("Vehicle " + vehicleNumber + " is already registered", HttpStatus.CONFLICT);
            }
        });

        ParkingRecord record = new ParkingRecord();
        record.setHostellerId(hostellerId);
        record.setHostelId(hostelId);
        record.setVehicleNumber(vehicleNumber.toUpperCase());
        record.setVehicleType(vehicleType);
        record.setSlotNumber(slotNumber);
        record.setStatus("ACTIVE");
        record.setRegisteredAt(LocalDateTime.now());
        return parkingRepo.save(record);
    }

    @Transactional
    public ParkingRecord deregisterParking(Long recordId) {
        ParkingRecord record = parkingRepo.findById(recordId)
                .orElseThrow(() -> new AppException("Parking record not found", HttpStatus.NOT_FOUND));
        record.setStatus("REMOVED");
        return parkingRepo.save(record);
    }

    @Transactional
    public ParkingRecord updateSlot(Long recordId, String slotNumber) {
        ParkingRecord record = parkingRepo.findById(recordId)
                .orElseThrow(() -> new AppException("Parking record not found", HttpStatus.NOT_FOUND));
        record.setSlotNumber(slotNumber);
        return parkingRepo.save(record);
    }
}
