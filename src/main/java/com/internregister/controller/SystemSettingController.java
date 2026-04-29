package com.internregister.controller;

import com.internregister.service.SystemSettingService;
import com.internregister.util.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system-settings")
@CrossOrigin(origins = "*")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;
    private final SecurityUtil securityUtil;

    public SystemSettingController(SystemSettingService systemSettingService, SecurityUtil securityUtil) {
        this.systemSettingService = systemSettingService;
        this.securityUtil = securityUtil;
    }

    @GetMapping("/help-widget")
    public ResponseEntity<?> getHelpWidgetSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("about", systemSettingService.getSetting("help_about",
                "The University of Venda Intern Online Register System is a modern platform designed to streamline intern management and performance monitoring."));
        settings.put("phone", systemSettingService.getSetting("help_phone", "+27 15 962 8000"));
        settings.put("email", systemSettingService.getSetting("help_email", "support@univen.ac.za"));
        settings.put("location", systemSettingService.getSetting("help_location", "Thohoyandou, Limpopo"));
        settings.put("website", systemSettingService.getSetting("help_website", "https://www.univen.ac.za/"));

        return ResponseEntity.ok(settings);
    }

    @PutMapping("/help-widget")
    public ResponseEntity<?> updateHelpWidgetSettings(@RequestBody Map<String, String> body) {
        try {
            securityUtil.requireSuperAdmin();

            if (body.containsKey("about"))
                systemSettingService.saveSetting("help_about", body.get("about"));
            if (body.containsKey("phone"))
                systemSettingService.saveSetting("help_phone", body.get("phone"));
            if (body.containsKey("email"))
                systemSettingService.saveSetting("help_email", body.get("email"));
            if (body.containsKey("location"))
                systemSettingService.saveSetting("help_location", body.get("location"));
            if (body.containsKey("website"))
                systemSettingService.saveSetting("help_website", body.get("website"));

            return ResponseEntity.ok(Map.of("message", "Help Widget settings updated successfully"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update settings: " + e.getMessage()));
        }
    }
}
