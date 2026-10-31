package com.olgav.invoicesecurity.repository;

import com.olgav.invoicesecurity.model.StepUpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StepUpChallengeRepository extends JpaRepository<StepUpChallenge, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StepUpChallenge c where c.id = :id and c.user.id = :userId")
    Optional<StepUpChallenge> findForUpdate(@Param("id") String id, @Param("userId") Long userId);
}
