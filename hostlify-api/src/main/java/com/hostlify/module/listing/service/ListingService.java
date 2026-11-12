package com.hostlify.module.listing.service;

import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hostel.service.HostelDiscoveryService;
import com.hostlify.module.listing.entity.HostelPhoto;
import com.hostlify.module.listing.repository.HostelPhotoRepository;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.storage.AzureBlobService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

/** Public hostel pages (no login) and the owner's photo gallery. */
@Service
public class ListingService {

    public static final int MAX_PHOTOS = 20;
    private static final int PUBLIC_LINK_MINUTES = 120;
    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

    private final HostelRepository hostelRepo;
    private final HostelDiscoveryService discovery;
    private final HostelPhotoRepository photoRepo;
    private final AzureBlobService storage;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public ListingService(HostelRepository hostelRepo, HostelDiscoveryService discovery,
                          HostelPhotoRepository photoRepo, AzureBlobService storage) {
        this.hostelRepo = hostelRepo;
        this.discovery = discovery;
        this.photoRepo = photoRepo;
        this.storage = storage;
    }

    // ─── Public pages ──────────────────────────────────────────────────────

    public List<Map<String, Object>> publicList(String city, String type, String roomType, BigDecimal maxRent, String q) {
        List<Map<String, Object>> out = new ArrayList<>();
        String query = q == null ? "" : q.trim().toLowerCase();
        for (Map<String, Object> m : discovery.searchHostels(blankToNull(city), null, blankToNull(roomType), maxRent)) {
            Hostel h = hostelRepo.findById((Long) m.get("id")).orElse(null);
            if (h == null || !h.isListingEnabled()) continue;
            if (type != null && !type.isBlank() && !type.equalsIgnoreCase(h.getHostelType())) continue;
            if (!query.isEmpty()) {
                String hay = (h.getName() + " " + h.getCity() + " " + nz(h.getAddress()) + " " + nz(h.getLandmark())).toLowerCase();
                if (!hay.contains(query)) continue;
            }
            m.remove("address"); // full address is on the detail page
            m.put("locality", h.getLandmark());
            m.put("coverUrl", coverUrl(h.getId()));
            out.add(m);
        }
        // Hostels with free beds first, then cheapest
        out.sort(Comparator
                .comparing((Map<String, Object> m) -> ((Number) m.getOrDefault("availableBeds", 0)).longValue() > 0 ? 0 : 1)
                .thenComparing(m -> m.get("minRent") instanceof BigDecimal b ? b : BigDecimal.valueOf(Long.MAX_VALUE)));
        return out;
    }

    public List<Map<String, Object>> publicCities() {
        Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Hostel h : hostelRepo.findAll()) {
            if (h.isListingEnabled() && h.getCity() != null) counts.merge(h.getCity().trim(), 1, Integer::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        counts.forEach((c, n) -> out.add(Map.of("city", c, "count", n)));
        return out;
    }

    public Hostel listedBySlug(String slug) {
        Hostel h = hostelRepo.findBySlug(slug).orElse(null);
        if (h == null || !h.isListingEnabled()) throw new AppException("Hostel not found", HttpStatus.NOT_FOUND);
        return h;
    }

    public Map<String, Object> publicDetail(String slug) {
        Hostel h = listedBySlug(slug);
        Map<String, Object> d = new LinkedHashMap<>(discovery.getHostelDetail(h.getId()));
        d.put("slug", h.getSlug());
        d.put("pincode", h.getPincode());
        d.put("photos", photos(h.getId(), PUBLIC_LINK_MINUTES));
        // Only enabled amenities, as plain labels
        Object am = d.get("amenities");
        if (am instanceof List<?> list) {
            d.put("amenities", list.stream()
                    .filter(a -> a instanceof com.hostlify.module.hostel.entity.HostelAmenity ha && ha.isEnabled())
                    .map(a -> ((com.hostlify.module.hostel.entity.HostelAmenity) a).getLabel())
                    .toList());
        }
        return d;
    }

    /** sitemap.xml of the public pages, for Google Search Console. */
    public String sitemap() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n")
                .append("  <url><loc>").append(frontendUrl).append("/hostels</loc><changefreq>daily</changefreq></url>\n");
        for (Map<String, Object> c : publicCities()) {
            sb.append("  <url><loc>").append(frontendUrl).append("/hostels?city=")
                    .append(xml(java.net.URLEncoder.encode((String) c.get("city"), java.nio.charset.StandardCharsets.UTF_8)))
                    .append("</loc><changefreq>daily</changefreq></url>\n");
        }
        for (Hostel h : hostelRepo.findAll()) {
            if (!h.isListingEnabled()) continue;
            sb.append("  <url><loc>").append(frontendUrl).append("/hostels/").append(xml(h.getSlug()))
                    .append("</loc><changefreq>weekly</changefreq></url>\n");
        }
        return sb.append("</urlset>\n").toString();
    }

    // ─── Owner: listing switch + photo gallery ─────────────────────────────

