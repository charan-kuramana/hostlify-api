package com.hostlify.module.service.service;

import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.hostel.repository.RoomRepository;
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
    private final HostellerProfileRepository profileRepo;
    private final BookingCheckinRepository bookingRepo;
    private final RoomRepository roomRepo;

    public ParkingService(ParkingRecordRepository parkingRepo,
                          HostellerProfileRepository profileRepo,
                          BookingCheckinRepository bookingRepo,
                          RoomRepository roomRepo) {
        this.parkingRepo = parkingRepo;
        this.profileRepo = profileRepo;
        this.bookingRepo = bookingRepo;
        this.roomRepo = roomRepo;
    }

    public List<ParkingRecord> getActiveParking(Long hostelId) {
        List<ParkingRecord> list = parkingRepo.findByHostelIdAndStatus(hostelId, "ACTIVE");
        list.forEach(item -> {
            profileRepo.findById(item.getHostellerId()).ifPresent(p ->
                item.setHostellerName(p.getFirstName() + " " + (p.getLastName() != null ? p.getLastName() : "").trim()));
            bookingRepo.findFirstByHostellerIdAndStatus(item.getHostellerId(), "ACTIVE")
                .ifPresent(b -> roomRepo.findById(b.getRoomId())
                    .ifPresent(r -> item.setRoomNumber(r.getRoomName())));
        });
        return list;
    }

    public List<ParkingRecord> getMyParking(Long hostellerId) {
        return parkingRepo.findByHostellerIdOrderByRegisteredAtDesc(hostellerId);
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

        // Resolve hostelId from active booking if not provided in JWT
        Long resolvedHostelId = hostelId;
        if (resolvedHostelId == null) {
            resolvedHostelId = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                    .map(b -> b.getHostelId())
                    .orElse(null);
        }

        ParkingRecord record = new ParkingRecord();
        record.setHostellerId(hostellerId);
        record.setHostelId(resolvedHostelId);
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
