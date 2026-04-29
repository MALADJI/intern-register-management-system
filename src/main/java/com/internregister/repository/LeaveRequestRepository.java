package com.internregister.repository;

import com.internregister.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    Page<LeaveRequest> findByStatus(String status, Pageable pageable);
    Page<LeaveRequest> findByIntern_InternId(Long internId, Pageable pageable);
    Page<LeaveRequest> findByStatusAndIntern_InternId(String status, Long internId, Pageable pageable);
    List<LeaveRequest> findByIntern_InternId(Long internId);
    
    @EntityGraph(attributePaths = {"intern", "intern.department", "intern.supervisor"})
    @Override
    @NonNull
    List<LeaveRequest> findAll();
}
