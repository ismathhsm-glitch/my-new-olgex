package com.olgav.invoicesecurity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.*;
import com.olgav.invoicesecurity.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    @Test
    void verifiesPasswordUsingPasswordEncoderAndPersistsFailedAttempt() {
        UserRepository users = mock(UserRepository.class);
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationMemberRepository members = mock(OrganizationMemberRepository.class);
        LoginAttemptRepository attempts = mock(LoginAttemptRepository.class);
        LoginChallengeRepository challenges = mock(LoginChallengeRepository.class);
        TrustedDeviceRepository devices = mock(TrustedDeviceRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        EmailVerificationTokenRepository verificationTokens = mock(EmailVerificationTokenRepository.class);
        FaceVerificationService face = mock(FaceVerificationService.class);
        RiskScoringService risk = mock(RiskScoringService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        JwtService jwt = mock(JwtService.class);
        EmailNotificationService email = mock(EmailNotificationService.class);
        GeoLocationService geo = mock(GeoLocationService.class);
        AuditLoggingService audit = mock(AuditLoggingService.class);

        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.test");
        user.setPasswordHash("$2a$12$stored-hash");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.getReferenceById(1L)).thenReturn(user);
        when(passwords.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

        AuthService service = new AuthService(users, organizations, members, attempts, challenges, devices,
                refreshTokens, resetTokens, verificationTokens,
                face, risk, passwords, jwt, email, geo, audit, new ObjectMapper(),
                Duration.ofDays(7), Duration.ofMinutes(2), Duration.ofMinutes(15), 30, 85, 10,
                "test-only-otp-hmac-secret-32-bytes-minimum", Duration.ofMinutes(10),
                Duration.ofSeconds(60), 5);

        assertThrows(ResponseStatusException.class,
                () -> service.login(new com.olgav.invoicesecurity.dto.AuthDtos.LoginRequest(
                        user.getEmail(), "wrong-password", "device"), "127.0.0.1", "test-agent"));

        verify(passwords).matches("wrong-password", user.getPasswordHash());
        verify(attempts).save(any());
        verify(users).save(user);
    }
}
