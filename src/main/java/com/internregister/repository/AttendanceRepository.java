package com.internregister.repository;

import com.internregister.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    // ✅ Eagerly load intern relationship to avoid lazy loading issues
    @EntityGraph(attributePaths = { "intern" })
    List<Attendance> findByInternInternId(Long internId);

    @EntityGraph(attributePaths = { "intern" })
    List<Attendance> findByStatusAndTimeOutIsNull(com.internregister.entity.AttendanceStatus status);

    @Override
    @NonNull
    @EntityGraph(attributePaths = { "intern" })
    Page<Attendance> findAll(@NonNull Pageable pageable);

    @EntityGraph(attributePaths = { "intern" })
    List<Attendance> findByInternAndDateBetween(com.internregister.entity.Intern intern,
            java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);
}
