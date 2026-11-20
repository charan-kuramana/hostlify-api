package com.hostlify.module.resident.housekeeping;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hostel.entity.Room;
import com.hostlify.module.hostel.policy.PolicyService;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.storage.AzureBlobService;
import com.hostlify.shared.util.DateUtils;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Cleaning schedule + proof: the owner sets which days rooms are cleaned (Hostel Setup → Rules), staff tick each room
 * off with an optional photo, and residents see when their room was last cleaned (and can report a miss).
 */
@Service
public class HousekeepingService {

    public interface Repo extends JpaRepository<CleaningLog, Long> {
        List<CleaningLog> findByHostelIdAndCleanedOnGreaterThanEqualOrderByIdDesc(Long hostelId, LocalDate from);
        List<CleaningLog> findTop10ByRoomIdOrderByCleanedOnDescIdDesc(Long roomId);
        Optional<CleaningLog> findFirstByRoomIdOrderByCleanedOnDescIdDesc(Long roomId);
    }

    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Repo repo;
    private final RoomRepository roomRepo;
    private final BookingCheckinRepository bookingRepo;
    private final PolicyService policies;
    private final AzureBlobService storage;

    public HousekeepingService(Repo repo, RoomRepository roomRepo, BookingCheckinRepository bookingRepo,
                               PolicyService policies, AzureBlobService storage) {
        this.repo = repo;
        this.roomRepo = roomRepo;
        this.bookingRepo = bookingRepo;
        this.policies = policies;
        this.storage = storage;
    }

    /** Cleaning days (MON…SUN); empty = every day. */
    public List<String> schedule(Long hostelId) {
        return PolicyService.split(policies.get(hostelId).getRoomCleaningDays());
    }

    public static boolean isCleaningDay(List<String> days, LocalDate d) {
        return days.isEmpty() || days.contains(d.getDayOfWeek().name().substring(0, 3));
    }

    /** Next scheduled day on or after {@code from}. */
    public static LocalDate nextCleaningDay(List<String> days, LocalDate from) {
        LocalDate d = from;
        for (int i = 0; i < 7; i++, d = d.plusDays(1)) if (isCleaningDay(days, d)) return d;
        return from;
    }

