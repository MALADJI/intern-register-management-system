package com.internregister.controller;

import com.internregister.entity.Intern;
import com.internregister.entity.Attendance;
import com.internregister.entity.AttendanceStatus;
import com.internregister.entity.LeaveRequest;
import com.internregister.entity.LeaveStatus;
import com.internregister.repository.InternRepository;
import com.internregister.repository.AttendanceRepository;
import com.internregister.repository.LeaveRequestRepository;
import com.internregister.service.DatabaseFixService;
import com.internregister.service.AuthHelperService;
import com.internregister.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    @Autowired
    private InternRepository internRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private DatabaseFixService databaseFixService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthHelperService authHelperService;

    @GetMapping("/attendance/pdf")
    public ResponseEntity<?> generateAttendancePdf(
            @RequestParam(required = false) String internName,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String field) {
        
        try {
            // Check if user is authenticated
            var currentUserOpt = authHelperService.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(java.util.Map.of("error", "Authentication required"));
            }
            
            User currentUser = currentUserOpt.get();
            System.out.println("=== Generating Attendance PDF Report ===");
            System.out.println("User: " + currentUser.getUsername() + ", Role: " + currentUser.getRole());
            
            // Admins and Supervisors can access all reports
            // Interns can only access their own reports
            if (currentUser.getRole() == User.Role.INTERN) {
                // Interns can only view their own attendance
                var internIdOpt = authHelperService.getCurrentInternId();
                if (internIdOpt.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(java.util.Map.of("error", "Intern profile not found"));
                }
                // Force filter to current intern's data
                return generateInternAttendancePdf(internIdOpt.get());
            }
            
            ByteArrayOutputStream baos = generatePdfReport(internName, department, field);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "attendance_report.pdf");
            // Don't set CORS headers here - let SecurityConfig handle it
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            System.err.println("✗ Error generating attendance PDF: " + e.getMessage());
            e.printStackTrace();
            HttpHeaders errorHeaders = new HttpHeaders();
            errorHeaders.setContentType(MediaType.APPLICATION_JSON);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(errorHeaders)
                    .body(java.util.Map.of("error", "Failed to generate attendance report: " + e.getMessage()));
        }
    }

    @GetMapping("/attendance/excel")
    public ResponseEntity<?> generateAttendanceExcel(
            @RequestParam(required = false) String internName,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String field) {
        
        try {
            // Check if user is authenticated
            var currentUserOpt = authHelperService.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(java.util.Map.of("error", "Authentication required"));
            }
            
            User currentUser = currentUserOpt.get();
            System.out.println("=== Generating Attendance Excel Report ===");
            System.out.println("User: " + currentUser.getUsername() + ", Role: " + currentUser.getRole());
            
            // Admins and Supervisors can access all reports
            // Interns can only access their own reports
            if (currentUser.getRole() == User.Role.INTERN) {
                // Interns can only view their own attendance
                var internIdOpt = authHelperService.getCurrentInternId();
                if (internIdOpt.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(java.util.Map.of("error", "Intern profile not found"));
                }
                // Force filter to current intern's data
                return generateInternAttendanceExcel(internIdOpt.get());
            }
            
            ByteArrayOutputStream baos = generateExcelReport(internName, department, field);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "attendance_report.xlsx");
            // Don't set CORS headers here - let SecurityConfig handle it
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            System.err.println("✗ Error generating attendance Excel: " + e.getMessage());
            e.printStackTrace();
            HttpHeaders errorHeaders = new HttpHeaders();
            errorHeaders.setContentType(MediaType.APPLICATION_JSON);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(errorHeaders)
                    .body(java.util.Map.of("error", "Failed to generate attendance report: " + e.getMessage()));
        }
    }

    @GetMapping("/leave/pdf")
    public ResponseEntity<?> generateLeaveRequestPdf(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long internId) {
        
        try {
            // Check if user is authenticated
            var currentUserOpt = authHelperService.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(java.util.Map.of("error", "Authentication required"));
            }
            
            User currentUser = currentUserOpt.get();
            System.out.println("=== Generating Leave Request PDF Report ===");
            System.out.println("User: " + currentUser.getUsername() + ", Role: " + currentUser.getRole());
            System.out.println("Status filter: " + (status != null ? status : "none"));
            System.out.println("Intern ID filter: " + (internId != null ? internId : "none"));
            
            // Role-based access control
            if (currentUser.getRole() == User.Role.INTERN) {
                // Interns can only view their own leave requests
                var currentInternIdOpt = authHelperService.getCurrentInternId();
                if (currentInternIdOpt.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(java.util.Map.of("error", "Intern profile not found"));
                }
                // Force filter to current intern's data
                internId = currentInternIdOpt.get();
                System.out.println("Intern accessing own report - filtering to intern ID: " + internId);
            } else if (currentUser.getRole() == User.Role.SUPERVISOR) {
                // Supervisors can view all leave requests (already allowed)
                System.out.println("Supervisor accessing all leave requests");
            } else if (currentUser.getRole() == User.Role.ADMIN) {
                // Admins can view all leave requests (already allowed)
                System.out.println("Admin accessing all leave requests");
            }
            
            // Fix database enum values before generating report
            try {
                databaseFixService.fixLeaveTypeEnumValues();
            } catch (Exception e) {
                System.err.println("⚠️ Warning: Could not fix database enum values: " + e.getMessage());
                // Continue anyway - will handle errors gracefully
            }
            
            ByteArrayOutputStream baos = generateLeaveRequestPdfReport(status, internId);
            
            if (baos == null || baos.size() == 0) {
                System.err.println("✗ PDF generation returned empty output");
                HttpHeaders errorHeaders = new HttpHeaders();
                errorHeaders.setContentType(MediaType.APPLICATION_JSON);
                return ResponseEntity.status(500)
                        .headers(errorHeaders)
                        .body(java.util.Map.of("error", "Failed to generate PDF report - empty output"));
            }
            
            System.out.println("✓ PDF generated successfully, size: " + baos.size() + " bytes");
            System.out.println("=== End Generating Leave Request PDF Report ===");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "leave_requests_report.pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            System.err.println("✗ Error generating PDF report:");
            System.err.println("  Message: " + e.getMessage());
            System.err.println("  Class: " + e.getClass().getName());
            System.err.println("  Stack trace:");
            e.printStackTrace();
            
            // Try to return JSON error, but if that fails, return plain text
            try {
                HttpHeaders errorHeaders = new HttpHeaders();
                errorHeaders.setContentType(MediaType.APPLICATION_JSON);
                String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
                // Limit error message length to prevent issues
                if (errorMessage.length() > 200) {
                    errorMessage = errorMessage.substring(0, 200) + "...";
                }
                return ResponseEntity.status(500)
                        .headers(errorHeaders)
                        .body(java.util.Map.of("error", "Failed to generate PDF report: " + errorMessage));
            } catch (Exception ex) {
                // If JSON serialization fails, return plain text
                System.err.println("✗ Error serializing error response: " + ex.getMessage());
                HttpHeaders errorHeaders = new HttpHeaders();
                errorHeaders.setContentType(MediaType.TEXT_PLAIN);
                String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
                return ResponseEntity.status(500)
                        .headers(errorHeaders)
                        .body("Failed to generate PDF report: " + errorMessage);
            }
        }
    }

    @GetMapping("/leave/excel")
    public ResponseEntity<?> generateLeaveRequestExcel(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long internId) {
        
        try {
            // Check if user is authenticated
            var currentUserOpt = authHelperService.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(java.util.Map.of("error", "Authentication required"));
            }
            
            User currentUser = currentUserOpt.get();
            System.out.println("=== Generating Leave Request Excel Report ===");
            System.out.println("User: " + currentUser.getUsername() + ", Role: " + currentUser.getRole());
            System.out.println("Status filter: " + (status != null ? status : "none"));
            System.out.println("Intern ID filter: " + (internId != null ? internId : "none"));
            
            // Role-based access control
            if (currentUser.getRole() == User.Role.INTERN) {
                // Interns can only view their own leave requests
                var currentInternIdOpt = authHelperService.getCurrentInternId();
                if (currentInternIdOpt.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(java.util.Map.of("error", "Intern profile not found"));
                }
                // Force filter to current intern's data
                internId = currentInternIdOpt.get();
                System.out.println("Intern accessing own report - filtering to intern ID: " + internId);
            } else if (currentUser.getRole() == User.Role.SUPERVISOR) {
                // Supervisors can view all leave requests (already allowed)
                System.out.println("Supervisor accessing all leave requests");
            } else if (currentUser.getRole() == User.Role.ADMIN) {
                // Admins can view all leave requests (already allowed)
                System.out.println("Admin accessing all leave requests");
            }
            
            // Fix database enum values before generating report
            try {
                databaseFixService.fixLeaveTypeEnumValues();
            } catch (Exception e) {
                System.err.println("⚠️ Warning: Could not fix database enum values: " + e.getMessage());
                // Continue anyway - will handle errors gracefully
            }
            
            ByteArrayOutputStream baos = generateLeaveRequestExcelReport(status, internId);
            
            if (baos == null || baos.size() == 0) {
                System.err.println("✗ Excel generation returned empty output");
                return ResponseEntity.status(500)
                        .body(java.util.Map.of("error", "Failed to generate Excel report"));
            }
            
            System.out.println("✓ Excel generated successfully, size: " + baos.size() + " bytes");
            System.out.println("=== End Generating Leave Request Excel Report ===");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "leave_requests_report.xlsx");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            System.err.println("✗ Error generating Excel report:");
            System.err.println("  Message: " + e.getMessage());
            System.err.println("  Class: " + e.getClass().getName());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(java.util.Map.of("error", "Failed to generate Excel report: " + e.getMessage()));
        }
    }

    private ByteArrayOutputStream generatePdfReport(String internName, String department, String field) throws IOException {
        List<Intern> interns = getFilteredInterns(internName, department, field);
        Map<Long, AttendanceStats> stats = calculateAttendanceStats(interns);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        
        try {
        
        // Title
        Paragraph title = new Paragraph("INTERN ATTENDANCE REPORT")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(18)
                .setBold();
        document.add(title);
        
        Paragraph date = new Paragraph("Generated: " + LocalDateTime.now().toString())
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(10);
        document.add(date);
        document.add(new Paragraph("\n"));
        
        // Create table
        float[] columnWidths = {150F, 120F, 100F, 70F, 70F, 70F, 100F};
        Table table = new Table(UnitValue.createPointArray(columnWidths));
        
        // Header row
        table.addHeaderCell(new Paragraph("Intern Name").setBold());
        table.addHeaderCell(new Paragraph("Email").setBold());
        table.addHeaderCell(new Paragraph("Department").setBold());
        table.addHeaderCell(new Paragraph("Present").setBold());
        table.addHeaderCell(new Paragraph("Absent").setBold());
        table.addHeaderCell(new Paragraph("On Leave").setBold());
        table.addHeaderCell(new Paragraph("Attendance %").setBold());
        
        // Data rows
        for (Intern intern : interns) {
            AttendanceStats stat = stats.get(intern.getInternId());
            if (stat == null) {
                stat = new AttendanceStats(0, 0, 0);
            }
            
            table.addCell(new Paragraph(intern.getName()));
            table.addCell(new Paragraph(intern.getEmail()));
            table.addCell(new Paragraph(intern.getDepartment() != null ? intern.getDepartment().getName() : "N/A"));
            table.addCell(new Paragraph(String.valueOf(stat.present)));
            table.addCell(new Paragraph(String.valueOf(stat.absent)));
            table.addCell(new Paragraph(String.valueOf(stat.onLeave)));
            table.addCell(new Paragraph(String.format("%.2f%%", stat.attendancePercent)));
        }
        
        document.add(table);
        return baos;
        } finally {
            if (document != null) {
                document.close();
            }
            if (pdf != null) {
                pdf.close();
            }
        }
    }

    private ByteArrayOutputStream generateExcelReport(String internName, String department, String field) throws IOException {
        List<Intern> interns = getFilteredInterns(internName, department, field);
        Map<Long, AttendanceStats> stats = calculateAttendanceStats(interns);
        
        Workbook workbook = new XSSFWorkbook();
        try {
        Sheet sheet = workbook.createSheet("Attendance Report");
        
        // Create header style
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Intern Name", "Email", "Department", "Present", "Absent", "On Leave", "Attendance %"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Create data rows
        int rowNum = 1;
        for (Intern intern : interns) {
            AttendanceStats stat = stats.get(intern.getInternId());
            if (stat == null) {
                stat = new AttendanceStats(0, 0, 0);
            }
            
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(intern.getName());
            row.createCell(1).setCellValue(intern.getEmail());
            row.createCell(2).setCellValue(intern.getDepartment() != null ? intern.getDepartment().getName() : "N/A");
            row.createCell(3).setCellValue(stat.present);
            row.createCell(4).setCellValue(stat.absent);
            row.createCell(5).setCellValue(stat.onLeave);
            row.createCell(6).setCellValue(String.format("%.2f%%", stat.attendancePercent));
        }
        
        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        return baos;
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    private List<Intern> getFilteredInterns(String internName, String department, String field) {
        List<Intern> allInterns = internRepository.findAll();
        
        return allInterns.stream()
                .filter(intern -> {
                    if (internName != null && !internName.isEmpty()) {
                        if (!intern.getName().toLowerCase().contains(internName.toLowerCase())) {
                            return false;
                        }
                    }
                    if (department != null && !department.isEmpty() && !department.equals("All")) {
                        if (intern.getDepartment() == null || 
                            !intern.getDepartment().getName().equals(department)) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * Generate PDF report for a specific intern's attendance
     */
    private ResponseEntity<?> generateInternAttendancePdf(Long internId) {
        try {
            Optional<Intern> internOpt = internRepository.findById(internId);
            if (internOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(java.util.Map.of("error", "Intern not found"));
            }
            
            Intern intern = internOpt.get();
            List<Intern> internList = java.util.Collections.singletonList(intern);
            Map<Long, AttendanceStats> stats = calculateAttendanceStats(internList);
            
            ByteArrayOutputStream baos = generatePdfReportForIntern(intern, stats.get(internId));
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "my_attendance_report.pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            System.err.println("✗ Error generating intern attendance PDF: " + e.getMessage());
            e.printStackTrace();
            HttpHeaders errorHeaders = new HttpHeaders();
            errorHeaders.setContentType(MediaType.APPLICATION_JSON);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(errorHeaders)
                    .body(java.util.Map.of("error", "Failed to generate attendance report: " + e.getMessage()));
        }
    }

    /**
     * Generate Excel report for a specific intern's attendance
     */
    private ResponseEntity<?> generateInternAttendanceExcel(Long internId) {
        try {
            Optional<Intern> internOpt = internRepository.findById(internId);
            if (internOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(java.util.Map.of("error", "Intern not found"));
            }
            
            Intern intern = internOpt.get();
            List<Intern> internList = java.util.Collections.singletonList(intern);
            Map<Long, AttendanceStats> stats = calculateAttendanceStats(internList);
            
            ByteArrayOutputStream baos = generateExcelReportForIntern(intern, stats.get(internId));
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "my_attendance_report.xlsx");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            System.err.println("✗ Error generating intern attendance Excel: " + e.getMessage());
            e.printStackTrace();
            HttpHeaders errorHeaders = new HttpHeaders();
            errorHeaders.setContentType(MediaType.APPLICATION_JSON);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(errorHeaders)
                    .body(java.util.Map.of("error", "Failed to generate attendance report: " + e.getMessage()));
        }
    }

    /**
     * Generate PDF report for a single intern
     */
    private ByteArrayOutputStream generatePdfReportForIntern(Intern intern, AttendanceStats stat) throws IOException {
        if (stat == null) {
            stat = new AttendanceStats(0, 0, 0);
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        
        try {
            // Title
            Paragraph title = new Paragraph("MY ATTENDANCE REPORT")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18)
                    .setBold();
            document.add(title);
            
            Paragraph subtitle = new Paragraph("Intern: " + intern.getName())
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(14);
            document.add(subtitle);
            
            Paragraph date = new Paragraph("Generated: " + LocalDateTime.now().toString())
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(10);
            document.add(date);
            document.add(new Paragraph("\n"));
            
            // Create table
            float[] columnWidths = {150F, 120F};
            Table table = new Table(UnitValue.createPointArray(columnWidths));
            
            // Header row
            table.addHeaderCell(new Paragraph("Metric").setBold());
            table.addHeaderCell(new Paragraph("Value").setBold());
            
            // Data rows
            table.addCell(new Paragraph("Intern Name"));
            table.addCell(new Paragraph(intern.getName()));
            
            table.addCell(new Paragraph("Email"));
            table.addCell(new Paragraph(intern.getEmail()));
            
            table.addCell(new Paragraph("Department"));
            table.addCell(new Paragraph(intern.getDepartment() != null ? intern.getDepartment().getName() : "N/A"));
            
            table.addCell(new Paragraph("Present Days"));
            table.addCell(new Paragraph(String.valueOf(stat.present)));
            
            table.addCell(new Paragraph("Absent Days"));
            table.addCell(new Paragraph(String.valueOf(stat.absent)));
            
            table.addCell(new Paragraph("On Leave Days"));
            table.addCell(new Paragraph(String.valueOf(stat.onLeave)));
            
            table.addCell(new Paragraph("Attendance Percentage"));
            table.addCell(new Paragraph(String.format("%.2f%%", stat.attendancePercent)));
            
            document.add(table);
            return baos;
        } finally {
            if (document != null) {
                document.close();
            }
            if (pdf != null) {
                pdf.close();
            }
        }
    }

    /**
     * Generate Excel report for a single intern
     */
    private ByteArrayOutputStream generateExcelReportForIntern(Intern intern, AttendanceStats stat) throws IOException {
        if (stat == null) {
            stat = new AttendanceStats(0, 0, 0);
        }
        
        Workbook workbook = new XSSFWorkbook();
        try {
            Sheet sheet = workbook.createSheet("My Attendance Report");
            
            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Metric");
            headerRow.createCell(1).setCellValue("Value");
            headerRow.getCell(0).setCellStyle(headerStyle);
            headerRow.getCell(1).setCellStyle(headerStyle);
            
            // Create data rows
            int rowNum = 1;
            String[] metrics = {"Intern Name", "Email", "Department", "Present Days", "Absent Days", "On Leave Days", "Attendance Percentage"};
            String[] values = {
                intern.getName(),
                intern.getEmail(),
                intern.getDepartment() != null ? intern.getDepartment().getName() : "N/A",
                String.valueOf(stat.present),
                String.valueOf(stat.absent),
                String.valueOf(stat.onLeave),
                String.format("%.2f%%", stat.attendancePercent)
            };
            
            for (int i = 0; i < metrics.length; i++) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(metrics[i]);
                row.createCell(1).setCellValue(values[i]);
            }
            
            // Auto-size columns
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos;
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    private Map<Long, AttendanceStats> calculateAttendanceStats(List<Intern> interns) {
        Map<Long, AttendanceStats> statsMap = new HashMap<>();
        
        for (Intern intern : interns) {
            List<Attendance> attendanceRecords = attendanceRepository.findByInternInternId(intern.getInternId());
            
            // Also check leave requests for on-leave status
            long leaveDays = 0;
            if (intern.getLeaveRequests() != null && !intern.getLeaveRequests().isEmpty()) {
                leaveDays = intern.getLeaveRequests().stream()
                    .filter(lr -> lr != null && lr.getStatus() != null && 
                            (lr.getStatus().name().equals("APPROVED") || lr.getStatus().name().equals("PENDING")))
                    .count();
            }
            
            int present = 0;
            int absent = 0;
            int onLeave = (int) leaveDays;
            
            LocalDate startDate = LocalDate.now().minusDays(30); // Last 30 days
            
            for (Attendance att : attendanceRecords) {
                if (att != null && att.getDate() != null && att.getStatus() != null) {
                    if (att.getDate().toLocalDate().isAfter(startDate.minusDays(1))) {
                        if (att.getStatus() == AttendanceStatus.SIGNED_IN || 
                            att.getStatus() == AttendanceStatus.SIGNED_OUT ||
                            att.getStatus() == AttendanceStatus.PRESENT) {
                            present++;
                        } else if (att.getStatus() == AttendanceStatus.ABSENT) {
                            absent++;
                        }
                    }
                }
            }
            
            int total = present + absent + onLeave;
            double attendancePercent = total > 0 ? (present * 100.0 / total) : 0.0;
            
            statsMap.put(intern.getInternId(), new AttendanceStats(present, absent, onLeave, attendancePercent));
        }
        
        return statsMap;
    }

    private static class AttendanceStats {
        int present;
        int absent;
        int onLeave;
        double attendancePercent;

        AttendanceStats(int present, int absent, int onLeave) {
            this.present = present;
            this.absent = absent;
            this.onLeave = onLeave;
            int total = present + absent + onLeave;
            this.attendancePercent = total > 0 ? (present * 100.0 / total) : 0.0;
        }

        AttendanceStats(int present, int absent, int onLeave, double attendancePercent) {
            this.present = present;
            this.absent = absent;
            this.onLeave = onLeave;
            this.attendancePercent = attendancePercent;
        }
    }

    private ByteArrayOutputStream generateLeaveRequestPdfReport(String status, Long internId) throws IOException {
        System.out.println("  [Report] Getting filtered leave requests...");
        List<LeaveRequest> leaveRequests = getFilteredLeaveRequests(status, internId);
        System.out.println("  [Report] Found " + leaveRequests.size() + " leave request(s)");
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = null;
        PdfDocument pdf = null;
        Document document = null;
        
        try {
            writer = new PdfWriter(baos);
            pdf = new PdfDocument(writer);
            document = new Document(pdf);
            
            // Title
            Paragraph title = new Paragraph("LEAVE REQUESTS REPORT")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18)
                    .setBold();
            document.add(title);
            
            Paragraph date = new Paragraph("Generated: " + LocalDateTime.now().toString())
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(10);
            document.add(date);
            document.add(new Paragraph("\n"));
            
            if (leaveRequests.isEmpty()) {
                System.out.println("  [Report] No leave requests found, adding empty message");
                Paragraph noData = new Paragraph("No leave requests found matching the criteria.")
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontSize(12);
                document.add(noData);
            } else {
                System.out.println("  [Report] Creating table with " + leaveRequests.size() + " rows");
                // Create table
                float[] columnWidths = {50F, 120F, 100F, 80F, 80F, 100F, 100F};
                Table table = new Table(UnitValue.createPointArray(columnWidths));
                
                // Header row
                table.addHeaderCell(new Paragraph("ID").setBold());
                table.addHeaderCell(new Paragraph("Intern Name").setBold());
                table.addHeaderCell(new Paragraph("Email").setBold());
                table.addHeaderCell(new Paragraph("Leave Type").setBold());
                table.addHeaderCell(new Paragraph("Status").setBold());
                table.addHeaderCell(new Paragraph("From Date").setBold());
                table.addHeaderCell(new Paragraph("To Date").setBold());
                
                // Data rows
                int rowCount = 0;
                for (LeaveRequest lr : leaveRequests) {
                    rowCount++;
                    String internName = "N/A";
                    String internEmail = "N/A";
                    try {
                        if (lr.getIntern() != null) {
                            internName = lr.getIntern().getName() != null ? lr.getIntern().getName() : "N/A";
                            internEmail = lr.getIntern().getEmail() != null ? lr.getIntern().getEmail() : "N/A";
                        }
                    } catch (Exception e) {
                        System.err.println("  [Report] Warning: Could not access intern for leave request " + lr.getRequestId() + ": " + e.getMessage());
                    }
                    
                    try {
                        // Safely get leave type
                        String leaveTypeStr = "N/A";
                        try {
                            if (lr.getLeaveType() != null) {
                                leaveTypeStr = lr.getLeaveType().toString();
                            }
                        } catch (Exception e) {
                            System.err.println("  [Report] Warning: Could not get leave type: " + e.getMessage());
                            leaveTypeStr = "N/A";
                        }
                        
                        // Safely get status
                        String statusStr = "N/A";
                        try {
                            if (lr.getStatus() != null) {
                                statusStr = lr.getStatus().toString();
                            }
                        } catch (Exception e) {
                            System.err.println("  [Report] Warning: Could not get status: " + e.getMessage());
                            statusStr = "N/A";
                        }
                        
                        table.addCell(new Paragraph(String.valueOf(lr.getRequestId())));
                        table.addCell(new Paragraph(internName != null ? internName : "N/A"));
                        table.addCell(new Paragraph(internEmail != null ? internEmail : "N/A"));
                        table.addCell(new Paragraph(leaveTypeStr));
                        table.addCell(new Paragraph(statusStr));
                        table.addCell(new Paragraph(lr.getFromDate() != null ? lr.getFromDate().toString() : "N/A"));
                        table.addCell(new Paragraph(lr.getToDate() != null ? lr.getToDate().toString() : "N/A"));
                    } catch (Exception e) {
                        System.err.println("  [Report] Error adding row " + rowCount + ": " + e.getMessage());
                        e.printStackTrace();
                        // Continue with other rows instead of throwing
                        continue;
                    }
                }
                
                System.out.println("  [Report] Added " + rowCount + " rows to table, adding to document");
                document.add(table);
            }
            
            System.out.println("  [Report] PDF content added successfully");
        } catch (Exception e) {
            System.err.println("  [Report] Error generating PDF content: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to generate PDF content: " + e.getMessage(), e);
        } finally {
            // Close resources in reverse order
            if (document != null) {
                try {
                    document.close();
                } catch (Exception e) {
                    System.err.println("  [Report] Error closing document: " + e.getMessage());
                }
            }
            if (pdf != null) {
                try {
                    pdf.close();
                } catch (Exception e) {
                    System.err.println("  [Report] Error closing PDF: " + e.getMessage());
                }
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {
                    System.err.println("  [Report] Error closing writer: " + e.getMessage());
                }
            }
        }
        
        System.out.println("  [Report] PDF generated successfully, size: " + baos.size() + " bytes");
        return baos;
    }

    private ByteArrayOutputStream generateLeaveRequestExcelReport(String status, Long internId) throws IOException {
        System.out.println("  [Report] Getting filtered leave requests...");
        List<LeaveRequest> leaveRequests = getFilteredLeaveRequests(status, internId);
        System.out.println("  [Report] Found " + leaveRequests.size() + " leave request(s)");
        
        Workbook workbook = new XSSFWorkbook();
        try {
            Sheet sheet = workbook.createSheet("Leave Requests Report");
            
            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "Intern Name", "Email", "Leave Type", "Status", "From Date", "To Date"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Create data rows
            int rowNum = 1;
            if (leaveRequests.isEmpty()) {
                Row row = sheet.createRow(rowNum++);
                Cell cell = row.createCell(0);
                cell.setCellValue("No leave requests found matching the criteria.");
            } else {
                for (LeaveRequest lr : leaveRequests) {
                    String internName = "N/A";
                    String internEmail = "N/A";
                    try {
                        if (lr.getIntern() != null) {
                            internName = lr.getIntern().getName() != null ? lr.getIntern().getName() : "N/A";
                            internEmail = lr.getIntern().getEmail() != null ? lr.getIntern().getEmail() : "N/A";
                        }
                    } catch (Exception e) {
                        System.err.println("  [Report] Warning: Could not access intern for leave request " + lr.getRequestId());
                    }
                    
                    Row row = sheet.createRow(rowNum++);
                    // Safely get leave type
                    String leaveTypeStr = "N/A";
                    try {
                        if (lr.getLeaveType() != null) {
                            leaveTypeStr = lr.getLeaveType().toString();
                        }
                    } catch (Exception e) {
                        System.err.println("  [Report] Warning: Could not get leave type: " + e.getMessage());
                        leaveTypeStr = "N/A";
                    }
                    
                    // Safely get status
                    String statusStr = "N/A";
                    try {
                        if (lr.getStatus() != null) {
                            statusStr = lr.getStatus().toString();
                        }
                    } catch (Exception e) {
                        System.err.println("  [Report] Warning: Could not get status: " + e.getMessage());
                        statusStr = "N/A";
                    }
                    
                    row.createCell(0).setCellValue(lr.getRequestId());
                    row.createCell(1).setCellValue(internName);
                    row.createCell(2).setCellValue(internEmail);
                    row.createCell(3).setCellValue(leaveTypeStr);
                    row.createCell(4).setCellValue(statusStr);
                    row.createCell(5).setCellValue(lr.getFromDate() != null ? lr.getFromDate().toString() : "N/A");
                    row.createCell(6).setCellValue(lr.getToDate() != null ? lr.getToDate().toString() : "N/A");
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            
            System.out.println("  [Report] Excel generated successfully, size: " + baos.size() + " bytes");
            return baos;
        } catch (Exception e) {
            System.err.println("  [Report] Error generating Excel: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    private List<LeaveRequest> getFilteredLeaveRequests(String status, Long internId) {
        // Use native SQL query to get raw data and avoid enum conversion errors
        System.out.println("  [Report] Fetching leave requests using native SQL query...");
        
        String sql = "SELECT lr.* FROM leave_requests lr WHERE 1=1";
        java.util.List<Object> params = new java.util.ArrayList<>();
        
        if (status != null && !status.isEmpty()) {
            try {
                LeaveStatus statusEnum = LeaveStatus.valueOf(status.toUpperCase());
                sql += " AND lr.status = ?";
                params.add(statusEnum.name());
            } catch (IllegalArgumentException e) {
                System.err.println("  [Report] Invalid status filter: " + status);
            }
        }
        
        if (internId != null) {
            sql += " AND lr.intern_id = ?";
            params.add(internId);
        }
        
        sql += " ORDER BY lr.from_date DESC";
        
        // Use native query to get raw results
        List<Map<String, Object>> rawResults = jdbcTemplate.queryForList(sql, params.toArray());
        System.out.println("  [Report] Found " + rawResults.size() + " leave request(s) from database");
        
        // Convert raw results to LeaveRequest entities, handling enum errors gracefully
        List<LeaveRequest> requests = new java.util.ArrayList<>();
        for (Map<String, Object> row : rawResults) {
            try {
                LeaveRequest lr = new LeaveRequest();
                lr.setRequestId(((Number) row.get("request_id")).longValue());
                
                // Safely set leave type
                try {
                    String leaveTypeStr = (String) row.get("leave_type");
                    if (leaveTypeStr != null) {
                        // Normalize the value
                        String normalized = leaveTypeStr.toUpperCase().trim();
                        if (normalized.contains("SICK") || normalized.equals("SICK LEAVE")) {
                            normalized = "SICK";
                        }
                        normalized = normalized.replace(" ", "_");
                        lr.setLeaveType(com.internregister.entity.LeaveType.valueOf(normalized));
                    }
                } catch (Exception e) {
                    System.err.println("  [Report] Warning: Could not parse leave_type '" + row.get("leave_type") + "': " + e.getMessage());
                    // Set to null or default - will show as N/A in report
                }
                
                // Safely set status
                try {
                    String statusStr = (String) row.get("status");
                    if (statusStr != null) {
                        lr.setStatus(LeaveStatus.valueOf(statusStr.toUpperCase()));
                    }
                } catch (Exception e) {
                    System.err.println("  [Report] Warning: Could not parse status '" + row.get("status") + "': " + e.getMessage());
                }
                
                // Set dates
                if (row.get("from_date") != null) {
                    lr.setFromDate(((java.sql.Date) row.get("from_date")).toLocalDate());
                }
                if (row.get("to_date") != null) {
                    lr.setToDate(((java.sql.Date) row.get("to_date")).toLocalDate());
                }
                
                // Get intern if intern_id exists
                Long internIdValue = row.get("intern_id") != null ? ((Number) row.get("intern_id")).longValue() : null;
                if (internIdValue != null) {
                    try {
                        var internOpt = internRepository.findById(internIdValue);
                        if (internOpt.isPresent()) {
                            lr.setIntern(internOpt.get());
                        }
                    } catch (Exception e) {
                        System.err.println("  [Report] Warning: Could not load intern " + internIdValue + ": " + e.getMessage());
                    }
                }
                
                requests.add(lr);
            } catch (Exception e) {
                System.err.println("  [Report] Error processing row: " + e.getMessage());
                e.printStackTrace();
                // Skip this row and continue
            }
        }
        
        System.out.println("  [Report] Successfully converted " + requests.size() + " leave request(s)");
        return requests;
    }
}