    @Transactional
    public Hostel setListing(Long hostelId, Boolean enabled, String slug) {
        Hostel h = hostelRepo.findById(hostelId).orElseThrow(() -> new AppException("Hostel not found", HttpStatus.NOT_FOUND));
        if (slug != null && !slug.isBlank()) {
            String s = Hostel.slugify(slug);
            if (s.length() < 3) throw new AppException("The link name must have at least 3 letters or numbers", HttpStatus.BAD_REQUEST);
            hostelRepo.findBySlug(s).filter(o -> !o.getId().equals(hostelId)).ifPresent(o -> {
                throw new AppException("That link is already taken. Try adding your area name.", HttpStatus.CONFLICT);
            });
            h.setSlug(s);
        }
        if (enabled != null) h.setListingEnabled(enabled);
        return hostelRepo.save(h);
    }

    public List<HostelPhoto> photos(Long hostelId, int linkMinutes) {
        List<HostelPhoto> list = photoRepo.findByHostelIdOrderBySortOrderAscIdAsc(hostelId);
        list.sort(Comparator.comparing((HostelPhoto p) -> p.isCover() ? 0 : 1).thenComparing(HostelPhoto::getSortOrder).thenComparing(HostelPhoto::getId));
        for (HostelPhoto p : list) {
            try {
                p.setUrl(storage.generateSasUrl(p.getBlobPath(), linkMinutes));
            } catch (Exception e) {
                p.setUrl(null);
            }
        }
        return list;
    }

    public List<HostelPhoto> photos(Long hostelId) {
        return photos(hostelId, 30);
    }

    @Transactional
    public HostelPhoto addPhoto(Long hostelId, MultipartFile file, String caption) {
        if (file == null || file.isEmpty()) throw new AppException("Choose a photo to upload", HttpStatus.BAD_REQUEST);
        if (file.getSize() > MAX_PHOTO_BYTES) throw new AppException("Photo is larger than 5 MB", HttpStatus.PAYLOAD_TOO_LARGE);
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String fn = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!PHOTO_TYPES.contains(ct) || !(fn.endsWith(".jpg") || fn.endsWith(".jpeg") || fn.endsWith(".png") || fn.endsWith(".webp"))) {
            throw new AppException("Upload a JPG, PNG or WebP image", HttpStatus.BAD_REQUEST);
        }
        long count = photoRepo.countByHostelId(hostelId);
        if (count >= MAX_PHOTOS) throw new AppException("You can add up to " + MAX_PHOTOS + " photos", HttpStatus.BAD_REQUEST);

        HostelPhoto p = new HostelPhoto();
        p.setHostelId(hostelId);
        p.setBlobPath(storage.uploadFile(file, "hostel-photos/" + hostelId));
        p.setCaption(trimTo(caption, 150));
        p.setSortOrder((int) count);
        p.setCover(count == 0); // first photo becomes the cover
        HostelPhoto saved = photoRepo.save(p);
        saved.setUrl(storage.generateSasUrl(saved.getBlobPath(), 30));
        return saved;
    }

    public HostelPhoto getPhoto(Long photoId) {
        return photoRepo.findById(photoId).orElseThrow(() -> new AppException("Photo not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public void deletePhoto(Long photoId) {
        HostelPhoto p = getPhoto(photoId);
        photoRepo.delete(p);
        storage.deleteFile(p.getBlobPath());
        if (p.isCover()) {
            photoRepo.findByHostelIdOrderBySortOrderAscIdAsc(p.getHostelId()).stream().findFirst().ifPresent(next -> {
                next.setCover(true);
                photoRepo.save(next);
            });
        }
    }

    @Transactional
    public void setCover(Long photoId) {
        HostelPhoto p = getPhoto(photoId);
        for (HostelPhoto o : photoRepo.findByHostelIdOrderBySortOrderAscIdAsc(p.getHostelId())) {
            boolean cover = o.getId().equals(photoId);
            if (o.isCover() != cover) {
                o.setCover(cover);
                photoRepo.save(o);
            }
        }
    }

    @Transactional
    public HostelPhoto updateCaption(Long photoId, String caption) {
        HostelPhoto p = getPhoto(photoId);
        p.setCaption(trimTo(caption, 150));
        return photoRepo.save(p);
    }

    /** Saves the gallery order; ids not belonging to the hostel are ignored. */
    @Transactional
    public void reorder(Long hostelId, List<Long> orderedIds) {
        Map<Long, HostelPhoto> byId = new HashMap<>();
        photoRepo.findByHostelIdOrderBySortOrderAscIdAsc(hostelId).forEach(p -> byId.put(p.getId(), p));
        int i = 0;
        for (Long id : orderedIds) {
            HostelPhoto p = byId.get(id);
            if (p != null) {
                p.setSortOrder(i++);
                photoRepo.save(p);
            }
        }
    }

    private String coverUrl(Long hostelId) {
        Optional<HostelPhoto> cover = photoRepo.findFirstByHostelIdAndCoverTrue(hostelId);
        if (cover.isEmpty()) cover = photoRepo.findByHostelIdOrderBySortOrderAscIdAsc(hostelId).stream().findFirst();
        try {
            return cover.map(p -> storage.generateSasUrl(p.getBlobPath(), PUBLIC_LINK_MINUTES)).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String nz(String s) { return s == null ? "" : s; }
    private static String trimTo(String s, int max) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
