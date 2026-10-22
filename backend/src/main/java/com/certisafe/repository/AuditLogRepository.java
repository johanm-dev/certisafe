package com.certisafe.repository;

import com.certisafe.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    long countByAction(String action);

    /** Filas de una accion concreta para un usuario: lo que comprueban las pruebas. */
    long countByUser_IdAndAction(Long userId, String action);
}
