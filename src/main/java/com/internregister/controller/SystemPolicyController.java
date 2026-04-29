package com.internregister.controller;

import com.internregister.entity.SystemPolicy;
import com.internregister.service.SystemPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class SystemPolicyController {

    private final SystemPolicyService systemPolicyService;

    @GetMapping
    public ResponseEntity<List<SystemPolicy>> getAllPolicies() {
        return ResponseEntity.ok(systemPolicyService.getAllPolicies());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SystemPolicy> getPolicyById(@PathVariable Long id) {
        return ResponseEntity.ok(systemPolicyService.getPolicyById(id));
    }

    @GetMapping("/by-title/{title}")
    public ResponseEntity<SystemPolicy> getPolicyByTitle(@PathVariable String title) {
        return ResponseEntity.ok(systemPolicyService.getPolicyByTitle(title));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<SystemPolicy> createPolicy(@RequestBody SystemPolicy policy) {
        return new ResponseEntity<>(systemPolicyService.createPolicy(policy), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<SystemPolicy> updatePolicy(@PathVariable Long id, @RequestBody SystemPolicy policyDetails) {
        return ResponseEntity.ok(systemPolicyService.updatePolicy(id, policyDetails));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
        systemPolicyService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }
}
