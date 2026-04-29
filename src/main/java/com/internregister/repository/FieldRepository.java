package com.internregister.repository;

import com.internregister.entity.Field;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FieldRepository extends JpaRepository<Field, Long> {
    
    List<Field> findByDepartment_DepartmentId(Long departmentId);
    
    List<Field> findByDepartment_DepartmentIdAndActiveTrue(Long departmentId);
    
    Optional<Field> findByDepartment_DepartmentIdAndName(Long departmentId, String name);
    
    @Modifying
    @Query("DELETE FROM Field f WHERE f.department.departmentId = :departmentId")
    void deleteByDepartmentId(@Param("departmentId") Long departmentId);
}

