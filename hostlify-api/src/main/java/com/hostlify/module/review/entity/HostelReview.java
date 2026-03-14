package com.hostlify.module.review.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
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
}
