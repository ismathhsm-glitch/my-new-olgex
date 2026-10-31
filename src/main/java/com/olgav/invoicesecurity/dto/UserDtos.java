package com.olgav.invoicesecurity.dto;

import com.olgav.invoicesecurity.model.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {
    }

    public record Profile(Long id, String email, String fullName, String status,
                          boolean emailVerified, boolean faceEnrolled) {
        public static Profile from(User user) {
            return new Profile(user.getId(), user.getEmail(), user.getName(), user.getStatus().name(),
                    user.isEmailVerified(), user.isFaceEnrolled());
        }
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 255) String fullName) {
    }
}
