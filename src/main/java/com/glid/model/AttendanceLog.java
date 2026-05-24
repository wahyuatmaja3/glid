package com.glid.model;

import java.time.LocalDateTime;

public record AttendanceLog(
        long id,
        long employeeId,
        String employeeCode,
        String employeeName,
        AttendanceType attendanceType,
        LocalDateTime timestamp,
        String imagePath,
        String cameraId
) {
}
