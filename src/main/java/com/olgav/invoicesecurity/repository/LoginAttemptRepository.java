package com.olgav.invoicesecurity.repository;

import com.olgav.invoicesecurity.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    List<LoginAttempt> findByEmail(String email);
    boolean existsByEmail(String email);
    List<LoginAttempt> findTop50ByEmailAndIsSuccessfulTrueOrderByAttemptedAtDesc(String email);
    long countByEmailAndAttemptedAtAfterAndIsSuccessfulFalse(String email, LocalDateTime since);
    boolean existsByEmailAndIpAddressAndIsSuccessfulTrueAndAttemptedAtAfter(
            String email, String ipAddress, LocalDateTime since);
}