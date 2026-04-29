package com.internregister.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from.address}")
    private String fromAddress;

    @Value("${mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.system.url}")
    private String systemUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendInternInvite(String email, String name, String password) {
        String defaultMessage = String.format(
                "You have been successfully registered as an intern in the Intern Register System.\n\n" +
                        "To access the system, please follow the link below:\n" +
                        "🔗 %s\n\n" +
                        "Your login credentials are:\n" +
                        "📧 Username: %s\n" +
                        "🔑 Password: %s\n\n" +
                        "⚠️ IMPORTANT: For security reasons, please log in and change your password immediately after your first login.",
                systemUrl, email, password);
        sendInternInviteWithCustomMessage(email, name, defaultMessage);
    }

    public void sendInternInviteWithCustomMessage(String email, String name, String messageContent) {
        String subject = "Welcome to the Intern Register System - Action Required";
        String fullMessage = String.format(
                "Dear %s,\n\n" +
                        "%s\n\n" +
                        "Best regards,\n" +
                        "Intern Register System Team",
                name, messageContent);

        sendSimpleMessage(email, subject, fullMessage);
    }

    private void sendSimpleMessage(String to, String subject, String text) {
        if (!mailEnabled) {
            System.out.println("===========================================");
            System.out.println("EMAIL SIMULATION (mail.enabled=false)");
            System.out.println("TO: " + to);
            System.out.println("SUBJECT: " + subject);
            System.out.println("BODY:\n" + text);
            System.out.println("===========================================");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            System.out.println("✓ Invitation email sent to: " + to);
        } catch (Exception e) {
            System.err.println("❌ Failed to send email to " + to + ": " + e.getMessage());
            // Important: We don't throw an exception here because we don't want to
            // roll back the entire import just because one email failed.
        }
    }
}
