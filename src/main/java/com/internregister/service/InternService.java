package com.internregister.service;

import com.internregister.dto.InternRequest;
import com.internregister.dto.InternResponse;
import com.internregister.entity.Department;
import com.internregister.entity.Intern;
import com.internregister.entity.Location;
import com.internregister.entity.Supervisor;
import com.internregister.repository.DepartmentRepository;
import com.internregister.repository.InternRepository;
import com.internregister.repository.LocationRepository;
import com.internregister.repository.SupervisorRepository;
import com.internregister.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.internregister.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class InternService {

    private final InternRepository internRepository;
    private final DepartmentRepository departmentRepository;
    private final SupervisorRepository supervisorRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.internregister.repository.InternContractRepository internContractRepository;

    public InternService(InternRepository internRepository,
            DepartmentRepository departmentRepository,
            SupervisorRepository supervisorRepository,
            LocationRepository locationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            com.internregister.repository.InternContractRepository internContractRepository) {
        this.internRepository = internRepository;
        this.departmentRepository = departmentRepository;
        this.supervisorRepository = supervisorRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.internContractRepository = internContractRepository;
    }

    private java.util.Map<String, Boolean> getLoginStatusMap() {
        return userRepository.findByRole(User.Role.INTERN).stream()
            .collect(Collectors.toMap(u -> u.getEmail().toLowerCase(), u -> u.getLastLoginAt() != null, (a, b) -> a));
    }

    public List<InternResponse> getAllInterns() {
        java.util.Map<String, Boolean> loginStatusMap = getLoginStatusMap();
        return internRepository.findAll().stream().map(i -> toResponse(i, loginStatusMap)).collect(Collectors.toList());
    }

    public List<InternResponse> getByDepartmentId(Long departmentId) {
        if (departmentId == null) return java.util.Collections.emptyList();
        java.util.Map<String, Boolean> loginStatusMap = getLoginStatusMap();
        return internRepository.findByDepartmentId(departmentId).stream().map(i -> toResponse(i, loginStatusMap))
                .collect(Collectors.toList());
    }

    public Optional<Intern> getInternById(Long id) {
        if (id == null) return Optional.empty();
        return internRepository.findById(id);
    }

    public Optional<InternResponse> getInternByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        
        java.util.List<Intern> interns = internRepository.findByEmail(email);
        if (interns.isEmpty()) {
            return Optional.empty();
        }
        
        // Return first matching intern profile
        java.util.Map<String, Boolean> loginStatusMap = getLoginStatusMap();
        return Optional.of(toResponse(interns.get(0), loginStatusMap));
    }

    public InternResponse createIntern(InternRequest request) {
        Long deptId = request.getDepartmentId();
        if (deptId == null) {
            throw new IllegalArgumentException("Department ID is required");
        }
        Department department = departmentRepository.findById(deptId)
                .orElseThrow(() -> new NotFoundException("Department not found: " + deptId));

        // ✅ AUTOMATIC SUPERVISOR ASSIGNMENT
        Supervisor supervisor;
        Long providedSupervisorId = request.getSupervisorId();
        if (providedSupervisorId != null) {
            // If supervisorId is provided, use it
            supervisor = supervisorRepository.findById(providedSupervisorId)
                    .orElseThrow(() -> new NotFoundException("Supervisor not found: " + providedSupervisorId));
            System.out.println("✓ Using provided supervisor: " + supervisor.getName());
        } else {
            // Auto-assign supervisor based on department
            java.util.List<Supervisor> supervisors = supervisorRepository
                    .findByDepartmentIdOrdered(department.getDepartmentId());
            if (!supervisors.isEmpty()) {
                supervisor = supervisors.get(0);
                System.out.println("✓ Auto-assigned supervisor: " + supervisor.getName() + " (Department: "
                        + department.getName() + ")");
                if (supervisors.size() > 1) {
                    System.out.println("⚠️ Found " + supervisors.size()
                            + " duplicate supervisors for department. Using first one.");
                }
            } else {
                // Create default supervisor if none exists
                Supervisor defaultSupervisor = new Supervisor();
                defaultSupervisor.setName("Default Supervisor - " + department.getName());
                String deptNameLower = department.getName().toLowerCase().replace(" ", "").replace("-", "");
                defaultSupervisor.setEmail("supervisor@" + deptNameLower + ".univen.ac.za");
                defaultSupervisor.setDepartment(department);
                supervisor = supervisorRepository.save(defaultSupervisor);
                System.out.println("✓ Created and assigned default supervisor: " + supervisor.getName()
                        + " (Department: " + department.getName() + ")");
            }
        }

        Intern intern = new Intern();
        intern.setName(request.getName());
        intern.setEmail(request.getEmail());
        intern.setDepartment(department);
        intern.setSupervisor(supervisor);

        // Set field if provided
        if (request.getField() != null && !request.getField().trim().isEmpty()) {
            intern.setField(request.getField().trim());
            System.out.println("✓ Assigned field: " + intern.getField());
        }

        // Set employer if provided
        if (request.getEmployer() != null && !request.getEmployer().trim().isEmpty()) {
            intern.setEmployer(request.getEmployer().trim());
            System.out.println("✓ Assigned employer: " + intern.getEmployer());
        }

        // Set ID number if provided
        if (request.getIdNumber() != null && !request.getIdNumber().trim().isEmpty()) {
            intern.setIdNumber(request.getIdNumber().trim());
            System.out.println("✓ Assigned ID number: " + intern.getIdNumber());
        }

        // Set start date if provided
        if (request.getStartDate() != null) {
            intern.setStartDate(request.getStartDate());
            System.out.println("✓ Assigned start date: " + intern.getStartDate());
        }

        // Set end date if provided
        if (request.getEndDate() != null) {
            intern.setEndDate(request.getEndDate());
            System.out.println("✓ Assigned end date: " + intern.getEndDate());
        }

        // Handle location assignment if provided
        Long locId = request.getLocationId();
        if (locId != null) {
            Location location = locationRepository.findById(locId)
                    .orElseThrow(() -> new NotFoundException("Location not found: " + locId));
            intern.setLocation(location);
            System.out.println("✓ Assigned location: " + location.getName());
        }

        Intern saved = internRepository.save(intern);

        System.out.println("✓ Intern saved to database with ID: " + saved.getInternId());
        System.out.println("  Name: " + saved.getName());
        System.out.println("  Email: " + saved.getEmail());
        System.out.println("  Department: " + department.getName());
        System.out.println("  Supervisor: " + supervisor.getName());
        System.out.println("  Created At: " + saved.getCreatedAt());

        return toResponse(saved);
    }

    @Transactional
    public InternResponse createInternWithUser(InternRequest request, String defaultPassword) {
        String email = request.getEmail().trim().toLowerCase();

        // Detailed check for existing user - resilient to duplicates
        Optional<User> userOpt = userRepository.findByUsername(email).stream().findFirst();
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(email).stream().findFirst();
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Check if it's already an INTERN but missing a profile
            if (user.getRole() == User.Role.INTERN) {
                // To avoid NonUniqueResultException, we check if ANY intern exists with this
                // email
                boolean internExists = !internRepository.findByEmail(email).isEmpty();

                if (internExists) {
                    throw new RuntimeException("Intern profile already exists for " + email
                            + ". (Duplicate records may exist in database)");
                }
                System.out.println(
                        "⚠️ User account exists but profile is missing for " + email + ". Creating profile...");
            } else {
                throw new RuntimeException("User with email " + email + " already exists with role " + user.getRole());
            }
        } else {
            // Create User account
            User user = new User();
            user.setUsername(email);
            user.setEmail(email);
            String passwordToSet = (request.getPassword() != null && !request.getPassword().isEmpty())
                    ? request.getPassword()
                    : defaultPassword;
            user.setPassword(passwordEncoder.encode(passwordToSet));
            user.setRole(User.Role.INTERN);
            user.setActive(true);
            user.setRequiresPasswordChange(true); // Force new imported interns to reset password
            userRepository.save(user);
            System.out.println("✓ Created user account for " + email);
        }

        // Create Intern profile
        InternResponse response = createIntern(request);

        // Auto-activate
        activateIntern(response.getId(), true);
        response.setActive(true);

        return response;
    }

    public InternResponse updateIntern(Long id, InternRequest request) {
        if (id == null) throw new IllegalArgumentException("Intern ID is required");
        Intern intern = internRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Intern not found with id: " + id));

        Long deptId = request.getDepartmentId();
        if (deptId == null) throw new IllegalArgumentException("Department ID is required");
        Department department = departmentRepository.findById(deptId)
                .orElseThrow(() -> new NotFoundException("Department not found: " + deptId));

        Long supervisorId = request.getSupervisorId();
        if (supervisorId == null) throw new IllegalArgumentException("Supervisor ID is required");
        Supervisor supervisor = supervisorRepository.findById(supervisorId)
                .orElseThrow(() -> new NotFoundException("Supervisor not found: " + supervisorId));

        intern.setName(request.getName());
        intern.setEmail(request.getEmail());
        intern.setDepartment(department);
        intern.setSupervisor(supervisor);

        // Set field if provided
        if (request.getField() != null && !request.getField().trim().isEmpty()) {
            intern.setField(request.getField().trim());
        } else {
            // If field is null or empty, set to null
            intern.setField(null);
        }

        // Set employer if provided
        if (request.getEmployer() != null && !request.getEmployer().trim().isEmpty()) {
            intern.setEmployer(request.getEmployer().trim());
        } else {
            // If employer is null or empty, set to null
            intern.setEmployer(null);
        }

        // Set ID number if provided
        if (request.getIdNumber() != null && !request.getIdNumber().trim().isEmpty()) {
            intern.setIdNumber(request.getIdNumber().trim());
        } else {
            intern.setIdNumber(null);
        }

        // Set start date if provided
        if (request.getStartDate() != null) {
            intern.setStartDate(request.getStartDate());
        } else {
            intern.setStartDate(null);
        }

        // Set end date if provided
        if (request.getEndDate() != null) {
            intern.setEndDate(request.getEndDate());
        } else {
            intern.setEndDate(null);
        }

        // Handle location assignment
        if (request.getLocationId() != null) {
            Location location = locationRepository.findById(request.getLocationId())
                    .orElseThrow(() -> new NotFoundException("Location not found: " + request.getLocationId()));
            intern.setLocation(location);
        } else {
            // If locationId is null, remove location assignment
            intern.setLocation(null);
        }

        Intern saved = internRepository.save(intern);
        return toResponse(saved);
    }

    public void deleteIntern(Long id) {
        // Find intern first to get email for user deletion
        internRepository.findById(id).ifPresent(intern -> {
            // Delete associated user account if it exists
            String email = intern.getEmail();
            userRepository.findByEmail(email).stream().findFirst().ifPresent(user -> {
                userRepository.delete(user);
                System.out.println("✓ Deleted associated user account: " + email);
            });
            internRepository.delete(intern);
            System.out.println("✓ Deleted intern profile: " + email);
        });
    }

    public InternResponse activateIntern(Long id, boolean active) {
        Intern intern = internRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Intern not found with id: " + id));
        intern.setActive(active);

        // Sync with User table - resilient to duplicates
        userRepository.findAll().stream()
                .filter(u -> intern.getEmail().equalsIgnoreCase(u.getEmail())
                        || intern.getEmail().equalsIgnoreCase(u.getUsername()))
                .findFirst()
                .ifPresent(user -> {
                    user.setActive(active);
                    userRepository.save(user);
                    System.out.println(
                            "✓ Synced active status with User table: " + intern.getEmail() + " (" + active + ")");
                });

        Intern saved = internRepository.save(intern);
        return toResponse(saved);
    }

    public void updateInternSignature(Intern intern) {
        internRepository.save(intern);
    }

    public String getContractAgreement(Long internId) {
        return internContractRepository.findByIntern_InternId(internId)
                .map(com.internregister.entity.InternContract::getContractAgreement)
                .orElse(null);
    }

    public InternResponse updateContractAgreement(Long id, String contractAgreement) {
        Intern intern = internRepository.findById(id)
                .orElseThrow(() -> new com.internregister.service.NotFoundException("Intern not found with id: " + id));
                
        com.internregister.entity.InternContract contract = internContractRepository.findByIntern_InternId(id)
                .orElse(new com.internregister.entity.InternContract());
        
        if (contract.getIntern() == null) {
            contract.setIntern(intern);
        }
        contract.setContractAgreement(contractAgreement);
        internContractRepository.save(contract);
        
        return toResponse(intern);
    }

    public InternResponse assignLocationToIntern(Long internId, Long locationId) {
        Intern intern = internRepository.findById(internId)
                .orElseThrow(() -> new NotFoundException("Intern not found with id: " + internId));

        if (locationId != null) {
            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new NotFoundException("Location not found: " + locationId));
            intern.setLocation(location);
        } else {
            intern.setLocation(null);
        }

        Intern saved = internRepository.save(intern);
        return toResponse(saved);
    }

    public Page<InternResponse> searchInterns(String name, Pageable pageable) {
        Page<Intern> result;
        if (name != null && !name.isBlank()) {
            result = internRepository.findByNameContainingIgnoreCase(name, pageable);
        } else {
            result = internRepository.findAll(pageable);
        }
        java.util.Map<String, Boolean> loginStatusMap = getLoginStatusMap();
        return result.map(i -> toResponse(i, loginStatusMap));
    }
    private InternResponse toResponse(Intern intern, java.util.Map<String, Boolean> loginStatusMap) {
        InternResponse res = toResponse(intern);
        if (loginStatusMap != null && intern.getEmail() != null) {
            res.setHasLoggedIn(loginStatusMap.getOrDefault(intern.getEmail().toLowerCase(), false));
        } else {
            res.setHasLoggedIn(false);
        }
        return res;
    }

    private InternResponse toResponse(Intern intern) {
        InternResponse res = new InternResponse();
        res.setId(intern.getInternId());
        res.setName(intern.getName());
        res.setEmail(intern.getEmail());
        if (intern.getDepartment() != null) {
            res.setDepartmentId(intern.getDepartment().getDepartmentId());
            res.setDepartmentName(intern.getDepartment().getName());
        }
        if (intern.getSupervisor() != null) {
            res.setSupervisorId(intern.getSupervisor().getSupervisorId());
            res.setSupervisorName(intern.getSupervisor().getName());
        }
        if (intern.getLocation() != null) {
            res.setLocationId(intern.getLocation().getLocationId());
            res.setLocationName(intern.getLocation().getName());
        }
        // Include field and employer from intern entity
        res.setField(intern.getField());
        res.setEmployer(intern.getEmployer());
        res.setIdNumber(intern.getIdNumber());
        res.setStartDate(intern.getStartDate());
        res.setEndDate(intern.getEndDate());
        // Do not return massive base64 payloads on the main list response
        res.setContractAgreement(null);
        
        // Optimize whether to show contract flag by checking if document is mapped
        boolean hasContract = internContractRepository.findByIntern_InternId(intern.getInternId()).isPresent();
        res.setHasContract(hasContract);
        res.setActive(intern.getActive());
        
        String email = intern.getEmail();
        if (email != null) {
            userRepository.findByEmail(email).stream().findFirst().ifPresent(user -> {
                res.setHasLoggedIn(user.getLastLoginAt() != null);
            });
        }
        
        return res;
    }
}
