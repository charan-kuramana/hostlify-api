package com.hostlify.module.hostel.service;

import com.hostlify.module.hostel.entity.*;
import com.hostlify.module.hostel.repository.*;
import com.hostlify.shared.exception.AppException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class HostelService {

    private final HostelRepository hostelRepo;
    private final HostelOwnerRepository ownerRepo;
    private final FloorRepository floorRepo;
    private final RoomRepository roomRepo;
    private final BedRepository bedRepo;

    public HostelService(HostelRepository hostelRepo, HostelOwnerRepository ownerRepo,
                         FloorRepository floorRepo, RoomRepository roomRepo, BedRepository bedRepo) {
        this.hostelRepo = hostelRepo;
        this.ownerRepo = ownerRepo;
        this.floorRepo = floorRepo;
        this.roomRepo = roomRepo;
        this.bedRepo = bedRepo;
    }

    // ===== Hostel Owner =====
    public List<HostelOwner> getAllOwners() { return ownerRepo.findAll(); }
    public HostelOwner getOwner(Long id) {
        return ownerRepo.findById(id).orElseThrow(() -> new AppException("Owner not found", HttpStatus.NOT_FOUND));
    }
    @Transactional
    public HostelOwner createOwner(HostelOwner owner) { return ownerRepo.save(owner); }
    @Transactional
    public HostelOwner updateOwner(Long id, HostelOwner req) {
        HostelOwner existing = getOwner(id);
        existing.setOwnerName(req.getOwnerName());
        existing.setMobile(req.getMobile());
        existing.setBusinessName(req.getBusinessName());
        existing.setStatus(req.getStatus());
        return ownerRepo.save(existing);
    }
    @Transactional
    public void deleteOwner(Long id) {
        HostelOwner owner = getOwner(id);
        owner.setDeleted(true);
        ownerRepo.save(owner);
    }

    // ===== Hostel =====
    public Page<Hostel> getHostels(Pageable pageable) { return hostelRepo.findAll(pageable); }
    public Hostel getHostel(Long id) {
        return hostelRepo.findById(id).orElseThrow(() -> new AppException("Hostel not found", HttpStatus.NOT_FOUND));
    }
    public List<Hostel> getHostelsByOwner(Long ownerId) { return hostelRepo.findByOwnerId(ownerId); }
    @Transactional
    public Hostel createHostel(Hostel hostel) { return hostelRepo.save(hostel); }
    @Transactional
    public Hostel updateHostel(Long id, Hostel req) {
        Hostel existing = getHostel(id);
        existing.setName(req.getName());
        existing.setHostelType(req.getHostelType());
        existing.setCity(req.getCity());
        existing.setPincode(req.getPincode());
        existing.setAddress(req.getAddress());
        existing.setLatitude(req.getLatitude());
        existing.setLongitude(req.getLongitude());
        existing.setDefaultRent(req.getDefaultRent());
        existing.setOnlinePaymentEnabled(req.isOnlinePaymentEnabled());
        return hostelRepo.save(existing);
    }

    @Transactional
    public Hostel partialUpdateHostel(Long id, Map<String, Object> body) {
        validateHostelUpdate(body);
        Hostel existing = getHostel(id);
        if (body.containsKey("name")) existing.setName((String) body.get("name"));
        if (body.containsKey("hostelName")) existing.setName((String) body.get("hostelName"));
        if (body.containsKey("type")) existing.setHostelType((String) body.get("type"));
        if (body.containsKey("hostelType")) existing.setHostelType((String) body.get("hostelType"));
        if (body.containsKey("city") && body.get("city") != null) existing.setCity((String) body.get("city"));
        if (body.containsKey("pincode") && body.get("pincode") != null) existing.setPincode((String) body.get("pincode"));
        if (body.containsKey("address")) existing.setAddress((String) body.get("address"));
        if (body.containsKey("locality")) existing.setLandmark((String) body.get("locality"));
        if (body.containsKey("contact")) existing.setContactPhone((String) body.get("contact"));
        if (body.containsKey("contactPhone")) existing.setContactPhone((String) body.get("contactPhone"));
        if (body.containsKey("about")) existing.setAbout((String) body.get("about"));
        if (body.containsKey("defaultRent") && body.get("defaultRent") != null) {
            existing.setDefaultRent(new BigDecimal(body.get("defaultRent").toString()));
        }
        if (body.containsKey("onlinePaymentEnabled")) {
            existing.setOnlinePaymentEnabled(Boolean.TRUE.equals(body.get("onlinePaymentEnabled")));
        }
        // Ensure non-null required fields have fallback values to prevent constraint violations
        if (existing.getName() == null || existing.getName().isBlank()) existing.setName("Unnamed Hostel");
        if (existing.getHostelType() == null || existing.getHostelType().isBlank()) existing.setHostelType("MIXED");
        if (existing.getCity() == null || existing.getCity().isBlank()) existing.setCity("Unknown");
        if (existing.getPincode() == null || existing.getPincode().isBlank()) existing.setPincode("000000");
        return hostelRepo.save(existing);
    }
    /** Server-side checks for hostel profile edits; only fields present in the request are checked. */
    private void validateHostelUpdate(Map<String, Object> body) {
        for (String key : List.of("name", "hostelName", "city")) {
            if (body.containsKey(key) && (body.get(key) == null || body.get(key).toString().isBlank())) {
                throw new AppException(("city".equals(key) ? "City" : "Hostel name") + " cannot be empty", HttpStatus.BAD_REQUEST);
            }
        }
        if (body.containsKey("pincode") && (body.get("pincode") == null
                || !body.get("pincode").toString().trim().matches("[1-9]\\d{5}"))) {
            throw new AppException("Pincode must be a valid 6-digit PIN code", HttpStatus.BAD_REQUEST);
        }
        for (String key : List.of("contact", "contactPhone")) {
            Object v = body.get(key);
            if (v == null || v.toString().isBlank()) continue;
            // Mobile or landline with STD code; spaces, dashes and a +91 prefix are allowed
            String digits = v.toString().replaceAll("[\\s-]", "").replaceFirst("^\\+91", "");
            if (!digits.matches("0?\\d{10}")) {
                throw new AppException("Contact phone must be a valid 10-digit number", HttpStatus.BAD_REQUEST);
            }
        }
        if (body.get("defaultRent") != null) {
            try {
                if (new BigDecimal(body.get("defaultRent").toString()).signum() < 0) {
                    throw new AppException("Default rent cannot be negative", HttpStatus.BAD_REQUEST);
                }
            } catch (NumberFormatException e) {
                throw new AppException("Default rent must be a number", HttpStatus.BAD_REQUEST);
            }
        }
    }

    @Transactional
    public void deleteHostel(Long id) {
        Hostel hostel = getHostel(id);
        hostel.setDeleted(true);
        hostelRepo.save(hostel);
    }

    // ===== Floor =====
    public List<Floor> getFloorsByHostel(Long hostelId) { return floorRepo.findByHostelIdOrderByFloorNumberAsc(hostelId); }
    public Floor getFloor(Long id) {
        return floorRepo.findById(id).orElseThrow(() -> new AppException("Floor not found", HttpStatus.NOT_FOUND));
    }
    @Transactional
    public Floor createFloor(Floor floor) { return floorRepo.save(floor); }
    @Transactional
    public void deleteFloor(Long id) {
        Floor floor = getFloor(id);
        floor.setDeleted(true);
        floorRepo.save(floor);
    }

    // ===== Room =====
    public List<Room> getRoomsByFloor(Long floorId) { return roomRepo.findByFloorIdOrderByRoomNameAsc(floorId); }
    public Room getRoom(Long id) {
        return roomRepo.findById(id).orElseThrow(() -> new AppException("Room not found", HttpStatus.NOT_FOUND));
    }
    @Transactional
    public Room createRoom(Room room) { return roomRepo.save(room); }
    @Transactional
    public void deleteRoom(Long id) {
        Room room = getRoom(id);
        room.setDeleted(true);
        roomRepo.save(room);
    }

    // ===== Bed =====
    public List<Bed> getBedsByRoom(Long roomId) { return bedRepo.findByRoomIdOrderByBedNameAsc(roomId); }
    public Bed getBed(Long id) {
        return bedRepo.findById(id).orElseThrow(() -> new AppException("Bed not found", HttpStatus.NOT_FOUND));
    }
    @Transactional
    public Bed createBed(Bed bed) { return bedRepo.save(bed); }
    @Transactional
    public void deleteBed(Long id) {
        Bed bed = getBed(id);
        bed.setDeleted(true);
        bedRepo.save(bed);
    }
}
