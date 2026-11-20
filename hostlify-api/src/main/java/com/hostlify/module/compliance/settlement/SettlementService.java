package com.hostlify.module.compliance.settlement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.compliance.ResidentNoticeEvent;
import com.hostlify.module.compliance.checklist.ChecklistService;
import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.entity.PaymentRecord;
import com.hostlify.module.payment.repository.PaymentDueRepository;
import com.hostlify.module.payment.repository.PaymentRecordRepository;
import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.module.payment.service.ReceiptPdfService;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.DateUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Deposit settlement at checkout:
 *   refund = deposit held − open dues − deductions (damage, notice-period rent, …); if negative, it's a balance still owed.
 * Finalizing settles open dues from the deposit (as "DEPOSIT_ADJUSTMENT" payments, so receipts and reports stay right),
 * and adds an "Other" due for any deductions the deposit couldn't cover.
 */
@Service
public class SettlementService {

    private final DepositSettlementRepository repo;
    private final BookingCheckinRepository bookingRepo;
    private final PaymentDueRepository dueRepo;
    private final PaymentRecordRepository recordRepo;
    private final PaymentService paymentService;
    private final ChecklistService checklistService;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper = new ObjectMapper();

    public SettlementService(DepositSettlementRepository repo, BookingCheckinRepository bookingRepo, PaymentDueRepository dueRepo,
                             PaymentRecordRepository recordRepo, PaymentService paymentService, ChecklistService checklistService,
                             ApplicationEventPublisher events) {
        this.repo = repo;
        this.bookingRepo = bookingRepo;
        this.dueRepo = dueRepo;
        this.recordRepo = recordRepo;
        this.paymentService = paymentService;
        this.checklistService = checklistService;
        this.events = events;
    }

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.hostlify.module.hostel.policy.PolicyService policies;
    @org.springframework.beans.factory.annotation.Autowired
    private com.hostlify.module.resident.checkout.CheckoutService.Repo checkoutRepo;
    @org.springframework.beans.factory.annotation.Autowired
    private com.hostlify.module.hostel.repository.HostelRepository hostelRepo;

    public BookingCheckin booking(Long bookingId) {
        return bookingRepo.findById(bookingId).orElseThrow(() -> new AppException("Booking not found", HttpStatus.NOT_FOUND));
    }

    /** Opens the settlement for a booking (or returns it), with amounts recalculated while it's a draft. */
    @Transactional
    public DepositSettlement prepare(Long bookingId) {
        BookingCheckin b = booking(bookingId);
        DepositSettlement s = repo.findByBookingId(bookingId).orElseGet(() -> {
            DepositSettlement n = new DepositSettlement();
            n.setBookingId(bookingId);
            n.setHostelId(b.getHostelId());
            n.setHostellerId(b.getHostellerId());
            return n;
        });
        if ("DRAFT".equals(s.getStatus())) recalc(s, b);
        s.setUpdatedAt(LocalDateTime.now());
        return view(repo.save(s), b);
    }

    @Transactional
    public DepositSettlement update(Long id, List<Map<String, Object>> deductions, String notes) {
        DepositSettlement s = get(id);
        if (!"DRAFT".equals(s.getStatus())) throw new AppException("This settlement is already finalized", HttpStatus.CONFLICT);
        if (deductions != null) {
            if (deductions.size() > 30) throw bad("Up to 30 deductions");
            Set<Long> photoIdsOfStay = new HashSet<>();
            checklistService.photosForBooking(s.getBookingId()).forEach(p -> photoIdsOfStay.add(((Number) p.get("id")).longValue()));
            List<DepositSettlement.Deduction> clean = new ArrayList<>();
            for (Map<String, Object> d : deductions) {
                String label = d.get("label") == null ? "" : String.valueOf(d.get("label")).trim();
                if (label.isEmpty() || label.length() > 120) throw bad("Each deduction needs a reason (max 120 characters)");
                BigDecimal amt;
                try { amt = new BigDecimal(String.valueOf(d.get("amount"))).setScale(2, RoundingMode.HALF_UP); } catch (Exception e) { throw bad("Deduction amount must be a number"); }
                if (amt.signum() <= 0 || amt.compareTo(new BigDecimal("1000000")) > 0) throw bad("Deduction amounts must be between 1 and 10,00,000");
                String note = d.get("note") == null ? null : String.valueOf(d.get("note")).trim();
                if (note != null && note.isEmpty()) note = null;
                if (note != null && note.length() > 300) throw bad("Keep each deduction note under 300 characters");
                List<Long> photos = new ArrayList<>();
                if (d.get("photoIds") instanceof List<?> l) {
                    for (Object o : l) {
                        long pid;
                        try { pid = Long.parseLong(String.valueOf(o)); } catch (Exception e) { throw bad("Invalid photo"); }
                        if (!photoIdsOfStay.contains(pid)) throw bad("Photos must come from this resident's move-in or move-out checklist");
                        if (!photos.contains(pid)) photos.add(pid);
                    }
                }
                clean.add(new DepositSettlement.Deduction(label, amt, note, photos.isEmpty() ? null : photos));
            }
            s.setDeductionsJson(json(clean));
        }
        if (notes != null) s.setNotes(notes.isBlank() ? null : notes.trim().length() > 1000 ? notes.trim().substring(0, 1000) : notes.trim());
        BookingCheckin b = booking(s.getBookingId());
        recalc(s, b);
        s.setUpdatedAt(LocalDateTime.now());
        return view(repo.save(s), b);
    }

