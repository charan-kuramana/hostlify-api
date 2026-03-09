package com.hostlify.module.booking.service;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {

    private final BookingCheckinRepository bookingRepo;

    public BookingService(BookingCheckinRepository bookingRepo) {
        this.bookingRepo = bookingRepo;
    }

    public List<BookingCheckin> getByHostel(Long hostelId) {
        return bookingRepo.findByHostelIdOrderByCreatedAtDesc(hostelId);
    }

    public List<BookingCheckin> getByHosteller(Long hostellerId) {
        return bookingRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId);
    }

    public BookingCheckin getById(Long id) {
        return bookingRepo.findById(id)
                .orElseThrow(() -> new AppException("Booking not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public BookingCheckin checkin(BookingCheckin booking) {
        booking.setStatus("ACTIVE");
        return bookingRepo.save(booking);
    }

    @Transactional
    public BookingCheckin checkout(Long id, String reason) {
        BookingCheckin booking = getById(id);
        booking.setStatus("CHECKED_OUT");
        booking.setCheckoutDate(LocalDate.now());
        booking.setCheckoutReason(reason);
        return bookingRepo.save(booking);
    }
}
