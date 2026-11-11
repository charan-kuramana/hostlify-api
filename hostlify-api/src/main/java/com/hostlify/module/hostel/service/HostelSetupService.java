package com.hostlify.module.hostel.service;

import com.hostlify.module.hostel.entity.*;
import com.hostlify.module.hostel.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class HostelSetupService {

    private final HostelAmenityRepository amenityRepo;
    private final HostelPricingRepository pricingRepo;
    private final HostelBookingSettingRepository settingRepo;
    private final BedRepository bedRepo;
    private final RoomRepository roomRepo;
    private final FloorRepository floorRepo;

    public HostelSetupService(HostelAmenityRepository amenityRepo,
                               HostelPricingRepository pricingRepo,
                               HostelBookingSettingRepository settingRepo,
                               BedRepository bedRepo,
                               RoomRepository roomRepo,
                               FloorRepository floorRepo) {
        this.amenityRepo = amenityRepo;
        this.pricingRepo = pricingRepo;
        this.settingRepo = settingRepo;
        this.bedRepo = bedRepo;
        this.roomRepo = roomRepo;
        this.floorRepo = floorRepo;
    }

    // ── Amenities ───────────────────────────────────────────────────────────────

    public List<HostelAmenity> getAmenities(Long hostelId) {
        return amenityRepo.findByHostelId(hostelId);
    }

    @Transactional
    public List<HostelAmenity> updateAmenities(Long hostelId, List<Map<String, Object>> amenities) {
        amenityRepo.deleteByHostelId(hostelId);
        List<HostelAmenity> saved = new ArrayList<>();
        for (Map<String, Object> a : amenities) {
            HostelAmenity entity = new HostelAmenity();
            entity.setHostelId(hostelId);
            entity.setAmenityKey((String) a.get("key"));
            entity.setEnabled(Boolean.TRUE.equals(a.get("enabled")));
            saved.add(amenityRepo.save(entity));
        }
        return saved;
    }

    // ── Pricing ─────────────────────────────────────────────────────────────────

    public List<HostelPricing> getPricing(Long hostelId) {
        return pricingRepo.findByHostelIdAndIsActiveTrue(hostelId);
    }

    @Transactional
    public List<HostelPricing> upsertPricing(Long hostelId, List<Map<String, Object>> tiers) {
        List<HostelPricing> saved = new ArrayList<>();
        for (Map<String, Object> t : tiers) {
            // Accept roomType or tierName
            String roomType = t.containsKey("roomType") ? (String) t.get("roomType") : (String) t.get("tierName");
            if (roomType == null || roomType.isBlank()) continue;  // skip invalid tier
            // Room types are stored upper-case (SINGLE, DOUBLE…) so "double" updates the DOUBLE tier
            roomType = roomType.trim().toUpperCase().replaceAll("[\\s-]+", "_");

            HostelPricing pricing = pricingRepo.findByHostelIdAndRoomType(hostelId, roomType)
                    .orElse(new HostelPricing());
            pricing.setHostelId(hostelId);
            pricing.setRoomType(roomType);

            // Accept baseRent or monthlyRent
            Object baseRentRaw = t.containsKey("baseRent") ? t.get("baseRent") : t.get("monthlyRent");
            if (baseRentRaw != null) {
                pricing.setBaseRent(new BigDecimal(baseRentRaw.toString()));
            } else {
                pricing.setBaseRent(BigDecimal.ZERO);
            }

            pricing.setDepositMonths(t.get("depositMonths") != null ? ((Number) t.get("depositMonths")).intValue() : 2);
            pricing.setLateFee(t.get("lateFee") != null ? new BigDecimal(t.get("lateFee").toString()) : BigDecimal.ZERO);
            if (pricing.getBaseRent().signum() <= 0) {
                throw new com.hostlify.shared.exception.AppException("Rent for " + roomType + " must be greater than 0",
                        org.springframework.http.HttpStatus.BAD_REQUEST);
            }
            if (pricing.getDepositMonths() < 0 || pricing.getDepositMonths() > 12 || pricing.getLateFee().signum() < 0) {
                throw new com.hostlify.shared.exception.AppException("Deposit months must be 0–12 and late fee cannot be negative",
                        org.springframework.http.HttpStatus.BAD_REQUEST);
            }
            pricing.setActive(true);
            saved.add(pricingRepo.save(pricing));
        }
        return saved;
    }

    // ── Booking Settings ────────────────────────────────────────────────────────

    public HostelBookingSetting getBookingSettings(Long hostelId) {
        return settingRepo.findByHostelId(hostelId)
                .orElseGet(() -> createDefaultSettings(hostelId));
    }

    @Transactional
    public HostelBookingSetting updateBookingSettings(Long hostelId, Map<String, Object> body) {
        HostelBookingSetting settings = settingRepo.findByHostelId(hostelId)
                .orElseGet(() -> createDefaultSettings(hostelId));
        if (body.containsKey("selfBookingEnabled"))
            settings.setSelfBookingEnabled(Boolean.TRUE.equals(body.get("selfBookingEnabled")));
        if (body.containsKey("requiresAdminApproval"))
            settings.setRequiresAdminApproval(Boolean.TRUE.equals(body.get("requiresAdminApproval")));
        if (body.containsKey("autoExpireDays"))
            settings.setAutoExpireDays(((Number) body.get("autoExpireDays")).intValue());
        if (body.containsKey("maxPendingPerBed"))
            settings.setMaxPendingPerBed(((Number) body.get("maxPendingPerBed")).intValue());
        if (body.containsKey("geolocationRadiusKm"))
            settings.setGeolocationRadiusKm(new BigDecimal(body.get("geolocationRadiusKm").toString()));
        return settingRepo.save(settings);
    }

    private HostelBookingSetting createDefaultSettings(Long hostelId) {
        HostelBookingSetting s = new HostelBookingSetting();
        s.setHostelId(hostelId);
        return settingRepo.save(s);
    }

    // ── Availability ────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getFloorsWithAvailability(Long hostelId) {
        List<Floor> floors = floorRepo.findByHostelIdOrderByFloorNumberAsc(hostelId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Floor floor : floors) {
            long available = bedRepo.countByHostelIdAndFloorIdAndStatus(hostelId, floor.getId(), "AVAILABLE");
            long total = bedRepo.countByHostelIdAndFloorId(hostelId, floor.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", floor.getId());          // alias so frontend can read .id
            m.put("floorId", floor.getId());
            m.put("floorName", floor.getFloorName());
            m.put("floorNumber", floor.getFloorNumber());
            m.put("availableBeds", available);
            m.put("totalBeds", total);
            result.add(m);
        }
        return result;
    }

    public List<Map<String, Object>> getRoomsWithAvailability(Long floorId) {
        List<Room> rooms = roomRepo.findByFloorIdOrderByRoomNameAsc(floorId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Room room : rooms) {
            long available = bedRepo.countByRoomIdAndStatus(room.getId(), "AVAILABLE");
            List<HostelPricing> pricing = pricingRepo.findByHostelIdAndIsActiveTrue(room.getHostelId());
            BigDecimal rent = pricing.stream()
                    .filter(p -> p.getRoomType().equals(room.getRoomType()))
                    .map(HostelPricing::getBaseRent)
                    .findFirst()
                    .orElse(room.getRentAmount());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", room.getId());           // alias so frontend can read .id
            m.put("roomId", room.getId());
            m.put("roomName", room.getRoomName());
            m.put("roomType", room.getRoomType());
            m.put("capacity", room.getCapacity());
            m.put("availableBeds", available);
            m.put("rentAmount", rent);
            m.put("beds", bedSummaries(room.getId()));   // per-bed status for color map
            result.add(m);
        }
        return result;
    }

    /** Lightweight per-bed summary used by the booking bed-map and admin structure view. */
    private List<Map<String, Object>> bedSummaries(Long roomId) {
        List<Bed> beds = bedRepo.findByRoomIdOrderByBedNameAsc(roomId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Bed b : beds) {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("id", b.getId());
            bm.put("bedName", b.getBedName());
            bm.put("status", b.getStatus());
            out.add(bm);
        }
        return out;
    }

    /**
     * Full nested hostel structure for the staff "Floors & Rooms" management screen:
     * floors → rooms → beds, with per-room occupancy counts.
     */
    public List<Map<String, Object>> getHostelStructure(Long hostelId) {
        List<Floor> floors = floorRepo.findByHostelIdOrderByFloorNumberAsc(hostelId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Floor floor : floors) {
            List<Room> rooms = roomRepo.findByFloorIdOrderByRoomNameAsc(floor.getId());
            List<Map<String, Object>> roomMaps = new ArrayList<>();
            for (Room room : rooms) {
                List<Map<String, Object>> beds = bedSummaries(room.getId());
                long occupied = beds.stream().filter(b -> "OCCUPIED".equals(b.get("status"))).count();
                long reserved = beds.stream().filter(b -> "RESERVED".equals(b.get("status"))).count();
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("id", room.getId());
                rm.put("roomNumber", room.getRoomName());
                rm.put("type", room.getRoomType());
                rm.put("capacity", room.getCapacity());
                rm.put("totalBeds", beds.size());
                rm.put("occupiedBeds", occupied);
                rm.put("reservedBeds", reserved);
                rm.put("beds", beds);
                roomMaps.add(rm);
            }
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("id", floor.getId());
            fm.put("floorName", floor.getFloorName());
            fm.put("floorNumber", floor.getFloorNumber());
            fm.put("rooms", roomMaps);
            result.add(fm);
        }
        return result;
    }

    // ── Room creation with auto-bed generation ───────────────────────────────

    @Transactional
    public Room createRoomWithBeds(Long floorId, Map<String, Object> body, Long hostelId) {
        Room room = new Room();
        room.setFloorId(floorId);
        room.setHostelId(hostelId);
        room.setRoomName((String) body.get("roomNumber"));
        room.setRoomType((String) body.getOrDefault("roomType", "SINGLE"));
        int bedsPerRoom = body.get("bedsPerRoom") != null ? ((Number) body.get("bedsPerRoom")).intValue() : 1;
        room.setCapacity(bedsPerRoom);
        room.setCurrentOccupancy(0);
        if (body.get("rentAmount") != null) {
            room.setRentAmount(new BigDecimal(body.get("rentAmount").toString()));
        }
        Room savedRoom = roomRepo.save(room);

        // Auto-create beds labeled A, B, C...
        char label = 'A';
        for (int i = 0; i < bedsPerRoom; i++) {
            Bed bed = new Bed();
            bed.setRoomId(savedRoom.getId());
            bed.setHostelId(hostelId);
            bed.setBedName(String.valueOf(label++));
            bed.setStatus("AVAILABLE");
            bedRepo.save(bed);
        }
        return savedRoom;
    }
}