    @Transactional
    public DepositSettlement finalizeSettlement(Long id, Long staffUserId) {
        DepositSettlement s = get(id);
        if (!"DRAFT".equals(s.getStatus())) throw new AppException("This settlement is already finalized", HttpStatus.CONFLICT);
        BookingCheckin b = booking(s.getBookingId());
        recalc(s, b);

        // 1. Settle open dues from the deposit, oldest first
        BigDecimal remaining = s.getDepositHeld();
        for (PaymentDue d : openDues(b)) {
            if (remaining.signum() <= 0) break;
            BigDecimal owed = outstanding(d);
            BigDecimal apply = owed.min(remaining);
            if (apply.signum() <= 0) continue;
            paymentService.recordCashPayment(staffUserId, d.getHostellerId(), d.getId(), apply, "DEPOSIT_ADJUSTMENT",
                    "SETTLEMENT-" + s.getId(), DateUtils.todayKolkata(), "Adjusted from security deposit at checkout");
            remaining = remaining.subtract(apply);
        }
        // 2. Deductions the deposit doesn't cover become a due, so they show up in Payments
        BigDecimal ded = deductionTotal(s);
        BigDecimal uncovered = ded.subtract(remaining.max(BigDecimal.ZERO));
        if (uncovered.signum() > 0) {
            paymentService.createCharges(b.getHostelId(), b.getHostellerId(), "OTHER", uncovered,
                    "Checkout deductions not covered by deposit (settlement #" + s.getId() + ")", DateUtils.todayKolkata(), false);
        }

        // Amounts stay as calculated above (before the dues were settled): that's the statement the resident sees
        s.setStatus("FINALIZED");
        s.setFinalizedBy(staffUserId);
        s.setFinalizedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        DepositSettlement saved = repo.save(s);
        events.publishEvent(new ResidentNoticeEvent(this, s.getHostellerId(), "SETTLEMENT_FINALIZED",
                "Your deposit settlement is ready",
                saved.getRefundAmount().signum() > 0
                        ? "Your deposit settlement is ready: Rs. " + saved.getRefundAmount() + " will be refunded to you."
                        : saved.getBalanceDue().signum() > 0
                        ? "Your deposit settlement is ready: Rs. " + saved.getBalanceDue() + " is still payable after adjusting your deposit."
                        : "Your deposit settlement is ready: your deposit has been fully adjusted against dues and deductions.",
                "/app/documents"));
        return view(saved, b);
    }

