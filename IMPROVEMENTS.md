# Perbaikan Face Recognition - Glid

## Ringkasan Perubahan

Proyek Glid telah diperbaiki dengan mengadopsi algoritma face recognition yang lebih baik dari referensi [prasadus92/face-recognition](https://github.com/prasadus92/face-recognition).

## Perubahan yang Dilakukan

### 1. Penambahan Dependency JAMA (Java Matrix Library)
- **File**: `pom.xml`
- **Perubahan**: Menambahkan dependency `gov.nist.math:jama:1.0.3`
- **Alasan**: Diperlukan untuk implementasi Eigenfaces (PCA) yang menggunakan operasi matriks dan eigenvalue decomposition

### 2. Implementasi Eigenfaces Extractor (PCA)
- **File Baru**: `src/main/java/com/glid/service/EigenfacesExtractor.java`
- **Algoritma**: Principal Component Analysis (PCA) untuk face recognition
- **Fitur**:
  - Training dengan multiple face images
  - Komputasi mean face dan eigenfaces
  - Proyeksi wajah ke eigenface space
  - Normalisasi feature vectors
  - Cosine similarity untuk matching

### 3. Perbaikan LBPH Extractor
- **File**: `src/main/java/com/glid/service/FaceEmbeddingExtractor.java`
- **Perubahan**:
  - Upgrade dari simple grid-based features ke Local Binary Pattern Histogram (LBPH)
  - Implementasi proper LBP computation dengan 8 neighbors
  - Grid-based histogram (8x8 regions)
  - Histogram normalization per region
  - Menambahkan Chi-Square distance metric sebagai alternatif

### 4. Penyesuaian Threshold
- **File**: `src/main/java/com/glid/service/FaceRecognitionPipeline.java`
- **Perubahan**:
  - `NORMAL_MATCH_THRESHOLD`: 0.75 → 0.65
  - `MASK_MATCH_THRESHOLD`: 0.70 → 0.60
- **Alasan**: Threshold disesuaikan untuk algoritma LBPH yang memiliki karakteristik similarity berbeda

## Algoritma yang Tersedia

### 1. LBPH (Local Binary Pattern Histogram)
- **Kelebihan**:
  - Tidak memerlukan training
  - Robust terhadap perubahan pencahayaan
  - Menangkap texture patterns lokal
  - Komputasi cepat
- **Kekurangan**:
  - Sensitif terhadap pose
  - Memory footprint lebih besar (8x8x256 = 16,384 dimensions)

### 2. Eigenfaces (PCA)
- **Kelebihan**:
  - Dimensi lebih kecil (configurable, default 10 components)
  - Baik untuk lingkungan terkontrol
  - Kompresi data yang efisien
- **Kekurangan**:
  - Memerlukan training dengan multiple samples
  - Sensitif terhadap pencahayaan dan pose
  - Perlu retrain saat menambah employee baru

## Cara Menggunakan Eigenfaces (Opsional)

Jika ingin menggunakan Eigenfaces sebagai pengganti LBPH:

1. Modifikasi `EmployeeService.java` untuk menggunakan `EigenfacesExtractor`
2. Kumpulkan multiple face crops per employee (minimal 5-10 samples)
3. Train extractor dengan semua samples:
   ```java
   EigenfacesExtractor extractor = new EigenfacesExtractor(10);
   List<Path> allFacePaths = collectAllEmployeeFaces();
   extractor.train(allFacePaths);
   ```
4. Gunakan extractor untuk extract dan match

## Perbandingan dengan Referensi

### Dari prasadus92/face-recognition:
- ✅ Eigenfaces implementation (PCA via JAMA)
- ✅ LBPH implementation
- ✅ Proper feature extraction
- ✅ Multiple distance metrics
- ❌ Fisherfaces (LDA) - tidak diimplementasikan (memerlukan labeled training data)
- ❌ REST API - tidak diperlukan untuk desktop app
- ❌ Deep learning backend - out of scope untuk MVP

### Arsitektur Glid:
- Desktop JavaFX application
- SQLite database
- OpenCV untuk camera capture dan face detection
- Real-time face recognition pipeline
- Attendance logging system

## Testing

Aplikasi berhasil dikompilasi dan berjalan. Warning JavaFX yang muncul terkait PropertyValueFactory adalah issue UI minor yang tidak mempengaruhi fungsi face recognition.

## Rekomendasi Selanjutnya

1. **Testing dengan Real Data**: Test dengan multiple employees dan berbagai kondisi pencahayaan
2. **Threshold Tuning**: Sesuaikan threshold berdasarkan hasil testing
3. **Hybrid Approach**: Pertimbangkan menggunakan ensemble dari LBPH dan Eigenfaces
4. **Face Alignment**: Tambahkan eye-based face alignment untuk meningkatkan akurasi
5. **Quality Check**: Tambahkan face quality assessment sebelum enrollment
6. **Performance Monitoring**: Log similarity scores untuk analisis dan tuning

## Referensi

- [prasadus92/face-recognition](https://github.com/prasadus92/face-recognition) - Classical face recognition library
- Turk, M. & Pentland, A. (1991). *Eigenfaces for Recognition*
- Ahonen, T., Hadid, A. & Pietikäinen, M. (2006). *Face Description with Local Binary Patterns*
