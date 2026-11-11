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
        validateProfile(req);
        HostellerProfile existing = getById(id);
        // Copy all editable personal-detail fields (only when provided, so partial updates don't wipe).
        if (req.getFirstName() != null)        existing.setFirstName(req.getFirstName());
        if (req.getLastName() != null)         existing.setLastName(req.getLastName());
        if (req.getMobile() != null)           existing.setMobile(req.getMobile());
        if (req.getAltMobile() != null)        existing.setAltMobile(req.getAltMobile());
        if (req.getGender() != null)           existing.setGender(req.getGender());
        if (req.getDateOfBirth() != null)      existing.setDateOfBirth(req.getDateOfBirth());
        if (req.getUserType() != null)         existing.setUserType(req.getUserType());
        if (req.getCompanyCollege() != null)   existing.setCompanyCollege(req.getCompanyCollege());
        if (req.getPermanentAddress() != null) existing.setPermanentAddress(req.getPermanentAddress());
        if (req.getCity() != null)             existing.setCity(req.getCity());
        if (req.getPincode() != null)          existing.setPincode(req.getPincode());
        if (req.getIdProofType() != null)      existing.setIdProofType(req.getIdProofType());
        if (req.getIdProofNumber() != null)    existing.setIdProofNumber(req.getIdProofNumber());
        return profileRepo.save(existing);
    }

    // Indian mobile: 10 digits starting 6-9 · PIN code: 6 digits, not starting with 0
    private static final java.util.regex.Pattern MOBILE = java.util.regex.Pattern.compile("[6-9]\\d{9}");
    private static final java.util.regex.Pattern PINCODE = java.util.regex.Pattern.compile("[1-9]\\d{5}");

    /** Server-side checks for profile edits; only fields present in the request are validated. */
    private void validateProfile(HostellerProfile req) {
        if (req.getFirstName() != null && req.getFirstName().isBlank()) {
            throw new AppException("First name cannot be empty", HttpStatus.BAD_REQUEST);
        }
        if (req.getMobile() != null && !MOBILE.matcher(req.getMobile().trim()).matches()) {
            throw new AppException("Mobile must be a valid 10-digit number starting with 6-9", HttpStatus.BAD_REQUEST);
        }
        if (req.getAltMobile() != null && !req.getAltMobile().isBlank() && !MOBILE.matcher(req.getAltMobile().trim()).matches()) {
            throw new AppException("Alternate mobile must be a valid 10-digit number starting with 6-9", HttpStatus.BAD_REQUEST);
        }
        if (req.getPincode() != null && !req.getPincode().isBlank() && !PINCODE.matcher(req.getPincode().trim()).matches()) {
            throw new AppException("Pincode must be a valid 6-digit PIN code", HttpStatus.BAD_REQUEST);
        }
        if (req.getDateOfBirth() != null) {
            java.time.LocalDate today = java.time.LocalDate.now();
            if (req.getDateOfBirth().isAfter(today)) {
                throw new AppException("Date of birth cannot be in the future", HttpStatus.BAD_REQUEST);
            }
            if (req.getDateOfBirth().isBefore(today.minusYears(100))) {
                throw new AppException("Date of birth is not valid", HttpStatus.BAD_REQUEST);
            }
        }
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

    // ─── Emergency contact ──────────────────────────────────────────────────
    public com.hostlify.module.hosteller.entity.HostellerEmergencyContact getEmergencyContact(Long hostellerId) {
        return emergencyRepo.findFirstByHostellerId(hostellerId).orElse(null);
    }

    @Transactional
    public com.hostlify.module.hosteller.entity.HostellerEmergencyContact upsertEmergencyContact(
            Long hostellerId, com.hostlify.module.hosteller.entity.HostellerEmergencyContact req) {
        var c = emergencyRepo.findFirstByHostellerId(hostellerId)
                .orElseGet(com.hostlify.module.hosteller.entity.HostellerEmergencyContact::new);
        c.setHostellerId(hostellerId);
        if (req.getMobile() != null && !MOBILE.matcher(req.getMobile().trim()).matches()) {
            throw new AppException("Emergency contact mobile must be a valid 10-digit number", HttpStatus.BAD_REQUEST);
        }
        if (req.getAltMobile() != null && !req.getAltMobile().isBlank() && !MOBILE.matcher(req.getAltMobile().trim()).matches()) {
            throw new AppException("Emergency contact alternate mobile must be a valid 10-digit number", HttpStatus.BAD_REQUEST);
        }
        if (req.getName() != null) c.setName(req.getName());
        if (req.getRelationship() != null) c.setRelationship(req.getRelationship());
        if (req.getMobile() != null) c.setMobile(req.getMobile());
        if (req.getAltMobile() != null) c.setAltMobile(req.getAltMobile());
        return emergencyRepo.save(c);
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
