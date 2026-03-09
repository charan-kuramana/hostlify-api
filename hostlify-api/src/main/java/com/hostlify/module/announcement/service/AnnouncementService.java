package com.hostlify.module.announcement.service;

import com.hostlify.module.announcement.entity.Announcement;
import com.hostlify.module.announcement.repository.AnnouncementRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepo;

    public AnnouncementService(AnnouncementRepository announcementRepo) {
        this.announcementRepo = announcementRepo;
    }

    public List<Announcement> getVisibleByHostel(Long hostelId) {
        LocalDate today = LocalDate.now();
        return announcementRepo
                .findByHostelIdAndValidFromLessThanEqualAndValidTillGreaterThanEqualOrValidTillIsNull(
                        hostelId, today, today);
    }

    public List<Announcement> getAllByHostel(Long hostelId) {
        return announcementRepo.findByHostelIdOrderByCreatedAtDesc(hostelId);
    }

    public Announcement getById(Long id) {
        return announcementRepo.findById(id)
                .orElseThrow(() -> new AppException("Announcement not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Announcement create(Announcement announcement) {
        return announcementRepo.save(announcement);
    }

    @Transactional
    public Announcement update(Long id, Announcement req) {
        Announcement existing = getById(id);
        existing.setTitle(req.getTitle());
        existing.setBody(req.getBody());
        existing.setValidFrom(req.getValidFrom());
        existing.setValidTill(req.getValidTill());
        return announcementRepo.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Announcement a = getById(id);
        a.setDeleted(true);
        announcementRepo.save(a);
    }
}
