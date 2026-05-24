package com.glid.service;

import com.glid.model.AttendanceLog;
import com.glid.model.AttendanceType;
import com.glid.model.Employee;
import com.glid.model.RecognitionEvent;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FaceRecognitionPipeline {
    private final EmployeeService employeeService;
    private final AttendanceService attendanceService;

    public FaceRecognitionPipeline(EmployeeService employeeService, AttendanceService attendanceService) {
        this.employeeService = employeeService;
        this.attendanceService = attendanceService;
    }

    public RecognitionEvent simulateRecognition(String cameraId, boolean masked) {
        List<Employee> activeEmployees = employeeService.findAll().stream()
                .filter(Employee::active)
                .sorted(Comparator.comparing(Employee::id))
                .toList();

        if (activeEmployees.isEmpty()) {
            throw new IllegalStateException("No active employees available for recognition");
        }

        Employee employee = activeEmployees.get(ThreadLocalRandom.current().nextInt(activeEmployees.size()));
        double confidence = masked
                ? randomConfidence(0.90, 0.97)
                : randomConfidence(0.95, 0.99);
        String snapshotPath = "evidence/" + employee.employeeCode().toLowerCase() + "-" + System.currentTimeMillis() + ".jpg";

        try {
            AttendanceLog attendance = attendanceService.recordAttendance(employee.id(), AttendanceType.CHECK_IN, snapshotPath, cameraId);
            return new RecognitionEvent(
                    employee.id(),
                    employee.employeeCode(),
                    employee.fullName(),
                    confidence,
                    masked,
                    attendance.timestamp(),
                    cameraId,
                    snapshotPath,
                    "Attendance recorded"
            );
        } catch (IllegalStateException blocked) {
            return new RecognitionEvent(
                    employee.id(),
                    employee.employeeCode(),
                    employee.fullName(),
                    confidence,
                    masked,
                    LocalDateTime.now(),
                    cameraId,
                    snapshotPath,
                    blocked.getMessage()
            );
        }
    }

    private double randomConfidence(double min, double max) {
        double raw = ThreadLocalRandom.current().nextDouble(min, max);
        return Math.round(raw * 1000.0) / 1000.0;
    }
}
