package com.hostlify.module.payment.service;

import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.payment.entity.HostelPaymentSetting;
import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.entity.PaymentRecord;
import com.hostlify.module.payment.repository.PaymentDueRepository;
import com.hostlify.module.payment.repository.PaymentRecordRepository;
import com.hostlify.shared.exception.AppException;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Generates PDF receipts (OpenPDF, free/open source):
 *  - a receipt for any single successful payment, and
 *  - an HRA rent-receipt statement for a financial year (Apr-Mar), one row per month of rent paid.
 * Uses "Rs." because the built-in PDF fonts have no rupee glyph.
 */
@Service
public class ReceiptPdfService {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final Color BRAND = new Color(29, 110, 168);
    private static final Color MUTED = new Color(110, 110, 110);
    private static final Color LINE = new Color(225, 225, 225);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND);
    private static final Font H = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font N = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
    private static final Font BIG = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);

    private final PaymentRecordRepository recordRepo;
    private final PaymentDueRepository dueRepo;
    private final HostellerProfileRepository profileRepo;
    private final HostelRepository hostelRepo;
    private final BookingCheckinRepository bookingRepo;
    private final RoomRepository roomRepo;
    private final PaymentSettingsService settingsService;

    public ReceiptPdfService(PaymentRecordRepository recordRepo, PaymentDueRepository dueRepo,
                             HostellerProfileRepository profileRepo, HostelRepository hostelRepo,
                             BookingCheckinRepository bookingRepo, RoomRepository roomRepo,
                             PaymentSettingsService settingsService) {
        this.recordRepo = recordRepo;
        this.dueRepo = dueRepo;
        this.profileRepo = profileRepo;
        this.hostelRepo = hostelRepo;
        this.bookingRepo = bookingRepo;
        this.roomRepo = roomRepo;
        this.settingsService = settingsService;
    }

    public static String receiptNumber(PaymentRecord r) {
        return "HST-" + r.getHostelId() + "-" + String.format("%06d", r.getId());
    }

    public static String typeLabel(String dueType) {
        if (dueType == null) return "Rent";
        return switch (dueType) {
            case "RENT" -> "Rent";
            case "DEPOSIT" -> "Security deposit";
            case "MESS" -> "Mess charges";
            case "ELECTRICITY" -> "Electricity charges";
            case "FINE" -> "Fine";
            case "LATE_FEE" -> "Late fee";
            default -> "Other charges";
        };
    }

    public static String periodLabel(int month, int year) {
        return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
    }

    // ─── Single payment receipt ─────────────────────────────────────────────

    public byte[] paymentReceipt(Long recordId) {
        PaymentRecord rec = recordRepo.findById(recordId)
                .orElseThrow(() -> new AppException("Payment not found", HttpStatus.NOT_FOUND));
        if (!rec.isSuccessful()) {
            throw new AppException("A receipt is available once the payment is confirmed", HttpStatus.CONFLICT);
        }
        PaymentDue due = rec.getDueId() != null ? dueRepo.findById(rec.getDueId()).orElse(null) : null;
        HostellerProfile p = profileRepo.findById(rec.getHostellerId()).orElse(null);
        Hostel hostel = hostelRepo.findById(rec.getHostelId()).orElse(null);
        HostelPaymentSetting s = settingsService.getOrDefault(rec.getHostelId());

        String type = due != null ? due.getDueType() : "OTHER";
        boolean rent = PaymentDue.TYPE_RENT.equals(type);
        String payer = fullName(p);
        String period = due != null ? periodLabel(due.getPeriodMonth(), due.getPeriodYear()) : "-";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A5.rotate(), 36, 36, 30, 30);
        PdfWriter.getInstance(doc, out);
        doc.open();

        header(doc, hostel, rent ? "RENT RECEIPT" : "PAYMENT RECEIPT");

        PdfPTable meta = new PdfPTable(new float[]{1, 1});
        meta.setWidthPercentage(100);
        meta.addCell(plain("Receipt No: " + receiptNumber(rec), H, Element.ALIGN_LEFT));
        meta.addCell(plain("Date: " + fmt(rec.getPaymentDate()), H, Element.ALIGN_RIGHT));
        doc.add(meta);
        doc.add(spacer(6));

        String place = hostel != null ? hostel.getName() + addressSuffix(hostel) : "the hostel";
        String room = roomOf(due);
        Paragraph text = new Paragraph(14,
                "Received with thanks from " + payer + " a sum of Rs. " + money(rec.getAmount())
                        + " (Rupees " + inWords(rec.getAmount()) + " only) towards "
                        + typeLabel(type).toLowerCase() + " for " + period
                        + (rent ? " for accommodation" + (room != null ? " (Room " + room + ")" : "") + " at " + place : " at " + place)
                        + (due != null && due.getDescription() != null ? " - " + due.getDescription() : "") + ".",
                N);
        doc.add(text);
        doc.add(spacer(8));

        PdfPTable t = new PdfPTable(new float[]{1.3f, 2f});
        t.setWidthPercentage(100);
        row(t, "Amount received", "Rs. " + money(rec.getAmount()), true);
        row(t, "Payment mode", methodLabel(rec.getPaymentMethod()), false);
        if (ref(rec) != null) row(t, "Reference", ref(rec), false);
        if (due != null) {
            BigDecimal paid = recordRepo.findByDueId(due.getId()).stream()
                    .filter(PaymentRecord::isSuccessful).map(PaymentRecord::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal balance = due.getRentAmount().subtract(paid).max(BigDecimal.ZERO);
            row(t, "Charge", typeLabel(type) + " - " + period + " (Rs. " + money(due.getRentAmount()) + ")", false);
            row(t, "Balance after this payment", "Rs. " + money(balance), false);
        }
        doc.add(t);

        landlordBlock(doc, s, hostel);
        footer(doc, rec.getPaymentMethod(), rec.getAmount(), rent);
        doc.close();
        return out.toByteArray();
    }

    // ─── HRA financial-year statement ───────────────────────────────────────

    /** @param fyStartYear 2026 means FY 2026-27 (1 Apr 2026 - 31 Mar 2027). */
    public byte[] hraStatement(Long hostellerId, int fyStartYear) {
        HostellerProfile p = profileRepo.findById(hostellerId)
                .orElseThrow(() -> new AppException("Resident not found", HttpStatus.NOT_FOUND));
        LocalDate from = LocalDate.of(fyStartYear, 4, 1);
        LocalDate to = LocalDate.of(fyStartYear + 1, 3, 31);

        List<PaymentDue> rentDues = new ArrayList<>(dueRepo.findByHostellerIdOrderByDueDateDesc(hostellerId).stream()
                .filter(d -> PaymentDue.TYPE_RENT.equals(d.getDueType()))
                .filter(d -> {
                    LocalDate m = LocalDate.of(d.getPeriodYear(), d.getPeriodMonth(), 1);
                    return !m.isBefore(from) && !m.isAfter(to);
                })
                .toList());
        rentDues.sort(Comparator.comparing((PaymentDue d) -> d.getPeriodYear()).thenComparing(PaymentDue::getPeriodMonth));

        record Line(PaymentDue due, BigDecimal paid, LocalDate lastPaidOn, String modes, String receipts) {}
        List<Line> lines = new ArrayList<>();
        for (PaymentDue d : rentDues) {
            List<PaymentRecord> recs = recordRepo.findByDueId(d.getId()).stream().filter(PaymentRecord::isSuccessful).toList();
            if (recs.isEmpty()) continue;
            BigDecimal paid = recs.stream().map(PaymentRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            LocalDate last = recs.stream().map(PaymentRecord::getPaymentDate).filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            String modes = String.join(", ", recs.stream().map(r -> methodLabel(r.getPaymentMethod())).distinct().toList());
            String receipts = String.join(", ", recs.stream().map(ReceiptPdfService::receiptNumber).toList());
            lines.add(new Line(d, paid, last, modes, receipts));
        }
        if (lines.isEmpty()) {
            throw new AppException("No rent payments found for FY " + fyStartYear + "-" + String.valueOf(fyStartYear + 1).substring(2),
                    HttpStatus.NOT_FOUND);
        }

        Long hostelId = lines.get(lines.size() - 1).due().getHostelId();
        Hostel hostel = hostelRepo.findById(hostelId).orElse(null);
        HostelPaymentSetting s = settingsService.getOrDefault(hostelId);
        BigDecimal total = lines.stream().map(Line::paid).reduce(BigDecimal.ZERO, BigDecimal::add);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();

        header(doc, hostel, "RENT RECEIPT - FY " + fyStartYear + "-" + String.valueOf(fyStartYear + 1).substring(2) + " (for HRA)");
        doc.add(new Paragraph(14, "Tenant: " + fullName(p), H));
        doc.add(new Paragraph(14, "Rented premises: " + (hostel != null ? hostel.getName() + addressSuffix(hostel) : "-"), N));
        doc.add(spacer(8));

        PdfPTable t = new PdfPTable(new float[]{1.4f, 1.1f, 1.1f, 1.2f, 2.2f});
        t.setWidthPercentage(100);
        for (String h : List.of("Month", "Rent paid (Rs.)", "Paid on", "Mode", "Receipt no.")) t.addCell(headCell(h));
        for (Line l : lines) {
            t.addCell(cell(periodLabel(l.due().getPeriodMonth(), l.due().getPeriodYear()), N));
            t.addCell(cellRight(money(l.paid()), N));
            t.addCell(cell(fmt(l.lastPaidOn()), N));
            t.addCell(cell(l.modes(), N));
            t.addCell(cell(l.receipts(), SMALL));
        }
        t.addCell(cell("Total", H));
        t.addCell(cellRight(money(total), H));
        PdfPCell blank = cell("", N);
        blank.setColspan(3);
        t.addCell(blank);
        doc.add(t);
        doc.add(spacer(10));

        doc.add(new Paragraph(14, "I confirm that I have received a total of Rs. " + money(total) + " (Rupees " + inWords(total)
                + " only) from " + fullName(p) + " as rent for the months listed above.", N));

        landlordBlock(doc, s, hostel);
        if (total.compareTo(new BigDecimal("100000")) > 0 && (s.getLandlordPan() == null || s.getLandlordPan().isBlank())) {
            doc.add(spacer(4));
            doc.add(new Paragraph(11, "Note: annual rent exceeds Rs. 1,00,000. Employers usually require the landlord's PAN; "
                    + "ask the hostel to add it in Settings > Payments.", SMALL));
        }
        footer(doc, null, null, true);
        doc.close();
        return out.toByteArray();
    }

    // ─── building blocks ────────────────────────────────────────────────────

    private void header(Document doc, Hostel hostel, String title) {
        Paragraph name = new Paragraph(hostel != null ? hostel.getName() : "Hostlify", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
        doc.add(name);
        if (hostel != null) {
            String addr = addressLine(hostel);
            if (!addr.isBlank()) doc.add(new Paragraph(11, addr, SMALL));
            if (hostel.getContactPhone() != null) doc.add(new Paragraph(11, "Phone: " + hostel.getContactPhone(), SMALL));
        }
        doc.add(spacer(6));
        Paragraph t = new Paragraph(title, TITLE);
        t.setAlignment(Element.ALIGN_CENTER);
        doc.add(t);
        doc.add(spacer(8));
    }

    private void landlordBlock(Document doc, HostelPaymentSetting s, Hostel hostel) {
        doc.add(spacer(14));
        PdfPTable t = new PdfPTable(new float[]{1.4f, 1});
        t.setWidthPercentage(100);
        StringBuilder sb = new StringBuilder();
        String landlord = s.getLandlordName() != null ? s.getLandlordName() : (hostel != null ? hostel.getName() : "-");
        sb.append("Landlord / Owner: ").append(landlord);
        if (s.getLandlordPan() != null) sb.append("\nPAN: ").append(s.getLandlordPan());
        if (s.getLandlordAddress() != null) sb.append("\nAddress: ").append(s.getLandlordAddress());
        t.addCell(plain(sb.toString(), N, Element.ALIGN_LEFT));
        PdfPCell sign = plain("\n\n______________________\nSignature of landlord", N, Element.ALIGN_RIGHT);
        sign.setVerticalAlignment(Element.ALIGN_BOTTOM);
        t.addCell(sign);
        doc.add(t);
    }

    private void footer(Document doc, String method, BigDecimal amount, boolean rent) {
        doc.add(spacer(10));
        String note = "This is a computer-generated receipt issued via Hostlify.";
        if (rent && "CASH".equals(method) && amount != null && amount.compareTo(new BigDecimal("5000")) > 0) {
            note += " For cash rent above Rs. 5,000, a signed receipt with a revenue stamp may be required by your employer.";
        }
        doc.add(new Paragraph(11, note, SMALL));
    }

    private static PdfPCell plain(String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        c.setPadding(2);
        return c;
    }

    private static void row(PdfPTable t, String label, String value, boolean strong) {
        PdfPCell l = cell(label, SMALL);
        PdfPCell v = cell(value, strong ? BIG : N);
        t.addCell(l);
        t.addCell(v);
    }

    private static PdfPCell headCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        c.setBackgroundColor(BRAND);
        c.setPadding(5);
        c.setBorderColor(BRAND);
        return c;
    }

    private static PdfPCell cell(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "-" : text, f));
        c.setPadding(5);
        c.setBorderColor(LINE);
        return c;
    }

    private static PdfPCell cellRight(String text, Font f) {
        PdfPCell c = cell(text, f);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private static Paragraph spacer(float h) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(h);
        return p;
    }

    private String roomOf(PaymentDue due) {
        if (due == null || due.getBookingId() == null) return null;
        return bookingRepo.findById(due.getBookingId())
                .flatMap(b -> roomRepo.findById(b.getRoomId()))
                .map(r -> r.getRoomName())
                .orElse(null);
    }

    private static String fullName(HostellerProfile p) {
        if (p == null) return "-";
        return (p.getFirstName() + " " + (p.getLastName() != null ? p.getLastName() : "")).trim();
    }

    private static String addressLine(Hostel h) {
        List<String> parts = new ArrayList<>();
        if (h.getAddress() != null && !h.getAddress().isBlank()) parts.add(h.getAddress().trim());
        if (h.getCity() != null && (h.getAddress() == null || !h.getAddress().contains(h.getCity()))) parts.add(h.getCity());
        if (h.getPincode() != null && (h.getAddress() == null || !h.getAddress().contains(h.getPincode()))) parts.add(h.getPincode());
        return String.join(", ", parts);
    }

    private static String addressSuffix(Hostel h) {
        String a = addressLine(h);
        return a.isBlank() ? "" : ", " + a;
    }

    private static String ref(PaymentRecord r) {
        return r.getRazorpayPaymentId() != null ? r.getRazorpayPaymentId() : r.getTransactionRef();
    }

    private static String methodLabel(String m) {
        if (m == null) return "-";
        return switch (m) {
            case "CASH" -> "Cash";
            case "UPI", "UPI_MANUAL", "UPI_DIRECT" -> "UPI";
            case "BANK_TRANSFER" -> "Bank transfer";
            case "CHEQUE" -> "Cheque";
            default -> m.startsWith("RAZORPAY") ? "Online (Razorpay)" : m.replace('_', ' ');
        };
    }

    private static String fmt(LocalDate d) {
        return d == null ? "-" : d.format(D);
    }

    /** Indian grouping: 1,25,000.00 */
    static String money(BigDecimal amount) {
        if (amount == null) return "0.00";
        BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);
        String[] parts = a.abs().toPlainString().split("\\.");
        String i = parts[0];
        String grouped;
        if (i.length() <= 3) {
            grouped = i;
        } else {
            String last3 = i.substring(i.length() - 3);
            String rest = i.substring(0, i.length() - 3);
            StringBuilder sb = new StringBuilder();
            while (rest.length() > 2) {
                sb.insert(0, "," + rest.substring(rest.length() - 2));
                rest = rest.substring(0, rest.length() - 2);
            }
            grouped = rest + sb + "," + last3;
        }
        return (a.signum() < 0 ? "-" : "") + grouped + "." + parts[1];
    }

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    /** Indian system words: 125000.50 -> "One Lakh Twenty Five Thousand and Fifty Paise" */
    static String inWords(BigDecimal amount) {
        if (amount == null) return "Zero";
        BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP).abs();
        long rupees = a.longValue();
        int paise = a.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String r = rupees == 0 ? "Zero" : words(rupees).trim();
        return paise > 0 ? r + " and " + below100(paise).trim() + " Paise" : r;
    }

    private static String words(long n) {
        StringBuilder sb = new StringBuilder();
        long crore = n / 10000000; n %= 10000000;
        long lakh = n / 100000; n %= 100000;
        long thousand = n / 1000; n %= 1000;
        long hundred = n / 100; n %= 100;
        if (crore > 0) sb.append(words(crore)).append(" Crore ");
        if (lakh > 0) sb.append(below100((int) lakh)).append(" Lakh ");
        if (thousand > 0) sb.append(below100((int) thousand)).append(" Thousand ");
        if (hundred > 0) sb.append(ONES[(int) hundred]).append(" Hundred ");
        if (n > 0) sb.append(below100((int) n));
        return sb.toString().replaceAll("\\s+", " ");
    }

    private static String below100(int n) {
        if (n < 20) return ONES[n];
        return (TENS[n / 10] + " " + ONES[n % 10]).trim();
    }
}
