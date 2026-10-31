package com.olgav.invoicesecurity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olgav.invoicesecurity.dto.AuthDtos;
import com.olgav.invoicesecurity.model.*;
import com.olgav.invoicesecurity.repository.*;
import com.olgav.invoicesecurity.security.JwtService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final LoginChallengeRepository loginChallengeRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final FaceVerificationService faceVerificationService;
    private final RiskScoringService riskScoringService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailNotificationService emailNotificationService;
    private final GeoLocationService geoLocationService;
    private final AuditLoggingService auditLoggingService;
    private final ObjectMapper objectMapper;
    private final Duration refreshTtl;
    private final Duration challengeTtl;
    private final Duration lockDuration;
    private final Duration emailOtpTtl;
    private final Duration emailOtpCooldown;
    private final String otpHmacSecret;
    private final int emailOtpMaxSendsPerHour;
    private final int mediumRiskThreshold;
    private final int lockRiskThreshold;
    private final int maxFailures;
    private final int faceRequiredAfterFailures;
    private final ConcurrentHashMap<String, EmailRateWindow> emailRateWindows = new ConcurrentHashMap<>();

    public AuthService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository organizationMemberRepository,
            LoginAttemptRepository loginAttemptRepository,
            LoginChallengeRepository loginChallengeRepository,
            TrustedDeviceRepository trustedDeviceRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            FaceVerificationService faceVerificationService,
            RiskScoringService riskScoringService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailNotificationService emailNotificationService,
            GeoLocationService geoLocationService,
            AuditLoggingService auditLoggingService,
            ObjectMapper objectMapper,
            @Value("${app.jwt.refresh-ttl:P7D}") Duration refreshTtl,
            @Value("${app.security.face.nonce-ttl:PT2M}") Duration challengeTtl,
            @Value("${app.security.risk.lock-duration:PT15M}") Duration lockDuration,
            @Value("${app.security.risk.medium-threshold:30}") int mediumRiskThreshold,
            @Value("${app.security.risk.lock-threshold:85}") int lockRiskThreshold,
            @Value("${app.security.risk.max-failures:10}") int maxFailures,
            @Value("${app.security.email-otp.hmac-secret:}") String otpHmacSecret,
            @Value("${app.security.email-otp.ttl:PT10M}") Duration emailOtpTtl,
            @Value("${app.security.email-otp.cooldown:PT60S}") Duration emailOtpCooldown,
            @Value("${app.security.email-otp.max-per-hour:5}") int emailOtpMaxSendsPerHour) {
        this(userRepository, organizationRepository, organizationMemberRepository, loginAttemptRepository,
                loginChallengeRepository, trustedDeviceRepository, refreshTokenRepository,
                passwordResetTokenRepository, emailVerificationTokenRepository, faceVerificationService,
                riskScoringService, passwordEncoder, jwtService, emailNotificationService,
                geoLocationService, auditLoggingService, objectMapper, refreshTtl, challengeTtl,
                lockDuration, mediumRiskThreshold, lockRiskThreshold, maxFailures, otpHmacSecret,
                emailOtpTtl, emailOtpCooldown, emailOtpMaxSendsPerHour, 5);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository organizationMemberRepository,
            LoginAttemptRepository loginAttemptRepository,
            LoginChallengeRepository loginChallengeRepository,
            TrustedDeviceRepository trustedDeviceRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            FaceVerificationService faceVerificationService,
            RiskScoringService riskScoringService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailNotificationService emailNotificationService,
            GeoLocationService geoLocationService,
            AuditLoggingService auditLoggingService,
            ObjectMapper objectMapper,
            @Value("${app.jwt.refresh-ttl:P7D}") Duration refreshTtl,
            @Value("${app.security.face.nonce-ttl:PT2M}") Duration challengeTtl,
            @Value("${app.security.risk.lock-duration:PT15M}") Duration lockDuration,
            @Value("${app.security.risk.medium-threshold:30}") int mediumRiskThreshold,
            @Value("${app.security.risk.lock-threshold:85}") int lockRiskThreshold,
            @Value("${app.security.risk.max-failures:10}") int maxFailures,
            @Value("${app.security.email-otp.hmac-secret:}") String otpHmacSecret,
            @Value("${app.security.email-otp.ttl:PT10M}") Duration emailOtpTtl,
            @Value("${app.security.email-otp.cooldown:PT60S}") Duration emailOtpCooldown,
            @Value("${app.security.email-otp.max-per-hour:5}") int emailOtpMaxSendsPerHour,
            @Value("${app.security.login.face-required-after-failures:5}") int faceRequiredAfterFailures) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.loginChallengeRepository = loginChallengeRepository;
        this.trustedDeviceRepository = trustedDeviceRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.faceVerificationService = faceVerificationService;
        this.riskScoringService = riskScoringService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailNotificationService = emailNotificationService;
        this.geoLocationService = geoLocationService;
        this.auditLoggingService = auditLoggingService;
        this.objectMapper = objectMapper;
        this.refreshTtl = refreshTtl;
        this.challengeTtl = challengeTtl;
        this.lockDuration = lockDuration;
        this.otpHmacSecret = otpHmacSecret;
        this.emailOtpTtl = emailOtpTtl;
        this.emailOtpCooldown = emailOtpCooldown;
        this.emailOtpMaxSendsPerHour = emailOtpMaxSendsPerHour;
        this.mediumRiskThreshold = mediumRiskThreshold;
        this.lockRiskThreshold = lockRiskThreshold;
        this.maxFailures = maxFailures;
        this.faceRequiredAfterFailures = faceRequiredAfterFailures;
    }

    @Transactional
    public void register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email address is already registered");
        }
        Organization organization = new Organization();
        organization.setName(request.organizationName());
        organization.setEmail("org-" + UUID.randomUUID() + "@olgax.local");
        organization.setCurrency("USD");
        organization = organizationRepository.save(organization);

        User user = new User();
        user.setOrganization(organization);
        user.setName(request.fullName());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(User.Role.ORG_ADMIN);
        user.setSystemRole(User.SystemRole.USER);
        user.setStatus(User.AccountStatus.PENDING_VERIFICATION);
        user.setEmailVerified(false);
        user.setIsActive(true);
        user = userRepository.save(user);

        OrganizationMember member = new OrganizationMember();
        member.setUser(user);
        member.setOrganization(organization);
        member.setRole(OrgRole.ORG_ADMIN);
        member.setStatus("ACTIVE");
        organizationMemberRepository.save(member);
        organization.setAdminUserId(user.getId());
        organizationRepository.save(organization);

        createAndSendVerificationOtp(user, LocalDateTime.now(), 0);
        audit(user.getId(), organization.getId(), "AUTH_REGISTER", "users", user.getId().toString(), null);
    }

    @Transactional
    public void sendVerificationCode(User user) {
        createAndSendVerificationOtp(user, LocalDateTime.now(), 0);
    }

    @Transactional(dontRollbackOn = ResponseStatusException.class)
    public void verifyEmail(String email, String otp) {
        String normalizedEmail = normalizeEmail(email);
        if (!allowEmailRequest(normalizedEmail, "verify")) {
            throw otpError(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
        }
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        long userId = user == null ? -1L : user.getId();
        String candidateHash = otpHmac(userId, otp);
        EmailVerificationToken stored = user == null ? null
                : emailVerificationTokenRepository.findTopByUserIdAndUsedFalseOrderByIdDesc(user.getId()).orElse(null);
        String expectedHash = stored == null ? "0".repeat(64) : stored.getOtpHash();
        boolean matches = MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                candidateHash.getBytes(StandardCharsets.US_ASCII));
        if (stored == null) {
            auditOtpFailure(user);
            throw otpError(HttpStatus.BAD_REQUEST, "INVALID_OTP");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            stored.setUsed(true);
            emailVerificationTokenRepository.save(stored);
            auditOtp(user, "EMAIL_OTP_FAILED");
            throw otpError(HttpStatus.BAD_REQUEST, "OTP_EXPIRED");
        }
        if (!matches) {
            int attempts = stored.getAttemptCount() + 1;
            stored.setAttemptCount(attempts);
            if (attempts >= 5) {
                stored.setUsed(true);
                emailVerificationTokenRepository.save(stored);
                auditOtp(user, "EMAIL_OTP_ATTEMPTS_EXCEEDED");
                throw otpError(HttpStatus.BAD_REQUEST, "OTP_ATTEMPTS_EXCEEDED");
            }
            emailVerificationTokenRepository.save(stored);
            auditOtp(user, "EMAIL_OTP_FAILED");
            throw otpError(HttpStatus.BAD_REQUEST, "INVALID_OTP");
        }
        stored.setUsed(true);
        emailVerificationTokenRepository.save(stored);
        user.setEmailVerified(true);
        if (user.getStatus() == User.AccountStatus.PENDING_VERIFICATION) {
            user.setStatus(User.AccountStatus.ACTIVE);
        }
        userRepository.save(user);
        auditOtp(user, "EMAIL_OTP_VERIFIED");
    }

    @Transactional
    public void resendVerification(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!allowEmailRequest(normalizedEmail, "resend")) {
            return;
        }
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || user.isEmailVerified()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        EmailVerificationToken latest = emailVerificationTokenRepository
                .findTopByUserIdOrderByIdDesc(user.getId()).orElse(null);
        LocalDateTime windowStartedAt = latest == null ? now : latest.getSendWindowStartedAt();
        int sendCount = latest == null ? 0 : latest.getSendCount();
        if (latest != null && latest.getLastSentAt() != null
                && latest.getLastSentAt().plus(emailOtpCooldown).isAfter(now)) {
            return;
        }
        if (windowStartedAt == null || !windowStartedAt.plusHours(1).isAfter(now)) {
            windowStartedAt = now;
            sendCount = 0;
        }
        if (sendCount >= emailOtpMaxSendsPerHour) {
            return;
        }
        if (latest != null && !latest.isUsed()) {
            latest.setUsed(true);
            emailVerificationTokenRepository.save(latest);
        }
        createAndSendVerificationOtp(user, windowStartedAt, sendCount + 1);
    }

    @Transactional(dontRollbackOn = ResponseStatusException.class)
    public AuthDtos.AuthResult login(AuthDtos.LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase()).orElse(null);
        if (user == null) {
            recordAttempt(request.email(), null, ipAddress, false, "INVALID_CREDENTIALS");
            audit(null, null, "LOGIN_FAILED", "users", null, null);
            throw unauthorized();
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked");
        }
        if (user.getStatus() == User.AccountStatus.LOCKED) {
            user.setStatus(User.AccountStatus.ACTIVE);
            user.setLockedUntil(null);
            user.setFailedAttempts(0);
            userRepository.save(user);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            failLogin(user, ipAddress);
            throw unauthorized();
        }
        if (!user.isEmailVerified()) {
            throw otpError(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");
        }
        if (user.getStatus() == User.AccountStatus.DISABLED || !Boolean.TRUE.equals(user.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled");
        }
        boolean hasFace = user.isFaceEnrolled() || faceVerificationService.isEnrolled(user.getId());
        if (hasFace && user.getFailedAttempts() >= faceRequiredAfterFailures) {
            List<String> reasons = new ArrayList<>(List.of("REPEATED_FAILED_ATTEMPTS"));
            RiskScoringService.RiskScore risk = new RiskScoringService.RiskScore(90, reasons);
            ChallengeIssue issued = createChallenge(user, request.deviceHash(), ipAddress, risk);
            recordAttempt(user.getEmail(), user.getId(), ipAddress, true, "FACE_VERIFICATION_REQUIRED",
                    risk.score(), risk.reasons(), request.deviceHash());
            auditLoggingService.record(user.getId(), organizationId(user), "LOGIN_CHALLENGE_CREATED",
                    "users", user.getId().toString(), risk.score(), VerificationResult.NOT_REQUIRED,
                    writeJson(risk.reasons()));
            return AuthDtos.AuthResult.faceChallenge(issued.challenge().getId(), issued.nonce(),
                    risk.score(), risk.reasons());
        }
        if (!hasFace) {
            RiskScoringService.RiskScore risk = riskScoringService.score(
                    user, request.deviceHash(), ipAddress, userAgent);
            if (risk.score() >= lockRiskThreshold) {
                recordAttempt(user.getEmail(), user.getId(), ipAddress, false,
                        "HIGH_RISK_LOGIN", risk.score(), risk.reasons(), request.deviceHash());
                lockAccount(user, ipAddress, "HIGH_RISK_LOGIN");
                throw new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked");
            }
            List<String> reasons = new ArrayList<>(risk.reasons());
            reasons.add("NO_FACE_ENROLLMENT");
            recordAttempt(user.getEmail(), user.getId(), ipAddress, true, "SUCCESS",
                    Math.min(30, risk.score()), reasons, request.deviceHash());
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
            rememberDevice(user, request.deviceHash(), ipAddress, userAgent, false);
            audit(user.getId(), organizationId(user), "LOGIN_SUCCEEDED", "users", user.getId().toString(), Math.min(30, risk.score()));
            AuthDtos.AuthResult tokens = issueTokens(user);
            return AuthDtos.AuthResult.noFace(tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds());
        }

        RiskScoringService.RiskScore risk = riskScoringService.score(
                user, request.deviceHash(), ipAddress, userAgent);
        if (risk.score() >= lockRiskThreshold) {
            recordAttempt(user.getEmail(), user.getId(), ipAddress, false,
                    "HIGH_RISK_LOGIN", risk.score(), risk.reasons(), request.deviceHash());
            lockAccount(user, ipAddress, "HIGH_RISK_LOGIN");
            throw new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked");
        }
        if (risk.score() >= mediumRiskThreshold) {
            if (!faceVerificationService.isEnrolled(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Face enrollment is required before high-risk sign-ins");
            }
            ChallengeIssue issued = createChallenge(user, request.deviceHash(), ipAddress, risk);
            recordAttempt(user.getEmail(), user.getId(), ipAddress, true, "FACE_VERIFICATION_REQUIRED",
                    risk.score(), risk.reasons(), request.deviceHash());
            if (!risk.reasons().isEmpty()) {
                GeoLocationService.Location location = geoLocationService.locate(ipAddress);
                emailNotificationService.sendLoginAnomaly(user.getEmail(), Instant.now().atOffset(ZoneOffset.UTC).toString(),
                        ipAddress, location == null ? "Unknown" : location.displayName(),
                        userAgent == null || userAgent.isBlank() ? "Unknown" : userAgent, risk.reasons());
            }
            auditLoggingService.record(user.getId(), organizationId(user), "LOGIN_CHALLENGE_CREATED",
                    "users", user.getId().toString(), risk.score(), VerificationResult.NOT_REQUIRED,
                    writeJson(risk.reasons()));
            return AuthDtos.AuthResult.faceChallenge(issued.challenge().getId(), issued.nonce(),
                    risk.score(), risk.reasons());
        }

        recordAttempt(user.getEmail(), user.getId(), ipAddress, true, "SUCCESS",
                risk.score(), risk.reasons(), request.deviceHash());
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        audit(user.getId(), organizationId(user), "LOGIN_SUCCEEDED", "users", user.getId().toString(), risk.score());
        rememberDevice(user, request.deviceHash(), ipAddress, userAgent, false);
        return issueTokens(user);
    }

    @Transactional(dontRollbackOn = ResponseStatusException.class)
    public AuthDtos.AuthResult verifyLoginFace(AuthDtos.FaceVerifyRequest request, String ipAddress) {
        LoginChallenge challenge = loginChallengeRepository.findForUpdate(
                request.challengeId(), findChallengeUserId(request.challengeId())).orElseThrow(this::invalidToken);
        if (challenge.isUsed() || challenge.getAttemptCount() >= 5
                || challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw invalidToken();
        }
        if (!validTimestamp(request.timestamp())
                || !constantTimeEquals(challenge.getNonceHash(), hash(request.nonce()))) {
            failFaceAttempt(challenge, ipAddress);
            audit(challenge.getUser().getId(), organizationId(challenge.getUser()), "LOGIN_FACE_FAILED",
                    "users", challenge.getUser().getId().toString(), challenge.getRiskScore());
            emailNotificationService.sendFaceVerificationFailure(challenge.getUser().getEmail(),
                    "A sign-in facial verification request was invalid or expired.");
            throw unauthorized();
        }
        boolean verified = faceVerificationService.verify(challenge.getUser(), request.embedding());
        if (!verified) {
            failFaceAttempt(challenge, ipAddress);
            audit(challenge.getUser().getId(), organizationId(challenge.getUser()), "LOGIN_FACE_FAILED",
                    "users", challenge.getUser().getId().toString(), challenge.getRiskScore());
            emailNotificationService.sendFaceVerificationFailure(challenge.getUser().getEmail(),
                    "A sign-in facial verification attempt was unsuccessful.");
            throw unauthorized();
        }
        challenge.setUsed(true);
        loginChallengeRepository.save(challenge);
        User user = challenge.getUser();
        user.setFailedAttempts(0);
        userRepository.save(user);
        recordAttempt(user.getEmail(), user.getId(), ipAddress, true, "FACE_VERIFIED");
        rememberDevice(user, challenge.getDeviceHash(), ipAddress, null, false);
        audit(user.getId(), organizationId(user), "LOGIN_FACE_VERIFIED", "users", user.getId().toString(), challenge.getRiskScore());
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResult refresh(String rawToken) {
        String tokenHash = hash(rawToken);
        RefreshToken stored = refreshTokenRepository.findActiveForUpdate(tokenHash).orElseThrow(this::invalidToken);
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) throw invalidToken();
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        User user = stored.getUser();
        if (!user.isEmailVerified() || user.getStatus() != User.AccountStatus.ACTIVE
                || !Boolean.TRUE.equals(user.getIsActive())) throw invalidToken();
        audit(user.getId(), organizationId(user), "TOKEN_REFRESHED", "users", user.getId().toString(), null);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken, Long userId) {
        RefreshToken token = refreshTokenRepository.findActiveForUpdate(hash(rawRefreshToken))
                .filter(candidate -> candidate.getUser().getId().equals(userId))
                .orElseThrow(this::invalidToken);
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        User user = userRepository.findById(userId).orElse(null);
        audit(userId, user == null ? null : organizationId(user), "LOGOUT", "users", userId.toString(), null);
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            String token = opaqueToken();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setTokenHash(hash(token));
            resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(30));
            passwordResetTokenRepository.save(resetToken);
            emailNotificationService.sendPasswordReset(user.getEmail(), token);
        });
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        PasswordResetToken stored = passwordResetTokenRepository.findUnusedForUpdate(hash(request.token()))
                .orElseThrow(this::invalidToken);
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) throw invalidToken();
        User user = stored.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == User.AccountStatus.LOCKED) {
            user.setStatus(User.AccountStatus.ACTIVE);
        }
        userRepository.save(user);
        stored.setUsed(true);
        passwordResetTokenRepository.save(stored);
        revokeRefreshTokens(user.getId());
        emailNotificationService.sendPasswordChanged(user.getEmail(),
                "Your account password was reset. Existing refresh sessions were signed out.");
        audit(user.getId(), organizationId(user), "PASSWORD_RESET", "users", user.getId().toString(), null);
    }

    @Transactional
    public void changePassword(Long userId, AuthDtos.ChangePasswordRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw unauthorized();
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        revokeRefreshTokens(userId);
        emailNotificationService.sendPasswordChanged(user.getEmail(),
                "Your account password was changed. All other sessions were signed out.");
        audit(userId, organizationId(user), "PASSWORD_CHANGED", "users", userId.toString(), null);
    }

    private void failLogin(User user, String ipAddress) {
        int nextFailures = user.getFailedAttempts() + 1;
        boolean hasFace = user.isFaceEnrolled() || faceVerificationService.isEnrolled(user.getId());
        user.setFailedAttempts(nextFailures);
        recordAttempt(user.getEmail(), user.getId(), ipAddress, false, "INVALID_CREDENTIALS");
        audit(user.getId(), organizationId(user), "LOGIN_FAILED", "users", user.getId().toString(), null);
        if (nextFailures >= maxFailures) {
            lockAccount(user, ipAddress, "TOO_MANY_FAILED_ATTEMPTS");
            return;
        }
        if (nextFailures == faceRequiredAfterFailures) {
            if (hasFace) {
                auditLoggingService.record(user.getId(), organizationId(user), "FAILED_ATTEMPTS_THRESHOLD_REACHED",
                        "users", user.getId().toString(), nextFailures, VerificationResult.NOT_REQUIRED, null);
                emailNotificationService.sendSecurityAlert(user.getEmail(),
                        "Face verification required on your next sign-in",
                        "This account reached " + nextFailures + " consecutive failed sign-ins. Face verification will be required before the next successful sign-in.");
            } else {
                lockAccount(user, ipAddress, "TOO_MANY_FAILED_ATTEMPTS");
            }
        }
        userRepository.save(user);
    }

    private void lockAccount(User user, String ipAddress, String reason) {
        user.setLockedUntil(LocalDateTime.now().plus(lockDuration));
        user.setStatus(User.AccountStatus.LOCKED);
        userRepository.save(user);
        emailNotificationService.sendAccountLocked(user.getEmail(),
                reason.replace('_', ' ').toLowerCase());
        audit(user.getId(), organizationId(user), "ACCOUNT_LOCKED", "users", user.getId().toString(), null);
    }

    private void createAndSendVerificationOtp(User user, LocalDateTime windowStartedAt, int sendCount) {
        String otp = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setOtpHash(otpHmac(user.getId(), otp));
        token.setExpiresAt(LocalDateTime.now().plus(emailOtpTtl));
        token.setAttemptCount(0);
        token.setLastSentAt(LocalDateTime.now());
        token.setSendWindowStartedAt(windowStartedAt);
        token.setSendCount(sendCount);
        emailVerificationTokenRepository.save(token);
        emailNotificationService.sendVerificationOtp(user.getEmail(), otp, emailOtpTtl);
        auditOtp(user, "EMAIL_OTP_SENT");
    }

    private boolean allowEmailRequest(String email, String operation) {
        long now = System.currentTimeMillis();
        String key = operation + ':' + email;
        EmailRateWindow window = emailRateWindows.compute(key, (ignored, current) ->
                current == null || now - current.startedAt() >= 60_000
                        ? new EmailRateWindow(now, 1)
                        : new EmailRateWindow(current.startedAt(), current.count() + 1));
        if (emailRateWindows.size() > 10_000) {
            emailRateWindows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= 60_000);
        }
        return window.count() <= 10;
    }

    private String otpHmac(long userId, String otp) {
        byte[] key = otpHmacSecret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalStateException("OTP_HMAC_SECRET must contain at least 32 UTF-8 bytes");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((userId + ":" + otp).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void auditOtp(User user, String action) {
        audit(user.getId(), organizationId(user), action, "users", user.getId().toString(), null);
    }

    private void auditOtpFailure(User user) {
        if (user != null) {
            auditOtp(user, "EMAIL_OTP_FAILED");
            return;
        }
        auditLoggingService.record(null, null, "EMAIL_OTP_FAILED", "users", null, null,
                VerificationResult.FAILED, null);
    }

    private ChallengeIssue createChallenge(User user, String deviceHash, String ipAddress,
                                           RiskScoringService.RiskScore risk) {
        LoginChallenge challenge = new LoginChallenge();
        String nonce = opaqueToken();
        challenge.setId(UUID.randomUUID().toString());
        challenge.setUser(user);
        challenge.setDeviceHash(deviceHash);
        challenge.setIpAddress(ipAddress);
        challenge.setRiskScore(risk.score());
        challenge.setRiskReasons(writeJson(risk.reasons()));
        challenge.setNonceHash(hash(nonce));
        challenge.setExpiresAt(LocalDateTime.now().plus(challengeTtl));
        loginChallengeRepository.save(challenge);
        return new ChallengeIssue(challenge, nonce);
    }

    private void failFaceAttempt(LoginChallenge challenge, String ipAddress) {
        challenge.setAttemptCount(challenge.getAttemptCount() + 1);
        loginChallengeRepository.save(challenge);
        recordAttempt(challenge.getUser().getEmail(), challenge.getUser().getId(), ipAddress,
                false, "FACE_VERIFICATION_FAILED");
    }

    private Long findChallengeUserId(String challengeId) {
        return loginChallengeRepository.findById(challengeId).map(c -> c.getUser().getId()).orElse(-1L);
    }

    private void rememberDevice(User user, String hash, String ipAddress, String userAgent, boolean trust) {
        if (hash == null || hash.isBlank()) return;
        TrustedDevice device = trustedDeviceRepository.findByUserId(user.getId()).stream()
                .filter(candidate -> hash.equals(candidate.getDeviceHash()))
                .findFirst().orElseGet(TrustedDevice::new);
        device.setUser(user);
        device.setDeviceIdentifier(hash);
        device.setDeviceHash(hash);
        device.setIpAddress(ipAddress);
        device.setUserAgent(userAgent);
        GeoLocationService.Location location = geoLocationService.locate(ipAddress);
        if (location != null) device.setLastLocation(location.displayName());
        device.setTrusted(device.isTrusted() || trust);
        device.setLastSeen(LocalDateTime.now());
        device.setLastUsedAt(LocalDateTime.now());
        if (device.getFirstSeen() == null) device.setFirstSeen(LocalDateTime.now());
        trustedDeviceRepository.save(device);
    }

    private AuthDtos.AuthResult issueTokens(User user) {
        String refresh = opaqueToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(refresh));
        refreshToken.setExpiresAt(LocalDateTime.now().plus(refreshTtl));
        refreshTokenRepository.save(refreshToken);
        String role = user.getSystemRole() == User.SystemRole.SUPER_ADMIN ? "SUPER_ADMIN"
                : organizationMemberRepository.findByUserIdAndOrganizationId(user.getId(),
                        user.getOrganization().getId())
                        .filter(member -> "ACTIVE".equals(member.getStatus()))
                        .map(member -> member.getRole().name()).orElse("VIEWER");
        return AuthDtos.AuthResult.tokens(jwtService.issueAccessToken(user, role), refresh, jwtService.accessTtlSeconds());
    }

    private void recordAttempt(String email, Long userId, String ip, boolean successful, String reason) {
        recordAttempt(email, userId, ip, successful, reason, 0, List.of(), null);
    }

    private void recordAttempt(String email, Long userId, String ip, boolean successful, String reason,
                               int riskScore, List<String> riskReasons, String deviceHash) {
        LoginAttempt attempt = new LoginAttempt();
        if (userId != null) attempt.setUser(userRepository.getReferenceById(userId));
        attempt.setEmail(email);
        attempt.setIpAddress(ip);
        attempt.setIsSuccessful(successful);
        attempt.setFailureReason(successful ? null : reason);
        attempt.setOutcome(LoginOutcome.valueOf(reason));
        attempt.setRiskScore(riskScore);
        attempt.setRiskReasons(writeJson(riskReasons));
        attempt.setDeviceHash(deviceHash);
        attempt.setAttemptedAt(LocalDateTime.now());
        loginAttemptRepository.save(attempt);
    }

    private void revokeRefreshTokens(Long userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        tokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
    }

    private void audit(Long actorId, Long organizationId, String action, String entityType, String entityId, Integer risk) {
        VerificationResult result = action.endsWith("_FAILED") ? VerificationResult.FAILED
                : action.contains("FACE_VERIFIED") || action.startsWith("STEP_UP_SUCCEEDED")
                || action.equals("EMAIL_VERIFIED") || action.equals("EMAIL_OTP_VERIFIED")
                ? VerificationResult.SUCCESS : VerificationResult.NOT_REQUIRED;
        auditLoggingService.record(actorId, organizationId, action, entityType, entityId, risk, result, null);
    }

    private Long organizationId(User user) {
        return user.getOrganization() == null ? null : user.getOrganization().getId();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to encode audit or risk details", e);
        }
    }

    private static boolean validTimestamp(String timestamp) {
        try {
            Instant time = Instant.parse(timestamp);
            return Duration.between(time, Instant.now()).abs().compareTo(Duration.ofMinutes(2)) <= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String opaqueToken() {
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token is invalid, expired, or already used");
    }

    private ResponseStatusException otpError(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }

    private record ChallengeIssue(LoginChallenge challenge, String nonce) {
    }

    private record EmailRateWindow(long startedAt, int count) {
    }
}
