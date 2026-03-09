package com.hostlify.module.menu.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Table(
    name = "menu_weekly",
    indexes = {
        @Index(name = "idx_mw_hostel_id",  columnList = "hostel_id"),
        @Index(name = "idx_mw_week_start", columnList = "week_start")
    }
)
@SQLRestriction("is_deleted = 0")
public class WeeklyMenu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHostelId() { return hostelId; }
    public void setHostelId(Long hostelId) { this.hostelId = hostelId; }
    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
