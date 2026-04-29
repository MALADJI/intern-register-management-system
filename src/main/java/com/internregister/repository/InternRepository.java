package com.internregister.repository;

import com.internregister.entity.Intern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import java.util.List;

public interface InternRepository extends JpaRepository<Intern, Long> {
    Page<Intern> findByNameContainingIgnoreCase(String name, Pageable pageable);
    java.util.List<Intern> findByEmail(String email);
    
    // ✅ Find by email with department and location loaded (using LEFT JOIN FETCH)
    @Query("SELECT DISTINCT i FROM Intern i LEFT JOIN FETCH i.department LEFT JOIN FETCH i.location WHERE i.email = :email")
    java.util.List<Intern> findByEmailWithDepartment(@Param("email") String email);
    
    // ✅ Eagerly load department and location when fetching all interns
    @EntityGraph(attributePaths = {"department", "location"})
    @Override
    @NonNull
    List<Intern> findAll();

    // ✅ Find interns by department ID
    @Query("SELECT i FROM Intern i WHERE i.department.departmentId = :departmentId")
    List<Intern> findByDepartmentId(@Param("departmentId") Long departmentId);
}
