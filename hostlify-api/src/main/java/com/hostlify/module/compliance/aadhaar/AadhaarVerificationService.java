package com.hostlify.module.compliance.aadhaar;

import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Free, offline Aadhaar check: verifies UIDAI's digital signature on the Secure QR with UIDAI's public
 * certificate(s), placed in {@code app.aadhaar.cert-dir} (.cer/.crt/.pem). Several files may be present
 * (UIDAI rotates keys); a QR is genuine if any of them verifies it.
 */
@Service
public class AadhaarVerificationService {

    private static final Logger log = LoggerFactory.getLogger(AadhaarVerificationService.class);

    private final IdentityVerificationRepository repo;
    private final HostellerProfileRepository profileRepo;

    @Value("${app.aadhaar.cert-dir:./aadhaar-certs}")
    private String certDir;

    private volatile List<PublicKey> keys = List.of();
    private volatile List<String> certInfo = List.of();
    private volatile long certDirStamp = -1;

    public AadhaarVerificationService(IdentityVerificationRepository repo, HostellerProfileRepository profileRepo) {
        this.repo = repo;
        this.profileRepo = profileRepo;
    }

    // ─── certificates ──────────────────────────────────────────────────────

    /** Loads certificates, re-reading the folder when its contents change (no restart needed). */
    synchronized List<PublicKey> keys() {
        Path dir = Path.of(certDir);
        long stamp = folderStamp(dir);
        if (stamp == certDirStamp) return keys;
        List<PublicKey> k = new ArrayList<>();
        List<String> info = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : files.filter(f -> f.toString().toLowerCase().matches(".*\\.(cer|crt|pem)$")).sorted().toList()) {
                    try (InputStream in = Files.newInputStream(p)) {
                        for (var c : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                            X509Certificate x = (X509Certificate) c;
                            k.add(x.getPublicKey());
                            info.add(p.getFileName() + " (" + x.getSubjectX500Principal().getName() + ", valid to " + x.getNotAfter().toInstant().toString().substring(0, 10) + ")");
                        }
                    } catch (Exception e) {
                        log.warn("Skipping {}: not a valid X.509 certificate ({})", p.getFileName(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Cannot read Aadhaar certificate folder {}: {}", dir, e.getMessage());
            }
        }
        keys = List.copyOf(k);
        certInfo = List.copyOf(info);
        certDirStamp = stamp;
        log.info("Aadhaar QR verification: {} UIDAI certificate(s) loaded from {}", k.size(), dir.toAbsolutePath());
        return keys;
    }

    private static long folderStamp(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.list(dir)) {
            return s.mapToLong(p -> { try { return Files.getLastModifiedTime(p).toMillis() ^ Files.size(p); } catch (Exception e) { return 0; } })
                    .reduce(17, (a, b) -> a * 31 + b);
        } catch (Exception e) {
            return 0;
        }
    }

    public Map<String, Object> setupStatus() {
        List<PublicKey> k = keys();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ready", !k.isEmpty());
        m.put("certificates", certInfo);
        return m;
    }

    // ─── verify ────────────────────────────────────────────────────────────

