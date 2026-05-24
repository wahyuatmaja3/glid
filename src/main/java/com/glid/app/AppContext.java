package com.glid.app;

import com.glid.persistence.AppDataStore;
import com.glid.service.AttendanceReportService;
import com.glid.service.AttendanceService;
import com.glid.service.EmployeeService;
import com.glid.service.FaceRecognitionPipeline;

public record AppContext(
        EmployeeService employeeService,
        AttendanceService attendanceService,
        AttendanceReportService reportService,
        FaceRecognitionPipeline pipeline
) {

    public static AppContext bootstrap() {
        AppDataStore store = AppDataStore.getInstance();
        EmployeeService employeeService = new EmployeeService(store);
        AttendanceService attendanceService = new AttendanceService(store, employeeService);
        AttendanceReportService reportService = new AttendanceReportService(store, employeeService);
        FaceRecognitionPipeline pipeline = new FaceRecognitionPipeline(employeeService, attendanceService);
        return new AppContext(employeeService, attendanceService, reportService, pipeline);
    }
}
