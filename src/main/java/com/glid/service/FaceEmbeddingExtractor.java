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
    private static final int TARGET_WIDTH = 48;
    private static final int TARGET_HEIGHT = 64;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 8;
    private static final int RADIUS = 1;
    private static final int NUM_BINS = 256;

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
        Imgproc.resize(image, resized, new Size(TARGET_WIDTH, TARGET_HEIGHT));
        Imgproc.equalizeHist(resized, resized);

        int[][] grayImage = new int[TARGET_HEIGHT][TARGET_WIDTH];
        for (int y = 0; y < TARGET_HEIGHT; y++) {
            for (int x = 0; x < TARGET_WIDTH; x++) {
                grayImage[y][x] = (int) resized.get(y, x)[0];
            }
        }

        int[][] lbpImage = computeLBP(grayImage);
        List<Double> histogram = computeRegionHistograms(lbpImage);

        image.release();
        resized.release();
        
        return normalizeHistogram(histogram);
    }

    private int[][] computeLBP(int[][] gray) {
        int[][] lbp = new int[TARGET_HEIGHT][TARGET_WIDTH];
        
        for (int y = RADIUS; y < TARGET_HEIGHT - RADIUS; y++) {
            for (int x = RADIUS; x < TARGET_WIDTH - RADIUS; x++) {
                int center = gray[y][x];
                int lbpCode = 0;
                
                if (gray[y - 1][x - 1] >= center) lbpCode |= (1 << 0);
                if (gray[y - 1][x] >= center) lbpCode |= (1 << 1);
                if (gray[y - 1][x + 1] >= center) lbpCode |= (1 << 2);
                if (gray[y][x + 1] >= center) lbpCode |= (1 << 3);
                if (gray[y + 1][x + 1] >= center) lbpCode |= (1 << 4);
                if (gray[y + 1][x] >= center) lbpCode |= (1 << 5);
                if (gray[y + 1][x - 1] >= center) lbpCode |= (1 << 6);
                if (gray[y][x - 1] >= center) lbpCode |= (1 << 7);
                
                lbp[y][x] = lbpCode;
            }
        }
        
        return lbp;
    }

    private List<Double> computeRegionHistograms(int[][] lbp) {
        int regionWidth = TARGET_WIDTH / GRID_X;
        int regionHeight = TARGET_HEIGHT / GRID_Y;
        List<Double> histogram = new ArrayList<>(GRID_X * GRID_Y * NUM_BINS);
        
        for (int i = 0; i < GRID_X * GRID_Y * NUM_BINS; i++) {
            histogram.add(0.0);
        }
        
        for (int gy = 0; gy < GRID_Y; gy++) {
            for (int gx = 0; gx < GRID_X; gx++) {
                int regionIdx = (gy * GRID_X + gx) * NUM_BINS;
                
                int startX = gx * regionWidth;
                int startY = gy * regionHeight;
                int endX = (gx == GRID_X - 1) ? TARGET_WIDTH : startX + regionWidth;
                int endY = (gy == GRID_Y - 1) ? TARGET_HEIGHT : startY + regionHeight;
                
                for (int y = Math.max(RADIUS, startY); y < Math.min(TARGET_HEIGHT - RADIUS, endY); y++) {
                    for (int x = Math.max(RADIUS, startX); x < Math.min(TARGET_WIDTH - RADIUS, endX); x++) {
                        int binIdx = regionIdx + lbp[y][x];
                        histogram.set(binIdx, histogram.get(binIdx) + 1.0);
                    }
                }
            }
        }
        
        return histogram;
    }

    private List<Double> normalizeHistogram(List<Double> histogram) {
        List<Double> normalized = new ArrayList<>(histogram.size());
        
        for (int region = 0; region < GRID_X * GRID_Y; region++) {
            int start = region * NUM_BINS;
            double sum = 0;
            
            for (int i = 0; i < NUM_BINS; i++) {
                sum += histogram.get(start + i);
            }
            
            if (sum > 0) {
                for (int i = 0; i < NUM_BINS; i++) {
                    normalized.add(histogram.get(start + i) / sum);
                }
            } else {
                for (int i = 0; i < NUM_BINS; i++) {
                    normalized.add(0.0);
                }
            }
        }
        
        return normalized;
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
    
    public double chiSquareDistance(List<Double> left, List<Double> right) {
        if (left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return Double.MAX_VALUE;
        }
        
        double distance = 0.0;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            if (a + b > 0) {
                distance += ((a - b) * (a - b)) / (a + b);
            }
        }
        
        return distance;
    }
}
