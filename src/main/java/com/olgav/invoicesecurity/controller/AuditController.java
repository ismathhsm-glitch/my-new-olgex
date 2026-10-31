package com.olgav.invoicesecurity.controller;

import com.olgav.invoicesecurity.dto.ApiResponse;
import com.olgav.invoicesecurity.dto.AuditDtos;
import com.olgav.invoicesecurity.service.AuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {
    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @Operation(summary = "Search paginated organization audit logs")
    @GetMapping("/audit-logs")
    public ApiResponse<AuditDtos.Page<AuditDtos.AuditEntry>> logs(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String verificationResult,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(auditQueryService.logs((Long) authentication.getPrincipal(),
                from, to, userId, action, verificationResult, page, size));
    }

    @Operation(summary = "Get security dashboard aggregates")
    @GetMapping("/security/dashboard-summary")
    public ApiResponse<AuditDtos.DashboardSummary> summary(Authentication authentication) {
        return ApiResponse.ok(auditQueryService.summary((Long) authentication.getPrincipal()));
    }

    @Operation(summary = "List high-risk and failed login attempts")
    @GetMapping("/security/risky-logins")
    public ApiResponse<List<AuditDtos.RiskyLogin>> riskyLogins(
            Authentication authentication, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(auditQueryService.riskyLogins((Long) authentication.getPrincipal(), page, size));
    }
}
