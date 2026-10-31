package com.olgav.invoicesecurity.repository;

import com.olgav.invoicesecurity.model.TrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {
    List<TrustedDevice> findByUserId(Long userId);
    boolean existsByUserIdAndDeviceIdentifier(Long userId, String deviceIdentifier);
    boolean existsByUserIdAndDeviceHashAndTrustedTrue(Long userId, String deviceHash);
}