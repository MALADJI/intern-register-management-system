package com.internregister.service;

import com.internregister.dto.SupervisorRequest;
import com.internregister.entity.Supervisor;
import com.internregister.entity.Department;
import com.internregister.repository.SupervisorRepository;
import com.internregister.repository.DepartmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SupervisorService {

    private final SupervisorRepository supervisorRepository;
    private final DepartmentRepository departmentRepository;

    public SupervisorService(SupervisorRepository supervisorRepository, 
                           DepartmentRepository departmentRepository) {
        this.supervisorRepository = supervisorRepository;
        this.departmentRepository = departmentRepository;
    }

    public List<Supervisor> getAllSupervisors() {
        return supervisorRepository.findAll();
    }

    public Optional<Supervisor> getSupervisorById(Long id) {
        return supervisorRepository.findById(id);
    }

    public Supervisor createSupervisor(SupervisorRequest request) {
        // Find the department
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found with id: " + request.getDepartmentId()));
        
        // Check if supervisor with email already exists
        if (supervisorRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Supervisor with email " + request.getEmail() + " already exists");
        }
        
        // Create new supervisor
        Supervisor supervisor = new Supervisor();
        supervisor.setName(request.getName());
        supervisor.setEmail(request.getEmail());
        supervisor.setDepartment(department);
        
        Supervisor saved = supervisorRepository.save(supervisor);
        System.out.println("✓ Supervisor created:");
        System.out.println("  Name: " + saved.getName());
        System.out.println("  Email: " + saved.getEmail());
        System.out.println("  Department: " + department.getName());
        System.out.println("  Supervisor ID: " + saved.getSupervisorId());
        
        return saved;
    }

    public Supervisor updateSupervisor(Long id, SupervisorRequest request) {
        Supervisor supervisor = supervisorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supervisor not found with id: " + id));
        
        // Find the department
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found with id: " + request.getDepartmentId()));
        
        // Check if email is being changed and if new email already exists
        if (!supervisor.getEmail().equals(request.getEmail())) {
            if (supervisorRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new RuntimeException("Supervisor with email " + request.getEmail() + " already exists");
            }
        }
        
        // Update supervisor
        supervisor.setName(request.getName());
        supervisor.setEmail(request.getEmail());
        supervisor.setDepartment(department);
        
        Supervisor saved = supervisorRepository.save(supervisor);
        System.out.println("✓ Supervisor updated:");
        System.out.println("  Supervisor ID: " + saved.getSupervisorId());
        System.out.println("  Name: " + saved.getName());
        System.out.println("  Email: " + saved.getEmail());
        System.out.println("  Department: " + department.getName());
        
        return saved;
    }

    public void deleteSupervisor(Long id) {
        if (!supervisorRepository.existsById(id)) {
            throw new RuntimeException("Supervisor not found with id: " + id);
        }
        supervisorRepository.deleteById(id);
        System.out.println("✓ Supervisor deleted: ID " + id);
    }
}
