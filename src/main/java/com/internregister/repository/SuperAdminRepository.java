package com.internregister.repository;

import com.internregister.entity.SuperAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SuperAdminRepository extends JpaRepository<SuperAdmin, Long> {
    List<SuperAdmin> findByEmail(String email);
}
