package com.glid.service;

import com.glid.model.AttendanceLog;
import com.glid.model.AttendanceType;
import com.glid.model.DailyAttendanceReport;
import com.glid.persistence.AppDataStore;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AttendanceReportService {
    private final AppDataStore store;
    private final EmployeeService employeeService;

    public AttendanceReportService(AppDataStore store, EmployeeService employeeService) {
        this.store = store;
        this.employeeService = employeeService;
    }

    public String exportCsvPreview(LocalDate from, LocalDate to, String department, String employeeCode) {
        List<DailyAttendanceReport> reports = generateDailyReports(from, to, department, employeeCode);
        String header = "date,employee_code,employee_name,department,check_in,check_out,check_in_camera,check_out_camera";
        String body = reports.stream()
                .map(report -> String.join(",",
                        report.date().toString(),
                        report.employeeCode(),
                        sanitize(report.employeeName()),
                        sanitize(report.department()),
                        report.checkIn() != null ? report.checkIn().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "",
                        report.checkOut() != null ? report.checkOut().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "",
                        report.checkInCamera() != null ? report.checkInCamera() : "",
                        report.checkOutCamera() != null ? report.checkOutCamera() : ""))
                .collect(Collectors.joining(System.lineSeparator()));
        return body.isBlank() ? header : header + System.lineSeparator() + body;
    }

    public void exportPdf(String filePath, LocalDate from, LocalDate to, String department, String employeeCode) {
        try {
            List<DailyAttendanceReport> reports = generateDailyReports(from, to, department, employeeCode);
            
            PdfWriter writer = new PdfWriter(new FileOutputStream(filePath));
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            Paragraph title = new Paragraph("Attendance Report")
                    .setFontSize(18)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(title);

            Paragraph period = new Paragraph("Period: " + from + " to " + to)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(period);
            document.add(new Paragraph("\n"));

            float[] columnWidths = {2, 2, 3, 2, 2, 2};
            Table table = new Table(UnitValue.createPercentArray(columnWidths));
            table.setWidth(UnitValue.createPercentValue(100));

            table.addHeaderCell("Date");
            table.addHeaderCell("Employee Code");
            table.addHeaderCell("Employee Name");
            table.addHeaderCell("Department");
            table.addHeaderCell("Check In");
            table.addHeaderCell("Check Out");

            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            for (DailyAttendanceReport report : reports) {
                table.addCell(report.date().toString());
                table.addCell(report.employeeCode());
                table.addCell(report.employeeName());
                table.addCell(report.department());
                table.addCell(report.checkIn() != null ? report.checkIn().format(timeFormatter) : "-");
                table.addCell(report.checkOut() != null ? report.checkOut().format(timeFormatter) : "-");
            }

            document.add(table);
            document.close();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export PDF", e);
        }
    }

    public int totalEmployees() {
        return employeeService.findAll().size();
    }

    private List<DailyAttendanceReport> generateDailyReports(LocalDate from, LocalDate to, String department, String employeeCode) {
        List<AttendanceLog> logs = store.findAttendanceLogs(from, to, department, employeeCode);
        
        Map<String, List<AttendanceLog>> groupedByEmployeeAndDate = logs.stream()
                .collect(Collectors.groupingBy(log -> 
                    log.employeeCode() + "|" + log.timestamp().toLocalDate().toString()
                ));

        List<DailyAttendanceReport> reports = new ArrayList<>();
        
        for (Map.Entry<String, List<AttendanceLog>> entry : groupedByEmployeeAndDate.entrySet()) {
            List<AttendanceLog> dayLogs = entry.getValue();
            
            AttendanceLog firstLog = dayLogs.get(0);
            LocalDate date = firstLog.timestamp().toLocalDate();
            
            LocalDateTime checkIn = dayLogs.stream()
                    .filter(log -> log.attendanceType() == AttendanceType.CHECK_IN)
                    .map(AttendanceLog::timestamp)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            
            LocalDateTime checkOut = dayLogs.stream()
                    .filter(log -> log.attendanceType() == AttendanceType.CHECK_OUT)
                    .map(AttendanceLog::timestamp)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            
            AttendanceLog checkInLog = dayLogs.stream()
                    .filter(log -> log.attendanceType() == AttendanceType.CHECK_IN && log.timestamp().equals(checkIn))
                    .findFirst()
                    .orElse(null);
            
            AttendanceLog checkOutLog = dayLogs.stream()
                    .filter(log -> log.attendanceType() == AttendanceType.CHECK_OUT && log.timestamp().equals(checkOut))
                    .findFirst()
                    .orElse(null);
            
            String empDepartment = employeeService.findByCode(firstLog.employeeCode())
                    .map(emp -> emp.department())
                    .orElse("");
            
            reports.add(new DailyAttendanceReport(
                    firstLog.employeeCode(),
                    firstLog.employeeName(),
                    empDepartment,
                    date,
                    checkIn,
                    checkOut,
                    checkInLog != null ? checkInLog.cameraId() : null,
                    checkOutLog != null ? checkOutLog.cameraId() : null,
                    checkInLog != null ? checkInLog.imagePath() : null,
                    checkOutLog != null ? checkOutLog.imagePath() : null
            ));
        }
        
        reports.sort(Comparator.comparing(DailyAttendanceReport::date)
                .thenComparing(DailyAttendanceReport::employeeCode));
        
        return reports;
    }

    private String sanitize(String input) {
        return input.replace(',', ' ');
    }
}
