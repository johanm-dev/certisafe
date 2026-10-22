package com.certisafe;

import com.certisafe.common.error.BusinessException;
import com.certisafe.domain.AttendanceStatus;
import com.certisafe.domain.User;
import com.certisafe.domain.WorkshopStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Inscripciones (RF-015, RF-012), otra de las reglas que RNF-014 pide probar.
 *
 * <p>Las tres comprobaciones que se repiten en la documentacion —estado, duplicado y aforo— se
 * hacen en ese orden, y el orden importa: el taller se bloquea antes de contar, porque si se
 * valida primero y se bloquea despues se cuela la carrera de las inscripciones simultaneas
 * (probada aparte en {@link ConcurrenciaAforoIT}).</p>
 */
@SpringBootTest
@Transactional
@DisplayName("RF-015 / RF-012: reglas de inscripción y asistencia")
class InscripcionReglasIT extends ReglasDeNegocioIT {

    private Long adminId;
    private Long tallerId;

    @BeforeEach
    void preparar() {
        adminId = crearAdministrador(sufijo()).getId();
        tallerId = crearTaller(adminId, sufijo(), 1);
    }

    @Test
    void inscribirFuncionaYReportaLosCupos() {
        User operario = crearOperario(sufijo());

        var inscripcion = enrollmentService.enroll(tallerId, operario.getId(), principal(adminId));

        assertThat(inscripcion.userId()).isEqualTo(operario.getId());
        assertThat(inscripcion.workshopEnrolledCount()).isEqualTo(1);
        assertThat(inscripcion.availableSeats()).isZero();
        assertThat(inscripcion.attendanceStatus()).isEqualTo(AttendanceStatus.PENDIENTE);
    }

    @Test
    void laSegundaInscripcionDelMismoOperarioSeRechaza() {
        User operario = crearOperario(sufijo());
        enrollmentService.enroll(tallerId, operario.getId(), principal(adminId));

        assertThatThrownBy(() -> enrollmentService.enroll(tallerId, operario.getId(), principal(adminId)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ya estás inscrito en este taller")
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void elSegundoOperarioEncuentraElAforoCompleto() {
        User primero = crearOperario(sufijo());
        User segundo = crearOperario(sufijo());
        enrollmentService.enroll(tallerId, primero.getId(), principal(adminId));

        assertThatThrownBy(() -> enrollmentService.enroll(tallerId, segundo.getId(), principal(adminId)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Aforo completo")
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void soloSeInscribeEnUnTallerProgramado() {
        User operario = crearOperario(sufijo());
        workshopService.changeStatus(tallerId, WorkshopStatus.EN_PROGRESO, "Empieza", principal(adminId));

        assertThatThrownBy(() -> enrollmentService.enroll(tallerId, operario.getId(), principal(adminId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Solo se puede inscribir a un taller PROGRAMADO");
    }

    @Test
    void unTallerCanceladoNoAdmiteInscripciones() {
        User operario = crearOperario(sufijo());
        workshopService.changeStatus(tallerId, WorkshopStatus.CANCELADO, "Imposible dictarlo", principal(adminId));

        assertThatThrownBy(() -> enrollmentService.enroll(tallerId, operario.getId(), principal(adminId)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unInstructorNoPuedeInscribirOperarios() {
        User instructor = crearInstructor(sufijo());
        User operario = crearOperario(sufijo());

        assertThatThrownBy(() ->
                enrollmentService.enroll(tallerId, operario.getId(), principal(instructor.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Un INSTRUCTOR no puede inscribir operarios")
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unOperarioSoloPuedeInscribirseASiMismo() {
        User operario = crearOperario(sufijo());
        User otroOperario = crearOperario(sufijo());

        assertThatThrownBy(() ->
                enrollmentService.enroll(tallerId, otroOperario.getId(), principal(operario.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Un OPERATOR solo puede inscribirse a sí mismo")
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Y a si mismo si puede.
        enrollmentService.enroll(tallerId, operario.getId(), principal(operario.getId()));
    }

    @Test
    void laAsistenciaSoloSeRegistraEnProgresoOFinalizado() {
        User operario = crearOperario(sufijo());
        var inscripcion = enrollmentService.enroll(tallerId, operario.getId(), principal(adminId));

        assertThatThrownBy(() ->
                enrollmentService.registerAttendance(inscripcion.id(), AttendanceStatus.ASISTIO, principal(adminId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Solo se puede registrar asistencia con el taller en progreso o finalizado");
    }

    @Test
    void unOperarioNoPuedeGestionarLaAsistencia() {
        User operario = crearOperario(sufijo());
        var inscripcion = enrollmentService.enroll(tallerId, operario.getId(), principal(adminId));
        workshopService.changeStatus(tallerId, WorkshopStatus.EN_PROGRESO, "Empieza", principal(adminId));

        assertThatThrownBy(() ->
                enrollmentService.registerAttendance(inscripcion.id(), AttendanceStatus.ASISTIO, principal(operario.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Un OPERATOR no puede gestionar la asistencia")
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unInstructorQueNoEsElAsignadoTampocoGestionaLaAsistencia() {
        User instructor = crearInstructor(sufijo());
        User operario = crearOperario(sufijo());
        var inscripcion = enrollmentService.enroll(tallerId, operario.getId(), principal(adminId));
        workshopService.changeStatus(tallerId, WorkshopStatus.EN_PROGRESO, "Empieza", principal(adminId));

        assertThatThrownBy(() ->
                enrollmentService.registerAttendance(inscripcion.id(), AttendanceStatus.ASISTIO, principal(instructor.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Solo el instructor asignado")
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
