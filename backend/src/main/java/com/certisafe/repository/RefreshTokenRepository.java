package com.certisafe.repository;

import com.certisafe.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    /** Invalidacion masiva (p.ej. al cambiar contraseña, RF-024). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken r set r.used = true where r.user.id = :userId and r.used = false")
    int invalidateAllForUser(@Param("userId") Long userId);

    /** Invalida todos menos el token indicado (rotacion en refresh). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken r set r.used = true
            where r.user.id = :userId and r.token <> :keepToken and r.used = false
            """)
    int invalidateAllForUserExcept(@Param("userId") Long userId, @Param("keepToken") String keepToken);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken r where r.expiresAt < :now")
    int deleteExpired(@Param("now") OffsetDateTime now);
}
