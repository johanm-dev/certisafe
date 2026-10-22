package com.certisafe;

import com.certisafe.common.error.BusinessException;
import com.certisafe.common.error.NotFoundException;
import com.certisafe.domain.User;
import com.certisafe.domain.WorkshopStatus;
import com.certisafe.workshop.dto.StatusHistoryResponse;
import com.certisafe.workshop.dto.WorkshopUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RNF-016 (ciclo de vida de los talleres), una de las tres reglas que RNF-014 pide probar.
 *
 * <p>El ciclo se implementa como una maquina de estados: desde cada estado solo salen las
 * transiciones declaradas. Los dos comportamientos que mas se prestan a error son los saltos
 * prohibidos (PROGRAMADO &rarr; FINALIZADO) y que el historial quede completo, que es lo que
 * RNF-013 exige para auditar.</p>
 */
@SpringBootTest
@Transactional
@DisplayName("RNF-016: ciclo de vida de los talleres")
class TallerCicloDeVidaIT extends ReglasDeNegocioIT {

    private Long adminId;
    private Long tallerId;

    @BeforeEach
    void preparar() {
        adminId = crearAdministrador(sufijo()).getId();
        tallerId = crearTaller(adminId, sufijo(), 10);
    }

    @Test
    void naceProgramadoYSoloOfreceLasSalidasDeEseEstado() {
        assertThat(statusDe(tallerId, adminId)).isEqualTo("PROGRAMADO");

        List<String> salidas = workshopService.get(tallerId, principal(adminId)).allowedTransitions();
        assertThat(salidas).containsExactlyInAnyOrder("EN_PROGRESO", "CANCELADO");
    }

    @Test
    void noSePuedeSaltarDeProgramadoAFinalizado() {
        assertThatThrownBy(() ->
                workshopService.changeStatus(tallerId, WorkshopStatus.FINALIZADO, "me ahorro pasos", principal(adminId)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(statusDe(tallerId, adminId)).isEqualTo("PROGRAMADO");
    }

    @Test
    void elCicloNormalEsProgramadoEnProgresoFinalizado() {
        workshopService.changeStatus(tallerId, WorkshopStatus.EN_PROGRESO, "Empieza la sesión", principal(adminId));
        assertThat(statusDe(tallerId, adminId)).isEqualTo("EN_PROGRESO");

        workshopService.changeStatus(tallerId, WorkshopStatus.FINALIZADO, "Termina la sesión", principal(adminId));
        assertThat(statusDe(tallerId, adminId)).isEqualTo("FINALIZADO");
    }

    @Test
    void unTallerYaDictadoNoSePuedeVolverAEjecutar() {
        workshopService.changeStatus(tallerId, WorkshopStatus.EN_PROGRESO, "Empieza", principal(adminId));
        workshopService.changeStatus(tallerId, WorkshopStatus.FINALIZADO, "Termina", principal(adminId));

        assertThatThrownBy(() ->
                workshopService.changeStatus(tallerId, WorkshopStatus.EN_PROGRESO, "otra vez", principal(adminId)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancelarExigeMotivoYReglaLaCosa() {
        assertThatThrownBy(() ->
                workshopService.changeStatus(tallerId, WorkshopStatus.CANCELADO, "  ", principal(adminId)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        workshopService.changeStatus(tallerId, WorkshopStatus.CANCELADO, "Lluvia", principal(adminId));
        assertThat(statusDe(tallerId, adminId)).isEqualTo("CANCELADO");
    }

    @Test
    void elHistorialGuardaElNacimientoYCadaCambio() {
        workshopService.changeStatus(tallerId, WorkshopStatus.EN_PROGRESO, "Empieza", principal(adminId));
        workshopService.changeStatus(tallerId, WorkshopStatus.FINALIZADO, "Termina", principal(adminId));

        List<StatusHistoryResponse> historial = workshopService.history(tallerId, principal(adminId));

        assertThat(historial).hasSize(3);
        assertThat(historial.get(0).previousStatus()).isNull();
        assertThat(historial.get(0).newStatus()).isEqualTo("PROGRAMADO");
        assertThat(historial.get(1).previousStatus()).isEqualTo("PROGRAMADO");
        assertThat(historial.get(1).newStatus()).isEqualTo("EN_PROGRESO");
        assertThat(historial.get(2).newStatus()).isEqualTo("FINALIZADO");
    }

    @Test
    void unTallerProgramadoSinInscripcionesSePuedeBorrar() {
        workshopService.delete(tallerId);

        assertThatThrownBy(() -> workshopService.get(tallerId, principal(adminId)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unTallerDictadoNoSePuedeBorrar() {
        workshopService.changeStatus(tallerId, WorkshopStatus.EN_PROGRESO, "Empieza", principal(adminId));
        workshopService.changeStatus(tallerId, WorkshopStatus.FINALIZADO, "Termina", principal(adminId));

        assertThatThrownBy(() -> workshopService.delete(tallerId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(statusDe(tallerId, adminId)).isEqualTo("FINALIZADO");
    }

    @Test
    void bajarElAforoPorDebajoDeLosInscritosSeRechaza() {
        User operario = crearOperario(sufijo());
        enrollmentService.enroll(tallerId, operario.getId(), principal(adminId));

        assertThatThrownBy(() -> workshopService.update(
                tallerId,
                new WorkshopUpdateRequest(
                        "Taller de prueba", "Descripcion", fechaFutura(), 120, "Sala", 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private static OffsetDateTime fechaFutura() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(31);
    }
}
