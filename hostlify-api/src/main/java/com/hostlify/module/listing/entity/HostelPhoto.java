package com.hostlify.module.listing.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "hostel_photos")
public class HostelPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    /** Storage path; clients get a temporary link in {@link #url}. */
    @JsonIgnore
    @Column(name = "blob_path", nullable = false, length = 500)
    private String blobPath;

    @Column(length = 150)
    private String caption;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_cover", nullable = false)
    private boolean cover = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Temporary read link, filled by the service. */
    @Transient
    private String url;
}
