package com.smartuniversity.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send password reset OTP email
     */
    public void sendPasswordResetOTP(String email, String otp, String name) throws MessagingException {
        String subject = "Password Reset OTP for Athena Account";
        String htmlContent = buildPasswordResetEmail(otp, name);
        sendHtmlEmail(email, subject, htmlContent);
    }

    /**
     * Send HTML email
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true); // true = HTML content

        mailSender.send(message);
    }

    /**
     * Build password reset email HTML
     */
    private String buildPasswordResetEmail(String otp, String name) {
        String greeting = name != null && !name.isEmpty() ? " " + name : "";

        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e4e4e4; border-radius: 8px;">
              <div style="text-align: center; margin-bottom: 20px;">
                <h1 style="color: #7C3AED; margin-bottom: 5px;">Athena</h1>
                <p style="color: #5f6368; font-size: 16px;">Password Reset Request</p>
              </div>

              <div style="border-top: 2px solid #f0f0f0; border-bottom: 2px solid #f0f0f0; padding: 20px 0; margin-bottom: 20px;">
                <p style="margin-bottom: 15px;">Hello%s,</p>
                <p style="margin-bottom: 15px;">You requested a password reset for your Athena account.</p>
                <p style="margin-bottom: 15px;">Your One-Time Password (OTP) is:</p>

                <div style="text-align: center; margin: 30px 0;">
                  <div style="display: inline-block; padding: 15px 30px; background-color: #f8f9fa; border: 1px dashed #7C3AED; border-radius: 4px;">
                    <span style="font-size: 24px; font-weight: bold; color: #7C3AED; letter-spacing: 5px;">%s</span>
                  </div>
                </div>

                <p style="margin-bottom: 15px;">This OTP will <strong>expire in 1 hour</strong>.</p>
                <p>Enter this code on the password reset page to create a new password.</p>
              </div>

              <div style="color: #5f6368; font-size: 13px;">
                <p>If you did not request this password reset, please ignore this email or contact support if you have concerns.</p>
                <p style="margin-top: 15px;">© %d Athena - Smart University Companion. All rights reserved.</p>
              </div>
            </div>
        """.formatted(greeting, otp, java.time.Year.now().getValue());
    }
}
