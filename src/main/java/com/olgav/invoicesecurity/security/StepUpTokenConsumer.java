package com.olgav.invoicesecurity.security;

import com.olgav.invoicesecurity.model.StepUpToken;
import com.olgav.invoicesecurity.repository.StepUpTokenRepository;
import com.olgav.invoicesecurity.service.AuditLoggingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@Service
public class StepUpTokenConsumer {
    private final StepUpTokenRepository tokenRepository;
    private final AuditLoggingService auditLoggingService;

    public StepUpTokenConsumer(StepUpTokenRepository tokenRepository, AuditLoggingService auditLoggingService) {
        this.tokenRepository = tokenRepository;
        this.auditLoggingService = auditLoggingService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consume(String rawToken, Long userId, com.olgav.invoicesecurity.model.SensitiveAction action) {
        StepUpToken token = tokenRepository.findForUpdate(hash(rawToken), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Step-up token is invalid"));
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now()) || token.getAction() != action) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Step-up token is expired, already used, or bound to a different action");
        }
        token.setUsed(true);
        tokenRepository.save(token);
        Long organizationId = token.getUser().getOrganization() == null
                ? null : token.getUser().getOrganization().getId();
        auditLoggingService.record(userId, organizationId, "STEP_UP_TOKEN_CONSUMED",
                "step_up_tokens", token.getId().toString(), null,
                com.olgav.invoicesecurity.model.VerificationResult.SUCCESS, null);
    }

    private static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
