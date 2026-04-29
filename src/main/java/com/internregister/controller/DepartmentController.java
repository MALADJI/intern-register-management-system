package com.internregister.controller;

import com.internregister.entity.Department;
import com.internregister.entity.Field;
import com.internregister.repository.DepartmentRepository;
import com.internregister.repository.FieldRepository;
import com.internregister.service.DepartmentService;
import com.internregister.service.WebSocketService;
import com.internregister.service.ActivityLogService;
import com.internregister.util.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/departments")
@CrossOrigin(origins = "*")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DepartmentRepository departmentRepository;
    private final FieldRepository fieldRepository;
    private final WebSocketService webSocketService;
    private final ActivityLogService activityLogService;
    private final SecurityUtil securityUtil;

    public DepartmentController(
            DepartmentService departmentService,
            DepartmentRepository departmentRepository,
            FieldRepository fieldRepository,
            WebSocketService webSocketService,
            ActivityLogService activityLogService,
            SecurityUtil securityUtil) {
        this.departmentService = departmentService;
        this.departmentRepository = departmentRepository;
        this.fieldRepository = fieldRepository;
        this.webSocketService = webSocketService;
        this.activityLogService = activityLogService;
        this.securityUtil = securityUtil;
    }

    @GetMapping
    public List<Department> getAllDepartments() {
        return departmentService.getAllDepartments();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Department> getDepartmentById(@PathVariable Long id) {
        Optional<Department> departmentOpt = departmentService.getDepartmentById(id);
        return departmentOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createDepartment(@RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Department name is required"));
            }

            name = name.trim();

            // Check if department with this name already exists
            Optional<Department> existingDeptOpt = departmentRepository.findByName(name);
            if (existingDeptOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Department with this name already exists"));
            }

            // Create new department
            Department department = new Department();
            department.setName(name);
            department.setActive(true); // Set active by default

            // Save department
            Department saved = departmentService.saveDepartment(department);

            // Broadcast real-time update
            webSocketService.broadcastDepartmentUpdate("CREATED", saved);

            // Log department creation
            securityUtil.getCurrentUser()
                    .ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(), "CREATE_DEPARTMENT",
                            "Created department: " + saved.getName(), null));

            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create department: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDepartment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Optional<Department> departmentOpt = departmentService.getDepartmentById(id);
        if (departmentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Department not found"));
        }

        Department department = departmentOpt.get();
        String name = body.get("name");
        if (name != null && !name.trim().isEmpty()) {
            department.setName(name.trim());
        }

        Department updated = departmentService.saveDepartment(department);

        // Broadcast real-time update
        webSocketService.broadcastDepartmentUpdate("UPDATED", updated);

        // Log department update
        securityUtil.getCurrentUser()
                .ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(), "UPDATE_DEPARTMENT",
                        "Updated department: " + updated.getName(), null));

        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<?> activateDepartment(@PathVariable Long id) {
        Optional<Department> departmentOpt = departmentService.getDepartmentById(id);
        if (departmentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Department not found"));
        }

        Department department = departmentOpt.get();
        department.setActive(true);
        Department updated = departmentService.saveDepartment(department);

        // Broadcast real-time update
        webSocketService.broadcastDepartmentUpdate("ACTIVATED", updated);

        return ResponseEntity.ok(Map.of(
                "message", "Department activated successfully",
                "departmentId", updated.getDepartmentId(),
                "active", true));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateDepartment(@PathVariable Long id) {
        Optional<Department> departmentOpt = departmentService.getDepartmentById(id);
        if (departmentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Department not found"));
        }

        Department department = departmentOpt.get();
        department.setActive(false);
        Department updated = departmentService.saveDepartment(department);

        // Broadcast real-time update
        webSocketService.broadcastDepartmentUpdate("DEACTIVATED", updated);

        return ResponseEntity.ok(Map.of(
                "message", "Department deactivated successfully",
                "departmentId", updated.getDepartmentId(),
                "active", false));
    }

    @DeleteMapping("/{id}")
    public void deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);

        // Broadcast real-time update
        webSocketService.broadcastDepartmentUpdate("DELETED", Map.of("departmentId", id));

        // Log department deletion
        securityUtil.getCurrentUser()
                .ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(), "DELETE_DEPARTMENT",
                        "Deleted department ID: " + id, null));
    }

    /**
     * Add a field to a department
     */
    @PostMapping("/{id}/fields")
    public ResponseEntity<?> addFieldToDepartment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            if (id == null) return ResponseEntity.badRequest().body(Map.of("error", "Department ID is required"));
            Optional<Department> departmentOpt = departmentRepository.findById(id);
            if (departmentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Department not found"));
            }

            String fieldName = body.get("name");
            if (fieldName == null || fieldName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Field name is required"));
            }

            Department department = departmentOpt.get();

            // Check if field already exists
            Optional<Field> existingFieldOpt = fieldRepository.findByDepartment_DepartmentIdAndName(id,
                    fieldName.trim());
            if (existingFieldOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Field with this name already exists in this department"));
            }

            // Create and save new field
            Field newField = new Field();
            newField.setName(fieldName.trim());
            newField.setDepartment(department);
            newField.setActive(true);

            Field savedField = fieldRepository.save(newField);

            // Reload department to get updated fields list
            Department updatedDepartment = departmentRepository.findById(id).orElse(department);

            // Broadcast real-time update
            webSocketService.broadcastDepartmentUpdate("FIELD_ADDED", Map.of(
                    "departmentId", id,
                    "fieldId", savedField.getFieldId(),
                    "fieldName", savedField.getName()));

            // Log field addition
            securityUtil.getCurrentUser()
                    .ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(), "ADD_FIELD",
                            "Added field: " + fieldName + " to department: " + department.getName(), null));

            return ResponseEntity.ok(updatedDepartment);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add field: " + e.getMessage()));
        }
    }

    /**
     * Update a field in a department
     */
    @PutMapping("/{id}/fields/{fieldId}")
    public ResponseEntity<?> updateField(@PathVariable Long id, @PathVariable Long fieldId,
            @RequestBody Map<String, String> body) {
        try {
            if (id == null || fieldId == null) return ResponseEntity.badRequest().body(Map.of("error", "IDs are required"));
            Optional<Department> departmentOpt = departmentRepository.findById(id);
            if (departmentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Department not found"));
            }

            Optional<Field> fieldOpt = fieldRepository.findById(fieldId);
            if (fieldOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Field not found"));
            }

            Field field = fieldOpt.get();
            if (!field.getDepartment().getDepartmentId().equals(id)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Field does not belong to this department"));
            }

            String fieldName = body.get("name");
            if (fieldName == null || fieldName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Field name is required"));
            }

            // Check if another field with this name already exists
            Optional<Field> existingFieldOpt = fieldRepository.findByDepartment_DepartmentIdAndName(id,
                    fieldName.trim());
            if (existingFieldOpt.isPresent() && !existingFieldOpt.get().getFieldId().equals(fieldId)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Field with this name already exists in this department"));
            }

            field.setName(fieldName.trim());
            fieldRepository.save(field);

            // Reload department to get updated fields list
            Department updatedDepartment = departmentRepository.findById(id).orElse(departmentOpt.get());

            // Broadcast real-time update
            webSocketService.broadcastDepartmentUpdate("FIELD_UPDATED", Map.of(
                    "departmentId", id,
                    "fieldId", fieldId,
                    "fieldName", field.getName()));

            return ResponseEntity.ok(updatedDepartment);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update field: " + e.getMessage()));
        }
    }

    /**
     * Delete a field from a department
     */
    @DeleteMapping("/{id}/fields/{fieldId}")
    public ResponseEntity<?> deleteField(@PathVariable Long id, @PathVariable Long fieldId) {
        try {
            if (id == null || fieldId == null) return ResponseEntity.badRequest().body(Map.of("error", "IDs are required"));
            Optional<Department> departmentOpt = departmentRepository.findById(id);
            if (departmentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Department not found"));
            }

            Optional<Field> fieldOpt = fieldRepository.findById(fieldId);
            if (fieldOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Field not found"));
            }

            Field field = fieldOpt.get();
            if (!field.getDepartment().getDepartmentId().equals(id)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Field does not belong to this department"));
            }

            fieldRepository.delete(field);

            // Broadcast real-time update
            webSocketService.broadcastDepartmentUpdate("FIELD_DELETED", Map.of(
                    "departmentId", id,
                    "fieldId", fieldId));

            return ResponseEntity.ok(Map.of(
                    "message", "Field deleted successfully",
                    "departmentId", id,
                    "fieldId", fieldId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete field: " + e.getMessage()));
        }
    }

    /**
     * Deactivate a field in a department
     */
    @PutMapping("/{id}/fields/{fieldId}/deactivate")
    public ResponseEntity<?> deactivateField(@PathVariable Long id, @PathVariable Long fieldId) {
        try {
            if (id == null || fieldId == null) return ResponseEntity.badRequest().body(Map.of("error", "IDs are required"));
            Optional<Department> departmentOpt = departmentRepository.findById(id);
            if (departmentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Department not found"));
            }

            Optional<Field> fieldOpt = fieldRepository.findById(fieldId);
            if (fieldOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Field not found"));
            }

            Field field = fieldOpt.get();
            if (!field.getDepartment().getDepartmentId().equals(id)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Field does not belong to this department"));
            }

            field.setActive(false);
            fieldRepository.save(field);

            // Reload department to get updated fields list
            Department updatedDepartment = departmentRepository.findById(id).orElse(departmentOpt.get());

            // Broadcast real-time update
            webSocketService.broadcastDepartmentUpdate("FIELD_DEACTIVATED", Map.of(
                    "departmentId", id,
                    "fieldId", fieldId,
                    "active", false));

            return ResponseEntity.ok(updatedDepartment);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to deactivate field: " + e.getMessage()));
        }
    }

    /**
     * Activate a field in a department
     */
    @PutMapping("/{id}/fields/{fieldId}/activate")
    public ResponseEntity<?> activateField(@PathVariable Long id, @PathVariable Long fieldId) {
        try {
            if (id == null || fieldId == null) return ResponseEntity.badRequest().body(Map.of("error", "IDs are required"));
            Optional<Department> departmentOpt = departmentRepository.findById(id);
            if (departmentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Department not found"));
            }

            Optional<Field> fieldOpt = fieldRepository.findById(fieldId);
            if (fieldOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Field not found"));
            }

            Field field = fieldOpt.get();
            if (!field.getDepartment().getDepartmentId().equals(id)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Field does not belong to this department"));
            }

            field.setActive(true);
            fieldRepository.save(field);

            // Reload department to get updated fields list
            Department updatedDepartment = departmentRepository.findById(id).orElse(departmentOpt.get());

            // Broadcast real-time update
            webSocketService.broadcastDepartmentUpdate("FIELD_ACTIVATED", Map.of(
                    "departmentId", id,
                    "fieldId", fieldId,
                    "active", true));

            return ResponseEntity.ok(updatedDepartment);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to activate field: " + e.getMessage()));
        }
    }
}
