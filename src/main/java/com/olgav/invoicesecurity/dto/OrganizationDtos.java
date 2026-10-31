package com.olgav.invoicesecurity.dto;

import com.olgav.invoicesecurity.model.OrgRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class OrganizationDtos {
    private OrganizationDtos() {
    }

    public record SettingsRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String logoUrl,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @Size(max = 10000) String taxSettings) {
    }

    public record BankDetailsRequest(@NotBlank @Size(max = 3500) String details) {
    }

    public record OrganizationView(Long id, String name, String logoUrl, String currency,
                                   String taxSettings, String maskedBankDetails) {
    }

    public record InviteMemberRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(max = 255) String fullName,
            OrgRole role) {
    }

    public record ChangeRoleRequest(OrgRole role) {
    }

    public record TransferAdminRequest(Long newAdminUserId) {
    }

    public record MemberView(Long userId, String email, String fullName, OrgRole role, String status,
                             LocalDateTime invitedAt) {
    }
}
