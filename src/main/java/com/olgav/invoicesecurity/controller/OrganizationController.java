package com.olgav.invoicesecurity.controller;

import com.olgav.invoicesecurity.dto.ApiResponse;
import com.olgav.invoicesecurity.dto.OrganizationDtos;
import com.olgav.invoicesecurity.model.OrgRole;
import com.olgav.invoicesecurity.security.RequiresStepUp;
import com.olgav.invoicesecurity.model.SensitiveAction;
import com.olgav.invoicesecurity.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/organizations")
@SecurityRequirement(name = "bearerAuth")
public class OrganizationController {
    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @Operation(summary = "Get the authenticated user's current organization")
    @GetMapping("/current")
    public ApiResponse<OrganizationDtos.OrganizationView> current(Authentication authentication) {
        return ApiResponse.ok(organizationService.current(userId(authentication)));
    }

    @Operation(summary = "Update organization settings")
    @PutMapping("/current")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<OrganizationDtos.OrganizationView> updateSettings(
            @Valid @RequestBody OrganizationDtos.SettingsRequest request, Authentication authentication) {
        return ApiResponse.ok(organizationService.updateSettings(userId(authentication), request));
    }

    @Operation(summary = "List current organization members")
    @GetMapping("/members")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<List<OrganizationDtos.MemberView>> members(Authentication authentication) {
        return ApiResponse.ok(organizationService.members(userId(authentication)));
    }

    @Operation(summary = "Invite an organization member")
    @PostMapping("/members")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<OrganizationDtos.MemberView> invite(
            @Valid @RequestBody OrganizationDtos.InviteMemberRequest request, Authentication authentication) {
        return ApiResponse.ok(organizationService.invite(userId(authentication), request));
    }

    @Operation(summary = "Accept an organization invitation using its email token")
    @PostMapping("/invitations/accept")
    public ApiResponse<Map<String, String>> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request, Authentication authentication) {
        organizationService.acceptInvitation(userId(authentication), request.token());
        return ApiResponse.ok(Map.of("status", "INVITATION_ACCEPTED"));
    }

    @Operation(summary = "Change an organization's member role")
    @PutMapping("/members/{id}/role")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<OrganizationDtos.MemberView> changeRole(
            @PathVariable Long id, @Valid @RequestBody OrganizationDtos.ChangeRoleRequest request,
            Authentication authentication) {
        return ApiResponse.ok(organizationService.changeRole(userId(authentication), id, request.role()));
    }

    @Operation(summary = "Remove an organization member")
    @DeleteMapping("/members/{id}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Map<String, String>> remove(@PathVariable Long id, Authentication authentication) {
        organizationService.removeMember(userId(authentication), id);
        return ApiResponse.ok(Map.of("status", "MEMBER_REMOVED"));
    }

    @Operation(summary = "Transfer organization administrator role")
    @PostMapping("/transfer-admin")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    @RequiresStepUp(action = SensitiveAction.CHANGE_ORGANIZATION_ADMIN)
    public ApiResponse<Map<String, String>> transferAdmin(
            @Valid @RequestBody OrganizationDtos.TransferAdminRequest request, Authentication authentication) {
        organizationService.transferAdmin(userId(authentication), request);
        return ApiResponse.ok(Map.of("status", "ADMIN_TRANSFERRED"));
    }

    @Operation(summary = "Update encrypted organization bank details")
    @PutMapping("/bank-details")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    @RequiresStepUp(action = SensitiveAction.CHANGE_BANK_DETAILS)
    public ApiResponse<OrganizationDtos.OrganizationView> updateBankDetails(
            @Valid @RequestBody OrganizationDtos.BankDetailsRequest request, Authentication authentication) {
        return ApiResponse.ok(organizationService.updateBankDetails(userId(authentication), request));
    }

    private static Long userId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }

    public record AcceptInvitationRequest(@NotBlank String token) {
    }
}