    /**
     * Verifies a QR for a resident. Returns details to show once (incl. photo + address), but stores only
     * the minimal result. {@code qrText} comes from {@link QrImageDecoder}.
     */
    @Transactional(noRollbackFor = AppException.class)
    public Map<String, Object> verify(Long hostellerId, String qrText, boolean consent, Long actorUserId, String actorRole) {
        if (!consent) throw new AppException("Consent is required before checking Aadhaar", HttpStatus.BAD_REQUEST);
        List<PublicKey> k = keys();
        if (k.isEmpty()) {
            throw new AppException("Aadhaar verification isn't set up yet: the UIDAI certificate is missing on the server. Ask the Hostlify admin.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        HostellerProfile profile = profileRepo.findById(hostellerId)
                .orElseThrow(() -> new AppException("Resident not found", HttpStatus.NOT_FOUND));

        AadhaarSecureQr.Parsed p;
        try {
            p = AadhaarSecureQr.parse(qrText);
        } catch (AadhaarSecureQr.QrException e) {
            throw new AppException(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        IdentityVerification v = new IdentityVerification();
        v.setHostellerId(hostellerId);
        v.setHostelId(profile.getHostelId());
        v.setConsentAt(LocalDateTime.now());
        v.setPerformedBy(actorUserId);
        v.setPerformedByRole(actorRole);
        v.setAadhaarLast4(p.last4());
        v.setQrGeneratedAt(p.generatedAt());

        if (!AadhaarSecureQr.signatureValid(p, k)) {
            v.setStatus("FAILED");
            v.setFailureReason("UIDAI digital signature did not match: the QR or its data has been altered, or it is not a genuine Aadhaar QR");
            repo.save(v);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("verified", false);
            res.put("message", "Not genuine: UIDAI's digital signature on this QR code does not match. The Aadhaar may be fake or edited.");
            return res;
        }

        v.setStatus("VERIFIED");
        v.setNameOnAadhaar(limit(p.name(), 150));
        v.setDobOnAadhaar(limit(p.dob(), 12));
        v.setGenderOnAadhaar(genderLabel(p.gender()));
        v.setStateOnAadhaar(limit(p.state(), 60));
        v.setNameMatches(nameMatches(profile, p.name()));
        v.setDobMatches(dobMatches(profile.getDateOfBirth(), p.dob()));
        IdentityVerification saved = repo.save(v);

        Map<String, Object> res = view(saved);
        res.put("verified", true);
        res.put("message", "Genuine Aadhaar: the details below are signed by UIDAI and have not been altered.");
        // Shown once, never stored:
        res.put("address", p.address());
        res.put("careOf", p.careOf());
        res.put("photo", photoDataUrl(p.photoJp2()));
        return res;
    }

    @Transactional
    public Map<String, Object> confirmPhoto(Long verificationId, Long staffUserId) {
        IdentityVerification v = get(verificationId);
        if (!"VERIFIED".equals(v.getStatus())) throw new AppException("Only a verified Aadhaar can be confirmed", HttpStatus.CONFLICT);
        v.setPhotoConfirmedBy(staffUserId);
        v.setPhotoConfirmedAt(LocalDateTime.now());
        return view(repo.save(v));
    }

    public IdentityVerification get(Long id) {
        return repo.findById(id).orElseThrow(() -> new AppException("Verification not found", HttpStatus.NOT_FOUND));
    }

    /** Latest successful verification for a resident, or {verified:false}. */
    public Map<String, Object> status(Long hostellerId) {
        return repo.findFirstByHostellerIdAndStatusOrderByCreatedAtDesc(hostellerId, "VERIFIED")
                .map(v -> { Map<String, Object> m = view(v); m.put("verified", true); return m; })
                .orElseGet(() -> { Map<String, Object> m = new LinkedHashMap<>(); m.put("verified", false); return m; });
    }

    public Set<Long> verifiedResidents(Long hostelId) {
        Set<Long> ids = new HashSet<>();
        repo.findByHostelIdAndStatus(hostelId, "VERIFIED").forEach(v -> ids.add(v.getHostellerId()));
        return ids;
    }

    private Map<String, Object> view(IdentityVerification v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("status", v.getStatus());
        m.put("aadhaarLast4", v.getAadhaarLast4());
        m.put("maskedAadhaar", v.getAadhaarLast4() != null ? "XXXX XXXX " + v.getAadhaarLast4() : null);
        m.put("name", v.getNameOnAadhaar());
        m.put("dob", v.getDobOnAadhaar());
        m.put("gender", v.getGenderOnAadhaar());
        m.put("state", v.getStateOnAadhaar());
        m.put("nameMatches", v.getNameMatches());
        m.put("dobMatches", v.getDobMatches());
        m.put("qrGeneratedAt", v.getQrGeneratedAt());
        m.put("verifiedAt", v.getCreatedAt() != null ? v.getCreatedAt() : LocalDateTime.now());
        m.put("photoConfirmed", v.getPhotoConfirmedAt() != null);
        m.put("photoConfirmedAt", v.getPhotoConfirmedAt());
        return m;
    }

    // ─── matching helpers (package-private for tests) ─────────────────────

    static boolean nameMatches(HostellerProfile p, String aadhaarName) {
        Set<String> a = tokens(aadhaarName);
        Set<String> mine = tokens(p.getFirstName() + " " + (p.getLastName() == null ? "" : p.getLastName()));
        if (a.isEmpty() || mine.isEmpty()) return false;
        // Every full word of the shorter name appears in the longer one (handles initials/middle names loosely)
        Set<String> small = a.size() <= mine.size() ? a : mine, big = small == a ? mine : a;
        long hits = small.stream().filter(t -> t.length() == 1 ? big.stream().anyMatch(b -> b.startsWith(t)) : big.contains(t)).count();
        return hits == small.size();
    }

    static Boolean dobMatches(LocalDate profileDob, String aadhaarDob) {
        if (profileDob == null || aadhaarDob == null || aadhaarDob.isBlank()) return null;
        String d = aadhaarDob.trim();
        for (String pattern : new String[]{"dd-MM-yyyy", "dd/MM/yyyy", "yyyy-MM-dd"}) {
            try { return LocalDate.parse(d, DateTimeFormatter.ofPattern(pattern)).equals(profileDob); } catch (Exception ignored) { }
        }
        if (d.matches("\\d{4}")) return Integer.parseInt(d) == profileDob.getYear(); // some Aadhaar have year only
        return null;
    }

    private static Set<String> tokens(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s == null) return out;
        for (String t : s.toUpperCase().replaceAll("[^A-Z ]", " ").trim().split("\\s+")) if (!t.isBlank()) out.add(t);
        return out;
    }

    private static String genderLabel(String g) {
        if (g == null) return null;
        return switch (g.trim().toUpperCase()) { case "M" -> "MALE"; case "F" -> "FEMALE"; case "T" -> "TRANSGENDER"; default -> limit(g.trim(), 10); };
    }

    private static String limit(String s, int n) {
        return s == null ? null : s.length() <= n ? s : s.substring(0, n);
    }

    /** JPEG 2000 → JPEG data URL so any browser can show it. Null when the image can't be decoded. */
    private static String photoDataUrl(byte[] jp2) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jp2));
            if (img == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.getGraphics().drawImage(img, 0, 0, null);
            ImageIO.write(rgb, "jpg", out);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }
}
