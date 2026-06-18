package com.hostlify.module.menu.entity;

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
    name = "menu_weekly",
    indexes = {
        @Index(name = "idx_mw_hostel_id",  columnList = "hostel_id"),
        @Index(name = "idx_mw_week_start", columnList = "week_start")
    }
)
@SQLRestriction("is_deleted = false")
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
}
