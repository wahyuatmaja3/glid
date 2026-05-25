package com.glid.model;

import javafx.scene.image.Image;

public record DetectionFrame(
        Image image,
        int faceCount,
        boolean detectorReady,
        String detectorStatus
) {
}
