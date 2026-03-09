package com.hostlify.module.booking.controller;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.service.BookingService;
import com.hostlify.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/hostel/{hostelId}")
    public ApiResponse<List<BookingCheckin>> getByHostel(@PathVariable Long hostelId) {
        return ApiResponse.ok(bookingService.getByHostel(hostelId));
    }

    @GetMapping("/hosteller/{hostellerId}")
    public ApiResponse<List<BookingCheckin>> getByHosteller(@PathVariable Long hostellerId) {
        return ApiResponse.ok(bookingService.getByHosteller(hostellerId));
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingCheckin> getById(@PathVariable Long id) {
        return ApiResponse.ok(bookingService.getById(id));
    }

    @PostMapping("/checkin")
    public ApiResponse<BookingCheckin> checkin(@RequestBody BookingCheckin booking) {
        return ApiResponse.ok(bookingService.checkin(booking));
    }

    @PutMapping("/{id}/checkout")
    public ApiResponse<BookingCheckin> checkout(@PathVariable Long id,
                                                 @RequestBody Map<String, String> body) {
        return ApiResponse.ok(bookingService.checkout(id, body.get("reason")));
    }
}
