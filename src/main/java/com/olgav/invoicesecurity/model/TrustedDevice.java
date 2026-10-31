package com.olgav.invoicesecurity.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trusted_devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrustedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_identifier", nullable = false)
    private String deviceIdentifier;

    @Column(name = "device_hash", length = 64)
    private String deviceHash;

    private String deviceName;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "last_location")
    private String lastLocation;

    @Column(nullable = false)
    private boolean trusted;

    @Column(name = "first_seen")
    private LocalDateTime firstSeen = LocalDateTime.now();

    @Column(name = "last_seen")
    private LocalDateTime lastSeen = LocalDateTime.now();

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt = LocalDateTime.now();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}