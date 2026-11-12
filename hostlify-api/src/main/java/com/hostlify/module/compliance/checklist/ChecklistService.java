package com.hostlify.module.compliance.checklist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.compliance.ResidentNoticeEvent;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.storage.AzureBlobService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChecklistService {

    public static final Set<String> CONDITIONS = Set.of("GOOD", "FAIR", "DAMAGED", "MISSING", "NA");
    public static final int MAX_PHOTOS = 15;
    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    /** Sensible defaults for a PG room; staff can rename, add or remove items. */
    public static final List<RoomChecklist.Item> DEFAULT_ITEMS = List.of(
            new RoomChecklist.Item("bed", "Bed & mattress", "GOOD", null),
            new RoomChecklist.Item("pillow", "Pillow & bedsheet", "GOOD", null),
            new RoomChecklist.Item("cupboard", "Cupboard / locker & lock", "GOOD", null),
            new RoomChecklist.Item("table", "Table & chair", "GOOD", null),
            new RoomChecklist.Item("fan", "Fan", "GOOD", null),
            new RoomChecklist.Item("lights", "Lights & switches", "GOOD", null),
            new RoomChecklist.Item("sockets", "Power sockets", "GOOD", null),
            new RoomChecklist.Item("ac", "AC / cooler & remote", "NA", null),
            new RoomChecklist.Item("windows", "Windows, curtains & mesh", "GOOD", null),
            new RoomChecklist.Item("walls", "Walls & paint", "GOOD", null),
            new RoomChecklist.Item("floor", "Floor & tiles", "GOOD", null),
            new RoomChecklist.Item("door", "Door & room lock", "GOOD", null),
            new RoomChecklist.Item("bathroom", "Bathroom fittings (tap, shower, flush)", "GOOD", null),
            new RoomChecklist.Item("mirror", "Mirror & shelves", "GOOD", null));

    private final RoomChecklistRepository repo;
    private final ChecklistPhotoRepository photoRepo;
    private final BookingCheckinRepository bookingRepo;
    private final HostellerProfileRepository profileRepo;
    private final AzureBlobService storage;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChecklistService(RoomChecklistRepository repo, ChecklistPhotoRepository photoRepo, BookingCheckinRepository bookingRepo,
                            HostellerProfileRepository profileRepo, AzureBlobService storage, ApplicationEventPublisher events) {
        this.repo = repo;
        this.photoRepo = photoRepo;
        this.bookingRepo = bookingRepo;
        this.profileRepo = profileRepo;
        this.storage = storage;
        this.events = events;
    }

    /** Opens (or returns the existing) checklist of this kind for the resident's current/last booking. */
    @Transactional
    public RoomChecklist start(Long hostellerId, String kind, Long staffUserId) {
        String k = kind == null ? "" : kind.toUpperCase();
        if (!k.equals("MOVE_IN") && !k.equals("MOVE_OUT")) throw bad("kind must be MOVE_IN or MOVE_OUT");
        HostellerProfile p = profileRepo.findById(hostellerId).orElseThrow(() -> new AppException("Resident not found", HttpStatus.NOT_FOUND));
        BookingCheckin b = currentOrLastBooking(hostellerId);
        if (b == null) throw bad("This resident has no booking");

        Optional<RoomChecklist> existing = repo.findFirstByBookingIdAndKind(b.getId(), k);
        if (existing.isPresent()) return view(existing.get());

        List<RoomChecklist.Item> items = DEFAULT_ITEMS;
        if (k.equals("MOVE_OUT")) {
            // Same items as move-in, so the two can be compared line by line
            items = repo.findFirstByBookingIdAndKind(b.getId(), "MOVE_IN").map(this::items).orElse(DEFAULT_ITEMS).stream()
                    .map(i -> new RoomChecklist.Item(i.key(), i.label(), "NA".equals(i.condition()) ? "NA" : "GOOD", null)).toList();
        }
        RoomChecklist c = new RoomChecklist();
        c.setHostelId(b.getHostelId() != null ? b.getHostelId() : p.getHostelId());
        c.setHostellerId(hostellerId);
        c.setBookingId(b.getId());
        c.setKind(k);
        c.setItemsJson(json(items));
        c.setCreatedBy(staffUserId);
        c.setUpdatedAt(LocalDateTime.now());
        return view(repo.save(c));
    }

    @Transactional
    public RoomChecklist update(Long id, List<Map<String, Object>> items, Integer keysHanded, String meterReading, String staffNotes) {
        RoomChecklist c = editable(id);
        if (items != null) {
            if (items.isEmpty() || items.size() > 60) throw bad("Add between 1 and 60 items");
            List<RoomChecklist.Item> clean = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            int n = 0;
            for (Map<String, Object> m : items) {
                String label = str(m.get("label"), 100);
                if (label == null) throw bad("Every item needs a name");
                String cond = String.valueOf(m.getOrDefault("condition", "GOOD")).toUpperCase();
                if (!CONDITIONS.contains(cond)) throw bad("Unknown condition: " + cond);
                String key = str(m.get("key"), 40);
                if (key == null || !keys.add(key)) key = "item" + (++n) + "_" + label.toLowerCase().replaceAll("[^a-z0-9]+", "-");
                keys.add(key);
                clean.add(new RoomChecklist.Item(key, label, cond, str(m.get("note"), 300)));
            }
            c.setItemsJson(json(clean));
        }
        if (keysHanded != null) {
            if (keysHanded < 0 || keysHanded > 20) throw bad("Keys must be 0-20");
            c.setKeysHanded(keysHanded);
        }
        if (meterReading != null) c.setMeterReading(str(meterReading, 30));
        if (staffNotes != null) c.setStaffNotes(str(staffNotes, 2000));
        c.setUpdatedAt(LocalDateTime.now());
        return view(repo.save(c));
    }

    @Transactional
    public Map<String, Object> addPhoto(Long id, MultipartFile file, String caption) {
        RoomChecklist c = editable(id);
        if (file == null || file.isEmpty()) throw bad("Choose a photo");
        if (file.getSize() > 5L * 1024 * 1024) throw new AppException("Photo is larger than 5 MB", HttpStatus.PAYLOAD_TOO_LARGE);
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!PHOTO_TYPES.contains(ct)) throw bad("Upload a JPG, PNG or WebP photo");
        if (photoRepo.countByChecklistId(id) >= MAX_PHOTOS) throw bad("Up to " + MAX_PHOTOS + " photos per checklist");
        ChecklistPhoto p = new ChecklistPhoto();
        p.setChecklistId(c.getId());
        p.setBlobPath(storage.uploadFile(file, "checklists/" + c.getHostelId() + "/" + c.getId()));
        p.setCaption(str(caption, 150));
        ChecklistPhoto saved = photoRepo.save(p);
        return photoView(saved);
    }

    @Transactional
    public void deletePhoto(Long photoId) {
        ChecklistPhoto p = photoRepo.findById(photoId).orElseThrow(() -> new AppException("Photo not found", HttpStatus.NOT_FOUND));
        editable(p.getChecklistId());
        photoRepo.delete(p);
        storage.deleteFile(p.getBlobPath());
    }

    public ChecklistPhoto getPhoto(Long photoId) {
        return photoRepo.findById(photoId).orElseThrow(() -> new AppException("Photo not found", HttpStatus.NOT_FOUND));
    }

    /** Staff finished: the resident is asked to confirm. */
    @Transactional
    public RoomChecklist submit(Long id) {
        RoomChecklist c = editable(id);
        c.setStatus("SUBMITTED");
        c.setSubmittedAt(LocalDateTime.now());
        c.setResidentResponse(null);
        c.setResidentComment(null);
        c.setRespondedAt(null);
        c.setUpdatedAt(LocalDateTime.now());
        RoomChecklist saved = repo.save(c);
        String what = "MOVE_IN".equals(c.getKind()) ? "move-in" : "move-out";
        events.publishEvent(new ResidentNoticeEvent(this, c.getHostellerId(), "CHECKLIST_SUBMITTED",
                "Please confirm your room " + what + " checklist",
                "Your hostel recorded the condition of your room at " + what + ". Please check it and confirm, or raise a concern.", "/app/documents"));
        return view(saved);
    }

    @Transactional
    public RoomChecklist respond(Long id, Long hostellerId, String response, String comment, String ip) {
        RoomChecklist c = get(id);
        if (!c.getHostellerId().equals(hostellerId)) throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        if (!"SUBMITTED".equals(c.getStatus())) throw new AppException("This checklist isn't waiting for your confirmation", HttpStatus.CONFLICT);
        String r = response == null ? "" : response.toUpperCase();
        if (!r.equals("ACKNOWLEDGED") && !r.equals("DISPUTED")) throw bad("response must be ACKNOWLEDGED or DISPUTED");
        if (r.equals("DISPUTED") && (comment == null || comment.isBlank())) throw bad("Tell the hostel what's wrong");
        c.setStatus(r);
        c.setResidentResponse(r);
        c.setResidentComment(str(comment, 1000));
        c.setRespondedAt(LocalDateTime.now());
        c.setRespondedIp(ip == null ? null : ip.length() > 64 ? ip.substring(0, 64) : ip);
        c.setUpdatedAt(LocalDateTime.now());
        return view(repo.save(c));
    }

    public RoomChecklist get(Long id) {
        return repo.findById(id).orElseThrow(() -> new AppException("Checklist not found", HttpStatus.NOT_FOUND));
    }

    public RoomChecklist getView(Long id) {
        return view(get(id));
    }

    public List<RoomChecklist> forResident(Long hostellerId) {
        return repo.findByHostellerIdOrderByCreatedAtDesc(hostellerId).stream().map(this::view).toList();
    }

    /** Items marked DAMAGED or MISSING at move-out that were fine at move-in: input for deposit deductions. */
    public List<RoomChecklist.Item> newDamage(Long bookingId) {
        Optional<RoomChecklist> out = repo.findFirstByBookingIdAndKind(bookingId, "MOVE_OUT");
        if (out.isEmpty()) return List.of();
        Map<String, String> before = new HashMap<>();
        repo.findFirstByBookingIdAndKind(bookingId, "MOVE_IN").ifPresent(in -> items(in).forEach(i -> before.put(i.key(), i.condition())));
        return items(out.get()).stream()
                .filter(i -> "DAMAGED".equals(i.condition()) || "MISSING".equals(i.condition()))
                .filter(i -> !i.condition().equals(before.get(i.key())))
                .toList();
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private RoomChecklist editable(Long id) {
        RoomChecklist c = get(id);
        if (!"DRAFT".equals(c.getStatus()) && !"DISPUTED".equals(c.getStatus())) {
            throw new AppException("This checklist was sent to the resident and can't be edited unless they raise a concern", HttpStatus.CONFLICT);
        }
        if ("DISPUTED".equals(c.getStatus())) c.setStatus("DRAFT"); // reopened for correction
        return c;
    }

    private BookingCheckin currentOrLastBooking(Long hostellerId) {
        return bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .orElseGet(() -> bookingRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId).stream().findFirst().orElse(null));
    }

    List<RoomChecklist.Item> items(RoomChecklist c) {
        try {
            return mapper.readValue(c.getItemsJson(), new TypeReference<List<RoomChecklist.Item>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private RoomChecklist view(RoomChecklist c) {
        c.setItems(items(c));
        c.setPhotos(photoRepo.findByChecklistIdOrderByIdAsc(c.getId() == null ? -1L : c.getId()).stream().map(this::photoView).toList());
        return c;
    }

    private Map<String, Object> photoView(ChecklistPhoto p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("caption", p.getCaption());
        try {
            m.put("url", storage.generateSasUrl(p.getBlobPath(), 30));
        } catch (Exception e) {
            m.put("url", null);
        }
        return m;
    }

    private String json(List<RoomChecklist.Item> items) {
        try {
            return mapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String str(Object v, int max) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static AppException bad(String m) { return new AppException(m, HttpStatus.BAD_REQUEST); }
}
