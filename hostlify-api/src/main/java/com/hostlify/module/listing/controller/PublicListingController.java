package com.hostlify.module.listing.controller;

import com.hostlify.module.listing.service.EnquiryService;
import com.hostlify.module.listing.service.ListingService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.SimpleRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** No-login endpoints behind the public hostel pages. Only hostels whose owner switched the listing on appear. */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public listing")
public class PublicListingController {

    private final ListingService listingService;
    private final EnquiryService enquiryService;
    private final SimpleRateLimiter limiter;

    public PublicListingController(ListingService listingService, EnquiryService enquiryService, SimpleRateLimiter limiter) {
        this.listingService = listingService;
        this.enquiryService = enquiryService;
        this.limiter = limiter;
    }

    @GetMapping("/hostels")
    @Operation(summary = "Public: search listed hostels")
    public ApiResponse<List<Map<String, Object>>> search(@RequestParam(required = false) String city,
                                                          @RequestParam(required = false) String type,
                                                          @RequestParam(required = false) String roomType,
                                                          @RequestParam(required = false) BigDecimal maxRent,
                                                          @RequestParam(required = false) String q,
                                                          HttpServletRequest req) {
        throttle(req, "search", 120, 60);
        return ApiResponse.ok(listingService.publicList(city, type, roomType, maxRent, q));
    }

    @GetMapping("/cities")
    @Operation(summary = "Public: cities with listed hostels")
    public ApiResponse<List<Map<String, Object>>> cities() {
        return ApiResponse.ok(listingService.publicCities());
    }

    @GetMapping("/hostels/{slug}")
    @Operation(summary = "Public: one listed hostel (photos, rooms, rent, amenities, map)")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String slug, HttpServletRequest req) {
        throttle(req, "detail", 120, 60);
        return ApiResponse.ok(listingService.publicDetail(slug));
    }

    @PostMapping("/hostels/{slug}/enquiries")
    @Operation(summary = "Public: send an enquiry, request a visit, or join the waitlist")
    public ApiResponse<Void> enquire(@PathVariable String slug, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        // Honeypot: real visitors never see/fill this field; bots do. Pretend success.
        Object trap = body.get("website");
        if (trap != null && !String.valueOf(trap).isBlank()) {
            return ApiResponse.ok(null, "Thanks! The hostel will contact you soon.");
        }
        throttle(req, "enquiry-hour", 5, 3600);
        throttle(req, "enquiry-day", 15, 86400);
        return ApiResponse.ok(null, enquiryService.create(slug, body));
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Public: sitemap of listing pages (submit to Google Search Console)")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.APPLICATION_XML)
                .body(listingService.sitemap());
    }

    private void throttle(HttpServletRequest req, String bucket, int max, long windowSeconds) {
        if (!limiter.allow(bucket + ":" + SimpleRateLimiter.clientIp(req), max, windowSeconds)) {
            throw new AppException("Too many requests. Please try again later.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
