package com.olgav.invoicesecurity.controller;

import com.olgav.invoicesecurity.dto.ApiResponse;
import com.olgav.invoicesecurity.dto.AuditDtos;
import com.olgav.invoicesecurity.dto.UserDtos;
import com.olgav.invoicesecurity.model.Permission;
import com.olgav.invoicesecurity.model.OrganizationMember;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.LoginAttemptRepository;
import com.olgav.invoicesecurity.repository.OrganizationMemberRepository;
import com.olgav.invoicesecurity.repository.UserRepository;
import com.olgav.invoicesecurity.service.AuditLoggingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserRepository userRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final AuditLoggingService auditLoggingService;
    private final JdbcTemplate jdbcTemplate;

    public UserController(UserRepository userRepository,
                          OrganizationMemberRepository organizationMemberRepository,
                          LoginAttemptRepository loginAttemptRepository,
                          AuditLoggingService auditLoggingService,
                          JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.auditLoggingService = auditLoggingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Operation(summary = "Get the authenticated user's profile")
    @GetMapping("/me")
    public ApiResponse<UserDtos.Profile> current(Authentication authentication) {
        User user = currentUser(authentication);
        OrganizationMember member = organizationMemberRepository.findByUserIdAndOrganizationId(user.getId(),
                user.getOrganization() == null ? null : user.getOrganization().getId()).orElse(null);
        List<Permission> permissions = Permission.forUser(user, member);
        LocalDateTime lastLoginAt = loginAttemptRepository.findTopByUserIdAndIsSuccessfulTrueOrderByAttemptedAtDesc(user.getId()) != null
                ? loginAttemptRepository.findTopByUserIdAndIsSuccessfulTrueOrderByAttemptedAtDesc(user.getId()).getAttemptedAt()
                : null;
        return ApiResponse.ok(UserDtos.Profile.from(
                user,
                user.getOrganization() == null ? null : user.getOrganization().getId(),
                user.getOrganization() == null ? null : user.getOrganization().getName(),
                member != null ? member.getRole().name() : null,
                user.getSystemRole().name(),
                permissions,
                lastLoginAt));
    }

    @Operation(summary = "Get recent activity for the authenticated user")
    @GetMapping("/me/activity")
    public ApiResponse<AuditDtos.Page<UserDtos.ActivityEntry>> activity(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        User user = currentUser(authentication);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        String where = " WHERE actor_id = ? AND action IN ('LOGIN_SUCCEEDED','LOGIN_FAILED','PASSWORD_CHANGED','PASSWORD_RESET','LOGIN_FACE_VERIFIED','LOGIN_FACE_FAILED','STEP_UP_SUCCEEDED','STEP_UP_FAILED','DEVICE_TRUSTED','DEVICE_REVOKED','FACE_ENROLLED','FACE_ENROLLMENT_REMOVED','LOGOUT','EMAIL_VERIFIED','EMAIL_OTP_VERIFIED')";
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs" + where, Long.class, user.getId());
        List<UserDtos.ActivityEntry> content = jdbcTemplate.query(
                "SELECT action, verification_result, created_at, location, COALESCE(device, ip_address) AS device_label FROM audit_logs"
                        + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new UserDtos.ActivityEntry(
                        rs.getString("action"),
                        rs.getString("verification_result"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("location"),
                        rs.getString("device_label")),
                user.getId(), safeSize, safePage * safeSize);
        return ApiResponse.ok(new AuditDtos.Page<>(content, safePage, safeSize, total == null ? 0L : total));
    }

    @Operation(summary = "Update the authenticated user's profile")
    @PutMapping("/me")
    public ApiResponse<UserDtos.Profile> update(@Valid @RequestBody UserDtos.UpdateProfileRequest request,
                                                Authentication authentication) {
        User user = currentUser(authentication);
        user.setName(request.fullName());
        userRepository.save(user);
        auditLoggingService.record(user.getId(),
                user.getOrganization() == null ? null : user.getOrganization().getId(), "USER_PROFILE_UPDATED");
        return ApiResponse.ok(UserDtos.Profile.from(
                user,
                user.getOrganization() == null ? null : user.getOrganization().getId(),
                user.getOrganization() == null ? null : user.getOrganization().getName(),
                organizationMemberRepository.findByUserIdAndOrganizationId(user.getId(),
                        user.getOrganization() == null ? null : user.getOrganization().getId())
                        .map(member -> member.getRole().name()).orElse(null),
                user.getSystemRole().name(),
                Permission.forUser(user, organizationMemberRepository.findByUserIdAndOrganizationId(user.getId(),
                        user.getOrganization() == null ? null : user.getOrganization().getId()).orElse(null)),
                loginAttemptRepository.findTopByUserIdAndIsSuccessfulTrueOrderByAttemptedAtDesc(user.getId()) == null ? null
                        : loginAttemptRepository.findTopByUserIdAndIsSuccessfulTrueOrderByAttemptedAtDesc(user.getId()).getAttemptedAt()));
    }

    private User currentUser(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return userRepository.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
