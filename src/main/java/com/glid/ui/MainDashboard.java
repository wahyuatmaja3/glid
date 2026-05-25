package com.glid.ui;

import com.glid.app.AppContext;
import com.glid.model.DetectionFrame;
import com.glid.model.Employee;
import com.glid.model.RecognitionEvent;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class MainDashboard {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final AppContext context;
    private final TableView<EmployeeRow> employeeTable = new TableView<>();
    private final TableView<AttendanceRow> attendanceTable = new TableView<>();
    private final ListView<String> eventList = new ListView<>();
    private final TextArea reportPreview = new TextArea();
    private final TextField employeeCodeField = new TextField();
    private final TextField fullNameField = new TextField();
    private final TextField departmentField = new TextField();
    private final TextField positionField = new TextField();
    private final CheckBox activeCheckBox = new CheckBox("Active");
    private final CheckBox maskCheckBox = new CheckBox("Mask registered");
    private final Label summaryLabel = new Label();
    private final DatePicker fromDate = new DatePicker(LocalDate.now().minusDays(7));
    private final DatePicker toDate = new DatePicker(LocalDate.now());
    private final TextField filterDepartmentField = new TextField();
    private final TextField filterEmployeeCodeField = new TextField();
    private final TextField cameraIndexField = new TextField("0");
    private final Label cameraStatusLabel = new Label("Camera idle");
    private final Label detectionStatusLabel = new Label("Faces detected: 0");
    private final Label evidenceStatusLabel = new Label("Evidence: none");
    private final CheckBox autoAttendanceCheckBox = new CheckBox("Auto attendance");
    private final ImageView cameraPreview = new ImageView();

    public MainDashboard(AppContext context) {
        this.context = context;
    }

    public Parent build() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        autoAttendanceCheckBox.setSelected(context.autoAttendanceService().isAutoModeEnabled());
        root.setTop(buildHeader());
        root.setCenter(buildMainTabs());
        refreshEmployees();
        refreshAttendance();
        refreshSummary();
        refreshReport();
        return root;
    }

    private VBox buildHeader() {
        Label title = new Label("Glid Offline Face Recognition Attendance");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label subtitle = new Label("MVP desktop dashboard with dedicated tabs for registration, face scan monitoring, and attendance reporting.");
        subtitle.setWrapText(true);

        HBox summary = new HBox(8, new Label("Summary:"), summaryLabel);
        summary.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, title, subtitle, summary);
        box.setPadding(new Insets(0, 0, 16, 0));
        return box;
    }

    private TabPane buildMainTabs() {
        Tab registerTab = new Tab("Register", buildRegistrationPanel());
        registerTab.setClosable(false);

        Tab faceScanTab = new Tab("Scan Wajah", buildScanPanel());
        faceScanTab.setClosable(false);

        Tab reportTab = new Tab("Report", buildReportPanel());
        reportTab.setClosable(false);

        TabPane tabPane = new TabPane(registerTab, faceScanTab, reportTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabPane;
    }

    private VBox buildRegistrationPanel() {
        Label section = new Label("Employee Registration");
        section.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        employeeCodeField.setPromptText("EMP-004");
        fullNameField.setPromptText("Full name");
        departmentField.setPromptText("Department");
        positionField.setPromptText("Position");
        activeCheckBox.setSelected(true);

        Button registerButton = new Button("Register Employee");
        registerButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setOnAction(event -> handleRegisterEmployee());

        configureEmployeeTable();

        VBox panel = new VBox(
                10,
                section,
                labeled("Employee Code", employeeCodeField),
                labeled("Full Name", fullNameField),
                labeled("Department", departmentField),
                labeled("Position", positionField),
                activeCheckBox,
                maskCheckBox,
                registerButton,
                new Label("Registered Employees"),
                employeeTable
        );
        panel.setPadding(new Insets(16));
        VBox.setVgrow(employeeTable, Priority.ALWAYS);
        return panel;
    }

    private VBox buildScanPanel() {
        Label section = new Label("Scan Wajah & Attendance Monitoring");
        section.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        cameraIndexField.setPrefWidth(60);
        autoAttendanceCheckBox.setOnAction(event -> context.autoAttendanceService().setAutoModeEnabled(autoAttendanceCheckBox.isSelected()));

        Button startCamera = new Button("Start Camera");
        startCamera.setOnAction(event -> startCamera());

        Button stopCamera = new Button("Stop Camera");
        stopCamera.setOnAction(event -> stopCamera());

        Button simulateNormal = new Button("Simulate Normal Face");
        simulateNormal.setOnAction(event -> handleRecognition(false));

        Button simulateMasked = new Button("Simulate Masked Face");
        simulateMasked.setOnAction(event -> handleRecognition(true));

        HBox actions = new HBox(
                10,
                new Label("Camera"),
                cameraIndexField,
                startCamera,
                stopCamera,
                autoAttendanceCheckBox,
                simulateNormal,
                simulateMasked
        );
        actions.setAlignment(Pos.CENTER_LEFT);

        HBox cameraStatus = new HBox(10, new Label("Status:"), cameraStatusLabel, new Label("Detection:"), detectionStatusLabel, evidenceStatusLabel);
        cameraStatus.setAlignment(Pos.CENTER_LEFT);

        configureAttendanceTable();
        eventList.setPrefHeight(170);
        cameraPreview.setFitWidth(500);
        cameraPreview.setFitHeight(280);
        cameraPreview.setPreserveRatio(true);
        cameraPreview.setSmooth(true);

        Button refreshButton = new Button("Refresh History");
        refreshButton.setOnAction(event -> {
            refreshAttendance();
            refreshReport();
        });

        Button deleteButton = new Button("Delete Selected Log");
        deleteButton.setOnAction(event -> deleteSelectedAttendance());

        HBox buttons = new HBox(10, refreshButton, deleteButton);

        VBox panel = new VBox(
                10,
                section,
                actions,
                cameraStatus,
                new Label("Live Camera Preview"),
                cameraPreview,
                new Label("Recognition Events"),
                eventList,
                buttons,
                attendanceTable
        );
        panel.setPadding(new Insets(16));
        VBox.setVgrow(attendanceTable, Priority.ALWAYS);
        return panel;
    }

    private VBox buildReportPanel() {
        Label section = new Label("Attendance Report");
        section.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        filterDepartmentField.setPromptText("Department filter");
        filterEmployeeCodeField.setPromptText("Employee code filter");

        Button applyButton = new Button("Generate Preview");
        applyButton.setOnAction(event -> refreshReport());

        reportPreview.setEditable(false);
        reportPreview.setWrapText(false);

        GridPane filters = new GridPane();
        filters.setHgap(8);
        filters.setVgap(8);
        filters.add(new Label("From"), 0, 0);
        filters.add(fromDate, 1, 0);
        filters.add(new Label("To"), 0, 1);
        filters.add(toDate, 1, 1);
        filters.add(new Label("Department"), 0, 2);
        filters.add(filterDepartmentField, 1, 2);
        filters.add(new Label("Employee Code"), 0, 3);
        filters.add(filterEmployeeCodeField, 1, 3);
        filters.add(applyButton, 1, 4);

        VBox panel = new VBox(10, section, filters, new Label("CSV Export Preview"), reportPreview);
        panel.setPadding(new Insets(16));
        VBox.setVgrow(reportPreview, Priority.ALWAYS);
        return panel;
    }

    private VBox labeled(String label, TextField field) {
        return new VBox(4, new Label(label), field);
    }

    private void handleRegisterEmployee() {
        if (employeeCodeField.getText().isBlank() || fullNameField.getText().isBlank() || departmentField.getText().isBlank()) {
            eventList.getItems().add(0, "Registration blocked: employee code, full name, and department are required.");
            return;
        }

        Employee employee = context.employeeService().registerEmployee(
                employeeCodeField.getText().trim(),
                fullNameField.getText().trim(),
                departmentField.getText().trim(),
                positionField.getText().trim(),
                activeCheckBox.isSelected(),
                maskCheckBox.isSelected()
        );

        eventList.getItems().add(0, "Registered " + employee.fullName() + " with 5 generated face embeddings.");
        employeeCodeField.clear();
        fullNameField.clear();
        departmentField.clear();
        positionField.clear();
        maskCheckBox.setSelected(false);
        activeCheckBox.setSelected(true);
        refreshEmployees();
        refreshSummary();
    }

    private void handleRecognition(boolean masked) {
        RecognitionEvent event = context.pipeline().simulateRecognition("CAM-01", masked);
        eventList.getItems().add(0, String.format(
                "%s | %s | confidence %.1f%% | %s",
                event.employeeName(),
                event.recognizedAt().format(TIMESTAMP_FORMAT),
                event.confidence() * 100,
                event.status()));
        refreshAttendance();
        refreshReport();
        refreshSummary();
    }

    private void startCamera() {
        int cameraIndex;
        try {
            cameraIndex = Integer.parseInt(cameraIndexField.getText().trim());
        } catch (NumberFormatException exception) {
            cameraStatusLabel.setText("Invalid camera index");
            return;
        }

        context.cameraCaptureService().startCamera(
                cameraIndex,
                this::renderDetectionFrame,
                status -> cameraStatusLabel.setText(status)
        );
    }

    private void stopCamera() {
        context.cameraCaptureService().stopCamera();
        cameraStatusLabel.setText("Camera stopping...");
    }

    private void renderDetectionFrame(DetectionFrame frame) {
        cameraPreview.setImage(frame.image());
        if (frame.detectorReady()) {
            detectionStatusLabel.setText("Faces detected: " + frame.faceCount());
        } else {
            detectionStatusLabel.setText(frame.detectorStatus());
        }

        if (context.cameraCaptureService().getLastDetectionArtifact() != null) {
            evidenceStatusLabel.setText("Evidence ready");
        } else {
            evidenceStatusLabel.setText("Evidence: none");
        }

        RecognitionEvent autoEvent = context.autoAttendanceService().tryAutoAttendance("CAM-01", false);
        if (autoEvent != null) {
            eventList.getItems().add(0, String.format(
                    "AUTO | %s | %s | confidence %.1f%% | %s",
                    autoEvent.employeeName(),
                    autoEvent.recognizedAt().format(TIMESTAMP_FORMAT),
                    autoEvent.confidence() * 100,
                    autoEvent.status()));
            refreshAttendance();
            refreshReport();
            refreshSummary();
        }
    }

    private void deleteSelectedAttendance() {
        AttendanceRow selected = attendanceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            eventList.getItems().add(0, "Delete skipped: choose an attendance log first.");
            return;
        }

        context.attendanceService().delete(selected.id());
        eventList.getItems().add(0, "Deleted attendance log " + selected.id() + " for " + selected.employeeName() + ".");
        refreshAttendance();
        refreshReport();
        refreshSummary();
    }

    private void refreshEmployees() {
        employeeTable.setItems(FXCollections.observableArrayList(
                context.employeeService().findAll().stream()
                        .map(employee -> new EmployeeRow(
                                employee.id(),
                                employee.employeeCode(),
                                employee.fullName(),
                                employee.department(),
                                employee.position(),
                                employee.active() ? "Active" : "Inactive",
                                employee.maskRegistered() ? "Yes" : "No"
                        ))
                        .toList()));
    }

    private void refreshAttendance() {
        attendanceTable.setItems(FXCollections.observableArrayList(
                context.attendanceService().history().stream()
                        .map(log -> new AttendanceRow(
                                log.id(),
                                log.employeeCode(),
                                log.employeeName(),
                                log.attendanceType().name(),
                                log.timestamp().format(TIMESTAMP_FORMAT),
                                log.cameraId(),
                                log.imagePath()
                        ))
                        .toList()));
    }

    private void refreshReport() {
        reportPreview.setText(context.reportService().exportCsvPreview(
                fromDate.getValue(),
                toDate.getValue(),
                filterDepartmentField.getText().trim(),
                filterEmployeeCodeField.getText().trim()
        ));
    }

    private void refreshSummary() {
        summaryLabel.setText("Employees: " + context.reportService().totalEmployees() + " | Logs: " + context.attendanceService().history().size());
    }

    private void configureEmployeeTable() {
        TableColumn<EmployeeRow, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(new PropertyValueFactory<>("employeeCode"));

        TableColumn<EmployeeRow, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(new PropertyValueFactory<>("fullName"));

        TableColumn<EmployeeRow, String> department = new TableColumn<>("Dept");
        department.setCellValueFactory(new PropertyValueFactory<>("department"));

        TableColumn<EmployeeRow, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(new PropertyValueFactory<>("status"));

        employeeTable.getColumns().setAll(code, name, department, status);
        employeeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void configureAttendanceTable() {
        TableColumn<AttendanceRow, Long> id = new TableColumn<>("ID");
        id.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<AttendanceRow, String> employeeCode = new TableColumn<>("Code");
        employeeCode.setCellValueFactory(new PropertyValueFactory<>("employeeCode"));

        TableColumn<AttendanceRow, String> employeeName = new TableColumn<>("Name");
        employeeName.setCellValueFactory(new PropertyValueFactory<>("employeeName"));

        TableColumn<AttendanceRow, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(new PropertyValueFactory<>("attendanceType"));

        TableColumn<AttendanceRow, String> timestamp = new TableColumn<>("Timestamp");
        timestamp.setCellValueFactory(new PropertyValueFactory<>("timestamp"));

        attendanceTable.getColumns().setAll(id, employeeCode, employeeName, type, timestamp);
        attendanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    public record EmployeeRow(long id, String employeeCode, String fullName, String department, String position, String status, String maskRegistered) {
    }

    public record AttendanceRow(long id, String employeeCode, String employeeName, String attendanceType, String timestamp, String cameraId, String imagePath) {
    }
}
