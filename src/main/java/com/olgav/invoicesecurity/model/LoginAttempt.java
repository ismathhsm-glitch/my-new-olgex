package com.olgav.invoicesecurity.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_attempts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String email;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "device_hash", length = 64)
    private String deviceHash;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore = 0;

    @Column(name = "risk_reasons", columnDefinition = "json")
    private String riskReasons;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private LoginOutcome outcome = LoginOutcome.UNKNOWN;

    @Column(name = "is_successful", nullable = false)
    private Boolean isSuccessful;

    private String failureReason;

    @Column(name = "attempted_at", updatable = false)
    private LocalDateTime attemptedAt = LocalDateTime.now();
}