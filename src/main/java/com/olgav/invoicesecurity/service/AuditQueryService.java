package com.olgav.invoicesecurity.service;

import com.olgav.invoicesecurity.dto.AuditDtos;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditQueryService {
    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final int highRiskThreshold;

    public AuditQueryService(JdbcTemplate jdbc, UserRepository userRepository,
                             @Value("${app.security.risk.high-threshold:60}") int highRiskThreshold) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.highRiskThreshold = highRiskThreshold;
    }

    @Transactional(readOnly = true)
    public AuditDtos.Page<AuditDtos.AuditEntry> logs(Long currentUserId, LocalDateTime from,
                                                      LocalDateTime to, Long actorId, String action,
                                                      String verificationResult, int page, int size) {
        Long organizationId = organizationId(currentUserId);
        StringBuilder where = new StringBuilder(" WHERE organization_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(organizationId);
        add(where, parameters, " AND created_at >= ?", from);
        add(where, parameters, " AND created_at <= ?", to);
        add(where, parameters, " AND actor_id = ?", actorId);
        add(where, parameters, " AND action = ?", action);
        add(where, parameters, " AND verification_result = ?", verificationResult);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs" + where, Long.class, parameters.toArray());
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(page, 0);
        List<Object> queryParameters = new ArrayList<>(parameters);
        queryParameters.add(safeSize);
        queryParameters.add((long) safePage * safeSize);
        List<AuditDtos.AuditEntry> entries = jdbc.query(
                "SELECT id, actor_id, action, entity_type, entity_id, ip_address, device, location, risk_score, "
                        + "verification_result, details, created_at FROM audit_logs" + where
                        + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, row) -> new AuditDtos.AuditEntry(rs.getLong("id"), nullableLong(rs.getObject("actor_id")),
                        rs.getString("action"), rs.getString("entity_type"), rs.getString("entity_id"),
                        rs.getString("ip_address"), rs.getString("device"), rs.getString("location"),
                        (Integer) rs.getObject("risk_score"), rs.getString("verification_result"),
                        rs.getString("details"), rs.getTimestamp("created_at").toLocalDateTime()),
                queryParameters.toArray());
        return new AuditDtos.Page<>(entries, safePage, safeSize, total);
    }

    @Transactional(readOnly = true)
    public AuditDtos.DashboardSummary summary(Long currentUserId) {
        Long organizationId = organizationId(currentUserId);
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<AuditDtos.DailyLogin> daily = jdbc.query(
                "SELECT DATE(l.attempted_at) AS login_day, COUNT(*) AS total, "
                        + "SUM(CASE WHEN l.is_successful = FALSE THEN 1 ELSE 0 END) AS failed "
                        + "FROM login_attempts l JOIN organization_members m ON m.user_id = l.user_id "
                        + "WHERE m.org_id = ? AND l.attempted_at >= ? GROUP BY DATE(l.attempted_at) ORDER BY login_day",
                (rs, row) -> new AuditDtos.DailyLogin(rs.getDate("login_day").toLocalDate(),
                        rs.getLong("total"), rs.getLong("failed")), organizationId, Timestamp.valueOf(since));
        long failures = jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_attempts l JOIN organization_members m ON m.user_id = l.user_id "
                        + "WHERE m.org_id = ? AND l.is_successful = FALSE AND l.attempted_at >= ?",
                Long.class, organizationId, Timestamp.valueOf(since));
        long faceSuccess = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE organization_id = ? "
                        + "AND action IN ('STEP_UP_SUCCEEDED', 'LOGIN_FACE_VERIFIED') AND created_at >= ?",
                Long.class, organizationId, Timestamp.valueOf(since));
        long faceFailure = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE organization_id = ? "
                        + "AND action IN ('STEP_UP_FAILED', 'LOGIN_FACE_FAILED') AND created_at >= ?",
                Long.class, organizationId, Timestamp.valueOf(since));
        double faceRate = faceSuccess + faceFailure == 0 ? 0.0
                : 100.0 * faceSuccess / (faceSuccess + faceFailure);
        long highRisk = jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_attempts l JOIN organization_members m ON m.user_id = l.user_id "
                        + "WHERE m.org_id = ? AND l.risk_score >= ? AND l.attempted_at >= ?",
                Long.class, organizationId, highRiskThreshold, Timestamp.valueOf(since));
        long stepUps = auditCountLike(organizationId, "STEP_UP_%", since);
        List<AuditDtos.RiskyUser> users = jdbc.query(
                "SELECT u.id, u.email, SUM(CASE WHEN l.is_successful = FALSE THEN 1 ELSE 0 END) AS failures, "
                        + "MAX(l.risk_score) AS max_risk FROM login_attempts l JOIN users u ON u.id = l.user_id "
                        + "JOIN organization_members m ON m.user_id = u.id WHERE m.org_id = ? AND l.attempted_at >= ? "
                        + "GROUP BY u.id, u.email ORDER BY max_risk DESC, failures DESC LIMIT 5",
                (rs, row) -> new AuditDtos.RiskyUser(rs.getLong("id"), rs.getString("email"),
                        rs.getLong("failures"), rs.getInt("max_risk")), organizationId, Timestamp.valueOf(since));
        List<AuditDtos.RiskyDevice> devices = jdbc.query(
                "SELECT COALESCE(l.device_hash, l.ip_address) AS device_ref, COUNT(*) AS attempts, MAX(l.risk_score) AS max_risk "
                        + "FROM login_attempts l JOIN organization_members m ON m.user_id = l.user_id "
                        + "WHERE m.org_id = ? AND l.attempted_at >= ? GROUP BY COALESCE(l.device_hash, l.ip_address) "
                        + "ORDER BY max_risk DESC, attempts DESC LIMIT 5",
                (rs, row) -> new AuditDtos.RiskyDevice(maskDeviceRef(rs.getString("device_ref")),
                        rs.getLong("attempts"), rs.getInt("max_risk")), organizationId, Timestamp.valueOf(since));
        return new AuditDtos.DashboardSummary(daily, failures, faceRate, highRisk, stepUps, users, devices);
    }

    @Transactional(readOnly = true)
    public List<AuditDtos.RiskyLogin> riskyLogins(Long currentUserId, int page, int size) {
        Long organizationId = organizationId(currentUserId);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = Math.max(page, 0) * safeSize;
        return jdbc.query(
                "SELECT l.email, l.ip_address, l.risk_score, l.risk_reasons, l.outcome, l.attempted_at "
                        + "FROM login_attempts l JOIN organization_members m ON m.user_id = l.user_id "
                        + "WHERE m.org_id = ? AND (l.risk_score >= ? OR l.is_successful = FALSE) "
                        + "ORDER BY l.attempted_at DESC LIMIT ? OFFSET ?",
                (rs, row) -> new AuditDtos.RiskyLogin(rs.getString("email"), rs.getString("ip_address"),
                        rs.getInt("risk_score"), rs.getString("risk_reasons"), rs.getString("outcome"),
                        rs.getTimestamp("attempted_at").toLocalDateTime()),
                organizationId, highRiskThreshold, safeSize, offset);
    }

    private long auditCountLike(Long organizationId, String action, LocalDateTime since) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE organization_id = ? AND action LIKE ? AND created_at >= ?",
                Long.class, organizationId, action, Timestamp.valueOf(since));
    }

    private Long organizationId(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No current organization");
        }
        return user.getOrganization().getId();
    }

    private static void add(StringBuilder where, List<Object> parameters, String clause, Object value) {
        if (value != null) {
            where.append(clause);
            parameters.add(value);
        }
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static String maskDeviceRef(String value) {
        if (value == null || value.length() <= 10) return value;
        return value.substring(0, 6) + "...";
    }
}
