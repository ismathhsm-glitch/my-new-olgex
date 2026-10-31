package com.olgav.invoicesecurity.dto;

import com.olgav.invoicesecurity.model.SensitiveAction;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 255) String fullName,
            @Email @NotBlank @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 72)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])[\\x21-\\x7E]{12,72}$")
            String password,
            @NotBlank @Size(max = 255) String organizationName) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password,
            @jakarta.validation.constraints.Pattern(regexp = "^[A-Fa-f0-9]{64}$") String deviceHash) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenRequest(@NotBlank String token) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 72)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])[\\x21-\\x7E]{12,72}$")
            String newPassword) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 72)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])[\\x21-\\x7E]{12,72}$")
            String newPassword) {
    }

    public record AuthResult(
            String status,
            String accessToken,
            String refreshToken,
            String tokenType,
            Long expiresInSeconds,
            String challengeId,
            String challengeNonce,
            Integer riskScore,
            List<String> riskReasons,
            Boolean faceEnrolled,
            Boolean recommendEnrollment) {
        public static AuthResult tokens(String access, String refresh, long ttl) {
            return new AuthResult("SUCCESS", access, refresh, "Bearer", ttl,
                    null, null, null, List.of(), true, false);
        }

        public static AuthResult noFace(String access, String refresh, long ttl) {
            return new AuthResult("SUCCESS", access, refresh, "Bearer", ttl,
                    null, null, null, List.of(), false, true);
        }

        public static AuthResult faceChallenge(String id, String nonce, int score, List<String> reasons) {
            return new AuthResult("FACE_VERIFICATION_REQUIRED", null, null, null, null,
                    id, nonce, score, reasons, null, null);
        }
    }

    public record FaceVerifyRequest(
            @NotBlank String challengeId,
            @NotBlank String nonce,
            @NotBlank String timestamp,
            @Size(min = 128, max = 128) List<Float> embedding) {
    }

    public record StepUpInitiateRequest(@jakarta.validation.constraints.NotNull SensitiveAction action) {
    }

    public record StepUpVerifyRequest(
            @NotBlank String challengeId,
            @jakarta.validation.constraints.NotNull SensitiveAction action,
            @NotBlank String nonce,
            @NotBlank String timestamp,
            @Size(min = 128, max = 128) List<Float> embedding) {
    }

    public record StepUpChallengeResult(String challengeId, String nonce, SensitiveAction action, String expiresAt) {
    }

    public record StepUpResult(String stepUpToken, String expiresAt) {
    }
}
