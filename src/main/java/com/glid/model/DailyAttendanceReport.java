package com.glid.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyAttendanceReport(
        String employeeCode,
        String employeeName,
        String department,
        LocalDate date,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        String checkInCamera,
        String checkOutCamera,
        String checkInImage,
        String checkOutImage
) {
}
