package com.glid.model;

import java.time.LocalDateTime;

public record Employee(
        long id,
        String employeeCode,
        String fullName,
        String department,
        String position,
        boolean active,
        boolean maskRegistered,
        LocalDateTime createdAt
) {
}
