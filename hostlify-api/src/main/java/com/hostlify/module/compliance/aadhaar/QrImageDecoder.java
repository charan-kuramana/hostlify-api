package com.hostlify.module.compliance.aadhaar;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;

/** Finds the Aadhaar Secure QR in a photo/screenshot or an e-Aadhaar PDF. Everything stays in memory. */
public final class QrImageDecoder {

    private QrImageDecoder() {}

    public static String fromImage(byte[] bytes) {
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            img = null;
        }
        if (img == null) throw new AadhaarSecureQr.QrException("Could not read the image. Upload a JPG or PNG photo/screenshot of the QR code.");
        String text = decode(img);
        if (text == null) throw new AadhaarSecureQr.QrException(
                "No Aadhaar QR code found in the image. Take a sharp, well-lit photo with the whole QR code filling most of the frame.");
        return text;
    }

    /** e-Aadhaar PDFs are password protected: first 4 letters of the name in CAPITALS + year of birth (e.g. RAVI1998). */
    public static String fromPdf(byte[] bytes, String password) {
        try (PDDocument doc = Loader.loadPDF(bytes, password == null ? "" : password.trim())) {
            PDFRenderer r = new PDFRenderer(doc);
            for (int page = 0; page < Math.min(doc.getNumberOfPages(), 3); page++) {
                for (int dpi : new int[]{300, 400}) {
                    String text = decode(r.renderImageWithDPI(page, dpi, ImageType.GRAY));
                    if (text != null) return text;
                }
            }
        } catch (InvalidPasswordException e) {
            throw new AadhaarSecureQr.QrException("Wrong PDF password. It's the first 4 letters of the name in CAPITALS followed by the year of birth, e.g. RAVI1998.");
        } catch (AadhaarSecureQr.QrException e) {
            throw e;
        } catch (Exception e) {
            throw new AadhaarSecureQr.QrException("Could not open the PDF. Upload the e-Aadhaar PDF downloaded from UIDAI.");
        }
        throw new AadhaarSecureQr.QrException("No Aadhaar QR code found in the PDF.");
    }

    /** Tries the image as-is, then scaled versions (dense QRs often decode better slightly up/down-scaled). Prefers the longest (secure) QR. */
    static String decode(BufferedImage img) {
        for (double scale : new double[]{1.0, 0.6, 1.5, 0.4}) {
            BufferedImage candidate = scale == 1.0 ? img : scaled(img, scale);
            if (candidate == null) continue;
            String best = null;
            for (String s : readAll(candidate)) {
                if (best == null || s.length() > best.length()) best = s;
            }
            if (best != null) return best;
        }
        return null;
    }

    private static List<String> readAll(BufferedImage img) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.CHARACTER_SET, "ISO-8859-1");
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)));
        List<String> out = new ArrayList<>();
        try {
            for (Result r : new QRCodeMultiReader().decodeMultiple(bitmap, hints)) out.add(r.getText());
        } catch (NotFoundException ignored) {
            try {
                out.add(new MultiFormatReader().decode(bitmap, hints).getText());
            } catch (NotFoundException ignored2) {
                // nothing here
            }
        }
        return out;
    }

    private static BufferedImage scaled(BufferedImage src, double f) {
        int w = (int) (src.getWidth() * f), h = (int) (src.getHeight() * f);
        if (w < 200 || h < 200 || w > 6000 || h > 6000) return null;
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }
}
