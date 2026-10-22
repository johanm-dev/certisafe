package com.certisafe.repository;

import com.certisafe.domain.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long>,
        JpaSpecificationExecutor<Certificate> {

    Optional<Certificate> findByEnrollmentId(Long enrollmentId);

    @Query("SELECT c.certificateNumber FROM Certificate c WHERE c.enrollment.id = :enrollmentId")
    Optional<String> findNumberByEnrollmentId(@Param("enrollmentId") Long enrollmentId);

    /**
     * Numeros de certificado de VARIAS inscripciones en una sola consulta.
     *
     * <p>La usa el listado de inscripciones para mostrar, junto a cada fila, el certificado
     * que genero. Sin esto seria una consulta por fila (N+1). Devuelve pares
     * {@code [enrollmentId, certificateNumber]}.</p>
     */
    @Query("SELECT c.enrollment.id, c.certificateNumber FROM Certificate c "
            + "WHERE c.enrollment.id IN :enrollmentIds")
    List<Object[]> findNumbersByEnrollmentIds(@Param("enrollmentIds") Collection<Long> enrollmentIds);
}
