package com.hostlify.module.hosteller.service;

import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HostellerService {

    private final HostellerProfileRepository profileRepo;

    public HostellerService(HostellerProfileRepository profileRepo) {
        this.profileRepo = profileRepo;
    }

    public HostellerProfile getByAuthUser(Long authUserId) {
        return profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
    }

    public List<HostellerProfile> getByHostel(Long hostelId) {
        return profileRepo.findByHostelId(hostelId);
    }

    public HostellerProfile getById(Long id) {
        return profileRepo.findById(id)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
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
}
