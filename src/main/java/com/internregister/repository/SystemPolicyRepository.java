package com.internregister.repository;

import com.internregister.entity.SystemPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemPolicyRepository extends JpaRepository<SystemPolicy, Long> {
    Optional<SystemPolicy> findByTitleIgnoreCase(String title);
}
