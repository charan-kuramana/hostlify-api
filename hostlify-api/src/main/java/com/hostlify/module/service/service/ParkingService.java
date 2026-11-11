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
        // Deregistered vehicles are history, not the resident's current vehicles
        return parkingRepo.findByHostellerIdOrderByRegisteredAtDesc(hostellerId).stream()
                .filter(r -> !"REMOVED".equals(r.getStatus()))
                .toList();
    }

    @Transactional
    public ParkingRecord registerParking(Long hostellerId, Long hostelId,
                                          String vehicleNumber, String vehicleType, String slotNumber) {
        String plate = vehicleNumber.trim().toUpperCase().replaceAll("[\\s-]+", " ");
        String canonical = plate.replace(" ", "");
        if (canonical.length() < 4 || plate.length() > 20 || !canonical.matches("[A-Z0-9]+")) {
            throw new AppException("Enter a valid vehicle number", HttpStatus.BAD_REQUEST);
        }
        if (vehicleType == null || vehicleType.isBlank() || vehicleType.length() > 20) {
            throw new AppException("A valid vehicle type is required", HttpStatus.BAD_REQUEST);
        }
        // Ensure no duplicate active registration for the same vehicle. (findByVehicleNumber threw
        // a 500 once a plate had been registered → removed → registered again, i.e. 2+ rows.)
        if (!parkingRepo.findActiveByCanonicalPlate(canonical).isEmpty()) {
            throw new AppException("Vehicle " + plate + " is already registered", HttpStatus.CONFLICT);
        }

        // Resolve hostelId from the active booking (the JWT value can be stale or missing)
        Long resolvedHostelId = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .map(b -> b.getHostelId())
                .orElse(hostelId);
        if (resolvedHostelId == null) {
            throw new AppException("You need an active stay to register parking", HttpStatus.BAD_REQUEST);
        }

        ParkingRecord record = new ParkingRecord();
        record.setHostellerId(hostellerId);
        record.setHostelId(resolvedHostelId);
        record.setVehicleNumber(plate);
        record.setVehicleType(vehicleType);
        record.setSlotNumber(slotNumber);
        record.setStatus("ACTIVE");
        record.setRegisteredAt(LocalDateTime.now());
        return parkingRepo.save(record);
    }

    @Transactional
    public ParkingRecord deregisterParking(Long recordId) {
        return deregisterParking(recordId, null);
    }

    /** @param requesterHostellerId when non-null (hosteller caller), the record must be theirs. */
    @Transactional
    public ParkingRecord deregisterParking(Long recordId, Long requesterHostellerId) {
        ParkingRecord record = parkingRepo.findById(recordId)
                .orElseThrow(() -> new AppException("Parking record not found", HttpStatus.NOT_FOUND));
        if (requesterHostellerId != null && !requesterHostellerId.equals(record.getHostellerId())) {
            throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        }
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
