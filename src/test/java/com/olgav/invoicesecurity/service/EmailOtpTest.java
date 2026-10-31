package com.olgav.invoicesecurity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olgav.invoicesecurity.dto.AuthDtos;
import com.olgav.invoicesecurity.model.EmailVerificationToken;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.*;
import com.olgav.invoicesecurity.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailOtpTest {
    private static final String EMAIL = "otp-user@example.test";
    private static final String OTP_SECRET = "test-only-otp-hmac-secret-32-bytes-minimum";
    private static final String OTP = "482619";

    @Test
    void registrationStoresOnlyUserBoundHmacAndEmailsSixDigitCode() {
        Fixture fixture = new Fixture();
        when(fixture.users.existsByEmail(EMAIL)).thenReturn(false);
        when(fixture.organizations.save(any())).thenAnswer(invocation -> {
            com.olgav.invoicesecurity.model.Organization organization = invocation.getArgument(0);
            organization.setId(8L);
            return organization;
        });
        when(fixture.users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(fixture.user.getId());
            return saved;
        });
        when(fixture.passwords.encode(anyString())).thenReturn("encoded");

        fixture.service.register(new AuthDtos.RegisterRequest(
                "OTP User", EMAIL, "StrongPass123!x", "Example Org"));

        org.mockito.ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                org.mockito.ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(fixture.tokens).save(tokenCaptor.capture());
        org.mockito.ArgumentCaptor<String> otpCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(fixture.email).sendVerificationOtp(eq(EMAIL), otpCaptor.capture(), eq(Duration.ofMinutes(10)));
        String otp = otpCaptor.getValue();
        EmailVerificationToken stored = tokenCaptor.getValue();
        assertTrue(otp.matches("[0-9]{6}"));
        assertEquals(hmac(fixture.user.getId(), otp), stored.getOtpHash());
        assertNotEquals(otp, stored.getOtpHash());
        assertEquals(0, stored.getSendCount());
        assertEquals(0, stored.getAttemptCount());
        assertTrue(stored.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(9)));
    }

    @Test
    void acceptsCorrectOtpAndInvalidatesIt() {
        Fixture fixture = new Fixture();
        EmailVerificationToken token = fixture.token(EMAIL, false);
        token.setOtpHash(hmac(fixture.user.getId(), OTP));
        when(fixture.tokens.findTopByUserIdAndUsedFalseOrderByIdDesc(fixture.user.getId()))
                .thenReturn(Optional.of(token));

        assertDoesNotThrow(() -> fixture.service.verifyEmail(EMAIL, OTP));

        assertTrue(token.isUsed());
        assertTrue(fixture.user.isEmailVerified());
        assertEquals(User.AccountStatus.ACTIVE, fixture.user.getStatus());
        verify(fixture.users).save(fixture.user);
    }

    @Test
    void wrongOtpIncrementsAttemptsAndReturnsGenericCode() {
        Fixture fixture = new Fixture();
        EmailVerificationToken token = fixture.token(EMAIL, false);
        token.setOtpHash(hmac(fixture.user.getId(), OTP));
        when(fixture.tokens.findTopByUserIdAndUsedFalseOrderByIdDesc(fixture.user.getId()))
                .thenReturn(Optional.of(token));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.verifyEmail(EMAIL, "111111"));

        assertEquals("INVALID_OTP", error.getReason());
        assertEquals(1, token.getAttemptCount());
        assertFalse(token.isUsed());
        verify(fixture.audit).record(eq(fixture.user.getId()), any(), eq("EMAIL_OTP_FAILED"),
                eq("users"), eq(fixture.user.getId().toString()), isNull(), any(), any());
    }

    @Test
    void expiredOtpIsInvalidatedWithExpiredCode() {
        Fixture fixture = new Fixture();
        EmailVerificationToken token = fixture.token(EMAIL, false);
        token.setOtpHash(hmac(fixture.user.getId(), OTP));
        token.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(fixture.tokens.findTopByUserIdAndUsedFalseOrderByIdDesc(fixture.user.getId()))
                .thenReturn(Optional.of(token));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.verifyEmail(EMAIL, OTP));

        assertEquals("OTP_EXPIRED", error.getReason());
        assertTrue(token.isUsed());
        verify(fixture.tokens).save(token);
    }

    @Test
    void fifthWrongAttemptInvalidatesOtpAndReturnsAttemptsExceeded() {
        Fixture fixture = new Fixture();
        EmailVerificationToken token = fixture.token(EMAIL, false);
        token.setOtpHash(hmac(fixture.user.getId(), OTP));
        when(fixture.tokens.findTopByUserIdAndUsedFalseOrderByIdDesc(fixture.user.getId()))
                .thenReturn(Optional.of(token));

        for (int attempt = 1; attempt < 5; attempt++) {
            ResponseStatusException wrongOtp = assertThrows(ResponseStatusException.class,
                    () -> fixture.service.verifyEmail(EMAIL, "111111"));
            assertEquals("INVALID_OTP", wrongOtp.getReason());
        }
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.verifyEmail(EMAIL, "111111"));
        assertEquals("OTP_ATTEMPTS_EXCEEDED", error.getReason());
        assertEquals(5, token.getAttemptCount());
        assertTrue(token.isUsed());
        verify(fixture.audit).record(eq(fixture.user.getId()), any(), eq("EMAIL_OTP_ATTEMPTS_EXCEEDED"),
                eq("users"), eq(fixture.user.getId().toString()), isNull(), any(), any());
    }

    @Test
    void resendDuringCooldownReturnsWithoutSendingAnotherCode() {
        Fixture fixture = new Fixture();
        EmailVerificationToken latest = fixture.token(EMAIL, false);
        latest.setLastSentAt(LocalDateTime.now().minusSeconds(30));
        latest.setSendWindowStartedAt(LocalDateTime.now().minusMinutes(2));
        latest.setSendCount(1);
        when(fixture.tokens.findTopByUserIdOrderByIdDesc(fixture.user.getId())).thenReturn(Optional.of(latest));

        assertDoesNotThrow(() -> fixture.service.resendVerification(EMAIL));

        verify(fixture.email, never()).sendVerificationOtp(anyString(), anyString(), any());
        verify(fixture.tokens, never()).save(any(EmailVerificationToken.class));
    }

    @Test
    void resendAfterCooldownInvalidatesOldCodeAndSendsFreshOtp() {
        Fixture fixture = new Fixture();
        EmailVerificationToken latest = fixture.token(EMAIL, false);
        latest.setLastSentAt(LocalDateTime.now().minusSeconds(61));
        latest.setSendWindowStartedAt(LocalDateTime.now().minusMinutes(2));
        latest.setSendCount(1);
        when(fixture.tokens.findTopByUserIdOrderByIdDesc(fixture.user.getId())).thenReturn(Optional.of(latest));

        fixture.service.resendVerification(EMAIL);

        assertTrue(latest.isUsed());
        verify(fixture.tokens).save(latest);
        verify(fixture.tokens).save(argThat(newToken -> newToken != latest
                && newToken.getUser().equals(fixture.user)
                && newToken.getSendCount() == 2
                && newToken.getOtpHash().matches("[0-9a-f]{64}")));
        verify(fixture.email).sendVerificationOtp(eq(EMAIL), matches("[0-9]{6}"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void resendStopsAfterFiveResendsInTheCurrentHour() {
        Fixture fixture = new Fixture();
        EmailVerificationToken latest = fixture.token(EMAIL, false);
        latest.setLastSentAt(LocalDateTime.now().minusSeconds(61));
        latest.setSendWindowStartedAt(LocalDateTime.now().minusMinutes(10));
        latest.setSendCount(5);
        when(fixture.tokens.findTopByUserIdOrderByIdDesc(fixture.user.getId())).thenReturn(Optional.of(latest));

        assertDoesNotThrow(() -> fixture.service.resendVerification(EMAIL));

        verify(fixture.email, never()).sendVerificationOtp(anyString(), anyString(), any());
        verify(fixture.tokens, never()).save(any(EmailVerificationToken.class));
    }

    @Test
    void unverifiedUserCannotLogInOrReceiveTokens() {
        Fixture fixture = new Fixture();
        fixture.user.setEmailVerified(false);
        when(fixture.users.findByEmail(EMAIL)).thenReturn(Optional.of(fixture.user));
        when(fixture.passwords.matches("CorrectPassword", fixture.user.getPasswordHash())).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.login(new AuthDtos.LoginRequest(EMAIL, "CorrectPassword", null),
                        "192.0.2.10", "Test Browser"));

        assertEquals("EMAIL_NOT_VERIFIED", error.getReason());
        verifyNoInteractions(fixture.jwt);
    }

    private static String hmac(long userId, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(OTP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((userId + ":" + otp).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class Fixture {
        private final UserRepository users = mock(UserRepository.class);
        private final OrganizationRepository organizations = mock(OrganizationRepository.class);
        private final OrganizationMemberRepository members = mock(OrganizationMemberRepository.class);
        private final LoginAttemptRepository attempts = mock(LoginAttemptRepository.class);
        private final LoginChallengeRepository challenges = mock(LoginChallengeRepository.class);
        private final TrustedDeviceRepository devices = mock(TrustedDeviceRepository.class);
        private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        private final EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
        private final FaceVerificationService face = mock(FaceVerificationService.class);
        private final RiskScoringService risk = mock(RiskScoringService.class);
        private final PasswordEncoder passwords = mock(PasswordEncoder.class);
        private final JwtService jwt = mock(JwtService.class);
        private final EmailNotificationService email = mock(EmailNotificationService.class);
        private final GeoLocationService geo = mock(GeoLocationService.class);
        private final AuditLoggingService audit = mock(AuditLoggingService.class);
        private final User user = new User();
        private final AuthService service;

        private Fixture() {
            user.setId(77L);
            user.setEmail(EMAIL);
            user.setName("OTP User");
            user.setPasswordHash("stored-password-hash");
            user.setEmailVerified(false);
            user.setStatus(User.AccountStatus.PENDING_VERIFICATION);
            user.setIsActive(true);
            when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            service = new AuthService(users, organizations, members, attempts, challenges, devices,
                    refreshTokens, resetTokens, tokens, face, risk, passwords, jwt, email, geo, audit,
                    new ObjectMapper(), Duration.ofDays(7), Duration.ofMinutes(2), Duration.ofMinutes(15),
                    30, 85, 10, OTP_SECRET, Duration.ofMinutes(10), Duration.ofSeconds(60), 5);
        }

        private EmailVerificationToken token(String email, boolean used) {
            EmailVerificationToken token = new EmailVerificationToken();
            token.setUser(user);
            token.setOtpHash(hmac(user.getId(), OTP));
            token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
            token.setUsed(used);
            token.setCreatedAt(LocalDateTime.now());
            return token;
        }
    }
}
