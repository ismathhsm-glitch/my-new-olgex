package com.olgav.invoicesecurity.repository;

import com.olgav.invoicesecurity.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash and t.revoked = false")
    Optional<RefreshToken> findActiveForUpdate(@Param("hash") String hash);
    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);
}
