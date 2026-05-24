package com.glid.model;

import java.time.LocalDateTime;
import java.util.List;

public record FaceEmbedding(
        long id,
        long employeeId,
        List<Double> embeddingVector,
        boolean maskVersion,
        LocalDateTime createdAt
) {
}
