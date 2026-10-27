package com.vegeai.backend.modules.auth.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.user.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
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

    @Value("${app.mail.from:giflvl123@gmail.com}")
    private String fromAddress;

    @Value("${app.mail.from-name:VEGEAI}")
    private String fromName;

    /**
     * Sends the registration OTP asynchronously. OTP values are never written to logs.
     */
    @Async
    public void sendRegistrationOtp(User user, String otp) {
        String subject = "[VEGEAI] 🌿 Mã xác thực tài khoản của bạn";
        String html = buildOtpEmailHtml(user.getFullName(), otp,
                "kích hoạt tài khoản mới", 3);
        try {
            sendHtml(user.getEmail(), subject, html);
        } catch (Exception ex) {
            log.error("Failed to send registration OTP email to {}: {}", user.getEmail(), ex.getMessage());
        }
    }

    /**
     * Sends the forgot-password OTP. Async.
     */
    @Async
    public void sendForgotPasswordOtp(User user, String otp) {
        String subject = "[VEGEAI] 🔑 Mã đặt lại mật khẩu của bạn";
        String html = buildOtpEmailHtml(user.getFullName(), otp,
                "đặt lại mật khẩu tài khoản", 3);
        try {
            sendHtml(user.getEmail(), subject, html);
        } catch (Exception ex) {
            log.error("Failed to send forgot-password OTP email to {}: {}", user.getEmail(), ex.getMessage());
        }
    }

    /**
     * Sends the login 2FA verification OTP. Async.
     */
    @Async
    public void sendLoginOtp(User user, String otp) {
        String subject = "[VEGEAI] 🔐 Mã xác thực đăng nhập của bạn";
        String html = buildOtpEmailHtml(user.getFullName(), otp,
                "xác thực đăng nhập tài khoản", 3);
        try {
            sendHtml(user.getEmail(), subject, html);
        } catch (Exception ex) {
            log.error("Failed to send login OTP email to {}: {}", user.getEmail(), ex.getMessage());
        }
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

            // Embed authentic project mascot image via CID
            ClassPathResource mascotResource = new ClassPathResource("images/vegeai_mascot.png");
            if (mascotResource.exists()) {
                helper.addInline("vegeaiMascot", mascotResource, "image/png");
            }

            mailSender.send(message);
            log.info("Email sent to: {}, subject: {}", to, subject);
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED,
                    "Failed to send email to " + to + ". " + ex.getMessage());
        }
    }

    private String buildOtpEmailHtml(String name, String otp, String purpose, int ttlMinutes) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>VEGEAI OTP</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #f2f7f3; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;">
                  <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f2f7f3; padding: 10px 8px;">
                    <tr>
                      <td align="center">
                        <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 440px; background-color: #ffffff; border-radius: 18px; box-shadow: 0 4px 20px rgba(27, 67, 50, 0.08); overflow: hidden; border: 1px solid #dceed5;">
                          
                          <!-- Compact Header with Mascot & Brand in one harmonious block -->
                          <tr>
                            <td align="center" style="background: linear-gradient(135deg, #e8f5e9 0%%, #f1f8e9 100%%); padding: 14px 16px 10px 16px; border-bottom: 1px solid #dceed5;">
                              <table border="0" cellspacing="0" cellpadding="0">
                                <tr>
                                  <td align="center">
                                    <table border="0" cellspacing="0" cellpadding="0" style="background-color: #ffffff; border-radius: 50%%; box-shadow: 0 4px 12px rgba(27, 67, 50, 0.12); border: 2px solid #e1eee2;">
                                      <tr>
                                        <td align="center" style="padding: 4px;">
                                          <img src="cid:vegeaiMascot" alt="VEGEAI Mascot" width="56" height="56" style="display: block; border-radius: 50%%;" />
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                                <tr>
                                  <td align="center" style="padding-top: 6px;">
                                    <span style="color: #1b4332; font-size: 19px; font-weight: 800; letter-spacing: 0.5px; display: block; line-height: 1.1;">VEGEAI</span>
                                    <span style="color: #2d6a4f; font-size: 10px; font-weight: 700; letter-spacing: 1.2px; text-transform: uppercase;">MÃ XÁC THỰC BẢO MẬT</span>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <!-- Main Content: Direct & Immediate OTP display -->
                          <tr>
                            <td style="padding: 14px 20px 12px 20px; text-align: center;">
                              <p style="margin: 0 0 2px 0; font-size: 14.5px; color: #2d3748; font-weight: 600;">
                                Xin chào <span style="color: #2d6a4f;">%s</span> 🌱
                              </p>
                              <p style="margin: 0 0 10px 0; font-size: 13px; color: #52796f; line-height: 1.35;">
                                Mã xác nhận để <strong>%s</strong> của bạn:
                              </p>

                              <!-- Ultra-Clean Highlighted OTP Box -->
                              <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background: linear-gradient(135deg, #f0fdf4 0%%, #dcfce7 100%%); border: 2px dashed #4ade80; border-radius: 12px; margin: 0 auto 10px auto;">
                                <tr>
                                  <td align="center" style="padding: 11px 8px;">
                                    <div style="font-family: 'Courier New', Courier, monospace; font-size: 35px; font-weight: 900; letter-spacing: 8px; color: #14532d; padding-left: 8px; line-height: 1.05;">
                                      %s
                                    </div>
                                  </td>
                                </tr>
                              </table>

                              <!-- Badges: Expiry & Security in 1 concise line -->
                              <div style="background-color: #fefce8; border: 1px solid #fef08a; border-radius: 6px; padding: 6px 10px; margin-bottom: 10px; display: inline-block;">
                                <span style="font-size: 11.5px; color: #854d0e; font-weight: 600;">
                                  ⏱️ Hiệu lực <strong>%d phút</strong> &nbsp;•&nbsp; 🔒 Không chia sẻ mã
                                </span>
                              </div>

                              <p style="margin: 0; font-size: 11px; color: #a0aec0; line-height: 1.3;">
                                Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email.
                              </p>
                            </td>
                          </tr>

                          <!-- Minimal Footer -->
                          <tr>
                            <td align="center" style="padding: 7px 16px 10px 16px; background-color: #fafdfa; border-top: 1px solid #edf2f7;">
                              <span style="font-size: 10px; color: #a0aec0;">
                                © 2026 VEGEAI • The Vegan Crocodile Team
                              </span>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(name, purpose, otp, ttlMinutes);
    }
}
