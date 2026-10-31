package com.olgav.invoicesecurity.controller;

import com.olgav.invoicesecurity.dto.ApiResponse;
import com.olgav.invoicesecurity.dto.AuthDtos;
import com.olgav.invoicesecurity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a user and organization")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, String>>> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("status", "VERIFICATION_EMAIL_SENT")));
    }

    @Operation(summary = "Verify a registration email")
    @PostMapping("/verify-email")
    public ApiResponse<Map<String, String>> verifyEmail(@Valid @RequestBody VerifyEmailBody request) {
        authService.verifyEmail(request.email(), request.otp());
        return ApiResponse.ok(Map.of("status", "EMAIL_VERIFIED"));
    }

    @Operation(summary = "Resend an email verification code")
    @PostMapping("/resend-verification")
    public ApiResponse<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationBody request) {
        authService.resendVerification(request.email());
        return ApiResponse.ok(Map.of("status", "VERIFICATION_REQUEST_ACCEPTED"));
    }

    @Operation(summary = "Sign in; returns tokens or a face challenge")
    @PostMapping("/login")
    public ApiResponse<AuthDtos.AuthResult> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                   HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.login(request, remoteAddress(servletRequest),
                servletRequest.getHeader("User-Agent")));
    }

    @Operation(summary = "Complete a risk-based face challenge")
    @PostMapping("/login/face-verify")
    public ApiResponse<AuthDtos.AuthResult> verifyLoginFace(
            @Valid @RequestBody AuthDtos.FaceVerifyRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.verifyLoginFace(request, remoteAddress(servletRequest)));
    }

    @Operation(summary = "Rotate a refresh token")
    @PostMapping("/refresh")
    public ApiResponse<AuthDtos.AuthResult> refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Revoke the current refresh token")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout(@Valid @RequestBody AuthDtos.RefreshRequest request,
                                                   Authentication authentication) {
        authService.logout(request.refreshToken(), (Long) authentication.getPrincipal());
        return ApiResponse.ok(Map.of("status", "SIGNED_OUT"));
    }

    @Operation(summary = "Request a password reset email")
    @PostMapping("/forgot-password")
    public ApiResponse<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordBody request) {
        authService.forgotPassword(request.email());
        return ApiResponse.ok(Map.of("status", "IF_ACCOUNT_EXISTS_EMAIL_SENT"));
    }

    @Operation(summary = "Set a new password using a reset token")
    @PostMapping("/reset-password")
    public ApiResponse<Map<String, String>> resetPassword(
            @Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok(Map.of("status", "PASSWORD_RESET"));
    }

    @Operation(summary = "Change the authenticated user's password")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/change-password")
    public ApiResponse<Map<String, String>> changePassword(
            @Valid @RequestBody AuthDtos.ChangePasswordRequest request, Authentication authentication) {
        authService.changePassword((Long) authentication.getPrincipal(), request);
        return ApiResponse.ok(Map.of("status", "PASSWORD_CHANGED"));
    }

    private static String remoteAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    public record VerifyEmailBody(@Email @NotBlank String email,
                                  @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otp) {
    }

    public record ResendVerificationBody(@Email @NotBlank String email) {
    }

    public record ForgotPasswordBody(@Email @NotBlank String email) {
    }
}
