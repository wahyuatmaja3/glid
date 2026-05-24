package com.glid.model;

import java.time.LocalDateTime;

public record RecognitionEvent(
        long employeeId,
        String employeeCode,
        String employeeName,
        double confidence,
        boolean masked,
        LocalDateTime recognizedAt,
        String cameraId,
        String snapshotPath,
        String status
) {
}
