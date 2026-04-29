package com.internregister.service;

import com.internregister.entity.SystemSetting;
import com.internregister.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SystemSettingService(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public String getSetting(String key, String defaultValue) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        String value = systemSettingRepository.findById(key)
                .map(SystemSetting::getValue)
                .orElse(defaultValue);
        if (value != null) {
            cache.put(key, value);
        }
        return value;
    }

    @Transactional
    public void saveSetting(String key, String value) {
        SystemSetting setting = systemSettingRepository.findById(key)
                .orElse(new SystemSetting(key, value));
        setting.setValue(value);
        systemSettingRepository.save(setting);
        cache.put(key, value);
    }
}
