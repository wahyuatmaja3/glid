package com.glid.ui;

import com.glid.app.AppContext;
import com.glid.model.DetectionArtifact;
import com.glid.model.DetectionFrame;
import com.glid.model.Employee;
import com.glid.model.RecognitionEvent;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;

public class MainDashboard {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final AppContext context;
    private final TableView<AttendanceRow> attendanceTable = new TableView<>();
    private final TableView<AttendanceRow> reportTable = new TableView<>();
    private final ListView<String> eventList = new ListView<>();
    private final TextField employeeCodeField = new TextField();
    private final TextField fullNameField = new TextField();
    private final TextField departmentField = new TextField();
    private final TextField positionField = new TextField();
    private final CheckBox activeCheckBox = new CheckBox("Active");
    private final CheckBox maskCheckBox = new CheckBox("Mask");
    private final TextField cameraIndexField = new TextField("0");
    private final Label cameraStatusLabel = new Label("Camera idle");
    private final Label detectionStatusLabel = new Label("Faces detected: 0");
    private final Label evidenceStatusLabel = new Label("Evidence: none");
    private final Label evidenceEmployeeLabel = new Label("Employee: -");
    private final CheckBox autoAttendanceCheckBox = new CheckBox("Auto");
    private final ImageView cameraPreview = new ImageView();
    private final Label registerFaceStatusLabel = new Label("Face sample: not captured");
    private VBox quickRegisterPanel;
    private HBox cameraControls;
    private VBox reportPanel;

    public MainDashboard(AppContext context) { this.context = context; }

