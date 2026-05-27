package com.glid.service;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class EigenfacesExtractor {
    private static final int TARGET_WIDTH = 48;
    private static final int TARGET_HEIGHT = 64;
    private static final double EIGENVALUE_THRESHOLD = 0.0001;
    
    private boolean trained = false;
    private Matrix meanFace;
    private Matrix eigenVectors;
    private int numComponents;
    
    public EigenfacesExtractor(int numComponents) {
        this.numComponents = numComponents;
    }
    
    public void train(List<Path> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            throw new IllegalArgumentException("Training set cannot be empty");
        }
        
        double[][] pixelMatrix = loadImages(imagePaths);
        if (pixelMatrix.length == 0) {
            throw new IllegalArgumentException("No valid images loaded");
        }
        
        int numSamples = pixelMatrix.length;
        int numPixels = pixelMatrix[0].length;
        
        computeMeanFace(pixelMatrix);
        Matrix centeredData = centerData(pixelMatrix);
        Matrix covarianceMatrix = computeCovarianceMatrix(centeredData, numSamples, numPixels);
        
        EigenvalueDecomposition eigen = covarianceMatrix.eig();
        Matrix tempEigenValues = eigen.getD();
        Matrix tempEigenVectors = eigen.getV();
        
        sortAndSelectEigenvectors(tempEigenValues, tempEigenVectors, centeredData, numSamples, numPixels);
        
        trained = true;
    }
    
    private double[][] loadImages(List<Path> imagePaths) {
        List<double[]> validImages = new ArrayList<>();
        
        for (Path path : imagePaths) {
            if (path == null || !Files.exists(path)) {
                continue;
            }
            
            Mat image = Imgcodecs.imread(path.toString(), Imgcodecs.IMREAD_GRAYSCALE);
            if (image.empty()) {
                image.release();
                continue;
            }
            
            Mat resized = new Mat();
            Imgproc.resize(image, resized, new Size(TARGET_WIDTH, TARGET_HEIGHT));
            Imgproc.equalizeHist(resized, resized);
            
            double[] pixels = new double[TARGET_WIDTH * TARGET_HEIGHT];
            for (int y = 0; y < TARGET_HEIGHT; y++) {
                for (int x = 0; x < TARGET_WIDTH; x++) {
                    pixels[y * TARGET_WIDTH + x] = resized.get(y, x)[0];
                }
            }
            
            validImages.add(pixels);
            image.release();
            resized.release();
        }
        
        return validImages.toArray(new double[0][]);
    }
    
    private void computeMeanFace(double[][] pixelMatrix) {
        int numSamples = pixelMatrix.length;
        int numPixels = pixelMatrix[0].length;
        
        double[] mean = new double[numPixels];
        for (int i = 0; i < numSamples; i++) {
            for (int j = 0; j < numPixels; j++) {
                mean[j] += pixelMatrix[i][j];
            }
        }
        for (int j = 0; j < numPixels; j++) {
            mean[j] /= numSamples;
        }
        
        meanFace = new Matrix(mean, 1).transpose();
    }
    
    private Matrix centerData(double[][] pixelMatrix) {
        int numSamples = pixelMatrix.length;
        int numPixels = pixelMatrix[0].length;
        
        Matrix matrix = new Matrix(pixelMatrix);
        Matrix meanRow = meanFace.transpose();
        
        for (int i = 0; i < numSamples; i++) {
            for (int j = 0; j < numPixels; j++) {
                matrix.set(i, j, matrix.get(i, j) - meanRow.get(0, j));
            }
        }
        
        return matrix.transpose();
    }
    
    private Matrix computeCovarianceMatrix(Matrix centeredData, int numSamples, int numPixels) {
        if (numSamples < numPixels) {
            Matrix At = centeredData.transpose();
            return At.times(centeredData);
        } else {
            return centeredData.times(centeredData.transpose());
        }
    }
    
    private void sortAndSelectEigenvectors(Matrix tempEigenValues, Matrix tempEigenVectors, 
                                          Matrix centeredData, int numSamples, int numPixels) {
        int n = tempEigenValues.getColumnDimension();
        
        double[] values = new double[n];
        int[] indices = new int[n];
        for (int i = 0; i < n; i++) {
            values[i] = tempEigenValues.get(i, i);
            indices[i] = i;
        }
        
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                if (values[j] > values[i]) {
                    double tempVal = values[i];
                    values[i] = values[j];
                    values[j] = tempVal;
                    
                    int tempIdx = indices[i];
                    indices[i] = indices[j];
                    indices[j] = tempIdx;
                }
            }
        }
        
        Matrix sortedEigenVectors = new Matrix(tempEigenVectors.getRowDimension(), n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < tempEigenVectors.getRowDimension(); j++) {
                sortedEigenVectors.set(j, i, tempEigenVectors.get(j, indices[i]));
            }
        }
        
        if (numSamples < numPixels) {
            sortedEigenVectors = centeredData.times(sortedEigenVectors);
        }
        
        int validComponents = 0;
        for (int i = 0; i < n && validComponents < numComponents; i++) {
            if (values[i] > EIGENVALUE_THRESHOLD) {
                double norm = 0;
                for (int j = 0; j < sortedEigenVectors.getRowDimension(); j++) {
                    norm += sortedEigenVectors.get(j, i) * sortedEigenVectors.get(j, i);
                }
                norm = Math.sqrt(norm);
                
                if (norm > 0) {
                    for (int j = 0; j < sortedEigenVectors.getRowDimension(); j++) {
                        sortedEigenVectors.set(j, i, sortedEigenVectors.get(j, i) / norm);
                    }
                    validComponents++;
                }
            }
        }
        
        numComponents = Math.min(validComponents, numComponents);
        eigenVectors = sortedEigenVectors.getMatrix(0, sortedEigenVectors.getRowDimension() - 1, 0, numComponents - 1);
    }
    
    public List<Double> extract(Path imagePath) {
        if (!trained) {
            throw new IllegalStateException("Extractor not trained");
        }
        
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
        
        double[] pixels = new double[TARGET_WIDTH * TARGET_HEIGHT];
        for (int y = 0; y < TARGET_HEIGHT; y++) {
            for (int x = 0; x < TARGET_WIDTH; x++) {
                pixels[y * TARGET_WIDTH + x] = resized.get(y, x)[0];
            }
        }
        
        Matrix faceVector = new Matrix(pixels, pixels.length);
        Matrix centered = faceVector.minus(meanFace);
        Matrix projection = eigenVectors.transpose().times(centered);
        
        List<Double> coefficients = new ArrayList<>();
        for (int i = 0; i < numComponents; i++) {
            coefficients.add(projection.get(i, 0));
        }
        
        image.release();
        resized.release();
        
        return normalizeVector(coefficients);
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
    
    private List<Double> normalizeVector(List<Double> values) {
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
    
    public boolean isTrained() {
        return trained;
    }
}
