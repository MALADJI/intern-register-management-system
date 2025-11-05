package com.internregister.service;

import com.internregister.entity.NotificationPreference;
import com.internregister.entity.User;
import com.internregister.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NotificationPreference getOrCreateFor(User user) {
        return repository.findByUser(user).orElseGet(() -> {
            NotificationPreference pref = new NotificationPreference();
            pref.setUser(user);
            return repository.save(pref);
        });
    }

    @Transactional
    public NotificationPreference update(User user, NotificationPreference updated) {
        NotificationPreference existing = getOrCreateFor(user);
        existing.setEmailLeaveUpdates(updated.isEmailLeaveUpdates());
        existing.setEmailAttendanceAlerts(updated.isEmailAttendanceAlerts());
        existing.setFrequency(updated.getFrequency() != null ? updated.getFrequency() : existing.getFrequency());
        return repository.save(existing);
    }
}


