package com.hostlify.module.compliance.aadhaar;

import com.hostlify.module.hosteller.entity.HostellerProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Builds Secure-QR payloads the way UIDAI does (fields, 0xFF delimiters, JPEG 2000 photo, hashes, RSA-2048 signature,
 * gzip, base-10) with a throwaway key, then checks parsing and signature verification.
 */
class AadhaarSecureQrTest {

    static KeyPair uidai;
    static KeyPair attacker;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        uidai = g.generateKeyPair();
        attacker = g.generateKeyPair();
    }

    static byte[] payload(String version, String indicator, String name, KeyPair signer) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        List<String> fields = new java.util.ArrayList<>();
        if (version != null) fields.add(version);
        fields.addAll(List.of(indicator, "9012" + "20261110143015123", name, "15-08-1998", "M", "S/O Ramesh Kumar",
                "Krishna", "Near Benz Circle", "12-3-45", "Patamata", "520010", "Vijayawada", "Andhra Pradesh",
                "MG Road", "Vijayawada Urban", "Vijayawada"));
        for (String f : fields) {
            b.write(f.getBytes(StandardCharsets.UTF_8));
            b.write(0xFF);
        }
        // fake JPEG 2000 photo: JP2 signature box + some bytes (incl. 0xFF, which must not break parsing)
        b.write(new byte[]{0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20, 0x0D, 0x0A, (byte) 0x87, 0x0A, (byte) 0xFF, 1, 2, 3, (byte) 0xFF, 9});
        int hashes = indicator.equals("3") ? 2 : indicator.equals("0") ? 0 : 1;
        for (int i = 0; i < hashes; i++) b.write(new byte[32]);
        byte[] signed = b.toByteArray();
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(signer.getPrivate());
        s.update(signed);
        b.write(s.sign());
        return b.toByteArray();
    }

    @Test
    void parsesV2AndVerifiesGenuineSignature() throws Exception {
        String qr = AadhaarSecureQr.encode(payload("V2", "3", "Sai Krishna Reddy", uidai));
        AadhaarSecureQr.Parsed p = AadhaarSecureQr.parse(qr);
        assertEquals("V2", p.version());
        assertEquals("9012", p.last4());
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 30, 15, 123_000_000), p.generatedAt());
        assertEquals("Sai Krishna Reddy", p.name());
        assertEquals("15-08-1998", p.dob());
        assertEquals("520010", p.pincode());
        assertEquals("Andhra Pradesh", p.state());
        assertEquals(18, p.photoJp2().length, "photo ends where the 2 hashes begin");
        assertTrue(AadhaarSecureQr.signatureValid(p, List.of(uidai.getPublic())));
    }

    @Test
    void parsesLegacyFormatWithoutVersionField() throws Exception {
        AadhaarSecureQr.Parsed p = AadhaarSecureQr.parse(AadhaarSecureQr.encode(payload(null, "0", "Lakshmi Prasanna", uidai)));
        assertNull(p.version());
        assertEquals("Lakshmi Prasanna", p.name());
        assertTrue(AadhaarSecureQr.signatureValid(p, List.of(uidai.getPublic())));
    }

    @Test
    void editedDataFailsVerification() throws Exception {
        byte[] data = payload("V2", "3", "Sai Krishna Reddy", uidai);
        // change one letter of the name after signing (what a forger would do)
        String s = new String(data, StandardCharsets.ISO_8859_1).replaceFirst("Sai", "Sam");
        AadhaarSecureQr.Parsed p = AadhaarSecureQr.parse(AadhaarSecureQr.encode(s.getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals("Sam Krishna Reddy", p.name());
        assertFalse(AadhaarSecureQr.signatureValid(p, List.of(uidai.getPublic())));
    }

    @Test
    void qrSignedByAnyoneElseFails() throws Exception {
        AadhaarSecureQr.Parsed p = AadhaarSecureQr.parse(AadhaarSecureQr.encode(payload("V2", "1", "Fake Person", attacker)));
        assertFalse(AadhaarSecureQr.signatureValid(p, List.of(uidai.getPublic())));
        assertTrue(AadhaarSecureQr.signatureValid(p, List.of(uidai.getPublic(), attacker.getPublic())), "any trusted cert may match");
    }

    @Test
    void rejectsOldUnsignedAndNonAadhaarCodes() {
        assertThrows(AadhaarSecureQr.QrException.class,
                () -> AadhaarSecureQr.parse("<?xml version=\"1.0\"?><PrintLetterBarcodeData uid=\"123412341234\"/>"));
        assertThrows(AadhaarSecureQr.QrException.class, () -> AadhaarSecureQr.parse("https://example.com"));
        assertThrows(AadhaarSecureQr.QrException.class, () -> AadhaarSecureQr.parse("12345"));
    }

    @Test
    void matchesNameAndDob() {
        HostellerProfile p = new HostellerProfile();
        p.setFirstName("Sai Krishna");
        p.setLastName("Reddy");
        assertTrue(AadhaarVerificationService.nameMatches(p, "SAI KRISHNA REDDY"));
        assertTrue(AadhaarVerificationService.nameMatches(p, "Sai Krishna Reddy Kothapalli"));
        assertFalse(AadhaarVerificationService.nameMatches(p, "Ravi Teja"));
        assertEquals(Boolean.TRUE, AadhaarVerificationService.dobMatches(LocalDate.of(1998, 8, 15), "15-08-1998"));
        assertEquals(Boolean.FALSE, AadhaarVerificationService.dobMatches(LocalDate.of(1998, 8, 15), "16-08-1998"));
        assertEquals(Boolean.TRUE, AadhaarVerificationService.dobMatches(LocalDate.of(1998, 8, 15), "1998"));
    }

    @Test
    void masksAadhaarNumbers() {
        assertEquals("XXXX-XXXX-9012", com.hostlify.module.hosteller.service.HostellerService.maskIfAadhaar("AADHAAR", "1234 5678 9012"));
        assertEquals("XXXX-XXXX-9012", com.hostlify.module.hosteller.service.HostellerService.maskIfAadhaar("AADHAAR", "XXXX-XXXX-9012"));
        assertEquals("ABCDE1234F", com.hostlify.module.hosteller.service.HostellerService.maskIfAadhaar("PAN", "ABCDE1234F"));
    }
}
