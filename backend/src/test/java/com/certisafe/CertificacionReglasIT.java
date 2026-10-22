package com.certisafe;

import com.certisafe.certificate.dto.CertificateResponse;
import com.certisafe.common.PageResponse;
import com.certisafe.domain.AttendanceStatus;
import com.certisafe.domain.CertificateStatus;
import com.certisafe.domain.WorkshopStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Certificaciones (RF-013 emisión y RF-016 consulta), la tercera regla de RNF-014.
 *
 * <p>La regla que sostiene todo lo demás: el certificado no se emite porque alguien lo pida,
 * sino como consecuencia de registrar asistencia. El primer criterio del requerimiento es
 * «asistió &rarr; hay certificación» y el segundo «ausente &rarr; no hay»; el tercero es la
 * vigencia. Este archivo prueba los tres.</p>
 */
@SpringBootTest
@Transactional
@DisplayName("RF-013 / RF-016: emisión, revocación y vigencia del certificado")
class CertificacionReglasIT extends ReglasDeNegocioIT {

    private Long adminId;
    private Long tallerId;
    private Long instructorId;
    private Long operarioId;
    private Long inscripcionId;

    @BeforeEach
    void preparar() {
        adminId = crearAdministrador(sufijo()).getId();
        instructorId = crearInstructor(sufijo()).getId();
        operarioId = crearOperario(sufijo()).getId();
        tallerId = crearTaller(adminId, sufijo(), 10);
        workshopService.assignInstructor(tallerId, instructorId, adminId);
        // Primero se inscribe: el taller esta PROGRAMADO. Despues arranca, y es entonces
        // cuando se puede registrar la asistencia.
        inscripcionId = enrollmentService.enroll(tallerId, operarioId, principal(adminId)).id();
        workshopService.changeStatus(tallerId, WorkshopStatus.EN_PROGRESO, "Empieza la sesión", principal(adminId));
    }

    private CertificateResponse certificadoDelOperario() {
        PageResponse<CertificateResponse> pagina = certificateService.mine(Pageable.ofSize(10), operarioId);
        assertThat(pagina.content()).hasSize(1);
        return pagina.content().get(0);
    }

    @Test
    void sinAsistenciaNoHayCertificado() {
        PageResponse<CertificateResponse> pagina = certificateService.mine(Pageable.ofSize(10), operarioId);
        assertThat(pagina.content()).isEmpty();
    }

    @Test
    void asistirEmiteElCertificadoConVigencia() {
        enrollmentService.registerAttendance(inscripcionId, AttendanceStatus.ASISTIO, principal(adminId));

        CertificateResponse certificado = certificadoDelOperario();
        assertThat(certificado.certificateNumber()).matches("CS-\\d{4}-\\d+");
        assertThat(certificado.status()).isEqualTo(CertificateStatus.VIGENTE);
        assertThat(certificado.revoked()).isFalse();
        assertThat(certificado.expiresAt()).isNotNull();
        assertThat(certificado.issuedAt()).isNotNull();
    }

    @Test
    void laCertificacionEsIdempotente() {
        enrollmentService.registerAttendance(inscripcionId, AttendanceStatus.ASISTIO, principal(adminId));
        var primero = certificadoDelOperario();

        enrollmentService.registerAttendance(inscripcionId, AttendanceStatus.ASISTIO, principal(adminId));
        var segundo = certificadoDelOperario();

        // Emitir dos veces no genera dos certificados: el numero es UNIQUE por inscripcion y
        // un certificado duplicado no serviria para nada.
        assertThat(segundo.id()).isEqualTo(primero.id());
        assertThat(certificateService.mine(Pageable.ofSize(10), operarioId).content()).hasSize(1);
    }

    @Test
    void ausentarRevocaElCertificado() {
        enrollmentService.registerAttendance(inscripcionId, AttendanceStatus.ASISTIO, principal(adminId));

        enrollmentService.registerAttendance(inscripcionId, AttendanceStatus.AUSENTE, principal(adminId));

        CertificateResponse certificado = certificadoDelOperario();
        assertThat(certificado.revoked()).isTrue();
        assertThat(certificado.revocationReason()).isNotBlank();
        assertThat(certificado.status()).isEqualTo(CertificateStatus.REVOCADA);
    }

    @Test
    void corregirLaAsistenciaRehabilitaElMismoCertificado() {
        enrollmentService.registerAttendance(inscripcionId, AttendanceStatus.ASISTIO, principal(adminId));
        var emitido = certificadoDelOperario();

        enrollmentService.registerAttendance(inscripcionId, AttendanceStatus.AUSENTE, principal(adminId));
        enrollmentService.registerAttendance(inscripcionId, AttendanceStatus.ASISTIO, principal(adminId));

        CertificateResponse corregido = certificadoDelOperario();
        // Lo que estaba mal era el dato de asistencia, no el certificado: se rehabilita en vez
        // de emitir otro, que ademas no cabria (enrollment_id es UNIQUE).
        assertThat(corregido.id()).isEqualTo(emitido.id());
        assertThat(corregido.revoked()).isFalse();
        assertThat(corregido.status()).isEqualTo(CertificateStatus.VIGENTE);
    }

    @Test
    void elInstructorAsignadoEsElResponsableDelCertificado() {
        enrollmentService.registerAttendance(inscripcionId, AttendanceStatus.ASISTIO, principal(instructorId));

        assertThat(certificadoDelOperario().instructorName()).contains("instructor.");
    }
}
