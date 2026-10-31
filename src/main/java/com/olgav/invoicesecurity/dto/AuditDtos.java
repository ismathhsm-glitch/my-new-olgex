package com.olgav.invoicesecurity.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AuditDtos {
    private AuditDtos() {
    }

    public record AuditEntry(Long id, Long actorId, String action, String entityType, String entityId,
                             String ipAddress, String device, String location, Integer riskScore,
                             String verificationResult, String details, LocalDateTime timestamp) {
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements) {
    }

    public record DailyLogin(LocalDate day, long total, long failed) {
    }

    public record RiskyUser(Long userId, String email, long failedAttempts, int maxRiskScore) {
    }

    public record RiskyDevice(String deviceReference, long attempts, int maxRiskScore) {
    }

    public record DashboardSummary(List<DailyLogin> loginsPerDay, long failedLogins,
                                   double faceVerificationSuccessRate, long highRiskLogins,
                                   long stepUpCount, List<RiskyUser> topRiskyUsers,
                                   List<RiskyDevice> topRiskyDevices) {
    }

    public record RiskyLogin(String email, String ipAddress, int riskScore, String riskReasons,
                             String outcome, LocalDateTime timestamp) {
    }
}
