package com.glid.service;

import com.glid.model.AttendanceLog;
import com.glid.model.AttendanceType;
import com.glid.model.DetectionArtifact;
import com.glid.model.Employee;
import com.glid.model.FaceEmbedding;
import com.glid.model.RecognitionEvent;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public class FaceRecognitionPipeline {
    private static final double NORMAL_MATCH_THRESHOLD = 0.965;
    private static final double MASK_MATCH_THRESHOLD = 0.940;

    private final EmployeeService employeeService;
    private final AttendanceService attendanceService;
    private final CameraCaptureService cameraCaptureService;
    private final FaceEmbeddingExtractor embeddingExtractor;

    public FaceRecognitionPipeline(EmployeeService employeeService, AttendanceService attendanceService, CameraCaptureService cameraCaptureService, FaceEmbeddingExtractor embeddingExtractor) {
        this.employeeService = employeeService;
        this.attendanceService = attendanceService;
        this.cameraCaptureService = cameraCaptureService;
        this.embeddingExtractor = embeddingExtractor;
    }

    public RecognitionEvent simulateRecognition(String cameraId, boolean masked) {
        DetectionArtifact artifact = cameraCaptureService.getLastDetectionArtifact();
        if (artifact == null) {
            return new RecognitionEvent(
                    0,
                    "UNKNOWN",
                    "Unknown Face",
                    0.0,
                    masked,
                    LocalDateTime.now(),
                    cameraId,
                    "",
                    "No detected face crop available"
            );
        }

        List<Double> queryEmbedding = embeddingExtractor.extract(Path.of(artifact.faceCropPath()));
        if (queryEmbedding.isEmpty()) {
            return new RecognitionEvent(
                    0,
                    "UNKNOWN",
                    "Unknown Face",
                    0.0,
                    masked,
                    LocalDateTime.now(),
                    cameraId,
                    artifact.faceCropPath(),
                    "Failed to build face embedding"
            );
        }

        List<Employee> activeEmployees = employeeService.findAll().stream()
                .filter(Employee::active)
                .sorted(Comparator.comparing(Employee::id))
                .toList();

        if (activeEmployees.isEmpty()) {
            throw new IllegalStateException("No active employees available for recognition");
        }

        MatchResult match = findBestMatch(activeEmployees, queryEmbedding, masked);
        if (match == null) {
            return new RecognitionEvent(
                    0,
                    "UNKNOWN",
                    "Unknown Face",
                    0.0,
                    masked,
                    LocalDateTime.now(),
                    cameraId,
                    artifact.faceCropPath(),
                    "No registered face matched"
            );
        }

        Employee employee = match.employee();
        double confidence = Math.round(match.similarity() * 1000.0) / 1000.0;
        String snapshotPath = artifact.faceCropPath();

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

    private MatchResult findBestMatch(List<Employee> employees, List<Double> queryEmbedding, boolean masked) {
        double threshold = masked ? MASK_MATCH_THRESHOLD : NORMAL_MATCH_THRESHOLD;
        MatchResult best = null;

        for (Employee employee : employees) {
            List<FaceEmbedding> embeddings = employeeService.findEmbeddings(employee.id());
            for (FaceEmbedding embedding : embeddings) {
                double similarity = embeddingExtractor.cosineSimilarity(queryEmbedding, embedding.embeddingVector());
                if (similarity < threshold) {
                    continue;
                }

                if (best == null || similarity > best.similarity()) {
                    best = new MatchResult(employee, similarity);
                }
            }
        }
        return best;
    }

    private record MatchResult(Employee employee, double similarity) {
    }
}
