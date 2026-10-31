package com.olgav.invoicesecurity.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailNotificationServiceTest {
    @Test
    void loginAnomalyEmailIncludesAllRequiredContextAndInstruction() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        EmailNotificationService service = new EmailNotificationService(sender, "sender@example.test",
                "https://frontend.example.test");

        service.sendLoginAnomaly("user@example.test", "2026-10-31T07:00:00Z", "203.0.113.8",
                "Example City, US", "Firefox on Windows", List.of("UNRECOGNIZED_DEVICE", "UNRECOGNIZED_IP"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        String mail = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(mail.contains("2026-10-31T07:00:00Z"));
        assertTrue(mail.contains("203.0.113.8"));
        assertTrue(mail.contains("Example City, US"));
        assertTrue(mail.contains("Firefox on Windows"));
        assertTrue(mail.contains("UNRECOGNIZED_DEVICE, UNRECOGNIZED_IP"));
        assertTrue(mail.contains("If this wasn&#39;t you"));
    }

    @Test
    void mailTransportFailureIsRetriedLoggedAndDoesNotEscape() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(MimeMessage.class));
        EmailNotificationService service = new EmailNotificationService(sender, "sender@example.test",
                "https://frontend.example.test");

        assertDoesNotThrow(() -> service.sendVerificationOtp(
                "user@example.test", "123456", java.time.Duration.ofMinutes(10)));

        verify(sender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void verificationOtpUsesRequiredSubjectAndHtmlCode() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        EmailNotificationService service = new EmailNotificationService(sender, "sender@example.test",
                "https://frontend.example.test");

        service.sendVerificationOtp("user@example.test", "031947", java.time.Duration.ofMinutes(10));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        String mail = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(mail.contains("Subject: Your Olgax Invoice verification code"));
        assertTrue(mail.contains("031947"));
        assertTrue(mail.contains("10 minutes"));
    }
}
