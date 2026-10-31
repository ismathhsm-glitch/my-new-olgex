package com.olgav.invoicesecurity.service;

import com.olgav.invoicesecurity.model.VerificationResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditLoggingService {
    private final JdbcTemplate jdbc;

    public AuditLoggingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(Long actorId, Long organizationId, String action, String entityType,
                       String entityId, Integer riskScore, VerificationResult result, String detailsJson) {
        String ipAddress = null;
        String device = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            ipAddress = request.getRemoteAddr();
            device = request.getHeader("User-Agent");
        }
        jdbc.update("INSERT INTO audit_logs (actor_id, user_id, organization_id, action, entity_type, entity_id, "
                        + "ip_address, device, risk_score, verification_result, details) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                actorId, actorId, organizationId, action, entityType, entityId, ipAddress, device,
                riskScore, result.name(), detailsJson);
    }

    public void record(Long actorId, Long organizationId, String action) {
        VerificationResult result = action.endsWith("_FAILED") ? VerificationResult.FAILED
                : action.contains("FACE_VERIFIED") || action.startsWith("STEP_UP_SUCCEEDED")
                || action.equals("EMAIL_VERIFIED") || action.equals("EMAIL_OTP_VERIFIED")
                ? VerificationResult.SUCCESS : VerificationResult.NOT_REQUIRED;
        record(actorId, organizationId, action, null, null, null, result, null);
    }
}
