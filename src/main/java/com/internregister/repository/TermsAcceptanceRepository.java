package com.internregister.repository;

import com.internregister.entity.TermsAcceptance;
import com.internregister.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, Long> {
    Optional<TermsAcceptance> findByUser(User user);
}


