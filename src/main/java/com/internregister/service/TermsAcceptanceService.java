package com.internregister.service;

import com.internregister.entity.TermsAcceptance;
import com.internregister.entity.User;
import com.internregister.repository.TermsAcceptanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TermsAcceptanceService {

    private final TermsAcceptanceRepository repository;

    public TermsAcceptanceService(TermsAcceptanceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TermsAcceptance getOrCreate(User user) {
        return repository.findByUser(user).orElseGet(() -> {
            TermsAcceptance ta = new TermsAcceptance();
            ta.setUser(user);
            return repository.save(ta);
        });
    }

    @Transactional
    public TermsAcceptance accept(User user, String version, String ipAddress) {
        TermsAcceptance ta = getOrCreate(user);
        ta.setAccepted(true);
        ta.setAcceptedAt(LocalDateTime.now());
        if (version != null && !version.isBlank()) {
            ta.setVersion(version);
        }
        ta.setIpAddress(ipAddress);
        return repository.save(ta);
    }
}


