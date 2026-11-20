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

    @org.springframework.beans.factory.annotation.Autowired
    private com.hostlify.module.booking.service.ShortStayService shortStays;
    @org.springframework.beans.factory.annotation.Autowired
    private com.hostlify.module.payment.service.PaymentSettingsService paymentSettings;
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.hostlify.module.hostel.policy.PolicyService policies;

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

    /**
     * Hostels whose owner-set reach covers the searcher: each hostel decides its own radius
     * (Settings → Nearby radius). {@code maxKm}, when given, only narrows the result further.
     */
    public List<Map<String, Object>> getNearbyHostels(double lat, double lng, Double maxKm) {
        if (Math.abs(lat) > 90 || Math.abs(lng) > 180) throw new com.hostlify.shared.exception.AppException(
                "Invalid location", org.springframework.http.HttpStatus.BAD_REQUEST);
        List<Hostel> all = hostelRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Hostel h : all) {
            if (h.getLatitude() == null || h.getLongitude() == null) continue;
            double dist = haversine(lat, lng, h.getLatitude().doubleValue(), h.getLongitude().doubleValue());
            double reach = h.getDiscoveryRadiusKm() > 0 ? h.getDiscoveryRadiusKm() : 10;
            if (dist <= reach && (maxKm == null || dist <= maxKm)) {
                result.add(buildHostelSummary(h, dist));
            }
        }
        result.sort(Comparator.comparingDouble(m -> (Double) m.get("distanceKm")));
        return result;
    }

    /**
     * One search for the Find & Book page. Without text: hostels whose owner-set reach covers the resident
     * (or every hostel when the location is unknown). With text: name / area / city matches anywhere, with
     * the distance shown when the location is known. Nearest first.
     */
    public List<Map<String, Object>> discover(String q, Double lat, Double lng) {
        boolean located = lat != null && lng != null && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
        String text = q == null ? "" : q.trim().toLowerCase();
        if (text.isEmpty() && located) return getNearbyHostels(lat, lng, null);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Hostel h : hostelRepo.findAll()) {
            if (!text.isEmpty()) {
                String hay = String.join(" ", Objects.toString(h.getName(), ""), Objects.toString(h.getCity(), ""),
                        Objects.toString(h.getAddress(), ""), Objects.toString(h.getLandmark(), ""), Objects.toString(h.getPincode(), "")).toLowerCase();
                if (!hay.contains(text)) continue;
            }
            Double dist = located && h.getLatitude() != null && h.getLongitude() != null
                    ? haversine(lat, lng, h.getLatitude().doubleValue(), h.getLongitude().doubleValue()) : null;
            out.add(buildHostelSummary(h, dist));
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> (Double) m.get("distanceKm"), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(m -> String.valueOf(m.get("name"))));
        return out;
    }

    public List<Map<String, Object>> searchHostels(String city, String locality, String roomType, BigDecimal maxRent) {
        List<Hostel> hostels = hostelRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Hostel h : hostels) {
            if (city != null && !h.getCity().toLowerCase().contains(city.toLowerCase())) continue;
            if (locality != null && !locality.isBlank()) {
                String place = ((h.getAddress() != null ? h.getAddress() : "") + " "
                        + (h.getLandmark() != null ? h.getLandmark() : "")).toLowerCase();
                if (!place.contains(locality.trim().toLowerCase())) continue;
            }
            List<HostelPricing> pricing = pricingRepo.findByHostelIdAndIsActiveTrue(h.getId());
            if (roomType != null && !roomType.isBlank()
                    && pricing.stream().noneMatch(p -> p.getRoomType().equalsIgnoreCase(roomType))) continue;
            if (maxRent != null) {
                // Cheapest bed that matches the requested room type (or any type) must fit the budget
                BigDecimal cheapest = pricing.stream()
                        .filter(p -> roomType == null || roomType.isBlank() || p.getRoomType().equalsIgnoreCase(roomType))
                        .map(HostelPricing::getBaseRent)
                        .min(BigDecimal::compareTo)
                        .orElse(h.getDefaultRent());
                if (cheapest == null || cheapest.compareTo(maxRent) > 0) continue;
            }
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
        detail.put("shortStay", shortStays.info(hostelId));
        detail.put("utilitiesIncluded", paymentSettings.getOrDefault(hostelId).isUtilitiesIncluded());
        // Full cost of stay, notice / lock-in, house rules and safety facilities, shown before booking
        detail.put("policies", policies.publicView(hostelId));
        return detail;
    }

    public Map<String, Object> buildHostelSummary(Hostel h, Double distanceKm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId());
        m.put("name", h.getName());
        m.put("hostelType", h.getHostelType());
        m.put("city", h.getCity());
        m.put("address", h.getAddress());
        m.put("landmark", h.getLandmark());
        m.put("slug", h.getSlug());
        if (distanceKm != null) {
            m.put("distanceKm", Math.round(distanceKm * 10.0) / 10.0);
        }
        m.put("latitude", h.getLatitude());
        m.put("longitude", h.getLongitude());
        long available = bedRepo.countByHostelIdAndStatus(h.getId(), "AVAILABLE");
        m.put("availableBeds", available);
        List<HostelPricing> pricing = pricingRepo.findByHostelIdAndIsActiveTrue(h.getId());
        if (!pricing.isEmpty()) {
            BigDecimal minRent = pricing.stream().map(HostelPricing::getBaseRent).min(BigDecimal::compareTo).orElse(h.getDefaultRent());
            m.put("minRent", minRent);
        } else {
            m.put("minRent", h.getDefaultRent());
        }
        // Discovery cards render amenities as plain labels (string[])
        m.put("amenities", amenityRepo.findByHostelId(h.getId()).stream()
                .filter(HostelAmenity::isEnabled)
                .map(HostelAmenity::getLabel)
                .toList());
        m.put("safetyFeatures", com.hostlify.module.hostel.policy.PolicyService.split(policies.get(h.getId()).getSafetyFeatures()));
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
