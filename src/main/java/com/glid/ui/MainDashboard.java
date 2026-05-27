package com.glid.ui;

import com.glid.app.AppContext;
import com.glid.model.DetectionArtifact;
import com.glid.model.DetectionFrame;
import com.glid.model.Employee;
import com.glid.model.RecognitionEvent;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.File;
import java.time.LocalDate;
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
    private final Label registerMessageLabel = new Label("");
    private VBox quickRegisterPanel;
    private HBox cameraControls;
    private StackPane registerOverlay;
    private StackPane reportOverlay;
    private VBox reportPanel;
    private DetectionArtifact capturedArtifact;

    public MainDashboard(AppContext context) { this.context = context; }

    public Parent build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("app-root", "kiosk-root");
        root.setPadding(new Insets(0));
        autoAttendanceCheckBox.setSelected(context.autoAttendanceService().isAutoModeEnabled());
        root.setCenter(buildKioskView());
        root.setFocusTraversable(true);
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F1 || event.getCode() == KeyCode.F2 || event.getCode() == KeyCode.F3 || event.getCode() == KeyCode.F4) {
                handleHotkey(event.getCode());
                event.consume();
            }
        });
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.F1 || event.getCode() == KeyCode.F2 || event.getCode() == KeyCode.F3 || event.getCode() == KeyCode.F4) {
                        handleHotkey(event.getCode());
                        event.consume();
                    }
                });
            }
        });
        Platform.runLater(root::requestFocus);
        refreshAttendance();
        return root;
    }

    private Parent buildKioskView() {
        quickRegisterPanel = buildQuickRegisterPanel();
        reportPanel = buildReportPanel();
        VBox scanPanel = buildScanPanel();

        registerOverlay = buildOverlay(quickRegisterPanel, Pos.TOP_CENTER);
        reportOverlay = buildOverlay(reportPanel, Pos.CENTER);
        setVisibleManaged(registerOverlay, false);
        setVisibleManaged(reportOverlay, false);

        return new StackPane(scanPanel, registerOverlay, reportOverlay);
    }

    private StackPane buildOverlay(Node content, Pos align) {
        StackPane holder = new StackPane(content);
        holder.setPickOnBounds(true);
        holder.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        StackPane.setAlignment(content, align);
        content.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-border-color: #cccccc; -fx-border-width: 2;");
        return holder;
    }

    private VBox buildQuickRegisterPanel() {
        employeeCodeField.setPromptText("Code");
        fullNameField.setPromptText("Name");
        departmentField.setPromptText("Dept");
        positionField.setPromptText("Position");
        activeCheckBox.setSelected(true);
        activeCheckBox.setStyle("-fx-text-fill: black; -fx-font-size: 14px;");
        maskCheckBox.setStyle("-fx-text-fill: black; -fx-font-size: 14px;");

        Button captureFaceButton = new Button("Capture");
        captureFaceButton.setOnAction(event -> captureFaceSampleForRegistration());
        Button registerButton = new Button("Register");
        registerButton.setOnAction(event -> handleRegisterEmployee());
        Button closeButton = new Button("Close (F1)");
        closeButton.setOnAction(event -> setVisibleManaged(registerOverlay, false));

        registerMessageLabel.setStyle("-fx-text-fill: #d9534f; -fx-font-size: 13px;");

        HBox row1 = new HBox(8, employeeCodeField, fullNameField, departmentField, positionField);
        HBox row2 = new HBox(8, activeCheckBox, maskCheckBox, captureFaceButton, registerButton, registerFaceStatusLabel);
        HBox row3 = new HBox(8, closeButton);
        row3.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(12, row1, row2, registerMessageLabel, row3);
        panel.setMaxWidth(900);
        panel.setPadding(new Insets(0));
        return panel;
    }

    private VBox buildScanPanel() {
        cameraIndexField.setPrefWidth(56);
        autoAttendanceCheckBox.setOnAction(event -> context.autoAttendanceService().setAutoModeEnabled(autoAttendanceCheckBox.isSelected()));
        autoAttendanceCheckBox.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

        Button startCamera = new Button("Start Camera");
        startCamera.setOnAction(event -> startCamera());
        Button stopCamera = new Button("Stop Camera");
        stopCamera.setOnAction(event -> stopCamera());

        HBox topControls = new HBox(10, new Label("Camera:"), cameraIndexField, startCamera, stopCamera, autoAttendanceCheckBox);
        topControls.setAlignment(Pos.CENTER_LEFT);
        topControls.setPadding(new Insets(10));
        topControls.setStyle("-fx-background-color: #2c3e50; -fx-border-color: #34495e; -fx-border-width: 0 0 2 0;");

        cameraControls = new HBox(6, cameraIndexField, startCamera, stopCamera, autoAttendanceCheckBox);
        cameraControls.getStyleClass().addAll("toolbar-row", "camera-overlay-controls");
        cameraControls.setAlignment(Pos.CENTER_LEFT);
        cameraControls.setPadding(new Insets(10));
        setVisibleManaged(cameraControls, false);

        configureAttendanceTable();
        cameraPreview.setPreserveRatio(false);

        StackPane cameraArea = new StackPane(cameraPreview, cameraControls);
        cameraArea.setStyle("-fx-background-color: #34495e;");
        StackPane.setAlignment(cameraControls, Pos.TOP_LEFT);

        Label attendanceTitle = new Label("Recent Attendance");
        attendanceTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white; -fx-padding: 10;");
        attendanceTitle.setMaxWidth(Double.MAX_VALUE);
        attendanceTitle.setStyle("-fx-background-color: #2c3e50; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white; -fx-padding: 10;");

        VBox right = new VBox(0, attendanceTitle, attendanceTable);
        right.setStyle("-fx-background-color: white;");
        VBox.setVgrow(attendanceTable, Priority.ALWAYS);
        
        HBox main = new HBox(0, cameraArea, right);
        HBox.setHgrow(cameraArea, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.NEVER);
        cameraArea.prefWidthProperty().bind(main.widthProperty().multiply(0.65));
        right.prefWidthProperty().bind(main.widthProperty().multiply(0.35));

        cameraPreview.fitWidthProperty().bind(cameraArea.widthProperty());
        cameraPreview.fitHeightProperty().bind(cameraArea.heightProperty());

        VBox panel = new VBox(0, topControls, main);
        panel.setPadding(new Insets(0));
        VBox.setVgrow(main, Priority.ALWAYS);
        return panel;
    }

    private VBox buildReportPanel() {
        Label reportTitle = new Label("Attendance Report");
        reportTitle.setStyle("-fx-font-size: 18px; -fx-text-fill: black; -fx-font-weight: bold;");
        
        javafx.scene.control.DatePicker fromDatePicker = new javafx.scene.control.DatePicker();
        fromDatePicker.setPromptText("From Date");
        fromDatePicker.setValue(LocalDate.now().minusDays(7));
        
        javafx.scene.control.DatePicker toDatePicker = new javafx.scene.control.DatePicker();
        toDatePicker.setPromptText("To Date");
        toDatePicker.setValue(LocalDate.now());
        
        TextField departmentFilter = new TextField();
        departmentFilter.setPromptText("Department");
        
        TextField employeeCodeFilter = new TextField();
        employeeCodeFilter.setPromptText("Employee Code");
        
        Button filterButton = new Button("Filter");
        filterButton.setOnAction(event -> {
            LocalDate from = fromDatePicker.getValue();
            LocalDate to = toDatePicker.getValue();
            String dept = departmentFilter.getText().trim();
            String empCode = employeeCodeFilter.getText().trim();
            refreshReportTable(from, to, dept.isEmpty() ? null : dept, empCode.isEmpty() ? null : empCode);
        });
        
        Button closeButton = new Button("Close (F3)");
        closeButton.setOnAction(event -> setVisibleManaged(reportOverlay, false));
        
        configureReportTable();
        
        HBox filters = new HBox(8, new Label("From:"), fromDatePicker, new Label("To:"), toDatePicker, 
                                departmentFilter, employeeCodeFilter, filterButton);
        filters.setAlignment(Pos.CENTER_LEFT);
        
        HBox header = new HBox(20, reportTitle, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(reportTitle, Priority.ALWAYS);
        
        VBox panel = new VBox(12, header, filters, reportTable);
        panel.setMaxWidth(1000);
        panel.setMaxHeight(700);
        panel.setPadding(new Insets(20));
        VBox.setVgrow(reportTable, Priority.ALWAYS);
        return panel;
    }

    private void handleHotkey(KeyCode code) {
        if (code == KeyCode.F1) {
            setVisibleManaged(registerOverlay, !registerOverlay.isVisible());
        } else if (code == KeyCode.F2) {
            autoAttendanceCheckBox.setSelected(true);
            context.autoAttendanceService().setAutoModeEnabled(true);
            if (context.cameraCaptureService().isRunning()) {
                stopCamera();
            } else {
                startCamera();
            }
        } else if (code == KeyCode.F3) {
            boolean willShow = !reportOverlay.isVisible();
            setVisibleManaged(reportOverlay, willShow);
            if (willShow) {
                refreshReportTable(LocalDate.now().minusDays(7), LocalDate.now(), null, null);
            }
        } else if (code == KeyCode.F4) {
            showCameraSelectionDialog();
        }
    }

    private void setVisibleManaged(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void handleRegisterEmployee() {
        if (employeeCodeField.getText().isBlank() || fullNameField.getText().isBlank() || departmentField.getText().isBlank()) {
            registerMessageLabel.setText("Registration blocked: employee code, full name, and department are required.");
            return;
        }
        if (capturedArtifact == null) {
            registerMessageLabel.setText("Registration blocked: please capture face first using Capture button.");
            return;
        }
        try {
            Employee employee = context.employeeService().registerEmployeeWithArtifact(
                    employeeCodeField.getText().trim(), fullNameField.getText().trim(), departmentField.getText().trim(),
                    positionField.getText().trim(), activeCheckBox.isSelected(), maskCheckBox.isSelected(), capturedArtifact);
            registerMessageLabel.setText("Registered " + employee.fullName());
            capturedArtifact = null;
            refreshRegistrationFaceStatus();
        } catch (IllegalStateException exception) {
            registerMessageLabel.setText("Registration failed: employee code already exists or data is invalid.");
        }
    }

    private void captureFaceSampleForRegistration() {
        DetectionArtifact artifact = context.cameraCaptureService().getLastDetectionArtifact();
        if (artifact == null) {
            registerFaceStatusLabel.setText("Face sample: not captured");
            registerMessageLabel.setText("No face detected. Please ensure camera is running and face is visible.");
            return;
        }
        capturedArtifact = artifact;
        registerFaceStatusLabel.setText("Face sample: " + artifact.capturedAt().format(TIMESTAMP_FORMAT));
        registerMessageLabel.setText("Face captured successfully. You can now register.");
    }

    private void refreshRegistrationFaceStatus() {
        if (capturedArtifact == null) {
            registerFaceStatusLabel.setText("Face sample: not captured");
            return;
        }
        registerFaceStatusLabel.setText("Face sample: " + capturedArtifact.capturedAt().format(TIMESTAMP_FORMAT));
    }

    private void startCamera() {
        int cameraIndex;
        try { cameraIndex = Integer.parseInt(cameraIndexField.getText().trim()); }
        catch (NumberFormatException exception) { cameraStatusLabel.setText("Invalid camera index"); return; }
        context.cameraCaptureService().startCamera(cameraIndex, this::renderDetectionFrame, status -> cameraStatusLabel.setText(status));
    }

    private void stopCamera() { context.cameraCaptureService().stopCamera(); }

    private void showCameraSelectionDialog() {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.setTitle("Select Camera");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        ListView<String> cameraListView = new ListView<>();
        cameraListView.setPrefHeight(200);
        
        Label statusLabel = new Label("Scanning for cameras...");
        Button refreshButton = new Button("Refresh");
        Button selectButton = new Button("Select");
        Button cancelButton = new Button("Cancel");

        refreshButton.setOnAction(event -> {
            statusLabel.setText("Scanning for cameras...");
            cameraListView.getItems().clear();
            new Thread(() -> {
                var cameras = scanAvailableCameras();
                Platform.runLater(() -> {
                    cameraListView.setItems(FXCollections.observableArrayList(cameras));
                    statusLabel.setText("Found " + cameras.size() + " camera(s)");
                });
            }).start();
        });

        selectButton.setOnAction(event -> {
            String selected = cameraListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                String index = selected.split(":")[0].replace("Camera ", "");
                cameraIndexField.setText(index);
                dialog.close();
            }
        });

        cancelButton.setOnAction(event -> dialog.close());

        HBox buttons = new HBox(8, refreshButton, selectButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        VBox layout = new VBox(12, statusLabel, cameraListView, buttons);
        layout.setPadding(new Insets(15));
        layout.setStyle("-fx-background-color: white;");
        
        javafx.scene.Scene scene = new javafx.scene.Scene(layout, 400, 300);
        dialog.setScene(scene);
        
        refreshButton.fire();
        dialog.showAndWait();
    }

    private java.util.List<String> scanAvailableCameras() {
        java.util.List<String> cameras = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            org.opencv.videoio.VideoCapture testCapture = new org.opencv.videoio.VideoCapture(i);
            if (testCapture.isOpened()) {
                cameras.add("Camera " + i + ": Available");
                testCapture.release();
            }
        }
        return cameras;
    }

    private void renderDetectionFrame(DetectionFrame frame) {
        cameraPreview.setImage(frame.image());

        if (autoAttendanceCheckBox.isSelected()) {
            RecognitionEvent autoEvent = context.autoAttendanceService().tryAutoAttendance("CAM-01", false);
            if (autoEvent != null) {
                context.cameraCaptureService().setFaceOverlayName(autoEvent.employeeName());
                refreshAttendance();
                System.out.println("[UI] Auto attendance: " + autoEvent.employeeName() + " - " + autoEvent.status());
            }
        }
    }

    private void refreshAttendance() {
        var rows = FXCollections.observableArrayList(
                context.attendanceService().history().stream().map(log -> new AttendanceRow(
                        log.id(), log.employeeCode(), log.employeeName(), log.attendanceType().name(),
                        log.timestamp().format(TIMESTAMP_FORMAT), log.cameraId(), log.imagePath())).toList());
        attendanceTable.setItems(rows);
    }

    private void refreshReportTable(LocalDate from, LocalDate to, String department, String employeeCode) {
        var rows = FXCollections.observableArrayList(
                context.attendanceService().filteredHistory(from, to, department, employeeCode).stream()
                        .map(log -> new AttendanceRow(
                                log.id(), log.employeeCode(), log.employeeName(), log.attendanceType().name(),
                                log.timestamp().format(TIMESTAMP_FORMAT), log.cameraId(), log.imagePath())).toList());
        reportTable.setItems(rows);
    }

    private void configureAttendanceTable() {
        TableColumn<AttendanceRow, String> photo = new TableColumn<>("Photo");
        photo.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().imagePath()));
        photo.setPrefWidth(80);
        photo.setMaxWidth(80);
        photo.setMinWidth(80);
        photo.setCellFactory(col -> new TableCell<>() {
            private final ImageView thumb = new ImageView();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setGraphic(null); return; }
                File file = new File(item);
                if (!file.exists()) { setGraphic(null); return; }
                thumb.setImage(new Image(file.toURI().toString(), 64, 64, true, true));
                setGraphic(thumb);
            }
        });
        TableColumn<AttendanceRow, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().employeeCode()));
        code.setPrefWidth(100);
        TableColumn<AttendanceRow, String> employeeName = new TableColumn<>("Name");
        employeeName.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().employeeName()));
        employeeName.setPrefWidth(150);
        TableColumn<AttendanceRow, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().attendanceType()));
        type.setPrefWidth(100);
        TableColumn<AttendanceRow, String> timestamp = new TableColumn<>("Time");
        timestamp.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().timestamp()));
        timestamp.setPrefWidth(150);
        
        TableColumn<AttendanceRow, Void> actionCol = new TableColumn<>("Action");
        actionCol.setPrefWidth(80);
        actionCol.setMaxWidth(80);
        actionCol.setMinWidth(80);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("Delete");
            {
                deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 8 4 8;");
                deleteBtn.setOnAction(event -> {
                    AttendanceRow row = getTableView().getItems().get(getIndex());
                    if (row != null) {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Delete Attendance");
                        alert.setHeaderText("Delete attendance record?");
                        alert.setContentText("Employee: " + row.employeeName() + "\nTime: " + row.timestamp());
                        alert.showAndWait().ifPresent(response -> {
                            if (response == javafx.scene.control.ButtonType.OK) {
                                context.attendanceService().delete(row.id());
                                refreshAttendance();
                            }
                        });
                    }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteBtn);
                }
            }
        });
        
        attendanceTable.getColumns().setAll(photo, code, employeeName, type, timestamp, actionCol);
        attendanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    private void configureReportTable() {
        if (!reportTable.getColumns().isEmpty()) {
            return;
        }
        TableColumn<AttendanceRow, Long> id = new TableColumn<>("ID");
        id.setCellValueFactory(cellData -> new javafx.beans.property.SimpleLongProperty(cellData.getValue().id()).asObject());
        id.setPrefWidth(60);
        TableColumn<AttendanceRow, String> employeeCode = new TableColumn<>("Code");
        employeeCode.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().employeeCode()));
        employeeCode.setPrefWidth(100);
        TableColumn<AttendanceRow, String> employeeName = new TableColumn<>("Name");
        employeeName.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().employeeName()));
        employeeName.setPrefWidth(150);
        TableColumn<AttendanceRow, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().attendanceType()));
        type.setPrefWidth(100);
        TableColumn<AttendanceRow, String> timestamp = new TableColumn<>("Timestamp");
        timestamp.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().timestamp()));
        timestamp.setPrefWidth(150);
        reportTable.getColumns().setAll(id, employeeCode, employeeName, type, timestamp);
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    public record AttendanceRow(long id, String employeeCode, String employeeName, String attendanceType, String timestamp, String cameraId, String imagePath) {}
}
