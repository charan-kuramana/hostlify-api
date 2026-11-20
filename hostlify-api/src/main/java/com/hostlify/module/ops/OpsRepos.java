package com.hostlify.module.ops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class OpsRepos {

    private OpsRepos() {}

    public interface Attendance extends JpaRepository<StaffAttendance, Long> {
        List<StaffAttendance> findByHostelIdAndAttDate(Long hostelId, LocalDate date);
        List<StaffAttendance> findByHostelIdAndAttDateBetween(Long hostelId, LocalDate from, LocalDate to);
        List<StaffAttendance> findByEmployeeIdAndAttDateBetween(Long employeeId, LocalDate from, LocalDate to);
        Optional<StaffAttendance> findByEmployeeIdAndAttDate(Long employeeId, LocalDate date);
    }

    public interface Meters extends JpaRepository<MeterReading, Long> {
        List<MeterReading> findByHostelIdAndPeriodYearAndPeriodMonth(Long hostelId, int year, int month);
        Optional<MeterReading> findByRoomIdAndMeterTypeAndPeriodYearAndPeriodMonth(Long roomId, String type, int year, int month);
        Optional<MeterReading> findFirstByRoomIdAndMeterTypeOrderByPeriodYearDescPeriodMonthDesc(Long roomId, String type);
        Optional<MeterReading> findFirstByHostelIdAndMeterTypeOrderByCreatedAtDesc(Long hostelId, String type);
    }
}
