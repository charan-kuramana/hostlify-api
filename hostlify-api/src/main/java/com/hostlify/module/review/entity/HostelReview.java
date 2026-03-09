package com.hostlify.module.review.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
    name = "hostel_reviews",
    indexes = {
        @Index(name = "idx_hvr_hosteller_id", columnList = "hosteller_id"),
        @Index(name = "idx_hvr_hostel_id",    columnList = "hostel_id")
    }
)
@SQLRestriction("is_deleted = 0")
public class HostelReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(nullable = false)
    private int rating;

    @Column(name = "review_text", columnDefinition = "TEXT")
    private String reviewText;

    @Column(name = "is_visible", nullable = false)
    private boolean isVisible = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHostellerId() { return hostellerId; }
    public void setHostellerId(Long hostellerId) { this.hostellerId = hostellerId; }
    public Long getHostelId() { return hostelId; }
    public void setHostelId(Long hostelId) { this.hostelId = hostelId; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getReviewText() { return reviewText; }
    public void setReviewText(String reviewText) { this.reviewText = reviewText; }
    public boolean isIsVisible() { return isVisible; }
    public void setIsVisible(boolean isVisible) { this.isVisible = isVisible; }
}
