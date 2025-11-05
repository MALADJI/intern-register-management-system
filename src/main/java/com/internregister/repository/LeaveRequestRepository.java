package com.internregister.repository;

import com.internregister.entity.LeaveRequest;
import com.internregister.entity.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    // Query by enum directly (Spring Data JPA handles this automatically)
    Page<LeaveRequest> findByStatus(LeaveStatus status, Pageable pageable);
    
    Page<LeaveRequest> findByIntern_InternId(Long internId, Pageable pageable);
    
    Page<LeaveRequest> findByStatusAndIntern_InternId(LeaveStatus status, Long internId, Pageable pageable);
    
    List<LeaveRequest> findByIntern_InternId(Long internId);
    
    // Query by string status (for backward compatibility and flexibility)
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = :status")
    Page<LeaveRequest> findByStatusString(@Param("status") LeaveStatus status, Pageable pageable);
    
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = :status AND lr.intern.internId = :internId")
    Page<LeaveRequest> findByStatusStringAndInternId(@Param("status") LeaveStatus status, @Param("internId") Long internId, Pageable pageable);
}
