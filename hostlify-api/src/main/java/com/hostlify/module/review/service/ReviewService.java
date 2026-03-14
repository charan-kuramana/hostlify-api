package com.hostlify.module.review.service;

import com.hostlify.module.review.entity.HostelReview;
import com.hostlify.module.review.repository.HostelReviewRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private final HostelReviewRepository reviewRepo;

    public ReviewService(HostelReviewRepository reviewRepo) {
        this.reviewRepo = reviewRepo;
    }

    public List<HostelReview> getVisibleReviews(Long hostelId) {
        return reviewRepo.findByHostelIdAndIsVisibleTrue(hostelId);
    }

    public Double getAverageRating(Long hostelId) {
        Double avg = reviewRepo.findAverageRatingByHostelId(hostelId);
        return avg != null ? avg : 0.0;
    }

    public Map<String, Object> getReviewSummary(Long hostelId) {
        List<HostelReview> reviews = getVisibleReviews(hostelId);
        double avg = getAverageRating(hostelId);
        return Map.of(
                "hostelId", hostelId,
                "totalReviews", reviews.size(),
                "averageRating", avg,
                "reviews", reviews
        );
    }

    @Transactional
    public HostelReview submitReview(Long hostellerId, Long hostelId, Long bookingId,
                                     int rating, String reviewText) {
        if (rating < 1 || rating > 5) {
            throw new AppException("Rating must be between 1 and 5", HttpStatus.BAD_REQUEST);
        }

        HostelReview review = new HostelReview();
        review.setHostellerId(hostellerId);
        review.setHostelId(hostelId);
        review.setBookingId(bookingId);
        review.setRating(rating);
        review.setReviewText(reviewText);
        review.setVisible(true);
        return reviewRepo.save(review);
    }

    @Transactional
    public HostelReview toggleVisibility(Long reviewId, boolean visible) {
        HostelReview review = reviewRepo.findById(reviewId)
                .orElseThrow(() -> new AppException("Review not found", HttpStatus.NOT_FOUND));
        review.setVisible(visible);
        return reviewRepo.save(review);
    }
}
