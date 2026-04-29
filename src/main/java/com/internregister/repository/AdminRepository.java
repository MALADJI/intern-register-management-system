package com.internregister.repository;

import com.internregister.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    java.util.List<Admin> findByEmail(String email);
    
    // ✅ Find by email with department loaded (using LEFT JOIN FETCH)
    @Query("SELECT a FROM Admin a LEFT JOIN FETCH a.department WHERE a.email = :email")
    java.util.List<Admin> findByEmailWithDepartment(@Param("email") String email);
    
    // ✅ Find all admins with department loaded
    @EntityGraph(attributePaths = {"department"})
    @Override
    @NonNull
    List<Admin> findAll();
    
    // ✅ Find by ID with department loaded
    @EntityGraph(attributePaths = {"department"})
    @Override
    @NonNull
    java.util.Optional<Admin> findById(@NonNull Long id);

    // ✅ Find admins by department ID
    @Query("SELECT a FROM Admin a WHERE a.department.departmentId = :departmentId")
    List<Admin> findByDepartmentId(@Param("departmentId") Long departmentId);
}
