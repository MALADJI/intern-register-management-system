package com.internregister.controller;

import com.internregister.entity.Intern;
import com.internregister.entity.Attendance;
import com.internregister.entity.AttendanceStatus;
import com.internregister.entity.Supervisor;
import com.internregister.entity.User;
import com.internregister.repository.InternRepository;
import com.internregister.repository.AttendanceRepository;
import com.internregister.repository.SupervisorRepository;
import com.internregister.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private SecurityUtil securityUtil;

    @Autowired
    private SupervisorRepository supervisorRepository;

    @GetMapping("/attendance/pdf")
    public ResponseEntity<byte[]> generateAttendancePdf(
            @RequestParam(required = false) String internName,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String field,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        
        try {
            ByteArrayOutputStream baos = generatePdfReport(internName, department, field, fromDate, toDate);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "attendance_report.pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/attendance/excel")
    public ResponseEntity<byte[]> generateAttendanceExcel(
            @RequestParam(required = false) String internName,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String field,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        
        try {
            ByteArrayOutputStream baos = generateExcelReport(internName, department, field, fromDate, toDate);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "attendance_report.xlsx");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

    private ByteArrayOutputStream generatePdfReport(String internName, String department, String field, String fromDate, String toDate) throws IOException {
        List<Intern> interns = getFilteredInterns(internName, department, field);
        Map<Long, AttendanceStats> stats = calculateAttendanceStats(interns, fromDate, toDate);
        
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

    private ByteArrayOutputStream generateExcelReport(String internName, String department, String field, String fromDate, String toDate) throws IOException {
        List<Intern> interns = getFilteredInterns(internName, department, field);
        Map<Long, AttendanceStats> stats = calculateAttendanceStats(interns, fromDate, toDate);
        
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
        List<Intern> allInterns;
        
        // Check if current user is a supervisor - if so, only show their assigned interns
        Optional<User> currentUserOpt = securityUtil.getCurrentUser();
        if (currentUserOpt.isPresent() && currentUserOpt.get().getRole() == User.Role.SUPERVISOR) {
            Optional<Supervisor> supervisorOpt = supervisorRepository.findByEmail(currentUserOpt.get().getEmail()).stream().findFirst();
            if (supervisorOpt.isPresent()) {
                Supervisor supervisor = supervisorOpt.get();
                allInterns = supervisor.getInterns() != null ? supervisor.getInterns() : List.of();
            } else {
                allInterns = List.of(); // Supervisor not found, return empty list
            }
        } else {
            // For ADMIN, SUPER_ADMIN, or INTERN - return all interns
            allInterns = internRepository.findAll();
        }
        
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
                    if (field != null && !field.isEmpty() && !field.equals("All")) {
                        if (intern.getField() == null || !intern.getField().equals(field)) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private Map<Long, AttendanceStats> calculateAttendanceStats(List<Intern> interns, String fromDate, String toDate) {
        Map<Long, AttendanceStats> statsMap = new HashMap<>();
        
        // Parse date filters
        LocalDate parsedStartDate = null;
        LocalDate parsedEndDate = null;
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        
        if (fromDate != null && !fromDate.isEmpty()) {
            try {
                parsedStartDate = LocalDate.parse(fromDate, formatter);
            } catch (Exception e) {
                System.err.println("Invalid fromDate format: " + fromDate);
            }
        }
        
        if (toDate != null && !toDate.isEmpty()) {
            try {
                parsedEndDate = LocalDate.parse(toDate, formatter);
            } catch (Exception e) {
                System.err.println("Invalid toDate format: " + toDate);
            }
        }
        
        // Default to last 30 days if no date range specified
        final LocalDate startDate;
        final LocalDate endDate;
        
        if (parsedStartDate == null && parsedEndDate == null) {
            startDate = LocalDate.now().minusDays(30);
            endDate = LocalDate.now();
        } else if (parsedStartDate == null) {
            startDate = parsedEndDate != null ? parsedEndDate.minusDays(30) : LocalDate.now().minusDays(30);
            endDate = parsedEndDate != null ? parsedEndDate : LocalDate.now();
        } else if (parsedEndDate == null) {
            startDate = parsedStartDate;
            endDate = LocalDate.now();
        } else {
            startDate = parsedStartDate;
            endDate = parsedEndDate;
        }
        
        for (Intern intern : interns) {
            List<Attendance> attendanceRecords = attendanceRepository.findByInternInternId(intern.getInternId());
            
            // Also check leave requests for on-leave status
            long leaveDays = 0;
            if (intern.getLeaveRequests() != null && !intern.getLeaveRequests().isEmpty()) {
                leaveDays = intern.getLeaveRequests().stream()
                    .filter(lr -> {
                        if (lr == null || lr.getStatus() == null) return false;
                        if (!lr.getStatus().name().equals("APPROVED") && !lr.getStatus().name().equals("PENDING")) {
                            return false;
                        }
                        // Filter by date range if provided
                        if (startDate != null && lr.getFromDate() != null && lr.getFromDate().isBefore(startDate)) {
                            return false;
                        }
                        if (endDate != null && lr.getToDate() != null && lr.getToDate().isAfter(endDate)) {
                            return false;
                        }
                        return true;
                    })
                    .count();
            }
            
            int present = 0;
            int absent = 0;
            int onLeave = (int) leaveDays;
            
            for (Attendance att : attendanceRecords) {
                if (att != null && att.getDate() != null && att.getStatus() != null) {
                    LocalDate attDate = att.getDate().toLocalDate();
                    
                    // Apply date filter
                    if (startDate != null && attDate.isBefore(startDate)) {
                        continue;
                    }
                    if (endDate != null && attDate.isAfter(endDate)) {
                        continue;
                    }
                    
                    if (att.getStatus() == AttendanceStatus.SIGNED_IN || 
                        att.getStatus() == AttendanceStatus.SIGNED_OUT ||
                        att.getStatus() == AttendanceStatus.PRESENT) {
                        present++;
                    } else if (att.getStatus() == AttendanceStatus.ABSENT) {
                        absent++;
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
}

