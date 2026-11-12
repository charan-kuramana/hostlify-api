package com.hostlify.module.compliance.checklist;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "checklist_photos")
public class ChecklistPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checklist_id", nullable = false)
    private Long checklistId;

    @Column(name = "blob_path", nullable = false, length = 500)
    private String blobPath;

    @Column(length = 150)
    private String caption;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
