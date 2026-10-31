package com.olgav.invoicesecurity.repository;

import com.olgav.invoicesecurity.model.LoginChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LoginChallenge c where c.id = :id and c.user.id = :userId")
    Optional<LoginChallenge> findForUpdate(@Param("id") String id, @Param("userId") Long userId);
}
