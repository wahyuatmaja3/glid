package com.glid.service;

import com.glid.model.AttendanceLog;
import com.glid.model.AttendanceType;
import com.glid.model.Employee;
import com.glid.persistence.AppDataStore;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AttendanceService {
    private static final Duration DEFAULT_COOLDOWN = Duration.ofMinutes(5);

    private final AppDataStore store;
    private final EmployeeService employeeService;

    public AttendanceService(AppDataStore store, EmployeeService employeeService) {
        this.store = store;
        this.employeeService = employeeService;
    }

    public AttendanceLog recordAttendance(long employeeId, AttendanceType type, String imagePath, String cameraId) {
        Employee employee = employeeService.findById(employeeId);
        if (employee == null) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastAttendance = store.findLatestAttendanceTimestamp(employeeId);
        if (lastAttendance != null && Duration.between(lastAttendance, now).compareTo(DEFAULT_COOLDOWN) < 0) {
            throw new IllegalStateException("Duplicate attendance blocked by cooldown window");
        }

        return store.saveAttendance(employee.id(), employee.employeeCode(), employee.fullName(), type, imagePath, cameraId);
    }

    public AttendanceType determineNextAttendanceType(long employeeId) {
        AttendanceType lastType = store.findLatestAttendanceType(employeeId);
        if (lastType == null) {
            return AttendanceType.CHECK_IN;
        }
        return lastType == AttendanceType.CHECK_IN ? AttendanceType.CHECK_OUT : AttendanceType.CHECK_IN;
    }

    public List<AttendanceLog> history() {
        return store.findAttendanceLogs();
    }

    public List<AttendanceLog> filteredHistory(LocalDate from, LocalDate to, String department, String employeeCode) {
        return store.findAttendanceLogs(from, to, department, employeeCode);
    }

    public void delete(long attendanceId) {
        store.deleteAttendance(attendanceId);
    }
}
