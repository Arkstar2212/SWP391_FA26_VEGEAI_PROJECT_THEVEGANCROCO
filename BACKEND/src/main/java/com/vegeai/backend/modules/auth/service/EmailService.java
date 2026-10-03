package com.vegeai.backend.modules.auth.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.user.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async email service for OTP delivery.
 * Sends emails in a separate thread to avoid blocking the request thread.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name:VEGEAI}")
    private String fromName;

    /**
     * Sends the registration OTP. Async — failures throw EMAIL_SEND_FAILED.
     */
    @Async
    public void sendRegistrationOtp(User user, String otp) {
        String subject = "[VEGEAI] Verify Your Email";
        String html = buildOtpEmailHtml(user.getFullName(), otp,
                "activate your account", 3);
        sendHtml(user.getEmail(), subject, html);
    }

    /**
     * Sends the forgot-password OTP. Async.
     */
    @Async
    public void sendForgotPasswordOtp(User user, String otp) {
        String subject = "[VEGEAI] Reset Your Password";
        String html = buildOtpEmailHtml(user.getFullName(), otp,
                "reset your password", 3);
        sendHtml(user.getEmail(), subject, html);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to: {}, subject: {}", to, subject);
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED,
                    "Failed to send email to " + to + ". " + ex.getMessage());
        }
    }

    private String buildOtpEmailHtml(String name, String otp, String purpose, int ttlMinutes) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background: #f4f4f4; padding: 20px;">
                  <div style="max-width:500px; margin:auto; background:#fff; border-radius:8px; padding:30px;">
                    <h2 style="color:#2e7d32;">🌿 VEGEAI</h2>
                    <p>Hi <strong>%s</strong>,</p>
                    <p>Use the following OTP to <strong>%s</strong>. It expires in <strong>%d minutes</strong>.</p>
                    <div style="text-align:center; margin:30px 0;">
                      <span style="font-size:36px; font-weight:bold; letter-spacing:10px; color:#2e7d32;">%s</span>
                    </div>
                    <p style="color:#888; font-size:12px;">
                      If you did not request this, please ignore this email.
                      Do not share this code with anyone.
                    </p>
                    <hr/>
                    <p style="color:#888; font-size:11px;">VEGEAI – Vegan & Vegetarian Support Platform</p>
                  </div>
                </body>
                </html>
                """.formatted(name, purpose, ttlMinutes, otp);
    }
}
