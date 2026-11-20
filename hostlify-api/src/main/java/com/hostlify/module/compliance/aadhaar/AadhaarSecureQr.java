package com.hostlify.module.compliance.aadhaar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Decodes and verifies the UIDAI "Secure QR" printed on Aadhaar letters, PVC cards and e-Aadhaar.
 *
 * Format (UIDAI Secure QR): the QR text is one large base-10 integer. Its big-endian bytes are a GZIP
 * stream. Decompressed: text fields separated by 0xFF, then the holder's photo (JPEG 2000), then 0, 1 or 2
 * SHA-256 hashes (32 bytes each: email/mobile, per the "indicator" field), then a 256-byte RSA signature
 * (SHA256withRSA) over every byte before it, made with UIDAI's private key.
 *
 * Newer versions prefix a version field ("V2", "V3", ...). Field positions after it are the same.
 * Pure Java, no I/O: easy to unit test.
 */
public final class AadhaarSecureQr {

    public static final int SIGNATURE_LEN = 256;
    private static final byte[] JP2_BOX = {0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20};          // JP2 file
    private static final byte[] J2K_SOC = {(byte) 0xFF, 0x4F, (byte) 0xFF, 0x51};                     // raw codestream
    private static final DateTimeFormatter REF_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** Thrown with a message that can be shown to the user. */
    public static class QrException extends RuntimeException {
        public QrException(String msg) { super(msg); }
    }

    public record Parsed(
            String version, int emailMobileIndicator, String referenceId, String last4, LocalDateTime generatedAt,
            String name, String dob, String gender, String careOf, String house, String street, String landmark,
            String location, String vtc, String postOffice, String subDistrict, String district, String state,
            String pincode, String mobileLast4, byte[] photoJp2, byte[] signedBytes, byte[] signature) {

        /** Formatted address, empty parts skipped. */
        public String address() {
            List<String> parts = new ArrayList<>();
            for (String s : new String[]{house, street, landmark, location, vtc, postOffice, subDistrict, district, state, pincode}) {
                if (s != null && !s.isBlank() && !parts.contains(s.trim())) parts.add(s.trim());
            }
            return String.join(", ", parts);
        }
    }

    private AadhaarSecureQr() {}

    /** Parses the QR text. Does not check the signature (see {@link #signatureValid}). */
    public static Parsed parse(String qrText) {
        if (qrText == null || qrText.isBlank()) throw new QrException("No QR code data found");
        String t = qrText.trim();
        if (t.startsWith("<") || t.contains("PrintLetterBarcodeData")) {
            throw new QrException("This is an old Aadhaar QR code that UIDAI did not digitally sign, so it can't be verified. "
                    + "Download a new e-Aadhaar (free) from myaadhaar.uidai.gov.in and scan its QR code.");
        }
        if (t.startsWith("openid4vp://") || t.startsWith("http")) {
            throw new QrException("This QR is from the Aadhaar App share screen, which isn't supported yet. "
                    + "Use the QR code printed on the Aadhaar card or e-Aadhaar PDF.");
        }
        if (!t.matches("\\d{200,}")) throw new QrException("This isn't an Aadhaar Secure QR code");

        byte[] packed = new BigInteger(t).toByteArray();
        if (packed.length > 1 && packed[0] == 0) packed = Arrays.copyOfRange(packed, 1, packed.length);
        byte[] data;
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            data = gz.readNBytes(512 * 1024);
        } catch (IOException e) {
            throw new QrException("The QR code could not be decoded. Try a clearer, closer photo of the QR code.");
        }
        if (data.length < SIGNATURE_LEN + 64) throw new QrException("The QR code data is incomplete");

        int sigStart = data.length - SIGNATURE_LEN;
        byte[] signed = Arrays.copyOfRange(data, 0, sigStart);
        byte[] signature = Arrays.copyOfRange(data, sigStart, data.length);

        int photoStart = indexOf(data, JP2_BOX, 0);
        if (photoStart < 0) photoStart = indexOf(data, J2K_SOC, 0);
        if (photoStart < 0 || photoStart >= sigStart) throw new QrException("The QR code data is not in the expected Aadhaar format");

        // Text fields: everything before the photo, separated by 0xFF (the last 0xFF closes the last field)
        List<String> f = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < photoStart; i++) {
            if (data[i] == (byte) 0xFF) {
                f.add(text(Arrays.copyOfRange(data, start, i)));
                start = i + 1;
            }
        }
        if (start < photoStart) f.add(text(Arrays.copyOfRange(data, start, photoStart)));

        int o = 0;
        String version = null;
        if (!f.isEmpty() && f.get(0).matches("V\\d+")) { version = f.get(0); o = 1; }
        if (f.size() < o + 16) throw new QrException("The QR code data is not in the expected Aadhaar format");

        int indicator;
        try { indicator = Integer.parseInt(f.get(o).trim()); } catch (NumberFormatException e) { indicator = 0; }
        int hashes = indicator == 3 ? 2 : (indicator == 1 || indicator == 2) ? 1 : 0;
        int photoEnd = sigStart - 32 * hashes;
        if (photoEnd <= photoStart) throw new QrException("The QR code data is not in the expected Aadhaar format");

        String ref = f.get(o + 1).trim();
        String last4 = ref.length() >= 4 && ref.substring(0, 4).matches("\\d{4}") ? ref.substring(0, 4) : null;
        LocalDateTime generated = null;
        if (ref.length() >= 4 + 17) {
            try { generated = LocalDateTime.parse(ref.substring(4, 4 + 17), REF_TS); } catch (Exception ignored) { }
        }
        String mobileLast4 = f.size() > o + 16 && f.get(o + 16).matches("\\d{4}") ? f.get(o + 16) : null;

        return new Parsed(version, indicator, ref, last4, generated,
                f.get(o + 2), f.get(o + 3), f.get(o + 4), f.get(o + 5), f.get(o + 8), f.get(o + 13), f.get(o + 7),
                f.get(o + 9), f.get(o + 15), f.get(o + 11), f.get(o + 14), f.get(o + 6), f.get(o + 12),
                f.get(o + 10), mobileLast4, Arrays.copyOfRange(data, photoStart, photoEnd), signed, signature);
    }

    /** True when the signature verifies with any of the given UIDAI public keys. */
    public static boolean signatureValid(Parsed p, List<PublicKey> uidaiKeys) {
        for (PublicKey k : uidaiKeys) {
            try {
                Signature s = Signature.getInstance("SHA256withRSA");
                s.initVerify(k);
                s.update(p.signedBytes());
                if (s.verify(p.signature())) return true;
            } catch (Exception ignored) {
                // wrong key type/size for this certificate: try the next one
            }
        }
        return false;
    }

    private static String text(byte[] b) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(b)).toString().trim();
        } catch (CharacterCodingException e) {
            return new String(b, StandardCharsets.ISO_8859_1).trim();
        }
    }

    static int indexOf(byte[] hay, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    /** Test helper: builds QR text the way UIDAI does (gzip, then base-10). */
    static String encode(byte[] decompressed) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bos)) { gz.write(decompressed); }
        return new BigInteger(1, bos.toByteArray()).toString();
    }
}
