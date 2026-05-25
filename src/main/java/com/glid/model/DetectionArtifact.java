package com.glid.model;

import java.time.LocalDateTime;

public record DetectionArtifact(
        String evidenceImagePath,
        String faceCropPath,
        int faceCount,
        LocalDateTime capturedAt
) {
}
