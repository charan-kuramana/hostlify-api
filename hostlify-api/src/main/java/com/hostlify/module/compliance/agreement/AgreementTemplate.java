package com.hostlify.module.compliance.agreement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** The owner's editable agreement text for a hostel, with {{placeholders}}. */
@Getter
@Setter
@Entity
@Table(name = "agreement_templates")
public class AgreementTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false, unique = true)
    private Long hostelId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "notice_days", nullable = false)
    private int noticeDays = 30;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
