package com.glid.service;

import com.glid.model.AttendanceLog;
import com.glid.persistence.AppDataStore;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class AttendanceReportService {
    private final AppDataStore store;
    private final EmployeeService employeeService;

    public AttendanceReportService(AppDataStore store, EmployeeService employeeService) {
        this.store = store;
        this.employeeService = employeeService;
    }

    public String exportCsvPreview(LocalDate from, LocalDate to, String department, String employeeCode) {
        List<AttendanceLog> logs = store.findAttendanceLogs(from, to, department, employeeCode);
        String header = "employee_code,employee_name,attendance_type,timestamp,camera_id,image_path";
        String body = logs.stream()
                .map(log -> String.join(",",
                        log.employeeCode(),
                        sanitize(log.employeeName()),
                        log.attendanceType().name(),
                        log.timestamp().toString(),
                        log.cameraId(),
                        log.imagePath()))
                .collect(Collectors.joining(System.lineSeparator()));
        return body.isBlank() ? header : header + System.lineSeparator() + body;
    }

    public int totalEmployees() {
        return employeeService.findAll().size();
    }

    private String sanitize(String input) {
        return input.replace(',', ' ');
    }
}
