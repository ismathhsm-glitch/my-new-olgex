package com.olgav.invoicesecurity.service;

import com.olgav.invoicesecurity.model.TrustedDevice;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.LoginAttemptRepository;
import com.olgav.invoicesecurity.repository.TrustedDeviceRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskScoringService {
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final GeoLocationService geoLocationService;

    public RiskScoringService(
            TrustedDeviceRepository trustedDeviceRepository,
            LoginAttemptRepository loginAttemptRepository,
            GeoLocationService geoLocationService) {
        this.trustedDeviceRepository = trustedDeviceRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.geoLocationService = geoLocationService;
    }

    public RiskScore score(User user, String deviceHash, String ipAddress, String userAgent) {
        if (user.getCreatedAt() != null
                && user.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24))
                && !loginAttemptRepository.existsByEmail(user.getEmail())) {
            return new RiskScore(0, List.of("INITIAL_DEVICE_ENROLLMENT"));
        }
        int score = 0;
        List<String> reasons = new ArrayList<>();
        boolean knownDevice = deviceHash != null
                && trustedDeviceRepository.existsByUserIdAndDeviceHashAndTrustedTrue(user.getId(), deviceHash);
        if (!knownDevice) {
            score += 30;
            reasons.add("UNRECOGNIZED_DEVICE");
        }

        GeoLocationService.Location location = geoLocationService.locate(ipAddress);
        List<TrustedDevice> knownDevices = trustedDeviceRepository.findByUserId(user.getId());
        boolean knownIp = knownDevices.stream().anyMatch(device -> ipAddress != null
                && (ipAddress.equals(device.getIpAddress()) || ipAddress.equals(device.getDeviceIdentifier())));
        if (!knownIp) {
            score += 15;
            reasons.add("UNRECOGNIZED_IP");
        }
        if (location == null || "UNKNOWN".equalsIgnoreCase(location.country())) {
            score += 5;
            reasons.add("LOCATION_UNAVAILABLE");
        }

        if (userAgent != null && knownDevices.stream().noneMatch(device -> userAgent.equals(device.getUserAgent()))) {
            score += 10;
            reasons.add("NEW_USER_AGENT");
        }

        int hour = LocalDateTime.now().getHour();
        List<com.olgav.invoicesecurity.model.LoginAttempt> successfulLogins =
                loginAttemptRepository.findTop50ByEmailAndIsSuccessfulTrueOrderByAttemptedAtDesc(user.getEmail());
        boolean unusualHour = successfulLogins.isEmpty()
                ? hour < 5
                : successfulLogins.stream().noneMatch(attempt ->
                        hourDistance(hour, attempt.getAttemptedAt().getHour()) <= 3);
        if (unusualHour) {
            score += 10;
            reasons.add("UNUSUAL_LOGIN_TIME");
        }

        long recentFailures = loginAttemptRepository.countByEmailAndAttemptedAtAfterAndIsSuccessfulFalse(
                user.getEmail(), LocalDateTime.now().minusHours(24));
        if (recentFailures > 0) {
            score += (int) Math.min(35, recentFailures * 10);
            reasons.add("RECENT_FAILED_ATTEMPTS:" + recentFailures);
        }

        LocalDateTime now = LocalDateTime.now();
        boolean rapidLocationChange = knownDevices.stream().anyMatch(device -> device.getLastSeen() != null
                && Duration.between(device.getLastSeen(), now).abs().toHours() < 2
                && location != null && device.getLastLocation() != null
                && !device.getLastLocation().equalsIgnoreCase(location.displayName()));
        if (rapidLocationChange) {
            score += 40;
            reasons.add("IMPOSSIBLE_TRAVEL");
        }
        return new RiskScore(Math.min(score, 100), List.copyOf(reasons));
    }

    private static int hourDistance(int first, int second) {
        int distance = Math.abs(first - second);
        return Math.min(distance, 24 - distance);
    }

    public record RiskScore(int score, List<String> reasons) {
    }
}
