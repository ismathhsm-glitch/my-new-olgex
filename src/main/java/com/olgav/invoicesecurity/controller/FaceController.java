package com.olgav.invoicesecurity.controller;

import com.olgav.invoicesecurity.dto.ApiResponse;
import com.olgav.invoicesecurity.dto.FaceDtos;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.FaceTemplateRepository;
import com.olgav.invoicesecurity.repository.UserRepository;
import com.olgav.invoicesecurity.service.FaceVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/face")
@SecurityRequirement(name = "bearerAuth")
public class FaceController {
    private final FaceVerificationService faceService;
    private final FaceTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final com.olgav.invoicesecurity.service.AuditLoggingService auditLoggingService;

    public FaceController(FaceVerificationService faceService, FaceTemplateRepository templateRepository,
                          UserRepository userRepository,
                          com.olgav.invoicesecurity.service.AuditLoggingService auditLoggingService) {
        this.faceService = faceService;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.auditLoggingService = auditLoggingService;
    }

    @Operation(summary = "Enroll three client-computed face embeddings")
    @PostMapping("/enroll")
    @Transactional
    public ApiResponse<Map<String, String>> enroll(@Valid @RequestBody FaceDtos.EnrollmentRequest request,
                                                    Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        faceService.enroll(user, request.samples());
        auditLoggingService.record(userId, user.getOrganization().getId(), "FACE_ENROLLED");
        return ApiResponse.ok(Map.of("status", "FACE_ENROLLED"));
    }

    @Operation(summary = "Get face enrollment state")
    @GetMapping("/status")
    public ApiResponse<FaceDtos.FaceStatus> status(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        int count = templateRepository.findByUserIdOrderBySampleIndex(userId).size();
        return ApiResponse.ok(new FaceDtos.FaceStatus(count > 0, count));
    }

    @Operation(summary = "Delete face enrollment")
    @DeleteMapping("/enrollment")
    @Transactional
    public ApiResponse<Map<String, String>> delete(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        templateRepository.deleteByUserId(userId);
        user.setFaceEnrolled(false);
        userRepository.save(user);
        auditLoggingService.record(userId, user.getOrganization().getId(), "FACE_ENROLLMENT_DELETED");
        return ApiResponse.ok(Map.of("status", "FACE_ENROLLMENT_DELETED"));
    }
}
