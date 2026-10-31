package com.olgav.invoicesecurity.controller;

import com.olgav.invoicesecurity.dto.ApiResponse;
import com.olgav.invoicesecurity.model.TrustedDevice;
import com.olgav.invoicesecurity.repository.TrustedDeviceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices")
@SecurityRequirement(name = "bearerAuth")
public class DeviceController {
    private final TrustedDeviceRepository deviceRepository;
    private final com.olgav.invoicesecurity.service.AuditLoggingService auditLoggingService;

    public DeviceController(TrustedDeviceRepository deviceRepository,
                            com.olgav.invoicesecurity.service.AuditLoggingService auditLoggingService) {
        this.deviceRepository = deviceRepository;
        this.auditLoggingService = auditLoggingService;
    }

    @Operation(summary = "List the current user's recognized devices")
    @GetMapping
    public ApiResponse<List<DeviceDto>> list(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(deviceRepository.findByUserId(userId).stream().map(DeviceDto::from).toList());
    }

    @Operation(summary = "Revoke a device")
    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, String>> revoke(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        TrustedDevice device = ownedDevice(id, userId);
        Long organizationId = device.getUser().getOrganization() == null
                ? null : device.getUser().getOrganization().getId();
        deviceRepository.delete(device);
        auditLoggingService.record(userId, organizationId, "DEVICE_REVOKED");
        return ApiResponse.ok(Map.of("status", "DEVICE_REVOKED"));
    }

    @Operation(summary = "Mark a device as trusted")
    @PostMapping("/{id}/trust")
    @Transactional
    public ApiResponse<DeviceDto> trust(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        TrustedDevice device = ownedDevice(id, userId);
        device.setTrusted(true);
        device.setLastSeen(LocalDateTime.now());
        device = deviceRepository.save(device);
        Long organizationId = device.getUser().getOrganization() == null
                ? null : device.getUser().getOrganization().getId();
        auditLoggingService.record(userId, organizationId, "DEVICE_TRUSTED");
        return ApiResponse.ok(DeviceDto.from(device));
    }

    private TrustedDevice ownedDevice(Long id, Long userId) {
        TrustedDevice device = deviceRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        if (!device.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found");
        }
        return device;
    }

    public record DeviceDto(Long id, String deviceName, String maskedIp, boolean trusted,
                            LocalDateTime firstSeen, LocalDateTime lastSeen) {
        static DeviceDto from(TrustedDevice device) {
            String ip = device.getIpAddress();
            String masked = maskIp(ip);
            return new DeviceDto(device.getId(), device.getDeviceName(), masked, device.isTrusted(),
                    device.getFirstSeen(), device.getLastSeen());
        }

        private static String maskIp(String ip) {
            if (ip == null) return null;
            if (ip.contains(":")) return ip.substring(0, Math.min(4, ip.length())) + ":...";
            String[] octets = ip.split("\\.");
            return octets.length == 4 ? octets[0] + ".***.***." + octets[3] : "***";
        }
    }
}
