package com.olgav.invoicesecurity.controller;

import com.olgav.invoicesecurity.dto.ApiResponse;
import com.olgav.invoicesecurity.dto.AuthDtos;
import com.olgav.invoicesecurity.service.StepUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/step-up")
@SecurityRequirement(name = "bearerAuth")
public class StepUpController {
    private final StepUpService stepUpService;

    public StepUpController(StepUpService stepUpService) {
        this.stepUpService = stepUpService;
    }

    @Operation(summary = "Initiate a face-based verification challenge for a sensitive action")
    @PostMapping("/initiate")
    public ApiResponse<AuthDtos.StepUpChallengeResult> initiate(
            @Valid @RequestBody AuthDtos.StepUpInitiateRequest request,
            Authentication authentication) {
        return ApiResponse.ok(stepUpService.initiate((Long) authentication.getPrincipal(), request.action()));
    }

    @Operation(summary = "Verify face and obtain a single-use action-bound token")
    @PostMapping("/verify")
    public ApiResponse<AuthDtos.StepUpResult> verify(
            @Valid @RequestBody AuthDtos.StepUpVerifyRequest request,
            Authentication authentication) {
        return ApiResponse.ok(stepUpService.verify((Long) authentication.getPrincipal(), request));
    }
}
