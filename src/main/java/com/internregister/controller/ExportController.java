package com.internregister.controller;

import com.internregister.repository.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/export")
@CrossOrigin(origins = "*")
public class ExportController {

    private final UserRepository userRepository;
    private final InternRepository internRepository;
    private final SupervisorRepository supervisorRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public ExportController(UserRepository userRepository,
                            InternRepository internRepository,
                            SupervisorRepository supervisorRepository,
                            DepartmentRepository departmentRepository,
                            AttendanceRepository attendanceRepository,
                            LeaveRequestRepository leaveRequestRepository) {
        this.userRepository = userRepository;
        this.internRepository = internRepository;
        this.supervisorRepository = supervisorRepository;
        this.departmentRepository = departmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @GetMapping("/db/json")
    public ResponseEntity<byte[]> exportDatabaseAsJson() {
        Map<String, Object> payload = Map.of(
                "users", userRepository.findAll(),
                "interns", internRepository.findAll(),
                "supervisors", supervisorRepository.findAll(),
                "departments", departmentRepository.findAll(),
                "attendance", attendanceRepository.findAll(),
                "leaveRequests", leaveRequestRepository.findAll()
        );

        // Serialize to JSON using a lightweight approach
        // In Spring, ResponseEntity with Jackson will serialize automatically, but we force attachment
        byte[] data = serializeToJsonBytes(payload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export.json");
        return ResponseEntity.ok().headers(headers).body(data);
    }

    private byte[] serializeToJsonBytes(Object obj) {
        try {
            // Use Jackson ObjectMapper if available on classpath
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.findAndRegisterModules();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(obj);
        } catch (Throwable t) {
            // Fallback to simple toString
            return String.valueOf(obj).getBytes(StandardCharsets.UTF_8);
        }
    }
}


