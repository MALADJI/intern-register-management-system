package com.internregister.service;

import com.internregister.entity.SystemPolicy;
import com.internregister.repository.SystemPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemPolicyService {

    private final SystemPolicyRepository systemPolicyRepository;

    public List<SystemPolicy> getAllPolicies() {
        return systemPolicyRepository.findAll();
    }

    public SystemPolicy getPolicyById(Long id) {
        return systemPolicyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("System Policy not found with id: " + id));
    }

    public SystemPolicy getPolicyByTitle(String title) {
        return systemPolicyRepository.findByTitleIgnoreCase(title)
                .orElseThrow(() -> new RuntimeException("System Policy not found with title: " + title));
    }

    public SystemPolicy createPolicy(SystemPolicy policy) {
        if (systemPolicyRepository.findByTitleIgnoreCase(policy.getTitle()).isPresent()) {
            throw new RuntimeException("A policy with this title already exists.");
        }
        return systemPolicyRepository.save(policy);
    }

    public SystemPolicy updatePolicy(Long id, SystemPolicy policyDetails) {
        SystemPolicy existingPolicy = getPolicyById(id);

        // If title changed, check uniqueness
        if (!existingPolicy.getTitle().equalsIgnoreCase(policyDetails.getTitle())) {
            if (systemPolicyRepository.findByTitleIgnoreCase(policyDetails.getTitle()).isPresent()) {
                throw new RuntimeException("A policy with this title already exists.");
            }
        }

        existingPolicy.setTitle(policyDetails.getTitle());
        existingPolicy.setDescription(policyDetails.getDescription());
        existingPolicy.setContent(policyDetails.getContent());

        return systemPolicyRepository.save(existingPolicy);
    }

    public void deletePolicy(Long id) {
        SystemPolicy existingPolicy = getPolicyById(id);
        systemPolicyRepository.delete(existingPolicy);
    }
}
