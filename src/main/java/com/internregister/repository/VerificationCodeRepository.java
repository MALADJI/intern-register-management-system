package com.internregister.repository;

import com.internregister.entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    List<VerificationCode> findByEmailAndCode(String email, String code);
    List<VerificationCode> findByEmail(String email);
    
    @Modifying
    @Query("DELETE FROM VerificationCode v WHERE v.expiresAt < ?1")
    void deleteExpiredCodes(LocalDateTime now);
    
    @Modifying
    @Query("DELETE FROM VerificationCode v WHERE v.email = ?1")
    void deleteByEmail(String email);
}