    public Parent build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("app-root", "kiosk-root");
        root.setPadding(new Insets(8));
        autoAttendanceCheckBox.setSelected(context.autoAttendanceService().isAutoModeEnabled());
        root.setCenter(buildKioskView());
        root.setFocusTraversable(true);
        root.setOnKeyPressed(event -> handleHotkey(event.getCode()));
        refreshAttendance();
        return root;
    }

    private VBox buildKioskView() {
        Label title = new Label("GLID KIOSK");
        title.getStyleClass().add("kiosk-title");

        Label subtitle = new Label("Face attendance realtime");
        subtitle.getStyleClass().add("kiosk-subtitle");

        quickRegisterPanel = buildQuickRegisterPanel();
        VBox scanPanel = buildScanPanel();
        reportPanel = buildReportPanel();
        setVisibleManaged(quickRegisterPanel, false);
        setVisibleManaged(reportPanel, false);
        return new VBox(8, title, subtitle, quickRegisterPanel, scanPanel, reportPanel);
    }

    private VBox buildQuickRegisterPanel() {
        employeeCodeField.setPromptText("Code");
        fullNameField.setPromptText("Name");
        departmentField.setPromptText("Dept");
        positionField.setPromptText("Position");
        activeCheckBox.setSelected(true);

        Button captureFaceButton = new Button("Capture");
        captureFaceButton.setOnAction(event -> captureFaceSampleForRegistration());
        Button registerButton = new Button("Register");
        registerButton.setOnAction(event -> handleRegisterEmployee());

        HBox row1 = new HBox(8, employeeCodeField, fullNameField, departmentField, positionField);
        HBox row2 = new HBox(8, activeCheckBox, maskCheckBox, captureFaceButton, registerButton, registerFaceStatusLabel);

        VBox panel = new VBox(8, row1, row2);
        panel.getStyleClass().add("panel-card");
        panel.setPadding(new Insets(10));
        return panel;
    }

    private VBox buildScanPanel() {
        cameraIndexField.setPrefWidth(56);
        autoAttendanceCheckBox.setOnAction(event -> context.autoAttendanceService().setAutoModeEnabled(autoAttendanceCheckBox.isSelected()));

        Button startCamera = new Button("Start");
        startCamera.setOnAction(event -> startCamera());
        Button stopCamera = new Button("Stop");
        stopCamera.setOnAction(event -> stopCamera());

        cameraControls = new HBox(6, cameraIndexField, startCamera, stopCamera, autoAttendanceCheckBox);
        cameraControls.getStyleClass().addAll("toolbar-row", "camera-overlay-controls");
        cameraControls.setAlignment(Pos.CENTER_RIGHT);
        setVisibleManaged(cameraControls, false);

        HBox cameraStatus = new HBox(10, cameraStatusLabel, detectionStatusLabel);
        cameraStatus.getStyleClass().addAll("status-row", "compact-muted-row");

        configureAttendanceTable();
        eventList.setPrefHeight(110);
        cameraPreview.setFitWidth(1200);
        cameraPreview.setFitHeight(620);
        cameraPreview.setPreserveRatio(true);

        VBox evidenceBox = new VBox(4, new Label("Evidence"), evidenceStatusLabel, evidenceEmployeeLabel);
        evidenceBox.getStyleClass().add("evidence-box");

        StackPane cameraArea = new StackPane(cameraPreview, cameraControls);
        cameraArea.getStyleClass().add("camera-stage");
        StackPane.setAlignment(cameraControls, Pos.TOP_RIGHT);

        VBox panel = new VBox(8, cameraStatus, cameraArea, evidenceBox, eventList, attendanceTable);
        panel.getStyleClass().add("panel-card");
        panel.setPadding(new Insets(10));
        VBox.setVgrow(attendanceTable, Priority.ALWAYS);
        return panel;
    }

    private VBox buildReportPanel() {
        Label reportTitle = new Label("Report");
        reportTitle.getStyleClass().add("section-title");
        configureReportTable();
        VBox panel = new VBox(8, reportTitle, reportTable);
        panel.getStyleClass().add("panel-card");
        panel.setPadding(new Insets(10));
        VBox.setVgrow(reportTable, Priority.ALWAYS);
        return panel;
    }

    private void handleHotkey(KeyCode code) {
        if (code == KeyCode.F1) {
            setVisibleManaged(quickRegisterPanel, !quickRegisterPanel.isVisible());
        } else if (code == KeyCode.F2) {
            autoAttendanceCheckBox.setSelected(true);
            context.autoAttendanceService().setAutoModeEnabled(true);
            if (context.cameraCaptureService().isRunning()) {
                stopCamera();
            } else {
                startCamera();
            }
        } else if (code == KeyCode.F3) {
            setVisibleManaged(reportPanel, !reportPanel.isVisible());
        }
    }

    private void setVisibleManaged(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void handleRegisterEmployee() {
        if (employeeCodeField.getText().isBlank() || fullNameField.getText().isBlank() || departmentField.getText().isBlank()) {
            eventList.getItems().add(0, "Registration blocked: employee code, full name, and department are required.");
            return;
        }
        DetectionArtifact artifact = context.cameraCaptureService().getLastDetectionArtifact();
        if (artifact == null) {
            eventList.getItems().add(0, "Registration blocked: no detected face yet.");
            return;
        }
        try {
            Employee employee = context.employeeService().registerEmployee(
                    employeeCodeField.getText().trim(), fullNameField.getText().trim(), departmentField.getText().trim(),
                    positionField.getText().trim(), activeCheckBox.isSelected(), maskCheckBox.isSelected());
            eventList.getItems().add(0, "Registered " + employee.fullName());
            refreshRegistrationFaceStatus();
        } catch (IllegalStateException exception) {
            eventList.getItems().add(0, "Registration failed: employee code already exists or data is invalid.");
        }
    }

    private void captureFaceSampleForRegistration() {
        DetectionArtifact artifact = context.cameraCaptureService().getLastDetectionArtifact();
        if (artifact == null) {
            registerFaceStatusLabel.setText("Face sample: not captured");
            return;
        }
        registerFaceStatusLabel.setText("Face sample: " + artifact.capturedAt().format(TIMESTAMP_FORMAT));
    }

    private void refreshRegistrationFaceStatus() {
        DetectionArtifact artifact = context.cameraCaptureService().getLastDetectionArtifact();
        if (artifact == null) {
            registerFaceStatusLabel.setText("Face sample: not captured");
            return;
        }
        registerFaceStatusLabel.setText("Face sample: " + artifact.capturedAt().format(TIMESTAMP_FORMAT));
    }

    private void startCamera() {
        int cameraIndex;
        try { cameraIndex = Integer.parseInt(cameraIndexField.getText().trim()); }
        catch (NumberFormatException exception) { cameraStatusLabel.setText("Invalid camera index"); return; }
        context.cameraCaptureService().startCamera(cameraIndex, this::renderDetectionFrame, status -> cameraStatusLabel.setText(status));
    }

    private void stopCamera() { context.cameraCaptureService().stopCamera(); }

    private void renderDetectionFrame(DetectionFrame frame) {
        cameraPreview.setImage(frame.image());
        detectionStatusLabel.setText(frame.detectorReady() ? "Faces detected: " + frame.faceCount() : frame.detectorStatus());
        if (context.cameraCaptureService().getLastDetectionArtifact() != null) evidenceStatusLabel.setText("Evidence ready");
        else { evidenceStatusLabel.setText("Evidence: none"); evidenceEmployeeLabel.setText("Employee: -"); }

        RecognitionEvent autoEvent = context.autoAttendanceService().tryAutoAttendance("CAM-01", false);
        if (autoEvent != null) {
            evidenceEmployeeLabel.setText("Employee: " + autoEvent.employeeName());
            eventList.getItems().add(0, String.format("AUTO | %s | %s | %.1f%% | %s", autoEvent.employeeName(), autoEvent.recognizedAt().format(TIMESTAMP_FORMAT), autoEvent.confidence() * 100, autoEvent.status()));
            refreshAttendance();
        }
    }

    private void refreshAttendance() {
        var rows = FXCollections.observableArrayList(
                context.attendanceService().history().stream().map(log -> new AttendanceRow(
                        log.id(), log.employeeCode(), log.employeeName(), log.attendanceType().name(),
                        log.timestamp().format(TIMESTAMP_FORMAT), log.cameraId(), log.imagePath())).toList());
        attendanceTable.setItems(rows);
        reportTable.setItems(FXCollections.observableArrayList(rows));
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

    private void configureReportTable() {
        if (!reportTable.getColumns().isEmpty()) {
            return;
        }
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
        reportTable.getColumns().setAll(id, employeeCode, employeeName, type, timestamp);
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    public record AttendanceRow(long id, String employeeCode, String employeeName, String attendanceType, String timestamp, String cameraId, String imagePath) {}
}
