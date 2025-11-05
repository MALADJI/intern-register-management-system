package com.internregister.service;

import com.internregister.entity.Department;
import com.internregister.repository.DepartmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public Optional<Department> getDepartmentById(Long id) {
        return departmentRepository.findById(id);
    }

    public Department createDepartment(String name) {
        // Check if department with same name already exists
        if (departmentRepository.findByName(name).isPresent()) {
            throw new RuntimeException("Department with name '" + name + "' already exists");
        }
        
        Department department = new Department();
        department.setName(name);
        
        Department saved = departmentRepository.save(department);
        System.out.println("✓ Department created:");
        System.out.println("  Name: " + saved.getName());
        System.out.println("  Department ID: " + saved.getDepartmentId());
        
        return saved;
    }

    public Department updateDepartment(Long id, String name) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found with id: " + id));
        
        // Check if name is being changed and if new name already exists
        if (!department.getName().equals(name)) {
            if (departmentRepository.findByName(name).isPresent()) {
                throw new RuntimeException("Department with name '" + name + "' already exists");
            }
        }
        
        department.setName(name);
        
        Department saved = departmentRepository.save(department);
        System.out.println("✓ Department updated:");
        System.out.println("  Department ID: " + saved.getDepartmentId());
        System.out.println("  Name: " + saved.getName());
        
        return saved;
    }

    public void deleteDepartment(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found with id: " + id));
        
        // Check if department has interns or supervisors
        if (department.getInterns() != null && !department.getInterns().isEmpty()) {
            throw new RuntimeException("Cannot delete department. It has " + department.getInterns().size() + " intern(s) assigned. Please reassign interns first.");
        }
        
        if (department.getSupervisors() != null && !department.getSupervisors().isEmpty()) {
            throw new RuntimeException("Cannot delete department. It has " + department.getSupervisors().size() + " supervisor(s) assigned. Please reassign supervisors first.");
        }
        
        departmentRepository.deleteById(id);
        System.out.println("✓ Department deleted: ID " + id + " (" + department.getName() + ")");
    }
}
