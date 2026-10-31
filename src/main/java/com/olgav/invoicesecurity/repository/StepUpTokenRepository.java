package com.olgav.invoicesecurity.repository;

import com.olgav.invoicesecurity.model.StepUpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface StepUpTokenRepository extends JpaRepository<StepUpToken, Long> {
    Optional<StepUpToken> findByTokenHashAndUserId(String tokenHash, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from StepUpToken t where t.tokenHash = :hash and t.user.id = :userId")
    Optional<StepUpToken> findForUpdate(@Param("hash") String hash, @Param("userId") Long userId);
}
