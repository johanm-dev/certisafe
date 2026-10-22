package com.certisafe.repository;

import com.certisafe.domain.Workshop;
import com.certisafe.domain.WorkshopStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkshopRepository extends JpaRepository<Workshop, Long>, JpaSpecificationExecutor<Workshop> {

    long countByStatus(WorkshopStatus status);

    /**
     * Bloquea la fila del taller ({@code SELECT ... FOR UPDATE}) para el control de aforo de
     * RF-015.
     *
     * <p>Mientras una transaccion mantiene el bloqueo, cualquier otra inscripcion al mismo
     * taller espera a que termine. Eso serializa las inscripciones de un taller y es lo que
     * evita que dos solicitudes simultaneas lean la misma cuenta de cupos y metan una de mas:
     * el cuarto criterio de aceptacion de RF-015 ("si queda un solo cupo y dos operarios se
     * inscriben al mismo tiempo, solo una inscripcion se registra") no se puede cumplir
     * contando sin bloquear.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Workshop w WHERE w.id = :id")
    Optional<Workshop> findByIdForUpdate(@Param("id") Long id);

    /**
     * Cuenta certificados de un taller sin necesitar la entidad Certificate (que llega en otra
     * rebanada). Se usa para impedir borrar un taller que ya certifico a alguien.
     */
    @Query(value = "SELECT count(*) FROM certificates WHERE workshop_id = :workshopId", nativeQuery = true)
    long countCertificates(@Param("workshopId") Long workshopId);
}
