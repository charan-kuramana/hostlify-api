package com.hostlify.module.compliance;

import com.hostlify.module.compliance.agreement.RentalAgreement;
import com.hostlify.module.compliance.checklist.RoomChecklist;
import com.hostlify.module.compliance.settlement.DepositSettlement;
import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** PDFs for the agreement (with its e-acceptance record), room checklists and the deposit settlement. */
@Service
public class CompliancePdfService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final Color BRAND = new Color(29, 110, 168);
    private static final Color MUTED = new Color(110, 110, 110);
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, BRAND);
    private static final Font H = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font N = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);

    private final HostelRepository hostelRepo;
    private final HostellerProfileRepository profileRepo;

    public CompliancePdfService(HostelRepository hostelRepo, HostellerProfileRepository profileRepo) {
        this.hostelRepo = hostelRepo;
        this.profileRepo = profileRepo;
    }

    public byte[] agreement(RentalAgreement a) {
        return doc(a.getHostelId(), a.getTitle(), doc -> {
            for (String para : a.getBody().split("\\n\\s*\\n")) {
                Paragraph p = new Paragraph(14, para.trim(), N);
                p.setSpacingAfter(7);
                p.setAlignment(Element.ALIGN_JUSTIFIED);
                doc.add(p);
            }
            doc.add(new Paragraph(" "));
            PdfPTable t = table(1.2f, 2.5f);
            if ("ACCEPTED".equals(a.getStatus())) {
                header(t, "Electronic acceptance record");
                row(t, "Accepted by", a.getAcceptedName());
                row(t, "Date & time", a.getAcceptedAt() == null ? "-" : a.getAcceptedAt().format(DT) + " IST");
                row(t, "Verification", a.isAcceptedWithOtp() ? "Signed-in account + one-time code sent to registered email" : "Signed-in account (password login)");
                row(t, "IP address", a.getAcceptedIp());
            } else {
                header(t, "Status");
                row(t, "Status", "VOID".equals(a.getStatus()) ? "Void" + (a.getVoidReason() != null ? " - " + a.getVoidReason() : "") : "Awaiting acceptance");
            }
            row(t, "Document fingerprint (SHA-256)", a.getBodySha256());
            doc.add(t);
        });
    }

    public byte[] checklist(RoomChecklist c) {
        String title = ("MOVE_IN".equals(c.getKind()) ? "Move-in" : "Move-out") + " room checklist";
        return doc(c.getHostelId(), title, doc -> {
            HostellerProfile p = profileRepo.findById(c.getHostellerId()).orElse(null);
            doc.add(new Paragraph(14, "Resident: " + name(p), H));
            doc.add(new Paragraph(" "));
            PdfPTable t = new PdfPTable(new float[]{2.2f, 1f, 2.4f});
            t.setWidthPercentage(100);
            for (String h : new String[]{"Item", "Condition", "Note"}) t.addCell(head(h));
            for (RoomChecklist.Item i : c.getItems()) {
                t.addCell(cell(i.label(), N));
                Font f = "DAMAGED".equals(i.condition()) || "MISSING".equals(i.condition()) ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(190, 30, 45)) : N;
                t.addCell(cell("NA".equals(i.condition()) ? "N/A" : cap(i.condition()), f));
                t.addCell(cell(i.note() == null ? "" : i.note(), N));
            }
            doc.add(t);
            doc.add(new Paragraph(" "));
            PdfPTable m = table(1.2f, 2.5f);
            if (c.getKeysHanded() != null) row(m, "Keys", String.valueOf(c.getKeysHanded()));
            if (c.getMeterReading() != null) row(m, "Meter reading", c.getMeterReading());
            if (c.getStaffNotes() != null) row(m, "Staff notes", c.getStaffNotes());
            row(m, "Photos on record", String.valueOf(c.getPhotos() == null ? 0 : c.getPhotos().size()));
            row(m, "Recorded on", c.getSubmittedAt() == null ? "Draft" : c.getSubmittedAt().format(DT));
            row(m, "Resident response", c.getResidentResponse() == null ? "Pending"
                    : cap(c.getResidentResponse()) + (c.getRespondedAt() != null ? " on " + c.getRespondedAt().format(DT) : "")
                    + (c.getResidentComment() != null ? " - \"" + c.getResidentComment() + "\"" : ""));
            doc.add(m);
        });
    }

    public byte[] settlement(DepositSettlement s) {
        return doc(s.getHostelId(), "Deposit settlement", doc -> {
            HostellerProfile p = profileRepo.findById(s.getHostellerId()).orElse(null);
            doc.add(new Paragraph(14, "Resident: " + name(p), H));
            doc.add(new Paragraph(14, "Settlement no: DS-" + s.getHostelId() + "-" + String.format("%05d", s.getId())
                    + (s.getFinalizedAt() != null ? "   Date: " + s.getFinalizedAt().format(DT) : ""), N));
            doc.add(new Paragraph(" "));
            PdfPTable t = new PdfPTable(new float[]{3f, 1.2f});
            t.setWidthPercentage(100);
            t.addCell(head("Description"));
            t.addCell(head("Amount (Rs.)"));
            line(t, "Security deposit held", s.getDepositHeld(), false);
            line(t, "Less: unpaid dues adjusted", s.getUnpaidDues().negate(), false);
            for (DepositSettlement.Deduction d : s.getDeductions()) line(t, "Less: " + d.label(), d.amount().negate(), false);
            if (s.getBalanceDue().signum() > 0) line(t, "Balance payable by resident", s.getBalanceDue(), true);
            else line(t, "Refund to resident", s.getRefundAmount(), true);
            doc.add(t);
            if (s.getNotes() != null) {
                doc.add(new Paragraph(" "));
                doc.add(new Paragraph(13, "Notes: " + s.getNotes(), N));
            }
            if ("REFUNDED".equals(s.getStatus())) {
                doc.add(new Paragraph(" "));
                doc.add(new Paragraph(13, "Refund paid by " + s.getRefundMethod().replace('_', ' ').toLowerCase()
                        + (s.getRefundReference() != null ? " (ref " + s.getRefundReference() + ")" : "")
                        + " on " + s.getRefundedAt().format(DT) + ".", N));
            }
        });
    }

    // ─── building blocks ───────────────────────────────────────────────────

    private interface Body { void write(Document d) throws DocumentException; }

    private byte[] doc(Long hostelId, String title, Body body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document d = new Document(PageSize.A4, 46, 46, 40, 40);
        PdfWriter.getInstance(d, out);
        d.open();
        Hostel h = hostelId == null ? null : hostelRepo.findById(hostelId).orElse(null);
        d.add(new Paragraph(h != null ? h.getName() : "Hostlify", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        if (h != null) d.add(new Paragraph(11, String.join(", ", java.util.stream.Stream.of(h.getAddress(), h.getCity(), h.getPincode())
                .filter(s -> s != null && !s.isBlank()).toList()), SMALL));
        Paragraph t = new Paragraph(title, TITLE);
        t.setAlignment(Element.ALIGN_CENTER);
        t.setSpacingBefore(10);
        t.setSpacingAfter(12);
        d.add(t);
        try {
            body.write(d);
        } catch (DocumentException e) {
            throw new IllegalStateException(e);
        }
        Paragraph f = new Paragraph(11, "Generated by Hostlify on " + LocalDateTime.now().format(DT) + ".", SMALL);
        f.setSpacingBefore(14);
        d.add(f);
        d.close();
        return out.toByteArray();
    }

    private static PdfPTable table(float a, float b) {
        PdfPTable t = new PdfPTable(new float[]{a, b});
        t.setWidthPercentage(100);
        return t;
    }

    private static void header(PdfPTable t, String text) {
        PdfPCell c = head(text);
        c.setColspan(2);
        t.addCell(c);
    }

    private static void row(PdfPTable t, String k, String v) {
        t.addCell(cell(k, SMALL));
        t.addCell(cell(v == null ? "-" : v, N));
    }

    private static void line(PdfPTable t, String label, BigDecimal amount, boolean bold) {
        Font f = bold ? H : N;
        t.addCell(cell(label, f));
        PdfPCell c = cell(amount == null ? "0.00" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), f);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c);
    }

    private static PdfPCell head(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        c.setBackgroundColor(BRAND);
        c.setBorderColor(BRAND);
        c.setPadding(5);
        return c;
    }

    private static PdfPCell cell(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setPadding(5);
        c.setBorderColor(new Color(225, 225, 225));
        return c;
    }

    private static String name(HostellerProfile p) {
        return p == null ? "-" : (p.getFirstName() + " " + (p.getLastName() == null ? "" : p.getLastName())).trim();
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : s.charAt(0) + s.substring(1).toLowerCase();
    }
}
