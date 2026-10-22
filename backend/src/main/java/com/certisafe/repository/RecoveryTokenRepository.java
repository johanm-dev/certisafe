package com.certisafe.repository;

import com.certisafe.domain.RecoveryToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecoveryTokenRepository extends JpaRepository<RecoveryToken, Long> {

    Optional<RecoveryToken> findByToken(String token);

    /**
     * Quema todos los enlaces pendientes del usuario (tarea 9 de la HU-17).
     *
     * <p>Se invalidan los anteriores al usar uno: si alguien pidio dos enlaces, el primero
     * deja de servir en cuanto se usa el segundo. Deja uno solo vivo por usuario, que es lo
     * que espera el criterio de «de un solo uso».</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RecoveryToken r set r.used = true where r.user.id = :userId and r.used = false")
    int invalidatePendingForUser(@Param("userId") Long userId);
}
