package com.olgav.invoicesecurity.repository;

import com.olgav.invoicesecurity.model.OrganizationInvitationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganizationInvitationTokenRepository extends JpaRepository<OrganizationInvitationToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OrganizationInvitationToken t where t.user.id = :userId "
            + "and t.tokenHash = :hash and t.used = false")
    Optional<OrganizationInvitationToken> findUnusedForUpdate(
            @Param("userId") Long userId, @Param("hash") String hash);
}
