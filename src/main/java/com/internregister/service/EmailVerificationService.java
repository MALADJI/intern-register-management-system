package com.internregister.service;

import com.internregister.entity.VerificationCode;
import com.internregister.repository.VerificationCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
public class EmailVerificationService {

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @org.springframework.beans.factory.annotation.Value("${verification.code.expiration.minutes:1}")
    private int expirationMinutes;

    private final Random random = new Random();

    // Store verification code for email in database
    @Transactional
    public String generateAndStoreCode(String email) {
        // Delete existing code for this email if any
        verificationCodeRepository.deleteByEmail(email);

        // Generate 6-digit code
        String code = String.format("%06d", random.nextInt(1000000));

        // Create and save verification code entity
        VerificationCode verificationCode = new VerificationCode();
        verificationCode.setEmail(email);
        verificationCode.setCode(code);
        verificationCode.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes)); // Expires in configured
                                                                                           // minutes (default 1)

        verificationCodeRepository.save(verificationCode);

        // Log the code (in production, send actual email)
        System.out.println("===========================================");
        System.out.println("VERIFICATION CODE FOR: " + email);
        System.out.println("CODE: " + code);
        System.out.println("CODE SAVED TO DATABASE");
        System.out.println("===========================================");
        System.out.println("NOTE: In production, this code would be sent via email.");
        System.out.println("For now, check the console/logs for the code.");
        System.out.println("===========================================");

        return code;
    }

    // Verify code from database
    @Transactional
    public boolean verifyCode(String email, String code) {
        System.out.println("🔍 Verifying code for email: " + email);
        System.out.println("🔍 Code to verify: '" + code + "' (length: " + code.length() + ")");

        Optional<VerificationCode> verificationCodeOpt = verificationCodeRepository.findByEmailAndCode(email, code).stream().findFirst();

        if (verificationCodeOpt.isEmpty()) {
            System.out.println("❌ No verification code found in database for email: " + email);
            // Try to find by email only to see if there's a code with different value
            Optional<VerificationCode> byEmailOnly = verificationCodeRepository.findByEmail(email).stream().findFirst();
            if (byEmailOnly.isPresent()) {
                VerificationCode found = byEmailOnly.get();
                System.out.println("⚠️ Found code in DB for this email, but value doesn't match:");
                System.out.println("   DB code: '" + found.getCode() + "' (length: " + found.getCode().length() + ")");
                System.out.println("   Provided code: '" + code + "' (length: " + code.length() + ")");
                System.out.println("   Codes match: " + found.getCode().equals(code));
                System.out.println("   Code expired: " + found.isExpired());
            } else {
                System.out.println("⚠️ No verification code found at all for email: " + email);
            }
            return false;
        }

        VerificationCode verificationCode = verificationCodeOpt.get();
        System.out.println("✅ Found verification code in database");
        System.out.println("   Code: '" + verificationCode.getCode() + "'");
        System.out.println("   Expires at: " + verificationCode.getExpiresAt());
        System.out.println("   Current time: " + LocalDateTime.now());

        // Check if code is expired
        if (verificationCode.isExpired()) {
            System.out.println("❌ Code has expired");
            verificationCodeRepository.delete(verificationCode);
            return false;
        }

        System.out.println("✅ Code is valid and not expired");
        // Code is valid - delete it (one-time use)
        verificationCodeRepository.delete(verificationCode);
        System.out.println("✅ Code deleted after successful verification (one-time use)");
        return true;
    }

    // Remove expired codes (cleanup job - runs every hour)
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    public void cleanupExpiredCodes() {
        verificationCodeRepository.deleteExpiredCodes(LocalDateTime.now());
    }

    // Remove code for specific email
    @Transactional
    public void removeCode(String email) {
        verificationCodeRepository.deleteByEmail(email);
    }
}
