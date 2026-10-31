package com.olgav.invoicesecurity.dto;

import com.olgav.invoicesecurity.model.Permission;
import com.olgav.invoicesecurity.model.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class UserDtos {
    private UserDtos() {
    }

    public record Profile(Long id, String email, String fullName, String status,
                          boolean emailVerified, Long organizationId, String organizationName,
                          String orgRole, String systemRole, List<Permission> permissions,
                          LocalDateTime lastLoginAt, boolean faceEnrolled) {
        public static Profile from(User user, Long organizationId, String organizationName,
                                  String orgRole, String systemRole, List<Permission> permissions,
                                  LocalDateTime lastLoginAt) {
            return new Profile(user.getId(), user.getEmail(), user.getName(), user.getStatus().name(),
                    user.isEmailVerified(), organizationId, organizationName, orgRole, systemRole,
                    permissions, lastLoginAt, user.isFaceEnrolled());
        }
    }

    public record ActivityEntry(String action, String result, LocalDateTime time,
                                String approximateLocation, String deviceLabel) {
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 255) String fullName) {
    }
}
