package com.hostlify.module.resident.housekeeping;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Housekeeping proof: a room (or common area) was cleaned, optionally with a photo. */
@Getter
@Setter
@Entity
@Table(name = "cleaning_logs")
public class CleaningLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(length = 80)
    private String area;

    @Column(name = "cleaned_on", nullable = false)
    private LocalDate cleanedOn;

    @Column(name = "cleaned_by", nullable = false)
    private Long cleanedBy;

    @JsonIgnore
    @Column(name = "photo_path", length = 500)
    private String photoPath;

    @Column(length = 300)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    private String photoUrl;

    @Transient
    private String roomName;
}
