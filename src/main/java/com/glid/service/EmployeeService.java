package com.glid.service;

import com.glid.model.DetectionArtifact;
import com.glid.model.Employee;
import com.glid.model.FaceEmbedding;
import com.glid.persistence.AppDataStore;

import java.nio.file.Path;
import java.util.List;

public class EmployeeService {
    private final AppDataStore store;
    private final CameraCaptureService cameraCaptureService;
    private final FaceEmbeddingExtractor embeddingExtractor;

    public EmployeeService(AppDataStore store, CameraCaptureService cameraCaptureService, FaceEmbeddingExtractor embeddingExtractor) {
        this.store = store;
        this.cameraCaptureService = cameraCaptureService;
        this.embeddingExtractor = embeddingExtractor;
    }

    public List<Employee> findAll() {
        return store.findAllEmployees();
    }

    public Employee registerEmployee(String employeeCode, String fullName, String department, String position, boolean active, boolean maskRegistered) {
        Employee employee = store.saveEmployee(employeeCode, fullName, department, position, active, maskRegistered);

        DetectionArtifact artifact = cameraCaptureService.getLastDetectionArtifact();
        if (artifact != null) {
            List<Double> baseEmbedding = embeddingExtractor.extract(Path.of(artifact.faceCropPath()));
            if (!baseEmbedding.isEmpty()) {
                for (int i = 0; i < 5; i++) {
                    store.saveEmbedding(employee.id(), varyEmbedding(baseEmbedding, i), maskRegistered && i % 2 == 0);
                }
                return employee;
            }
        }

        for (int i = 0; i < 5; i++) {
            store.saveEmbedding(employee.id(), fallbackEmbedding(employeeCode, i), maskRegistered && i % 2 == 0);
        }
        return employee;
    }

    public Employee registerEmployeeWithArtifact(String employeeCode, String fullName, String department, String position, boolean active, boolean maskRegistered, DetectionArtifact artifact) {
        Employee employee = store.saveEmployee(employeeCode, fullName, department, position, active, maskRegistered);

        if (artifact != null) {
            List<Double> baseEmbedding = embeddingExtractor.extract(Path.of(artifact.faceCropPath()));
            if (!baseEmbedding.isEmpty()) {
                for (int i = 0; i < 5; i++) {
                    store.saveEmbedding(employee.id(), varyEmbedding(baseEmbedding, i), maskRegistered && i % 2 == 0);
                }
                return employee;
            }
        }

        for (int i = 0; i < 5; i++) {
            store.saveEmbedding(employee.id(), fallbackEmbedding(employeeCode, i), maskRegistered && i % 2 == 0);
        }
        return employee;
    }

    public Employee findById(long employeeId) {
        return store.findEmployeeById(employeeId);
    }

    public List<FaceEmbedding> findEmbeddings(long employeeId) {
        return store.findEmbeddings(employeeId);
    }

    private List<Double> varyEmbedding(List<Double> baseEmbedding, int variant) {
        java.util.ArrayList<Double> values = new java.util.ArrayList<>(baseEmbedding.size());
        double adjustment = (variant - 2) * 0.005;
        for (double value : baseEmbedding) {
            values.add(Math.max(0.0, Math.min(1.0, value + adjustment)));
        }
        return values;
    }

    private List<Double> fallbackEmbedding(String employeeCode, int variant) {
        java.util.ArrayList<Double> values = new java.util.ArrayList<>();
        int seed = Math.abs((employeeCode + "-" + variant).hashCode());
        for (int i = 0; i < 16; i++) {
            int shifted = (seed >> (i % 8)) & 0xFF;
            values.add((shifted + 1) / 256.0);
        }
        return normalize(values);
    }

    private List<Double> normalize(List<Double> values) {
        double norm = 0.0;
        for (double value : values) {
            norm += value * value;
        }

        if (norm == 0.0) {
            return values;
        }

        double length = Math.sqrt(norm);
        java.util.ArrayList<Double> normalized = new java.util.ArrayList<>(values.size());
        for (double value : values) {
            normalized.add(value / length);
        }
        return normalized;
    }
}
