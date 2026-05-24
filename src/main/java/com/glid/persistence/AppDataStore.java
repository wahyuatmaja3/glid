package com.glid.persistence;

import com.glid.model.AttendanceLog;
import com.glid.model.AttendanceType;
import com.glid.model.Employee;
import com.glid.model.FaceEmbedding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class AppDataStore {
    private static final AppDataStore INSTANCE = new AppDataStore();
    private static final String DATABASE_URL = "jdbc:sqlite:data/glid.db";

    private Connection connection;

    private AppDataStore() {
        initializeDatabase();
        seedDemoDataIfEmpty();
    }

    public static AppDataStore getInstance() {
        return INSTANCE;
    }

    public static void shutdown() {
        INSTANCE.closeConnection();
    }

    public Employee saveEmployee(String employeeCode, String fullName, String department, String position, boolean active, boolean maskRegistered) {
        String sql = """
                INSERT INTO employees (employee_code, full_name, department, position, status, mask_registered, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        LocalDateTime createdAt = LocalDateTime.now();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, employeeCode);
            statement.setString(2, fullName);
            statement.setString(3, department);
            statement.setString(4, position);
            statement.setString(5, active ? "ACTIVE" : "INACTIVE");
            statement.setInt(6, maskRegistered ? 1 : 0);
            statement.setString(7, createdAt.toString());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new Employee(keys.getLong(1), employeeCode, fullName, department, position, active, maskRegistered, createdAt);
                }
            }
            throw new IllegalStateException("Failed to retrieve employee id");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save employee", exception);
        }
    }

    public FaceEmbedding saveEmbedding(long employeeId, List<Double> vector, boolean maskVersion) {
        String sql = """
                INSERT INTO face_embeddings (employee_id, embedding_vector, mask_version, created_at)
                VALUES (?, ?, ?, ?)
                """;
        LocalDateTime createdAt = LocalDateTime.now();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, employeeId);
            statement.setString(2, serializeVector(vector));
            statement.setInt(3, maskVersion ? 1 : 0);
            statement.setString(4, createdAt.toString());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new FaceEmbedding(keys.getLong(1), employeeId, List.copyOf(vector), maskVersion, createdAt);
                }
            }
            throw new IllegalStateException("Failed to retrieve embedding id");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save face embedding", exception);
        }
    }

    public AttendanceLog saveAttendance(long employeeId, String employeeCode, String employeeName, AttendanceType type, String imagePath, String cameraId) {
        String sql = """
                INSERT INTO attendance_logs (employee_id, attendance_type, timestamp, image_path, camera_id)
                VALUES (?, ?, ?, ?, ?)
                """;
        LocalDateTime timestamp = LocalDateTime.now();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, employeeId);
            statement.setString(2, type.name());
            statement.setString(3, timestamp.toString());
            statement.setString(4, imagePath);
            statement.setString(5, cameraId);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new AttendanceLog(keys.getLong(1), employeeId, employeeCode, employeeName, type, timestamp, imagePath, cameraId);
                }
            }
            throw new IllegalStateException("Failed to retrieve attendance id");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save attendance log", exception);
        }
    }

    public List<Employee> findAllEmployees() {
        String sql = """
                SELECT id, employee_code, full_name, department, position, status, mask_registered, created_at
                FROM employees
                ORDER BY full_name ASC
                """;
        List<Employee> employees = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                employees.add(mapEmployee(resultSet));
            }
            return employees;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load employees", exception);
        }
    }

    public List<FaceEmbedding> findEmbeddings(long employeeId) {
        String sql = """
                SELECT id, employee_id, embedding_vector, mask_version, created_at
                FROM face_embeddings
                WHERE employee_id = ?
                ORDER BY created_at ASC
                """;
        List<FaceEmbedding> faceEmbeddings = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, employeeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    faceEmbeddings.add(mapEmbedding(resultSet));
                }
            }
            return faceEmbeddings;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load face embeddings", exception);
        }
    }

    public List<AttendanceLog> findAttendanceLogs() {
        return queryAttendanceLogs(null, null, null, null);
    }

    public List<AttendanceLog> findAttendanceLogs(LocalDate from, LocalDate to, String department, String employeeCode) {
        return queryAttendanceLogs(from, to, department, employeeCode);
    }

    public Employee findEmployeeById(long employeeId) {
        String sql = """
                SELECT id, employee_code, full_name, department, position, status, mask_registered, created_at
                FROM employees
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, employeeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapEmployee(resultSet);
                }
                return null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find employee", exception);
        }
    }

    public void deleteAttendance(long attendanceId) {
        String sql = "DELETE FROM attendance_logs WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, attendanceId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete attendance log", exception);
        }
    }

    public LocalDateTime findLatestAttendanceTimestamp(long employeeId) {
        String sql = """
                SELECT timestamp
                FROM attendance_logs
                WHERE employee_id = ?
                ORDER BY timestamp DESC
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, employeeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return LocalDateTime.parse(resultSet.getString("timestamp"));
                }
                return null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load latest attendance", exception);
        }
    }

    private void initializeDatabase() {
        try {
            Files.createDirectories(Path.of("data"));
            connection = DriverManager.getConnection(DATABASE_URL);
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS employees (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            employee_code TEXT NOT NULL UNIQUE,
                            full_name TEXT NOT NULL,
                            department TEXT NOT NULL,
                            position TEXT,
                            status TEXT NOT NULL,
                            mask_registered INTEGER NOT NULL DEFAULT 0,
                            created_at TEXT NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS face_embeddings (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            employee_id INTEGER NOT NULL,
                            embedding_vector TEXT NOT NULL,
                            mask_version INTEGER NOT NULL DEFAULT 0,
                            created_at TEXT NOT NULL,
                            FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS attendance_logs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            employee_id INTEGER NOT NULL,
                            attendance_type TEXT NOT NULL,
                            timestamp TEXT NOT NULL,
                            image_path TEXT NOT NULL,
                            camera_id TEXT NOT NULL,
                            FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
                        )
                        """);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize SQLite database", exception);
        }
    }

    private void seedDemoDataIfEmpty() {
        if (!findAllEmployees().isEmpty()) {
            return;
        }

        Employee nisa = saveEmployee("EMP-001", "Nisa Ramadhani", "HR", "HR Lead", true, true);
        Employee bima = saveEmployee("EMP-002", "Bima Prasetyo", "Warehouse", "Supervisor", true, false);
        Employee rani = saveEmployee("EMP-003", "Rani Saputra", "Finance", "Analyst", true, true);

        saveEmbedding(nisa.id(), vector(0.12, 0.45, 0.88, 0.17, 0.56), true);
        saveEmbedding(nisa.id(), vector(0.14, 0.42, 0.84, 0.20, 0.51), false);
        saveEmbedding(bima.id(), vector(0.77, 0.15, 0.34, 0.68, 0.09), false);
        saveEmbedding(rani.id(), vector(0.41, 0.63, 0.12, 0.75, 0.23), true);

        saveAttendance(nisa.id(), nisa.employeeCode(), nisa.fullName(), AttendanceType.CHECK_IN, "evidence/emp-001-1.jpg", "CAM-01");
        saveAttendance(bima.id(), bima.employeeCode(), bima.fullName(), AttendanceType.CHECK_IN, "evidence/emp-002-1.jpg", "CAM-01");
    }

    private List<AttendanceLog> queryAttendanceLogs(LocalDate from, LocalDate to, String department, String employeeCode) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id, a.employee_id, e.employee_code, e.full_name, a.attendance_type, a.timestamp, a.image_path, a.camera_id
                FROM attendance_logs a
                JOIN employees e ON e.id = a.employee_id
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();

        if (from != null) {
            sql.append(" AND date(a.timestamp) >= date(?)");
            parameters.add(from.toString());
        }
        if (to != null) {
            sql.append(" AND date(a.timestamp) <= date(?)");
            parameters.add(to.toString());
        }
        if (department != null && !department.isBlank()) {
            sql.append(" AND lower(e.department) = lower(?)");
            parameters.add(department);
        }
        if (employeeCode != null && !employeeCode.isBlank()) {
            sql.append(" AND lower(e.employee_code) = lower(?)");
            parameters.add(employeeCode);
        }
        sql.append(" ORDER BY a.timestamp DESC");

        List<AttendanceLog> attendanceLogs = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    attendanceLogs.add(mapAttendance(resultSet));
                }
            }
            return attendanceLogs;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load attendance logs", exception);
        }
    }

    private Employee mapEmployee(ResultSet resultSet) throws SQLException {
        return new Employee(
                resultSet.getLong("id"),
                resultSet.getString("employee_code"),
                resultSet.getString("full_name"),
                resultSet.getString("department"),
                resultSet.getString("position"),
                "ACTIVE".equalsIgnoreCase(resultSet.getString("status")),
                resultSet.getInt("mask_registered") == 1,
                LocalDateTime.parse(resultSet.getString("created_at"))
        );
    }

    private FaceEmbedding mapEmbedding(ResultSet resultSet) throws SQLException {
        return new FaceEmbedding(
                resultSet.getLong("id"),
                resultSet.getLong("employee_id"),
                deserializeVector(resultSet.getString("embedding_vector")),
                resultSet.getInt("mask_version") == 1,
                LocalDateTime.parse(resultSet.getString("created_at"))
        );
    }

    private AttendanceLog mapAttendance(ResultSet resultSet) throws SQLException {
        return new AttendanceLog(
                resultSet.getLong("id"),
                resultSet.getLong("employee_id"),
                resultSet.getString("employee_code"),
                resultSet.getString("full_name"),
                AttendanceType.valueOf(resultSet.getString("attendance_type")),
                LocalDateTime.parse(resultSet.getString("timestamp")),
                resultSet.getString("image_path"),
                resultSet.getString("camera_id")
        );
    }

    private String serializeVector(List<Double> vector) {
        return vector.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private List<Double> deserializeVector(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        List<Double> values = new ArrayList<>();
        for (String token : raw.split(",")) {
            values.add(Double.parseDouble(token));
        }
        return values;
    }

    private void closeConnection() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to close SQLite connection", exception);
        }
    }

    private List<Double> vector(double... values) {
        List<Double> list = new ArrayList<>();
        for (double value : values) {
            list.add(value);
        }
        return list;
    }
}
