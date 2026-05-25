package com.glid.service;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FaceEmbeddingExtractor {
    private static final int TARGET_SIZE = 32;
    private static final int GRID = 4;

    public List<Double> extract(Path imagePath) {
        if (imagePath == null || !Files.exists(imagePath)) {
            return List.of();
        }

        Mat image = Imgcodecs.imread(imagePath.toString(), Imgcodecs.IMREAD_GRAYSCALE);
        if (image.empty()) {
            image.release();
            return List.of();
        }

        Mat resized = new Mat();
        Imgproc.resize(image, resized, new Size(TARGET_SIZE, TARGET_SIZE));
        Imgproc.equalizeHist(resized, resized);

        List<Double> features = new ArrayList<>();
        int blockSize = TARGET_SIZE / GRID;
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                int startY = row * blockSize;
                int startX = col * blockSize;
                double sum = 0.0;
                for (int y = startY; y < startY + blockSize; y++) {
                    for (int x = startX; x < startX + blockSize; x++) {
                        sum += resized.get(y, x)[0];
                    }
                }
                features.add(sum / (blockSize * blockSize * 255.0));
            }
        }

        image.release();
        resized.release();
        return normalize(features);
    }

    public double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return 0.0;
        }

        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }

        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
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
        List<Double> normalized = new ArrayList<>(values.size());
        for (double value : values) {
            normalized.add(value / length);
        }
        return normalized;
    }
}
