package com.internregister.repository;

import com.internregister.entity.Supervisor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SupervisorRepository extends JpaRepository<Supervisor, Long> {
    java.util.List<Supervisor> findByEmail(String email);

    // ✅ Find by email with department loaded (using LEFT JOIN FETCH)
    @Query("SELECT s FROM Supervisor s LEFT JOIN FETCH s.department WHERE s.email = :email")
    java.util.List<Supervisor> findByEmailWithDepartment(@Param("email") String email);

    // ✅ Find supervisors by department ID
    @Query("SELECT s FROM Supervisor s LEFT JOIN FETCH s.department WHERE s.department.departmentId = :departmentId")
    java.util.List<Supervisor> findByDepartmentId(@Param("departmentId") Long departmentId);

    // ✅ Find first supervisor by department ID
    @Query("SELECT s FROM Supervisor s LEFT JOIN FETCH s.department WHERE s.department.departmentId = :departmentId ORDER BY s.supervisorId ASC")
    java.util.List<Supervisor> findByDepartmentIdOrdered(@Param("departmentId") Long departmentId);

    // ✅ Find supervisor by department ID and field
    @Query("SELECT s FROM Supervisor s LEFT JOIN FETCH s.department WHERE s.department.departmentId = :departmentId AND s.field = :field")
    java.util.List<Supervisor> findByDepartmentIdAndField(@Param("departmentId") Long departmentId,
            @Param("field") String field);
}
