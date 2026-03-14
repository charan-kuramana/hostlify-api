package com.hostlify.module.review.controller;

import com.hostlify.module.review.entity.HostelReview;
import com.hostlify.module.review.service.ReviewService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reviews")
@Tag(name = "Reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/hostel/{hostelId}")
    @Operation(summary = "Public: get visible reviews and average rating for a hostel")
    public ApiResponse<Map<String, Object>> getReviewSummary(@PathVariable Long hostelId) {
        return ApiResponse.ok(reviewService.getReviewSummary(hostelId));
    }

    @PostMapping
    @Operation(summary = "Hosteller: submit a review for their hostel")
    public ApiResponse<HostelReview> submitReview(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        int rating = ((Number) body.get("rating")).intValue();
        String reviewText = (String) body.get("reviewText");
        Long hostelId = p.getHostelId();
        Long bookingId = p.getBookingId();

        if (hostelId == null || bookingId == null) {
            throw new AppException("Active booking required to submit a review", HttpStatus.BAD_REQUEST);
        }

        HostelReview review = reviewService.submitReview(
                p.getHostellerProfileId(), hostelId, bookingId, rating, reviewText);
        return ApiResponse.ok(review, "Review submitted successfully");
    }

    @PatchMapping("/{reviewId}/visibility")
    @Operation(summary = "Staff: toggle review visibility")
    public ApiResponse<HostelReview> toggleVisibility(@PathVariable Long reviewId,
                                                        @RequestParam boolean visible) {
        return ApiResponse.ok(reviewService.toggleVisibility(reviewId, visible),
                "Review visibility updated");
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AppException("Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        return (UserPrincipal) auth.getPrincipal();
    }
}
