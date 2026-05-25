package com.glid.app;

import com.glid.persistence.AppDataStore;
import com.glid.service.AutoAttendanceService;
import com.glid.service.AttendanceReportService;
import com.glid.service.AttendanceService;
import com.glid.service.CameraCaptureService;
import com.glid.service.EmployeeService;
import com.glid.service.FaceEmbeddingExtractor;
import com.glid.service.FaceRecognitionPipeline;

public record AppContext(
        EmployeeService employeeService,
        AttendanceService attendanceService,
        AttendanceReportService reportService,
        FaceRecognitionPipeline pipeline,
        CameraCaptureService cameraCaptureService,
        AutoAttendanceService autoAttendanceService
) {

    public static AppContext bootstrap() {
        AppDataStore store = AppDataStore.getInstance();
        CameraCaptureService cameraCaptureService = new CameraCaptureService();
        FaceEmbeddingExtractor embeddingExtractor = new FaceEmbeddingExtractor();
        EmployeeService employeeService = new EmployeeService(store, cameraCaptureService, embeddingExtractor);
        AttendanceService attendanceService = new AttendanceService(store, employeeService);
        AttendanceReportService reportService = new AttendanceReportService(store, employeeService);
        FaceRecognitionPipeline pipeline = new FaceRecognitionPipeline(employeeService, attendanceService, cameraCaptureService, embeddingExtractor);
        AutoAttendanceService autoAttendanceService = new AutoAttendanceService(cameraCaptureService, pipeline);
        return new AppContext(employeeService, attendanceService, reportService, pipeline, cameraCaptureService, autoAttendanceService);
    }
}
