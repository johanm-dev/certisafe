package com.certisafe.repository;

import com.certisafe.domain.AttendanceStatus;
import com.certisafe.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long>,
        JpaSpecificationExecutor<Enrollment> {

    boolean existsByUserIdAndWorkshopId(Long userId, Long workshopId);

    Optional<Enrollment> findByUserIdAndWorkshopId(Long userId, Long workshopId);

    long countByWorkshopId(Long workshopId);

    long countByWorkshopIdAndAttendanceStatus(Long workshopId, AttendanceStatus attendanceStatus);

    /**
     * Cuenta las inscripciones de VARIOS talleres en una sola consulta.
     *
     * <p>La usan los listados paginados. Sin esto habria que contar taller por taller y
     * saldria una consulta por fila: el clasico N+1. Devuelve pares
     * {@code [workshopId, total]}.</p>
     */
    @Query("SELECT e.workshop.id, COUNT(e) FROM Enrollment e "
            + "WHERE e.workshop.id IN :workshopIds GROUP BY e.workshop.id")
    List<Object[]> countGroupedByWorkshopId(@Param("workshopIds") Collection<Long> workshopIds);

    /**
     * Una inscripcion con certificado emitido no se puede borrar: la FK de
     * {@code certificates.enrollment_id} es {@code ON DELETE RESTRICT} y el motor lanzaria un
     * error crudo de integridad. Se comprueba antes para responder 409 con un mensaje util.
     */
    @Query(value = "SELECT count(*) FROM certificates WHERE enrollment_id = :enrollmentId",
            nativeQuery = true)
    long countCertificates(@Param("enrollmentId") Long enrollmentId);
}
