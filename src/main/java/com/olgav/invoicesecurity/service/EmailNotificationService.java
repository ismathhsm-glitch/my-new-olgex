package com.olgav.invoicesecurity.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.Duration;

@Service
public class EmailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);
    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendUrl;

    public EmailNotificationService(JavaMailSender mailSender,
                                    @Value("${SMTP_FROM:noreply@olgax.local}") String from,
                                    @Value("${FRONTEND_URL:http://localhost:5173}") String frontendUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    @Async
    public void sendVerificationOtp(String email, String otp, Duration ttl) {
        String body = "<!doctype html><html><body><h2>Your Olgax Invoice verification code</h2>"
                + "<p>Enter this code to verify your email address:</p><p style=\"font-size:24px;"
                + "font-weight:bold;letter-spacing:4px\">" + escape(otp) + "</p>"
                + "<p>This code expires in " + escape(formatDuration(ttl))
                + ". If you did not create an account, you can ignore this email.</p>"
                + "</body></html>";
        sendWithRetry(email, "Your Olgax Invoice verification code", body);
    }

    @Async
    public void sendPasswordReset(String email, String token) {
        sendWithRetry(email, "Reset your Olgax Invoice password",
                html("Reset password", "Use the link below to reset your password.",
                        frontendUrl + "/reset-password?token=" + token));
    }

    @Async
    public void sendInvitation(String email, String organizationName, String token) {
        sendWithRetry(email, "You were invited to Olgax Invoice",
                html("Organization invitation", "You have been invited to join " + organizationName + ".",
                        frontendUrl + "/accept-invitation?token=" + token));
    }

    @Async
    public void sendLoginAnomaly(String email, String occurredAt, String ipAddress,
                                 String approximateLocation, String deviceBrowser, List<String> riskReasons) {
        String body = "<!doctype html><html><body><h2>Unrecognized sign-in attempt</h2>"
                + "<p>A sign-in from a new device, network, or location requires facial verification.</p>"
                + "<ul><li>Time: " + escape(occurredAt) + "</li>"
                + "<li>IP address: " + escape(ipAddress) + "</li>"
                + "<li>Approximate location: " + escape(approximateLocation) + "</li>"
                + "<li>Device/browser: " + escape(deviceBrowser) + "</li>"
                + "<li>Risk reasons: " + escape(String.join(", ", riskReasons)) + "</li></ul>"
                + "<p>If this wasn&#39;t you, change your password immediately and contact support.</p>"
                + "</body></html>";
        sendWithRetry(email, "Unrecognized sign-in attempt", body);
    }

    @Async
    public void sendAccountLocked(String email, String reason) {
        sendSecurityAlert(email, "Account temporarily locked",
                "Your account was temporarily locked due to " + reason + ".");
    }

    @Async
    public void sendFaceVerificationFailure(String email, String details) {
        sendSecurityAlert(email, "Facial verification failed", details);
    }

    @Async
    public void sendPasswordChanged(String email, String details) {
        sendSecurityAlert(email, "Password changed", details);
    }

    @Async
    public void sendSecurityAlert(String email, String heading, String details) {
        sendWithRetry(email, "Olgax Invoice security alert",
                "<!doctype html><html><body><h2>" + escape(heading) + "</h2><p>"
                        + escape(details) + "</p><p>If this was not you, reset your password immediately.</p></body></html>");
    }

    private void sendWithRetry(String to, String subject, String body) {
        RuntimeException failure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
                helper.setFrom(from);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(body, true);
                mailSender.send(message);
                return;
            } catch (Exception e) {
                failure = new IllegalStateException("Email delivery attempt " + attempt + " failed", e);
            }
        }
        log.error("Email delivery failed after retries for recipient {}", to, failure);
    }

    private static String html(String heading, String message, String url) {
        return "<!doctype html><html><body><h2>" + escape(heading) + "</h2><p>" + escape(message)
                + "</p><p><a href=\"" + escape(url) + "\">Continue</a></p>"
                + "<p>If you did not request this, you can ignore the email.</p></body></html>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String formatDuration(Duration duration) {
        long minutes = Math.max(1, duration.toMinutes());
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