    @Transactional
    public DepositSettlement markRefunded(Long id, String method, String reference) {
        DepositSettlement s = get(id);
        if (!"FINALIZED".equals(s.getStatus())) throw new AppException("Finalize the settlement first", HttpStatus.CONFLICT);
        if (s.getRefundAmount().signum() <= 0) throw bad("There is no refund to mark as paid");
        String m = method == null ? "" : method.toUpperCase();
        if (!Set.of("CASH", "UPI", "BANK_TRANSFER", "CHEQUE").contains(m)) throw bad("Refund method must be CASH, UPI, BANK_TRANSFER or CHEQUE");
        s.setRefundMethod(m);
        s.setRefundReference(reference == null || reference.isBlank() ? null : reference.trim().length() > 100 ? reference.trim().substring(0, 100) : reference.trim());
        s.setRefundedAt(LocalDateTime.now());
        s.setStatus("REFUNDED");
        s.setUpdatedAt(LocalDateTime.now());
        DepositSettlement saved = repo.save(s);
        events.publishEvent(new ResidentNoticeEvent(this, s.getHostellerId(), "DEPOSIT_REFUNDED",
                "Deposit refund of Rs. " + s.getRefundAmount() + " sent",
                "Your deposit refund of Rs. " + s.getRefundAmount() + " has been paid by " + m.replace('_', ' ').toLowerCase()
                        + (s.getRefundReference() != null ? " (ref " + s.getRefundReference() + ")" : "") + ".", "/app/documents"));
        return view(saved, booking(s.getBookingId()));
    }

    public DepositSettlement get(Long id) {
        return repo.findById(id).orElseThrow(() -> new AppException("Settlement not found", HttpStatus.NOT_FOUND));
    }

    public DepositSettlement getView(Long id) {
        DepositSettlement s = get(id);
        return view(s, booking(s.getBookingId()));
    }

    public List<DepositSettlement> forResident(Long hostellerId) {
        return repo.findByHostellerIdOrderByCreatedAtDesc(hostellerId).stream()
                .filter(s -> !"DRAFT".equals(s.getStatus()))
                .map(s -> view(s, booking(s.getBookingId()))).toList();
    }

    public Optional<DepositSettlement> forBooking(Long bookingId) {
        return repo.findByBookingId(bookingId).map(s -> view(s, booking(bookingId)));
    }

    // ─── refund deadline + disputes ────────────────────────────────────────

    /** Check-out date + the hostel's refund days (null while the resident is still staying). */
    public java.time.LocalDate refundDueBy(BookingCheckin b) {
        if (b.getCheckoutDate() == null) return null;
        return b.getCheckoutDate().plusDays(policies.get(b.getHostelId()).getDepositRefundDays());
    }

    /** Nothing more to pay back: refunded, or finalized with no refund. */
    public static boolean isSettled(DepositSettlement s) {
        return s != null && ("REFUNDED".equals(s.getStatus()) || ("FINALIZED".equals(s.getStatus()) && s.getRefundAmount().signum() <= 0));
    }

