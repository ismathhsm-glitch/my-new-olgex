package com.olgav.invoicesecurity.controller;

import com.olgav.invoicesecurity.dto.ApiResponse;
import com.olgav.invoicesecurity.dto.UserDtos;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.UserRepository;
import com.olgav.invoicesecurity.service.AuditLoggingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserRepository userRepository;
    private final AuditLoggingService auditLoggingService;

    public UserController(UserRepository userRepository, AuditLoggingService auditLoggingService) {
        this.userRepository = userRepository;
        this.auditLoggingService = auditLoggingService;
    }

    @Operation(summary = "Get the authenticated user's profile")
    @GetMapping("/me")
    public ApiResponse<UserDtos.Profile> current(Authentication authentication) {
        return ApiResponse.ok(UserDtos.Profile.from(currentUser(authentication)));
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
        return ApiResponse.ok(UserDtos.Profile.from(user));
    }

    private User currentUser(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return userRepository.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
