package com.hostlify.module.hosteller.service;

import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerEmergencyContactRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class HostellerService {

    private final HostellerProfileRepository profileRepo;
    private final BookingCheckinRepository bookingRepo;
    private final RoomRepository roomRepo;
    private final HostellerEmergencyContactRepository emergencyRepo;

    public HostellerService(HostellerProfileRepository profileRepo,
                            BookingCheckinRepository bookingRepo,
                            RoomRepository roomRepo,
                            HostellerEmergencyContactRepository emergencyRepo) {
        this.profileRepo = profileRepo;
        this.bookingRepo = bookingRepo;
        this.roomRepo = roomRepo;
        this.emergencyRepo = emergencyRepo;
    }

    public HostellerProfile getByAuthUser(Long authUserId) {
        return profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
    }

    public List<HostellerProfile> getByHostel(Long hostelId) {
        List<HostellerProfile> profiles = profileRepo.findByHostelId(hostelId);
        profiles.forEach(p -> bookingRepo.findFirstByHostellerIdAndStatus(p.getId(), "ACTIVE")
                .ifPresent(b -> roomRepo.findById(b.getRoomId())
                        .ifPresent(r -> p.setRoomNumber(r.getRoomName()))));
        return profiles;
    }

    public HostellerProfile getById(Long id) {
        HostellerProfile profile = profileRepo.findById(id)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
        bookingRepo.findFirstByHostellerIdAndStatus(profile.getId(), "ACTIVE").ifPresent(b -> {
            profile.setBookingId(b.getId());
            profile.setAdvance(b.getAdvanceAmount());
            profile.setMonthlyRent(b.getRentAmount());
            roomRepo.findById(b.getRoomId()).ifPresent(r -> {
                profile.setRoomNumber(r.getRoomName());
                profile.setType(r.getRoomType());
            });
        });
        return profile;
    }

    @Transactional
    public HostellerProfile update(Long id, HostellerProfile req) {
        HostellerProfile existing = getById(id);
        existing.setFirstName(req.getFirstName());
        existing.setLastName(req.getLastName());
        existing.setMobile(req.getMobile());
        existing.setDateOfBirth(req.getDateOfBirth());
        return profileRepo.save(existing);
    }

    @Transactional
    public HostellerProfile savePhotoUrl(Long hostellerId, String blobPath) {
        HostellerProfile profile = getById(hostellerId);
        profile.setPhotoUrl(blobPath);
        return profileRepo.save(profile);
    }

    @Transactional
    public HostellerProfile saveIdProofUrl(Long hostellerId, String blobPath) {
        HostellerProfile profile = getById(hostellerId);
        profile.setIdProofUrl(blobPath);
        return profileRepo.save(profile);
    }

    public Map<String, Object> getProfileCompletion(Long authUserId) {
        HostellerProfile profile = profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));

        int total = 8; // required fields
        int filled = 0;
        List<String> missing = new ArrayList<>();

        if (profile.getFirstName() != null && !profile.getFirstName().isEmpty()) filled++;
        else missing.add("firstName");

        if (profile.getLastName() != null && !profile.getLastName().isEmpty()) filled++;
        else missing.add("lastName");

        if (profile.getIdProofType() != null && !profile.getIdProofType().isEmpty()) filled++;
        else missing.add("idProofType");

        if (profile.getMobile() != null && !profile.getMobile().isEmpty()) filled++;
        else missing.add("mobile");

        if (profile.getDateOfBirth() != null) filled++;
        else missing.add("dateOfBirth");

        if (profile.getPhotoUrl() != null) filled++;
        else missing.add("photo");

        if (profile.getIdProofUrl() != null) filled++;
        else missing.add("idProof");

        // Check emergency contact
        var emergency = emergencyRepo.findFirstByHostellerId(profile.getId());
        if (emergency.isPresent() && emergency.get().getMobile() != null) filled++;
        else missing.add("emergencyContact");

        int percent = (int) Math.round((filled * 100.0) / total);
        return Map.of("completionPercent", percent, "missingFields", missing, "filled", filled, "total", total);
    }
}
