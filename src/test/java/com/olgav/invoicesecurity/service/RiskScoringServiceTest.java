package com.olgav.invoicesecurity.service;

import com.olgav.invoicesecurity.model.TrustedDevice;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.LoginAttemptRepository;
import com.olgav.invoicesecurity.repository.TrustedDeviceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class RiskScoringServiceTest {
    private final TrustedDeviceRepository devices = mock(TrustedDeviceRepository.class);
    private final LoginAttemptRepository attempts = mock(LoginAttemptRepository.class);
    private final GeoLocationService geo = ip -> new GeoLocationService.Location("US", "Seattle");
    private final RiskScoringService service = new RiskScoringService(devices, attempts, geo);

    @Test
    void flagsAnUnrecognizedDeviceAndNetwork() {
        User user = user();
        when(devices.existsByUserIdAndDeviceHashAndTrustedTrue(7L, "device-hash")).thenReturn(false);
        when(devices.findByUserId(7L)).thenReturn(List.of());
        when(attempts.countByEmailAndAttemptedAtAfterAndIsSuccessfulFalse(
                eq("user@example.test"), any(LocalDateTime.class))).thenReturn(0L);

        RiskScoringService.RiskScore result = service.score(user, "device-hash", "203.0.113.8", "Browser");

        assertTrue(result.score() >= 40);
        assertTrue(result.reasons().contains("UNRECOGNIZED_DEVICE"));
        assertTrue(result.reasons().contains("UNRECOGNIZED_IP"));
    }

    @Test
    void doesNotFlagKnownDeviceNetworkOrUserAgent() {
        User user = user();
        TrustedDevice device = new TrustedDevice();
        device.setIpAddress("203.0.113.8");
        device.setUserAgent("Browser");
        device.setLastLocation("Seattle, US");
        device.setLastSeen(LocalDateTime.now().minusDays(1));
        when(devices.existsByUserIdAndDeviceHashAndTrustedTrue(7L, "device-hash")).thenReturn(true);
        when(devices.findByUserId(7L)).thenReturn(List.of(device));
        when(attempts.countByEmailAndAttemptedAtAfterAndIsSuccessfulFalse(
                eq("user@example.test"), any(LocalDateTime.class))).thenReturn(0L);

        RiskScoringService.RiskScore result = service.score(user, "device-hash", "203.0.113.8", "Browser");

        assertFalse(result.reasons().contains("UNRECOGNIZED_DEVICE"));
        assertFalse(result.reasons().contains("UNRECOGNIZED_IP"));
        assertFalse(result.reasons().contains("NEW_USER_AGENT"));
    }

    private static User user() {
        User user = new User();
        user.setId(7L);
        user.setEmail("user@example.test");
        user.setCreatedAt(LocalDateTime.now().minusDays(5));
        return user;
    }
}