    /** Staff board: every room with when it was last cleaned and whether it's due today. */
    public Map<String, Object> board(Long hostelId) {
        LocalDate today = DateUtils.todayKolkata();
        List<String> days = schedule(hostelId);
        boolean cleaningDay = isCleaningDay(days, today);
        List<Map<String, Object>> rooms = new ArrayList<>();
        List<Room> all = new ArrayList<>(roomRepo.findByHostelId(hostelId));
        all.sort(Comparator.comparing(Room::getRoomName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        int done = 0, due = 0;
        for (Room r : all) {
            CleaningLog last = repo.findFirstByRoomIdOrderByCleanedOnDescIdDesc(r.getId()).orElse(null);
            boolean cleanedToday = last != null && last.getCleanedOn().equals(today);
            boolean dueToday = cleaningDay && !cleanedToday;
            if (cleanedToday) done++;
            if (dueToday) due++;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roomId", r.getId());
            m.put("roomName", r.getRoomName());
            m.put("lastCleanedOn", last == null ? null : last.getCleanedOn());
            m.put("cleanedToday", cleanedToday);
            m.put("dueToday", dueToday);
            rooms.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scheduleDays", days);
        out.put("cleaningDay", cleaningDay);
        out.put("doneToday", done);
        out.put("dueToday", due);
        out.put("rooms", rooms);
        out.put("recent", repo.findByHostelIdAndCleanedOnGreaterThanEqualOrderByIdDesc(hostelId, today.minusDays(7)).stream()
                .limit(60).map(this::view).toList());
        return out;
    }

    @Transactional
    public CleaningLog log(Long hostelId, Long roomId, String area, MultipartFile photo, String note, Long staffUserId) {
        CleaningLog l = new CleaningLog();
        l.setHostelId(hostelId);
        if (roomId != null) {
            Room r = roomRepo.findById(roomId).orElseThrow(() -> new AppException("Room not found", HttpStatus.NOT_FOUND));
            if (!Objects.equals(r.getHostelId(), hostelId)) throw new AppException("Room not found", HttpStatus.NOT_FOUND);
            l.setRoomId(roomId);
        } else {
            String a = area == null ? "" : area.trim();
            if (a.isEmpty() || a.length() > 80) throw new AppException("Pick a room or name the area (max 80 characters)", HttpStatus.BAD_REQUEST);
            l.setArea(a);
        }
        if (photo != null && !photo.isEmpty()) {
            if (photo.getSize() > 5L * 1024 * 1024) throw new AppException("Photo is larger than 5 MB", HttpStatus.PAYLOAD_TOO_LARGE);
            String ct = photo.getContentType() == null ? "" : photo.getContentType().toLowerCase();
            if (!PHOTO_TYPES.contains(ct)) throw new AppException("Upload a JPG, PNG or WebP photo", HttpStatus.BAD_REQUEST);
            l.setPhotoPath(storage.uploadFile(photo, "cleaning/" + hostelId));
        }
        String n = note == null ? null : note.trim();
        l.setNote(n == null || n.isEmpty() ? null : n.length() > 300 ? n.substring(0, 300) : n);
        l.setCleanedOn(DateUtils.todayKolkata());
        l.setCleanedBy(staffUserId);
        return view(repo.save(l));
    }

    public CleaningLog get(Long id) {
        return repo.findById(id).orElseThrow(() -> new AppException("Entry not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public void delete(Long id) {
        CleaningLog l = get(id);
        if (!l.getCleanedOn().equals(DateUtils.todayKolkata())) throw new AppException("Only today's entries can be removed", HttpStatus.CONFLICT);
        if (l.getPhotoPath() != null) {
            try { storage.deleteFile(l.getPhotoPath()); } catch (Exception ignored) { /* best effort */ }
        }
        repo.delete(l);
    }

    /** Resident: my room's schedule and the last few cleanings (with photos). */
    public Map<String, Object> mine(Long hostellerId) {
        Map<String, Object> m = new LinkedHashMap<>();
        BookingCheckin b = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE").orElse(null);
        if (b == null || b.getRoomId() == null) {
            m.put("activeStay", false);
            return m;
        }
        LocalDate today = DateUtils.todayKolkata();
        List<String> days = schedule(b.getHostelId());
        List<CleaningLog> logs = repo.findTop10ByRoomIdOrderByCleanedOnDescIdDesc(b.getRoomId()).stream().map(this::view).toList();
        boolean cleanedToday = !logs.isEmpty() && logs.get(0).getCleanedOn().equals(today);
        m.put("activeStay", true);
        m.put("scheduleDays", days);
        m.put("cleanedToday", cleanedToday);
        m.put("nextCleaning", nextCleaningDay(days, cleanedToday ? today.plusDays(1) : today));
        m.put("logs", logs);
        // A scheduled day that passed with no entry: the resident can report it
        LocalDate lastScheduled = null;
        for (LocalDate d = today.minusDays(1); d.isAfter(today.minusDays(8)); d = d.minusDays(1)) {
            if (isCleaningDay(days, d)) { lastScheduled = d; break; }
        }
        LocalDate last = logs.isEmpty() ? null : logs.get(0).getCleanedOn();
        m.put("missedOn", lastScheduled != null && (last == null || last.isBefore(lastScheduled)) && !b.getCheckinDate().isAfter(lastScheduled)
                ? lastScheduled : null);
        return m;
    }

    private CleaningLog view(CleaningLog l) {
        if (l.getPhotoPath() != null) {
            try { l.setPhotoUrl(storage.generateSasUrl(l.getPhotoPath(), 30)); } catch (Exception ignored) { /* no photo link */ }
        }
        if (l.getRoomId() != null) roomRepo.findById(l.getRoomId()).ifPresent(r -> l.setRoomName(r.getRoomName()));
        return l;
    }

    static String dayCode(DayOfWeek d) { return d.name().substring(0, 3); }
}
