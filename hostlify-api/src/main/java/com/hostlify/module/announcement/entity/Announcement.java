package com.hostlify.module.announcement.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
    name = "announcements",
    indexes = {
        @Index(name = "idx_ann_hostel_id",  columnList = "hostel_id"),
        @Index(name = "idx_ann_valid_from", columnList = "valid_from")
    }
)
@SQLRestriction("is_deleted = 0")
public class Announcement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 20)
    private String priority = "NORMAL";

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_till")
    private LocalDate validTill;

    @Column(name = "is_emailed", nullable = false)
    private boolean isEmailed = false;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}
