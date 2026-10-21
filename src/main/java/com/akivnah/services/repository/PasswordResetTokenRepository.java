package com.akivnah.services.repository;

import com.akivnah.services.model.PasswordResetToken;
import com.akivnah.services.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    long countByUserAndCreatedAtAfter(UserAccount user, Instant since);

    @Modifying
    @Query("delete from PasswordResetToken t where t.user = :u")
    void deleteAllForUser(@Param("u") UserAccount u);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
