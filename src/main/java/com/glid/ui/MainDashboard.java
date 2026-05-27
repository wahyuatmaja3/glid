package com.glid.ui;

import com.glid.app.AppContext;
import com.glid.model.DetectionArtifact;
import com.glid.model.DetectionFrame;
import com.glid.model.Employee;
import com.glid.model.RecognitionEvent;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

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
    private TextField overlayCameraIndexField;
    private StackPane registerOverlay;
    private StackPane reportOverlay;
    private VBox reportPanel;
    private DetectionArtifact capturedArtifact;

    // overlay for attendance info
    private final Label attendanceOverlayLabel = new Label();
    private final PauseTransition attendanceOverlayTimer = new PauseTransition(Duration.seconds(10));

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
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                    if (ev.getCode() == KeyCode.F1 || ev.getCode() == KeyCode.F2 || ev.getCode() == KeyCode.F3 || ev.getCode() == KeyCode.F4) {
                        handleHotkey(ev.getCode());
                        ev.consume();
                    }
                });
            }
        });
        Platform.runLater(root::requestFocus);
        refreshAttendance();
        attendanceOverlayTimer.setOnFinished(e -> {
            attendanceOverlayLabel.setVisible(false);
            attendanceOverlayLabel.setManaged(false);
        });
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
        holder.getStyleClass().add("app-overlay");
        StackPane.setAlignment(content, align);
        content.getStyleClass().add("overlay-card");
        return holder;
    }

    private VBox buildQuickRegisterPanel() {
        employeeCodeField.setPromptText("Code");
        fullNameField.setPromptText("Name");
        departmentField.setPromptText("Department");
        positionField.setPromptText("Position");
        activeCheckBox.setSelected(true);
        employeeCodeField.getStyleClass().add("editorial-input");
        fullNameField.getStyleClass().add("editorial-input");
        departmentField.getStyleClass().add("editorial-input");
        positionField.getStyleClass().add("editorial-input");
        registerFaceStatusLabel.getStyleClass().add("helper-label");
        registerMessageLabel.getStyleClass().add("error-label");
        Button captureFaceButton = new Button("Capture");
        captureFaceButton.getStyleClass().add("button-secondary");
        captureFaceButton.setOnAction(e -> captureFaceSampleForRegistration());
        Button registerButton = new Button("Register");
        registerButton.getStyleClass().add("button-dark");
        registerButton.setOnAction(e -> handleRegisterEmployee());
        Button closeButton = new Button("Close (F1)");
        closeButton.getStyleClass().add("button-cream");
        closeButton.setOnAction(e -> setVisibleManaged(registerOverlay, false));
        HBox row1 = new HBox(12, employeeCodeField, fullNameField, departmentField, positionField);
        HBox row2 = new HBox(12, activeCheckBox, maskCheckBox, captureFaceButton, registerButton);
        HBox row3 = new HBox(12, registerFaceStatusLabel, closeButton);
        row3.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(registerFaceStatusLabel, Priority.ALWAYS);
        VBox panel = new VBox(16, row1, row2, registerMessageLabel, row3);
        panel.getStyleClass().add("register-panel");
        panel.setMaxWidth(980);
        panel.setPadding(new Insets(0));
        return panel;
    }

    private VBox buildScanPanel() {
        cameraIndexField.setPrefWidth(56);
        cameraIndexField.getStyleClass().add("editorial-input");
        overlayCameraIndexField = new TextField(cameraIndexField.getText());
        overlayCameraIndexField.setPrefWidth(56);
        overlayCameraIndexField.getStyleClass().add("editorial-input");
        autoAttendanceCheckBox.setOnAction(e -> context.autoAttendanceService().setAutoModeEnabled(autoAttendanceCheckBox.isSelected()));
        Button startCamera = new Button("Start Camera");
        startCamera.getStyleClass().add("button-dark");
        startCamera.setOnAction(e -> startCamera());
        Button stopCamera = new Button("Stop Camera");
        stopCamera.getStyleClass().add("button-cream");
        stopCamera.setOnAction(e -> stopCamera());
        Button overlayStartCamera = new Button("Start");
        overlayStartCamera.getStyleClass().add("button-dark");
        overlayStartCamera.setOnAction(e -> startCamera());
        Button overlayStopCamera = new Button("Stop");
        overlayStopCamera.getStyleClass().add("button-cream");
        overlayStopCamera.setOnAction(e -> stopCamera());
        CheckBox overlayAutoAttendanceCheckBox = new CheckBox("Auto");
        overlayAutoAttendanceCheckBox.setSelected(autoAttendanceCheckBox.isSelected());
        overlayAutoAttendanceCheckBox.setOnAction(ev -> {
            autoAttendanceCheckBox.setSelected(overlayAutoAttendanceCheckBox.isSelected());
            context.autoAttendanceService().setAutoModeEnabled(overlayAutoAttendanceCheckBox.isSelected());
        });
        autoAttendanceCheckBox.selectedProperty().addListener((obs, oldV, newV) -> overlayAutoAttendanceCheckBox.setSelected(newV));
        cameraIndexField.textProperty().addListener((obs, oldV, newV) -> overlayCameraIndexField.setText(newV));
        overlayCameraIndexField.textProperty().addListener((obs, oldV, newV) -> {
            if (!cameraIndexField.getText().equals(newV)) {
                cameraIndexField.setText(newV);
            }
        });
        Label cameraLabel = new Label("Camera");
        cameraLabel.getStyleClass().add("field-label");
        HBox topControls = new HBox(12, cameraLabel, cameraIndexField, startCamera, stopCamera, autoAttendanceCheckBox);
        topControls.getStyleClass().add("top-toolbar");
        topControls.setAlignment(Pos.CENTER_LEFT);
        topControls.setVisible(false);
        topControls.setManaged(false);
        cameraControls = new HBox(8, overlayCameraIndexField, overlayStartCamera, overlayStopCamera, overlayAutoAttendanceCheckBox);
        cameraControls.getStyleClass().addAll("toolbar-row", "camera-overlay-controls");
        cameraControls.setAlignment(Pos.CENTER_LEFT);
        setVisibleManaged(cameraControls, false);
        configureAttendanceTable();
        cameraPreview.setPreserveRatio(true);
        cameraPreview.setSmooth(true);
        VBox recognitionCard = new VBox(8,
                buildRecognitionMetric("Camera Status", cameraStatusLabel),
                buildRecognitionMetric("Detection", detectionStatusLabel),
                buildRecognitionMetric("Evidence", evidenceStatusLabel),
                buildRecognitionMetric("Employee", evidenceEmployeeLabel));
        recognitionCard.getStyleClass().add("recognition-card");
        recognitionCard.setMaxWidth(320);
        recognitionCard.setVisible(false);
        recognitionCard.setManaged(false);
        StackPane cameraStage = new StackPane(cameraPreview);
        cameraStage.getStyleClass().add("camera-stage-modern");
        attendanceOverlayLabel.getStyleClass().add("attendance-overlay-label");
        attendanceOverlayLabel.setVisible(false);
        attendanceOverlayLabel.setManaged(false);
        StackPane cameraArea = new StackPane(cameraStage, cameraControls, recognitionCard, attendanceOverlayLabel);
        cameraArea.getStyleClass().add("camera-shell");
        cameraArea.setMinWidth(0);
        cameraArea.setMaxWidth(Double.MAX_VALUE);
        cameraArea.setMinHeight(0);
        cameraArea.setMaxHeight(Double.MAX_VALUE);
        StackPane.setAlignment(cameraControls, Pos.TOP_LEFT);
        StackPane.setAlignment(recognitionCard, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(attendanceOverlayLabel, Pos.TOP_LEFT);
        StackPane.setMargin(recognitionCard, new Insets(0, 0, 20, 20));
        StackPane.setMargin(attendanceOverlayLabel, new Insets(20, 0, 0, 20));
        Label attendanceTitle = new Label("Recent Attendance");
        attendanceTitle.getStyleClass().add("panel-heading");
        attendanceTitle.setMaxWidth(Double.MAX_VALUE);
        VBox right = new VBox(0, attendanceTitle, attendanceTable);
        right.getStyleClass().add("side-panel");
        VBox.setVgrow(attendanceTable, Priority.ALWAYS);
        right.setVisible(false);
        right.setManaged(false);
        HBox main = new HBox(24, cameraArea, right);
        main.getStyleClass().add("content-shell");
        main.setFillHeight(true);
        HBox.setHgrow(cameraArea, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.NEVER);
        // camera area occupies full width when right panel hidden
        cameraArea.prefWidthProperty().bind(main.widthProperty());
        cameraPreview.fitWidthProperty().bind(cameraArea.widthProperty());
        cameraPreview.fitHeightProperty().bind(cameraArea.heightProperty());
        VBox panel = new VBox(18, topControls, main, buildSunsetStripe());
        panel.getStyleClass().add("dashboard-shell");
        panel.setFillWidth(true);
        VBox.setVgrow(main, Priority.ALWAYS);
        return panel;
    }

    private VBox buildReportPanel() {
        Label reportTitle = new Label("Attendance Report");
        reportTitle.getStyleClass().add("modal-title");
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
        filterButton.setOnAction(e -> {
            LocalDate from = fromDatePicker.getValue();
            LocalDate to = toDatePicker.getValue();
            String dept = departmentFilter.getText().trim();
            String emp = employeeCodeFilter.getText().trim();
            refreshReportTable(from, to, dept.isEmpty() ? null : dept, emp.isEmpty() ? null : emp);
        });
        Button exportCsvButton = new Button("Export CSV");
        exportCsvButton.setOnAction(e -> {
            LocalDate from = fromDatePicker.getValue();
            LocalDate to = toDatePicker.getValue();
            String dept = departmentFilter.getText().trim();
            String emp = employeeCodeFilter.getText().trim();
            exportCsvReport(from, to, dept.isEmpty() ? null : dept, emp.isEmpty() ? null : emp);
        });
        Button exportPdfButton = new Button("Export PDF");
        exportPdfButton.setOnAction(e -> {
            LocalDate from = fromDatePicker.getValue();
            LocalDate to = toDatePicker.getValue();
            String dept = departmentFilter.getText().trim();
            String emp = employeeCodeFilter.getText().trim();
            exportPdfReport(from, to, dept.isEmpty() ? null : dept, emp.isEmpty() ? null : emp);
        });
        Button closeButton = new Button("Close (F3)");
        closeButton.setOnAction(e -> setVisibleManaged(reportOverlay, false));
        configureReportTable();
        HBox filters = new HBox(8, new Label("From:"), fromDatePicker, new Label("To:"), toDatePicker, departmentFilter, employeeCodeFilter, filterButton);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox exportButtons = new HBox(8, exportCsvButton, exportPdfButton);
        exportButtons.setAlignment(Pos.CENTER_LEFT);
        HBox header = new HBox(20, reportTitle, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(reportTitle, Priority.ALWAYS);
        VBox panel = new VBox(12, header, filters, exportButtons, reportTable);
        panel.setMaxWidth(Double.MAX_VALUE);
        panel.setMaxHeight(Double.MAX_VALUE);
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
            Employee emp = context.employeeService().registerEmployeeWithArtifact(
                    employeeCodeField.getText().trim(), fullNameField.getText().trim(), departmentField.getText().trim(),
                    positionField.getText().trim(), activeCheckBox.isSelected(), maskCheckBox.isSelected(), capturedArtifact);
            registerMessageLabel.setText("Registered " + emp.fullName());
            capturedArtifact = null;
            refreshRegistrationFaceStatus();
        } catch (IllegalStateException ex) {
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
        int idx;
        try { idx = Integer.parseInt(cameraIndexField.getText().trim()); } catch (NumberFormatException e) { cameraStatusLabel.setText("Invalid camera index"); return; }
        if (overlayCameraIndexField != null && !overlayCameraIndexField.getText().equals(cameraIndexField.getText())) {
            overlayCameraIndexField.setText(cameraIndexField.getText());
        }
        context.cameraCaptureService().startCamera(idx, this::renderDetectionFrame, status -> cameraStatusLabel.setText(status));
    }

    private void stopCamera() { context.cameraCaptureService().stopCamera(); }

    private void showCameraSelectionDialog() {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.setTitle("Select Camera");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        ListView<String> list = new ListView<>();
        list.setPrefHeight(200);
        Label status = new Label("Scanning for cameras...");
        Button refresh = new Button("Refresh");
        Button select = new Button("Select");
        Button cancel = new Button("Cancel");
        refresh.setOnAction(ev -> {
            status.setText("Scanning for cameras...");
            list.getItems().clear();
            new Thread(() -> {
                var cams = scanAvailableCameras();
                Platform.runLater(() -> {
                    list.setItems(FXCollections.observableArrayList(cams));
                    status.setText("Found " + cams.size() + " camera(s)");
                });
            }).start();
        });
        select.setOnAction(ev -> {
            String sel = list.getSelectionModel().getSelectedItem();
            if (sel != null) {
                String index = sel.split(":")[0].replace("Camera ", "");
                cameraIndexField.setText(index);
                dialog.close();
            }
        });
        cancel.setOnAction(ev -> dialog.close());
        HBox btns = new HBox(8, refresh, select, cancel);
        btns.setAlignment(Pos.CENTER_RIGHT);
        VBox layout = new VBox(12, status, list, btns);
        layout.setPadding(new Insets(15));
        layout.setStyle("-fx-background-color: white;");
        dialog.setScene(new javafx.scene.Scene(layout, 400, 300));
        refresh.fire();
        dialog.showAndWait();
    }

    private java.util.List<String> scanAvailableCameras() {
        java.util.List<String> cams = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            org.opencv.videoio.VideoCapture cap = new org.opencv.videoio.VideoCapture(i);
            if (cap.isOpened()) { cams.add("Camera " + i + ": Available"); cap.release(); }
        }
        return cams;
    }

    private void renderDetectionFrame(DetectionFrame frame) {
        cameraPreview.setImage(frame.image());
        detectionStatusLabel.setText("Faces detected: " + frame.faceCount());
        if (autoAttendanceCheckBox.isSelected()) {
            RecognitionEvent ev = context.autoAttendanceService().tryAutoAttendance("CAM-01", false);
            if (ev != null) {
                context.cameraCaptureService().setFaceOverlayName(ev.employeeName());
                showAttendanceOverlay(ev.employeeName(), ev.recognizedAt().format(TIMESTAMP_FORMAT));
                refreshAttendance();
                System.out.println("[UI] Auto attendance: " + ev.employeeName() + " - " + ev.status());
            }
        }
    }

    private void showAttendanceOverlay(String employeeName, String timestamp) {
        attendanceOverlayLabel.setText(employeeName + "\n" + timestamp);
        attendanceOverlayLabel.setVisible(true);
        attendanceOverlayLabel.setManaged(true);
        attendanceOverlayTimer.playFromStart();
    }

    private void refreshAttendance() {
        var rows = FXCollections.observableArrayList(
                context.attendanceService().history().stream()
                        .map(l -> new AttendanceRow(l.id(), l.employeeCode(), l.employeeName(), l.attendanceType().name(), l.timestamp().format(TIMESTAMP_FORMAT), l.cameraId(), l.imagePath()))
                .toList());
        attendanceTable.setItems(rows);
    }

    private void refreshReportTable(LocalDate from, LocalDate to, String dept, String emp) {
        var rows = FXCollections.observableArrayList(
                context.attendanceService().filteredHistory(from, to, dept, emp).stream()
                        .map(l -> new AttendanceRow(l.id(), l.employeeCode(), l.employeeName(), l.attendanceType().name(), l.timestamp().format(TIMESTAMP_FORMAT), l.cameraId(), l.imagePath()))
                .toList());
        reportTable.setItems(rows);
    }

    private void configureAttendanceTable() {
        TableColumn<AttendanceRow, String> photo = new TableColumn<>("Photo");
        photo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().imagePath()));
        photo.setPrefWidth(80);
        photo.setCellFactory(col -> new TableCell<>() {
            private final ImageView thumb = new ImageView();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setGraphic(null); return; }
                File f = new File(item);
                if (!f.exists()) { setGraphic(null); return; }
                thumb.setImage(new Image(f.toURI().toString(), 64, 64, true, true));
                setGraphic(thumb);
            }
        });
        TableColumn<AttendanceRow, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().employeeCode()));
        code.setPrefWidth(100);
        TableColumn<AttendanceRow, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().employeeName()));
        name.setPrefWidth(150);
        TableColumn<AttendanceRow, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().attendanceType()));
        type.setPrefWidth(100);
        TableColumn<AttendanceRow, String> time = new TableColumn<>("Time");
        time.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().timestamp()));
        time.setPrefWidth(150);
        TableColumn<AttendanceRow, Void> action = new TableColumn<>("Action");
        action.setPrefWidth(80);
        action.setCellFactory(col -> new TableCell<>() {
            private final Button del = new Button("Delete");
            {
                del.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 8 4 8;");
                del.setOnAction(e -> {
                    AttendanceRow r = getTableView().getItems().get(getIndex());
                    if (r != null) {
                        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                        a.setTitle("Delete Attendance");
                        a.setHeaderText("Delete attendance record?");
                        a.setContentText("Employee: " + r.employeeName() + "\nTime: " + r.timestamp());
                        a.showAndWait().ifPresent(btn -> {
                            if (btn == javafx.scene.control.ButtonType.OK) {
                                context.attendanceService().delete(r.id());
                                refreshAttendance();
                            }
                        });
                    }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : del);
            }
        });
        attendanceTable.getColumns().setAll(photo, code, name, type, time, action);
        attendanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    private void configureReportTable() {
        if (!reportTable.getColumns().isEmpty()) return;
        reportTable.getStyleClass().add("editorial-table");
        TableColumn<AttendanceRow, Long> id = new TableColumn<>("ID");
        id.setCellValueFactory(c -> new javafx.beans.property.SimpleLongProperty(c.getValue().id()).asObject());
        id.setPrefWidth(60);
        TableColumn<AttendanceRow, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().employeeCode()));
        code.setPrefWidth(100);
        TableColumn<AttendanceRow, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().employeeName()));
        name.setPrefWidth(150);
        TableColumn<AttendanceRow, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().attendanceType()));
        type.setPrefWidth(100);
        TableColumn<AttendanceRow, String> ts = new TableColumn<>("Timestamp");
        ts.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().timestamp()));
        ts.setPrefWidth(150);
        reportTable.getColumns().setAll(id, code, name, type, ts);
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void exportCsvReport(LocalDate from, LocalDate to, String dept, String emp) {
        try {
            String fileName = "attendance_report_" + from + "_to_" + to + ".csv";
            String filePath = "reports/" + fileName;
            new File("reports").mkdirs();
            String csv = context.reportService().exportCsvPreview(from, to, dept, emp);
            java.nio.file.Files.writeString(java.nio.file.Path.of(filePath), csv);
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            a.setTitle("Export Success");
            a.setHeaderText("CSV exported successfully");
            a.setContentText("File saved to: " + filePath);
            a.showAndWait();
        } catch (Exception e) { showErrorAlert("Export CSV", e.getMessage()); }
    }

    private void exportPdfReport(LocalDate from, LocalDate to, String dept, String emp) {
        try {
            String fileName = "attendance_report_" + from + "_to_" + to + ".pdf";
            String filePath = "reports/" + fileName;
            new File("reports").mkdirs();
            context.reportService().exportPdf(filePath, from, to, dept, emp);
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            a.setTitle("Export Success");
            a.setHeaderText("PDF exported successfully");
            a.setContentText("File saved to: " + filePath);
            a.showAndWait();
        } catch (Exception e) { showErrorAlert("Export PDF", e.getMessage()); }
    }

    private void showErrorAlert(String title, String msg) {
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText("Failed");
        a.setContentText(msg);
        a.showAndWait();
    }

    private VBox buildRecognitionMetric(String title, Label val) {
        Label t = new Label(title);
        t.getStyleClass().add("recognition-label");
        val.getStyleClass().add("recognition-value");
        VBox box = new VBox(2, t, val);
        box.getStyleClass().add("recognition-metric");
        return box;
    }

    private Region buildSunsetStripe() {
        Region r = new Region();
        r.getStyleClass().add("sunset-stripe");
        r.setPrefHeight(18);
        r.setMinHeight(18);
        return r;
    }

    public record AttendanceRow(long id, String employeeCode, String employeeName, String attendanceType, String timestamp, String cameraId, String imagePath) {}
}

