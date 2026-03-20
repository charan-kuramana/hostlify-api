package com.hostlify.module.hostel.service;

import com.hostlify.module.hostel.entity.*;
import com.hostlify.module.hostel.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class HostelDiscoveryService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final HostelRepository hostelRepo;
    private final HostelAmenityRepository amenityRepo;
    private final HostelPricingRepository pricingRepo;
    private final FloorRepository floorRepo;
    private final RoomRepository roomRepo;
    private final BedRepository bedRepo;

    public HostelDiscoveryService(HostelRepository hostelRepo,
                                   HostelAmenityRepository amenityRepo,
                                   HostelPricingRepository pricingRepo,
                                   FloorRepository floorRepo,
                                   RoomRepository roomRepo,
                                   BedRepository bedRepo) {
        this.hostelRepo = hostelRepo;
        this.amenityRepo = amenityRepo;
        this.pricingRepo = pricingRepo;
        this.floorRepo = floorRepo;
        this.roomRepo = roomRepo;
        this.bedRepo = bedRepo;
    }

    public List<Map<String, Object>> getNearbyHostels(double lat, double lng, double radiusKm) {
        List<Hostel> all = hostelRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Hostel h : all) {
            if (h.getLatitude() == null || h.getLongitude() == null) continue;
            double dist = haversine(lat, lng, h.getLatitude().doubleValue(), h.getLongitude().doubleValue());
            if (dist <= radiusKm) {
                result.add(buildHostelSummary(h, dist));
            }
        }
        result.sort(Comparator.comparingDouble(m -> (Double) m.get("distanceKm")));
        return result;
    }

    public List<Map<String, Object>> searchHostels(String city, String locality, String roomType, BigDecimal maxRent) {
        List<Hostel> hostels = hostelRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Hostel h : hostels) {
            if (city != null && !h.getCity().toLowerCase().contains(city.toLowerCase())) continue;
            result.add(buildHostelSummary(h, null));
        }
        return result;
    }

    public Map<String, Object> getHostelDetail(Long hostelId) {
        Hostel h = hostelRepo.findById(hostelId)
                .orElseThrow(() -> new com.hostlify.shared.exception.AppException("Hostel not found",
                        org.springframework.http.HttpStatus.NOT_FOUND));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", h.getId());
        detail.put("name", h.getName());
        detail.put("hostelType", h.getHostelType());
        detail.put("city", h.getCity());
        detail.put("address", h.getAddress());
        detail.put("landmark", h.getLandmark());
        detail.put("about", h.getAbout());
        detail.put("contactPhone", h.getContactPhone());
        detail.put("latitude", h.getLatitude());
        detail.put("longitude", h.getLongitude());
        detail.put("defaultRent", h.getDefaultRent());
        detail.put("amenities", amenityRepo.findByHostelId(hostelId));
        detail.put("pricing", pricingRepo.findByHostelIdAndIsActiveTrue(hostelId));
        // Floor stats
        List<Floor> floors = floorRepo.findByHostelIdOrderByFloorNumberAsc(hostelId);
        List<Map<String, Object>> floorStats = new ArrayList<>();
        long totalAvailable = 0;
        for (Floor f : floors) {
            long available = bedRepo.countByHostelIdAndFloorIdAndStatus(hostelId, f.getId(), "AVAILABLE");
            long total = bedRepo.countByHostelIdAndFloorId(hostelId, f.getId());
            totalAvailable += available;
            Map<String, Object> fs = new LinkedHashMap<>();
            fs.put("floorId", f.getId());
            fs.put("floorName", f.getFloorName());
            fs.put("availableBeds", available);
            fs.put("totalBeds", total);
            floorStats.add(fs);
        }
        detail.put("floors", floorStats);
        detail.put("totalAvailableBeds", totalAvailable);
        return detail;
    }

    private Map<String, Object> buildHostelSummary(Hostel h, Double distanceKm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId());
        m.put("name", h.getName());
        m.put("hostelType", h.getHostelType());
        m.put("city", h.getCity());
        m.put("address", h.getAddress());
        m.put("landmark", h.getLandmark());
        if (distanceKm != null) {
            m.put("distanceKm", Math.round(distanceKm * 10.0) / 10.0);
        }
        long available = bedRepo.countByHostelIdAndStatus(h.getId(), "AVAILABLE");
        m.put("availableBeds", available);
        List<HostelPricing> pricing = pricingRepo.findByHostelIdAndIsActiveTrue(h.getId());
        if (!pricing.isEmpty()) {
            BigDecimal minRent = pricing.stream().map(HostelPricing::getBaseRent).min(BigDecimal::compareTo).orElse(h.getDefaultRent());
            m.put("minRent", minRent);
        } else {
            m.put("minRent", h.getDefaultRent());
        }
        m.put("amenities", amenityRepo.findByHostelId(h.getId()));
        return m;
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
