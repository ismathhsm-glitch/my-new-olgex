package com.olgav.invoicesecurity.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_challenges")
@Getter
@Setter
@NoArgsConstructor
public class LoginChallenge {
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_hash", length = 64)
    private String deviceHash;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "risk_reasons", columnDefinition = "json")
    private String riskReasons;

    @Column(name = "nonce_hash", nullable = false, length = 64)
    private String nonceHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
