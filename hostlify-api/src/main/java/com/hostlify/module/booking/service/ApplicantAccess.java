package com.hostlify.module.booking.service;

import com.hostlify.module.booking.repository.BookingRequestRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.shared.security.UserPrincipal;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Who may see a not-yet-allocated applicant. Applicants are not tied to a hostel until they get a bed,
 * so without this every owner's staff could read every applicant on the platform. Staff see an
 * applicant only when the applicant asked one of their hostels for a bed (a booking request), or
 * when staff look the person up by their exact mobile number or email (a walk-in at the desk).
 */
@Component
public class ApplicantAccess {

    /** Requests that still give the hostel a reason to see the applicant. */
    private static final Set<String> LIVE_REQUEST = Set.of("PENDING_APPROVAL", "REASSIGNED", "APPROVED");

    private final BookingRequestRepository requestRepo;
    private final HostellerProfileRepository profileRepo;

    public ApplicantAccess(BookingRequestRepository requestRepo, HostellerProfileRepository profileRepo) {
        this.requestRepo = requestRepo;
        this.profileRepo = profileRepo;
    }

    /** True when the applicant (auth user id) has a booking request for one of the current staff user's hostels. */
    public boolean linkedToMyHostels(Long applicantAuthUserId) {
        UserPrincipal p = HostelAccess.principal();
        if (p.isSuperAdmin()) return true;
        if (!p.isStaff() || applicantAuthUserId == null || p.getHostelIds() == null || p.getHostelIds().isEmpty()) return false;
        return requestRepo.findByHostellerUserIdOrderByCreatedAtDesc(applicantAuthUserId).stream()
                .anyMatch(r -> p.getHostelIds().contains(r.getHostelId()) && LIVE_REQUEST.contains(r.getStatus()));
    }

    /** Applicants a hostel may allocate: those who requested it, plus an exact mobile/email match. */
    public List<HostellerProfile> pendingFor(Long hostelId, String find) {
        Map<Long, HostellerProfile> out = new LinkedHashMap<>();
        Set<Long> requested = new HashSet<>();
        requestRepo.findByHostelIdOrderByCreatedAtDesc(hostelId).stream()
                .filter(r -> LIVE_REQUEST.contains(r.getStatus()))
                .forEach(r -> requested.add(r.getHostellerUserId()));
        for (HostellerProfile prof : profileRepo.findByStatusOrderByCreatedAtDesc("PENDING")) {
            if (Objects.equals(prof.getHostelId(), hostelId) || requested.contains(prof.getAuthUserId())) out.put(prof.getId(), prof);
        }
        // Someone who checked out of another hostel and applied here (moving)
        for (HostellerProfile prof : profileRepo.findByStatusOrderByCreatedAtDesc("CHECKED_OUT")) {
            if (requested.contains(prof.getAuthUserId())) out.put(prof.getId(), prof);
        }
        HostellerProfile match = lookup(find);
        if (match != null && ("PENDING".equals(match.getStatus()) || "CHECKED_OUT".equals(match.getStatus()))) {
            out.putIfAbsent(match.getId(), match);
        }
        return new ArrayList<>(out.values());
    }

    /** Exact match on a 10-digit mobile or a full email address; anything else finds nobody. */
    public HostellerProfile lookup(String find) {
        if (find == null) return null;
        String q = find.trim();
        if (q.matches("^(\\+91)?[6-9]\\d{9}$")) return profileRepo.findByMobile(q.substring(q.length() - 10)).orElse(null);
        if (q.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) return profileRepo.findByEmail(q.toLowerCase()).or(() -> profileRepo.findByEmail(q)).orElse(null);
        return null;
    }

    /** The desk proved they know this walk-in: the contact they typed matches the applicant. */
    public static boolean contactMatches(HostellerProfile prof, String contact) {
        if (prof == null || contact == null || contact.isBlank()) return false;
        String c = contact.trim();
        String digits = c.replaceAll("\\D", "");
        if (prof.getMobile() != null && digits.length() >= 10 && prof.getMobile().endsWith(digits.substring(digits.length() - 10))) return true;
        return prof.getEmail() != null && prof.getEmail().equalsIgnoreCase(c);
    }
}