    /** Stays the resident has left whose deposit isn't settled yet (no statement, or still a draft). */
    public List<Map<String, Object>> pendingForResident(Long hostellerId) {
        java.time.LocalDate today = DateUtils.todayKolkata();
        List<Map<String, Object>> out = new ArrayList<>();
        for (BookingCheckin b : bookingRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId)) {
            if (!"CHECKED_OUT".equals(b.getStatus()) || b.getCheckoutDate() == null || b.getCheckoutDate().isBefore(today.minusDays(180))) continue;
            DepositSettlement s = repo.findByBookingId(b.getId()).orElse(null);
            if (s != null && !"DRAFT".equals(s.getStatus())) continue;
            BigDecimal held = depositHeld(b);
            if (held.signum() <= 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bookingId", b.getId());
            m.put("hostelName", hostelRepo.findById(b.getHostelId()).map(h -> h.getName()).orElse("Hostel"));
            m.put("checkoutDate", b.getCheckoutDate());
            m.put("depositHeld", held);
            java.time.LocalDate due = refundDueBy(b);
            m.put("refundDueBy", due);
            m.put("overdue", due != null && today.isAfter(due));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public DepositSettlement dispute(Long id, Long hostellerId, String reason) {
        DepositSettlement s = get(id);
        if (!Objects.equals(s.getHostellerId(), hostellerId)) throw new AppException("Settlement not found", HttpStatus.NOT_FOUND);
        if ("DRAFT".equals(s.getStatus())) throw new AppException("Settlement not found", HttpStatus.NOT_FOUND);
        if ("OPEN".equals(s.getDisputeStatus())) throw new AppException("You have already raised this. The hostel will reply.", HttpStatus.CONFLICT);
        if ("RESOLVED".equals(s.getDisputeStatus())) throw new AppException("This was already reviewed by the hostel", HttpStatus.CONFLICT);
        if (s.getFinalizedAt() != null && s.getFinalizedAt().isBefore(LocalDateTime.now().minusDays(60))) {
            throw bad("Disputes can be raised within 60 days of the settlement");
        }
        String r = reason == null ? "" : reason.trim();
        if (r.length() < 10 || r.length() > 1000) throw bad("Explain what you disagree with (10 to 1000 characters)");
        s.setDisputeStatus("OPEN");
        s.setDisputeReason(r);
        s.setDisputedAt(LocalDateTime.now());
        DepositSettlement saved = repo.save(s);
        events.publishEvent(new com.hostlify.shared.event.StaffNoticeEvent(this, s.getHostelId(), "SETTLEMENT_DISPUTED",
                "Deposit settlement disputed",
                "A resident disagrees with their deposit settlement #" + s.getId() + ": \"" + (r.length() > 160 ? r.substring(0, 160) + "…" : r)
                        + "\". Reply from the resident's Documents tab.", "/admin/members/" + s.getHostellerId(), false, true));
        return view(saved, booking(s.getBookingId()));
    }

    /** Staff reply to a dispute; optionally give up part of the deductions (added to the refund). */
    @Transactional
    public DepositSettlement resolveDispute(Long id, String response, BigDecimal waive) {
        DepositSettlement s = get(id);
        if (!"OPEN".equals(s.getDisputeStatus())) throw new AppException("There is no open dispute on this settlement", HttpStatus.CONFLICT);
        String r = response == null ? "" : response.trim();
        if (r.length() < 5 || r.length() > 1000) throw bad("Write a reply to the resident (5 to 1000 characters)");
        BigDecimal w = waive == null ? BigDecimal.ZERO : waive.setScale(2, RoundingMode.HALF_UP);
        if (w.signum() < 0) throw bad("Amount given up can't be negative");
        if (w.signum() > 0) {
            if (!"FINALIZED".equals(s.getStatus())) throw bad("The refund was already paid: settle any extra amount outside the app and say so in the reply");
            if (s.getBalanceDue().signum() > 0) throw bad("This resident still owes a balance; reduce that due in Payments instead");
            BigDecimal room = deductionTotal(s).subtract(s.getWaivedAmount());
            if (w.compareTo(room) > 0) throw bad("You can give up at most Rs. " + room + " (the deductions)");
            s.setWaivedAmount(s.getWaivedAmount().add(w));
            s.setRefundAmount(s.getRefundAmount().add(w));
        }
        s.setDisputeStatus("RESOLVED");
        s.setDisputeResponse(r);
        s.setDisputeResolvedAt(LocalDateTime.now());
        DepositSettlement saved = repo.save(s);
        events.publishEvent(new ResidentNoticeEvent(this, s.getHostellerId(), "SETTLEMENT_DISPUTE_REPLY",
                "Reply about your deposit settlement",
                (w.signum() > 0 ? "The hostel reduced the deductions by Rs. " + w + "; your refund is now Rs. " + saved.getRefundAmount() + ". " : "")
                        + "Their reply: " + r, "/app/documents"));
        return view(saved, booking(s.getBookingId()));
    }

    // ─── maths ─────────────────────────────────────────────────────────────

    /** Deposit actually held: the advance taken at allocation + any paid "Deposit" charges. */
    public BigDecimal depositHeld(BookingCheckin b) {
        BigDecimal advance = b.getAdvanceAmount() == null ? BigDecimal.ZERO : b.getAdvanceAmount();
        BigDecimal depositCharges = dueRepo.findByHostellerIdOrderByDueDateDesc(b.getHostellerId()).stream()
                .filter(d -> "DEPOSIT".equals(d.getDueType()))
                .filter(d -> d.getBookingId() == null || d.getBookingId().equals(b.getId()))
                .map(this::paid).reduce(BigDecimal.ZERO, BigDecimal::add);
        return advance.add(depositCharges);
    }

    private void recalc(DepositSettlement s, BookingCheckin b) {
        s.setDepositHeld(depositHeld(b));
        s.setUnpaidDues(openDues(b).stream().map(this::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal net = s.getDepositHeld().subtract(s.getUnpaidDues()).subtract(deductionTotal(s));
        s.setRefundAmount(net.max(BigDecimal.ZERO));
        s.setBalanceDue(net.signum() < 0 ? net.negate() : BigDecimal.ZERO);
    }

    /** Total still unpaid for this stay (what the deposit would be adjusted against). */
    public BigDecimal openDuesTotal(BookingCheckin b) {
        return openDues(b).stream().map(this::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Open dues of this resident for this stay, oldest first. Unpaid "Deposit" charges aren't owed back to the hostel. */
    private List<PaymentDue> openDues(BookingCheckin b) {
        List<PaymentDue> list = new ArrayList<>(dueRepo.findByHostellerIdOrderByDueDateDesc(b.getHostellerId()).stream()
                .filter(PaymentDue::isOpen)
                .filter(d -> !"DEPOSIT".equals(d.getDueType()))
                .filter(d -> d.getBookingId() == null || d.getBookingId().equals(b.getId()))
                .filter(d -> outstanding(d).signum() > 0)
                .toList());
        list.sort(Comparator.comparing(PaymentDue::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    private BigDecimal paid(PaymentDue d) {
        return recordRepo.findByDueId(d.getId()).stream().filter(PaymentRecord::isSuccessful)
                .map(PaymentRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal outstanding(PaymentDue d) {
        return d.getRentAmount().subtract(paid(d)).max(BigDecimal.ZERO);
    }

    BigDecimal deductionTotal(DepositSettlement s) {
        return deductions(s).stream().map(DepositSettlement.Deduction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    List<DepositSettlement.Deduction> deductions(DepositSettlement s) {
        try {
            return mapper.readValue(s.getDeductionsJson(), new TypeReference<List<DepositSettlement.Deduction>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private DepositSettlement view(DepositSettlement s, BookingCheckin b) {
        s.setDeductions(deductions(s));
        s.setRefundDueBy(refundDueBy(b));
        s.setRefundOverdue(!isSettled(s) && s.getRefundDueBy() != null && DateUtils.todayKolkata().isAfter(s.getRefundDueBy()));
        s.setEvidencePhotos(checklistService.photosForBooking(b.getId()));
        if ("DRAFT".equals(s.getStatus())) {
            s.setOpenDues(openDues(b).stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", d.getId());
                m.put("label", ReceiptPdfService.typeLabel(d.getDueType()) + " - " + ReceiptPdfService.periodLabel(d.getPeriodMonth(), d.getPeriodYear()));
                m.put("outstanding", outstanding(d));
                return m;
            }).toList());
            List<String> suggestions = new ArrayList<>(checklistService.newDamage(b.getId()).stream()
                    .map(i -> i.label() + " (" + i.condition().toLowerCase() + (i.note() != null ? ": " + i.note() : "") + ")").toList());
            // From the resident's check-out request: short notice / early exit, as estimated when they asked
            checkoutRepo.findByHostellerIdOrderByIdDesc(b.getHostellerId()).stream()
                    .filter(r -> Objects.equals(r.getBookingId(), b.getId()) && r.getEstimateJson() != null && !"CANCELLED".equals(r.getStatus()))
                    .findFirst().map(r -> policies.parseTerms(r.getEstimateJson())).ifPresent(est -> {
                        Object sn = est.get("shortNoticeCharge"), ex = est.get("earlyExitFee");
                        if (sn != null && new BigDecimal(String.valueOf(sn)).signum() > 0)
                            suggestions.add("Short notice: " + est.get("shortNoticeDays") + " days (Rs. " + new BigDecimal(String.valueOf(sn)).setScale(2, RoundingMode.HALF_UP) + ")");
                        if (Boolean.TRUE.equals(est.get("inLockIn")) && ex != null && new BigDecimal(String.valueOf(ex)).signum() > 0)
                            suggestions.add("Early exit within lock-in (Rs. " + new BigDecimal(String.valueOf(ex)).setScale(2, RoundingMode.HALF_UP) + ")");
                    });
            s.setDamageSuggestions(suggestions);
        }
        return s;
    }

    private String json(List<DepositSettlement.Deduction> d) {
        try { return mapper.writeValueAsString(d); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static AppException bad(String m) { return new AppException(m, HttpStatus.BAD_REQUEST); }
}
