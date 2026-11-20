package com.hostlify.module.ops;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** One employee's attendance for one day. */
@Getter
@Setter
@Entity
@Table(name = "staff_attendance")
public class StaffAttendance {

    public static final List<String> STATUSES = List.of("PRESENT", "ABSENT", "HALF_DAY", "LEAVE", "WEEK_OFF");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "att_date", nullable = false)
    private LocalDate attDate;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "in_time")
    private LocalTime inTime;

    @Column(name = "out_time")
    private LocalTime outTime;

    @Column(length = 150)
    private String note;

    @Column(name = "marked_by", nullable = false)
    private Long markedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
