package com.glid.service;

import com.glid.model.DetectionFrame;
import com.glid.model.DetectionArtifact;
import javafx.application.Platform;
import javafx.scene.image.Image;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class CameraCaptureService {
    private static final Scalar FACE_BOX_COLOR = new Scalar(40, 220, 120);
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Duration EVIDENCE_SAVE_INTERVAL = Duration.ofSeconds(2);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private VideoCapture capture;
    private volatile Image lastFrame;
    private volatile boolean nativeLoaded;
    private volatile CascadeClassifier faceClassifier;
    private volatile String classifierStatus = "Detector not loaded";
    private volatile DetectionArtifact lastDetectionArtifact;
    private volatile Instant lastEvidenceSavedAt;

    public synchronized void startCamera(int cameraIndex, Consumer<DetectionFrame> frameConsumer, Consumer<String> statusConsumer) {
        if (running.get()) {
            statusConsumer.accept("Camera already running");
            return;
        }

        ensureOpenCvLoaded(statusConsumer);
        if (!nativeLoaded) {
            return;
        }

        ensureFaceDetectorLoaded(statusConsumer);

        running.set(true);
        executor.submit(() -> {
            capture = new VideoCapture(cameraIndex);
            if (!capture.isOpened()) {
                running.set(false);
                dispatchStatus(statusConsumer, "Failed to open camera index " + cameraIndex);
                return;
            }

            dispatchStatus(statusConsumer, "Camera connected: index " + cameraIndex);
            Mat frame = new Mat();
            while (running.get()) {
                if (!capture.read(frame) || frame.empty()) {
                    dispatchStatus(statusConsumer, "Waiting for camera frame...");
                    sleep(80);
                    continue;
                }

                DetectionFrame detectionFrame = buildDetectionFrame(frame);
                Image image = detectionFrame.image();
                lastFrame = image;
                Platform.runLater(() -> frameConsumer.accept(detectionFrame));
                sleep(33);
            }

            frame.release();
            releaseCapture();
            dispatchStatus(statusConsumer, "Camera stopped");
        });
    }

    public synchronized void stopCamera() {
        running.set(false);
    }

    public Image getLastFrame() {
        return lastFrame;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void shutdown() {
        stopCamera();
        executor.shutdownNow();
        releaseCapture();
    }

    private void ensureOpenCvLoaded(Consumer<String> statusConsumer) {
        if (nativeLoaded) {
            return;
        }

        try {
            nu.pattern.OpenCV.loadLocally();
            nativeLoaded = true;
            statusConsumer.accept("OpenCV native library loaded");
        } catch (Throwable firstFailure) {
            try {
                System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
                nativeLoaded = true;
                statusConsumer.accept("OpenCV native library loaded via system path");
            } catch (Throwable secondFailure) {
                nativeLoaded = false;
                statusConsumer.accept("Failed to load OpenCV native library");
            }
        }
    }

    public boolean isDetectorReady() {
        return faceClassifier != null && !faceClassifier.empty();
    }

    public String getClassifierStatus() {
        return classifierStatus;
    }

    public DetectionArtifact getLastDetectionArtifact() {
        return lastDetectionArtifact;
    }

    private Image matToImage(Mat mat) {
        MatOfByte encoded = new MatOfByte();
        Imgcodecs.imencode(".bmp", mat, encoded);
        byte[] bytes = encoded.toArray();
        encoded.release();
        return new Image(new ByteArrayInputStream(bytes));
    }

    private void dispatchStatus(Consumer<String> statusConsumer, String message) {
        Platform.runLater(() -> statusConsumer.accept(message));
    }

    private DetectionFrame buildDetectionFrame(Mat originalFrame) {
        Mat annotatedFrame = originalFrame.clone();
        int faceCount = 0;
        Rect[] detectedFaces = new Rect[0];

        if (isDetectorReady()) {
            Mat grayscaleFrame = new Mat();
            Imgproc.cvtColor(originalFrame, grayscaleFrame, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(grayscaleFrame, grayscaleFrame);

            MatOfRect faces = new MatOfRect();
            faceClassifier.detectMultiScale(
                    grayscaleFrame,
                    faces,
                    1.05,
                    3,
                    0,
                    new Size(48, 48),
                    new Size()
            );

            detectedFaces = faces.toArray();
            faceCount = detectedFaces.length;
            for (Rect face : detectedFaces) {
                Imgproc.rectangle(
                        annotatedFrame,
                        new Point(face.x, face.y),
                        new Point(face.x + face.width, face.y + face.height),
                        FACE_BOX_COLOR,
                        2
                );
            }

            faces.release();
            grayscaleFrame.release();
        }

        updateDetectionArtifact(originalFrame, detectedFaces);

        Image image = matToImage(annotatedFrame);
        annotatedFrame.release();
        return new DetectionFrame(image, faceCount, isDetectorReady(), classifierStatus);
    }

    private void ensureFaceDetectorLoaded(Consumer<String> statusConsumer) {
        if (isDetectorReady()) {
            return;
        }

        Path classifierPath = resolveClassifierPath();
        if (classifierPath == null) {
            classifierStatus = "Face detector XML not found";
            statusConsumer.accept(classifierStatus);
            return;
        }

        CascadeClassifier classifier = new CascadeClassifier(classifierPath.toString());
        if (classifier.empty()) {
            classifierStatus = "Failed to load face detector XML";
            statusConsumer.accept(classifierStatus);
            return;
        }

        faceClassifier = classifier;
        classifierStatus = "Face detector ready: " + classifierPath.getFileName();
        statusConsumer.accept(classifierStatus);
    }

    private Path resolveClassifierPath() {
        String environmentPath = System.getenv("GLID_OPENCV_CASCADE");
        if (environmentPath != null && !environmentPath.isBlank()) {
            Path path = Path.of(environmentPath);
            if (Files.exists(path)) {
                return path;
            }
        }

        Path[] candidates = new Path[] {
                Path.of("assets", "haarcascade_frontalface_default.xml"),
                Path.of("data", "haarcascade_frontalface_default.xml"),
                Path.of("C:/opencv/sources/data/haarcascades/haarcascade_frontalface_default.xml"),
                Path.of("C:/Program Files/OpenCV/data/haarcascades/haarcascade_frontalface_default.xml")
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void updateDetectionArtifact(Mat originalFrame, Rect[] detectedFaces) {
        if (detectedFaces.length == 0) {
            return;
        }

        Instant now = Instant.now();
        if (lastEvidenceSavedAt != null && Duration.between(lastEvidenceSavedAt, now).compareTo(EVIDENCE_SAVE_INTERVAL) < 0) {
            return;
        }

        try {
            Path evidenceDirectory = Paths.get("evidence");
            Path cropsDirectory = evidenceDirectory.resolve("crops");
            Files.createDirectories(cropsDirectory);

            LocalDateTime capturedAt = LocalDateTime.now();
            String stamp = FILE_TIMESTAMP.format(capturedAt);
            Path evidencePath = evidenceDirectory.resolve("frame-" + stamp + ".jpg");
            Imgcodecs.imwrite(evidencePath.toString(), originalFrame);

            Rect largestFace = java.util.Arrays.stream(detectedFaces)
                    .max(Comparator.comparingInt(rect -> rect.width * rect.height))
                    .orElse(detectedFaces[0]);

            Rect boundedRect = clampRect(largestFace, originalFrame.cols(), originalFrame.rows());
            Mat croppedFace = new Mat(originalFrame, boundedRect).clone();
            Path cropPath = cropsDirectory.resolve("face-" + stamp + ".jpg");
            Imgcodecs.imwrite(cropPath.toString(), croppedFace);
            croppedFace.release();

            lastDetectionArtifact = new DetectionArtifact(
                    evidencePath.toString().replace('\\', '/'),
                    cropPath.toString().replace('\\', '/'),
                    detectedFaces.length,
                    capturedAt
            );
            lastEvidenceSavedAt = now;
        } catch (Exception exception) {
            classifierStatus = "Failed to save detection evidence";
        }
    }

    private Rect clampRect(Rect rect, int maxWidth, int maxHeight) {
        int x = Math.max(0, rect.x);
        int y = Math.max(0, rect.y);
        int width = Math.min(rect.width, maxWidth - x);
        int height = Math.min(rect.height, maxHeight - y);
        return new Rect(x, y, Math.max(1, width), Math.max(1, height));
    }

    private void releaseCapture() {
        if (capture != null) {
            capture.release();
            capture = null;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
