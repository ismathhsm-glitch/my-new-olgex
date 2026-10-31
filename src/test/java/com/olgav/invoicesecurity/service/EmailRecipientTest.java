package com.olgav.invoicesecurity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olgav.invoicesecurity.dto.AuthDtos;
import com.olgav.invoicesecurity.dto.OrganizationDtos;
import com.olgav.invoicesecurity.model.*;
import com.olgav.invoicesecurity.repository.*;
import com.olgav.invoicesecurity.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailRecipientTest {
    private static final String USER_EMAIL = "account-owner@example.test";

    @Test
    void registrationVerificationUsesSavedUserEmail() {
        AuthFixture fixture = new AuthFixture();
        when(fixture.users.existsByEmail(USER_EMAIL)).thenReturn(false);
        when(fixture.organizations.save(any(Organization.class))).thenAnswer(invocation -> {
            Organization organization = invocation.getArgument(0);
            organization.setId(10L);
            return organization;
        });
        when(fixture.users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        fixture.service.register(new AuthDtos.RegisterRequest(
                "Account Owner", USER_EMAIL, "StrongPass123!x", "Acme"));

        verify(fixture.email).sendVerificationOtp(eq(USER_EMAIL), anyString(), eq(Duration.ofMinutes(10)));
    }

    @Test
    void loginAnomalyUsesDatabaseUserEmail() {
        AuthFixture fixture = new AuthFixture();
        fixture.user.setEmail(USER_EMAIL);
        when(fixture.users.findByEmail(USER_EMAIL)).thenReturn(Optional.of(fixture.user));
        when(fixture.passwords.matches(anyString(), anyString())).thenReturn(true);
        when(fixture.risk.score(fixture.user, "device-hash", "203.0.113.8", "Test Browser"))
                .thenReturn(new RiskScoringService.RiskScore(45, List.of("UNRECOGNIZED_DEVICE")));
        when(fixture.face.isEnrolled(fixture.user.getId())).thenReturn(true);
        when(fixture.geo.locate("203.0.113.8")).thenReturn(new GeoLocationService.Location("US", "Example City"));

        fixture.service.login(new AuthDtos.LoginRequest(USER_EMAIL, "password", "device-hash"),
                "203.0.113.8", "Test Browser");

        verify(fixture.email).sendLoginAnomaly(eq(USER_EMAIL), anyString(), eq("203.0.113.8"),
                eq("Example City, US"), eq("Test Browser"), eq(List.of("UNRECOGNIZED_DEVICE")));
    }

    @Test
    void accountLockUsesDatabaseUserEmail() {
        AuthFixture fixture = new AuthFixture();
        when(fixture.users.findByEmail(USER_EMAIL)).thenReturn(Optional.of(fixture.user));
        when(fixture.passwords.matches(anyString(), anyString())).thenReturn(true);
        when(fixture.risk.score(any(), anyString(), anyString(), anyString()))
                .thenReturn(new RiskScoringService.RiskScore(90, List.of("IMPOSSIBLE_TRAVEL")));

        assertThrows(ResponseStatusException.class, () -> fixture.service.login(
                new AuthDtos.LoginRequest(USER_EMAIL, "password", "device-hash"), "203.0.113.8", "Test Browser"));

        verify(fixture.email).sendAccountLocked(eq(USER_EMAIL), anyString());
    }

    @Test
    void faceVerificationFailureUsesChallengeUsersEmail() {
        AuthFixture fixture = new AuthFixture();
        LoginChallenge challenge = new LoginChallenge();
        challenge.setId("challenge-id");
        challenge.setUser(fixture.user);
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(1));
        challenge.setNonceHash("stored-nonce-hash");
        when(fixture.challenges.findById("challenge-id")).thenReturn(Optional.of(challenge));
        when(fixture.challenges.findForUpdate("challenge-id", fixture.user.getId())).thenReturn(Optional.of(challenge));

        assertThrows(ResponseStatusException.class, () -> fixture.service.verifyLoginFace(
                new AuthDtos.FaceVerifyRequest("challenge-id", "invalid-nonce", "invalid-time", null),
                "203.0.113.8"));

        verify(fixture.email).sendFaceVerificationFailure(eq(USER_EMAIL), anyString());
    }

    @Test
    void passwordResetEmailUsesDatabaseUserEmail() {
        AuthFixture fixture = new AuthFixture();
        when(fixture.users.findByEmail(USER_EMAIL)).thenReturn(Optional.of(fixture.user));

        fixture.service.forgotPassword(USER_EMAIL);

        verify(fixture.email).sendPasswordReset(eq(USER_EMAIL), anyString());
    }

    @Test
    void passwordChangedEmailAfterResetUsesTokenUsersEmail() {
        AuthFixture fixture = new AuthFixture();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(fixture.user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(fixture.resetTokens.findUnusedForUpdate(anyString())).thenReturn(Optional.of(token));
        when(fixture.passwords.encode(anyString())).thenReturn("encoded");
        when(fixture.refreshTokens.findByUserIdAndRevokedFalse(fixture.user.getId())).thenReturn(List.of());

        fixture.service.resetPassword(new AuthDtos.ResetPasswordRequest("reset-token", "NewStrongPass123!"));

        verify(fixture.email).sendPasswordChanged(eq(USER_EMAIL), anyString());
    }

    @Test
    void passwordChangedEmailAfterPasswordChangeUsesDatabaseUserEmail() {
        AuthFixture fixture = new AuthFixture();
        when(fixture.passwords.matches("CurrentPass123!", fixture.user.getPasswordHash())).thenReturn(true);
        when(fixture.passwords.encode("NewStrongPass123!")).thenReturn("encoded");
        when(fixture.refreshTokens.findByUserIdAndRevokedFalse(fixture.user.getId())).thenReturn(List.of());

        fixture.service.changePassword(fixture.user.getId(),
                new AuthDtos.ChangePasswordRequest("CurrentPass123!", "NewStrongPass123!"));

        verify(fixture.email).sendPasswordChanged(eq(USER_EMAIL), anyString());
    }

    @Test
    void organizationInvitationAndNewUserVerificationUseSavedInviteeEmail() {
        Organization organization = new Organization();
        organization.setId(30L);
        organization.setName("Acme");
        User administrator = new User();
        administrator.setId(40L);
        administrator.setEmail("administrator@example.test");
        administrator.setOrganization(organization);
        administrator.setSystemRole(User.SystemRole.USER);
        User invitee = new User();
        invitee.setId(50L);
        invitee.setEmail(USER_EMAIL);

        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationMemberRepository members = mock(OrganizationMemberRepository.class);
        UserRepository users = mock(UserRepository.class);
        OrganizationInvitationTokenRepository invitations = mock(OrganizationInvitationTokenRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        EmailNotificationService email = mock(EmailNotificationService.class);
        AuditLoggingService audit = mock(AuditLoggingService.class);
        AuthService authService = mock(AuthService.class);
        OrganizationService service = new OrganizationService(organizations, members, users, invitations,
                passwords, email, authService, audit);

        OrganizationMember adminMembership = new OrganizationMember();
        adminMembership.setUser(administrator);
        adminMembership.setOrganization(organization);
        adminMembership.setRole(OrgRole.ORG_ADMIN);
        adminMembership.setStatus("ACTIVE");
        when(users.findById(administrator.getId())).thenReturn(Optional.of(administrator));
        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(members.findByUserIdAndOrganizationId(administrator.getId(), organization.getId()))
                .thenReturn(Optional.of(adminMembership));
        when(users.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());
        when(passwords.encode(anyString())).thenReturn("encoded");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(invitee.getId());
            return saved;
        });
        when(members.findByUserIdAndOrganizationId(invitee.getId(), organization.getId()))
                .thenReturn(Optional.empty());
        when(members.save(any(OrganizationMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.invite(administrator.getId(),
                new OrganizationDtos.InviteMemberRequest(USER_EMAIL, "Invitee", OrgRole.VIEWER));

        verify(authService).sendVerificationCode(argThat(user -> USER_EMAIL.equals(user.getEmail())));
        verify(email).sendInvitation(eq(invitee.getEmail()), eq(organization.getName()), anyString());
    }

    private static final class AuthFixture {
        private final UserRepository users = mock(UserRepository.class);
        private final OrganizationRepository organizations = mock(OrganizationRepository.class);
        private final OrganizationMemberRepository members = mock(OrganizationMemberRepository.class);
        private final LoginAttemptRepository attempts = mock(LoginAttemptRepository.class);
        private final LoginChallengeRepository challenges = mock(LoginChallengeRepository.class);
        private final TrustedDeviceRepository devices = mock(TrustedDeviceRepository.class);
        private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        private final EmailVerificationTokenRepository verificationTokens = mock(EmailVerificationTokenRepository.class);
        private final FaceVerificationService face = mock(FaceVerificationService.class);
        private final RiskScoringService risk = mock(RiskScoringService.class);
        private final PasswordEncoder passwords = mock(PasswordEncoder.class);
        private final JwtService jwt = mock(JwtService.class);
        private final EmailNotificationService email = mock(EmailNotificationService.class);
        private final GeoLocationService geo = mock(GeoLocationService.class);
        private final AuditLoggingService audit = mock(AuditLoggingService.class);
        private final User user = new User();
        private final AuthService service;

        private AuthFixture() {
            Organization organization = new Organization();
            organization.setId(10L);
            user.setId(20L);
            user.setEmail(USER_EMAIL);
            user.setName("Account Owner");
            user.setPasswordHash("stored-password-hash");
            user.setOrganization(organization);
            user.setEmailVerified(true);
            user.setStatus(User.AccountStatus.ACTIVE);
            user.setIsActive(true);
            user.setSystemRole(User.SystemRole.USER);
            user.setRole(User.Role.ORG_ADMIN);
            when(users.getReferenceById(user.getId())).thenReturn(user);
            when(users.findById(user.getId())).thenReturn(Optional.of(user));
            when(members.findByUserIdAndOrganizationId(user.getId(), organization.getId()))
                    .thenReturn(Optional.empty());
            service = new AuthService(users, organizations, members, attempts, challenges, devices,
                    refreshTokens, resetTokens, verificationTokens, face, risk, passwords, jwt, email,
                    geo, audit, new ObjectMapper(), Duration.ofDays(7), Duration.ofMinutes(2),
                    Duration.ofMinutes(15), 30, 85, 10, "test-only-otp-hmac-secret-32-bytes-minimum",
                    Duration.ofMinutes(10), Duration.ofSeconds(60), 5);
        }
    }
}
