package com.olgav.invoicesecurity.service;

import com.olgav.invoicesecurity.dto.AuthDtos;
import com.olgav.invoicesecurity.model.*;
import com.olgav.invoicesecurity.repository.StepUpChallengeRepository;
import com.olgav.invoicesecurity.repository.StepUpTokenRepository;
import com.olgav.invoicesecurity.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class StepUpService {
    private final UserRepository userRepository;
    private final StepUpChallengeRepository challengeRepository;
    private final StepUpTokenRepository tokenRepository;
    private final FaceVerificationService faceVerificationService;
    private final EmailNotificationService emailNotificationService;
    private final AuditLoggingService auditLoggingService;
    private final Duration challengeTtl;
    private final Duration tokenTtl;
    private final SecureRandom random = new SecureRandom();

    public StepUpService(
            UserRepository userRepository,
            StepUpChallengeRepository challengeRepository,
            StepUpTokenRepository tokenRepository,
            FaceVerificationService faceVerificationService,
            EmailNotificationService emailNotificationService,
            AuditLoggingService auditLoggingService,
            @Value("${app.security.face.nonce-ttl:PT2M}") Duration challengeTtl,
            @Value("${app.security.step-up.ttl:PT5M}") Duration tokenTtl) {
        this.userRepository = userRepository;
        this.challengeRepository = challengeRepository;
        this.tokenRepository = tokenRepository;
        this.faceVerificationService = faceVerificationService;
        this.emailNotificationService = emailNotificationService;
        this.auditLoggingService = auditLoggingService;
        this.challengeTtl = challengeTtl;
        this.tokenTtl = tokenTtl;
    }

    @Transactional(dontRollbackOn = ResponseStatusException.class)
    public AuthDtos.StepUpChallengeResult initiate(Long userId, SensitiveAction action) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!faceVerificationService.isEnrolled(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Face enrollment is required for step-up");
        }
        String nonce = opaqueToken();
        StepUpChallenge challenge = new StepUpChallenge();
        challenge.setId(UUID.randomUUID().toString());
        challenge.setUser(user);
        challenge.setAction(action);
        challenge.setNonceHash(hash(nonce));
        challenge.setExpiresAt(LocalDateTime.now().plus(challengeTtl));
        challengeRepository.save(challenge);
        return new AuthDtos.StepUpChallengeResult(challenge.getId(), nonce, action,
                challenge.getExpiresAt().toString());
    }

    @Transactional
    public AuthDtos.StepUpResult verify(Long userId, AuthDtos.StepUpVerifyRequest request) {
        StepUpChallenge challenge = challengeRepository.findForUpdate(request.challengeId(), userId)
                .orElseThrow(this::invalidChallenge);
        if (challenge.isUsed() || challenge.getAttemptCount() >= 5
                || challenge.getExpiresAt().isBefore(LocalDateTime.now())
                || challenge.getAction() != request.action()
                || !validTimestamp(request.timestamp())
                || !constantTimeEquals(challenge.getNonceHash(), hash(request.nonce()))) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            challengeRepository.save(challenge);
            auditLoggingService.record(userId, challenge.getUser().getOrganization().getId(), "STEP_UP_FAILED");
            emailNotificationService.sendSecurityAlert(challenge.getUser().getEmail(),
                    "Step-up verification failed", "A sensitive-action verification request was invalid or expired.");
            throw invalidChallenge();
        }
        if (!faceVerificationService.verify(challenge.getUser(), request.embedding())) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            challengeRepository.save(challenge);
            auditLoggingService.record(userId, challenge.getUser().getOrganization().getId(), "STEP_UP_FAILED");
            emailNotificationService.sendSecurityAlert(challenge.getUser().getEmail(),
                    "Step-up verification failed", "Facial verification for a sensitive action was unsuccessful.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Facial verification failed");
        }
        challenge.setUsed(true);
        challengeRepository.save(challenge);
        String rawToken = opaqueToken();
        StepUpToken stepUpToken = new StepUpToken();
        stepUpToken.setUser(challenge.getUser());
        stepUpToken.setAction(challenge.getAction());
        stepUpToken.setTokenHash(hash(rawToken));
        stepUpToken.setNonceHash(challenge.getNonceHash());
        stepUpToken.setExpiresAt(LocalDateTime.now().plus(tokenTtl));
        tokenRepository.save(stepUpToken);
        auditLoggingService.record(userId, challenge.getUser().getOrganization().getId(), "STEP_UP_SUCCEEDED");
        return new AuthDtos.StepUpResult(rawToken, stepUpToken.getExpiresAt().toString());
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
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String opaqueToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ResponseStatusException invalidChallenge() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Step-up challenge is invalid or expired");
    }
}
