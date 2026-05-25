package com.glid.service;

import com.glid.model.DetectionArtifact;
import com.glid.model.RecognitionEvent;

import java.time.Duration;
import java.time.Instant;

public class AutoAttendanceService {
    private static final Duration AUTO_TRIGGER_INTERVAL = Duration.ofSeconds(4);

    private final CameraCaptureService cameraCaptureService;
    private final FaceRecognitionPipeline recognitionPipeline;

    private DetectionArtifact lastProcessedArtifact;
    private Instant lastTriggerAt;
    private boolean autoModeEnabled;

    public AutoAttendanceService(CameraCaptureService cameraCaptureService, FaceRecognitionPipeline recognitionPipeline) {
        this.cameraCaptureService = cameraCaptureService;
        this.recognitionPipeline = recognitionPipeline;
    }

    public void setAutoModeEnabled(boolean autoModeEnabled) {
        this.autoModeEnabled = autoModeEnabled;
    }

    public boolean isAutoModeEnabled() {
        return autoModeEnabled;
    }

    public RecognitionEvent tryAutoAttendance(String cameraId, boolean masked) {
        if (!autoModeEnabled) {
            return null;
        }

        DetectionArtifact artifact = cameraCaptureService.getLastDetectionArtifact();
        if (artifact == null) {
            return null;
        }

        if (lastProcessedArtifact != null && artifact.faceCropPath().equals(lastProcessedArtifact.faceCropPath())) {
            return null;
        }

        Instant now = Instant.now();
        if (lastTriggerAt != null && Duration.between(lastTriggerAt, now).compareTo(AUTO_TRIGGER_INTERVAL) < 0) {
            return null;
        }

        lastProcessedArtifact = artifact;
        lastTriggerAt = now;
        return recognitionPipeline.simulateRecognition(cameraId, masked);
    }
}
