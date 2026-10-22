package com.certisafe;

import com.certisafe.common.error.BusinessException;
import com.certisafe.domain.BatchStatus;
import com.certisafe.domain.User;
import com.certisafe.importer.ImportService;
import com.certisafe.importer.dto.ImportResultResponse;
import com.certisafe.security.UserPrincipal;
import com.certisafe.workshop.WorkshopService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Carga masiva (RF-017, RF-018), la tercera regla que RNF-014 nombra.
 *
 * <p>Lo que se prueba no es que el archivo se lea, sino lo que la documentacion promete y que
 * es facil romper: las filas buenas se guardan y las malas no, cada fila rechazada se explica
 * con su numero, y el lote completo se revierte si algo falla al escribir.</p>
 *
 * <p><b>Esta prueba no va envuelta en la transaccion de prueba.</b> El lote se registra con
 * {@code REQUIRES_NEW} a proposito —si el lote quedara dentro de la transaccion de la
 * importacion, el reporte de errores desapareceria con el rollback que el CA3 de la HU-11
 * pide—, y una transaccion nueva no ve lo que la transaccion de prueba todavia no ha
 * confirmado. Los datos se crean y se borran de verdad.</p>
 */
@SpringBootTest
@DisplayName("RF-017 / RF-018: carga masiva con reporte por fila")
class CargaMasivaIT extends ReglasDeNegocioIT {

    private static final String TALLER_IMPORTADO = "Taller importado";

    @Autowired
    private ImportService importService;

    @Autowired
    private JdbcTemplate jdbc;

    private UserPrincipal admin;

    @BeforeEach
    void preparar() {
        admin = principal(crearAdministrador(sufijo()).getId());
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM status_history WHERE workshop_id IN "
                + "(SELECT id FROM workshops WHERE name = ?)", TALLER_IMPORTADO);
        jdbc.update("DELETE FROM workshop_assignments WHERE workshop_id IN "
                + "(SELECT id FROM workshops WHERE name = ?)", TALLER_IMPORTADO);
        jdbc.update("DELETE FROM workshops WHERE name = ?", TALLER_IMPORTADO);
        jdbc.update("DELETE FROM import_errors WHERE batch_id IN (SELECT id FROM import_batches "
                + "WHERE uploaded_by IN (SELECT id FROM users WHERE email LIKE '%@certisafe.test'))");
        jdbc.update("DELETE FROM import_batches WHERE uploaded_by IN (SELECT id FROM users "
                + "WHERE email LIKE '%@certisafe.test')");
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN "
                + "(SELECT id FROM users WHERE email LIKE '%@certisafe.test')");
        jdbc.update("DELETE FROM audit_logs WHERE user_id IN "
                + "(SELECT id FROM users WHERE email LIKE '%@certisafe.test')");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@certisafe.test'");
    }

    private ImportResultResponse importarUsuarios(String csv, String nombreArchivo) {
        return importService.importUsers(
                new MockMultipartFile("archivo", nombreArchivo, "text/csv", csv.getBytes(StandardCharsets.UTF_8)),
                admin);
    }

    private ImportResultResponse importarTalleres(String csv, String nombreArchivo) {
        return importService.importWorkshops(
                new MockMultipartFile("archivo", nombreArchivo, "text/csv", csv.getBytes(StandardCharsets.UTF_8)),
                admin);
    }

    @Test
    void insertaLasFilasBuenasYReportaLasMalas() {
        String csv = "correo,nombres,apellidos,contrasena,telefono,rol\n"
                + "uno@certisafe.test,Uno,Primer,Prueba123*,3001112233,OPERATOR\n"
                + "dos@certisafe.test,Dos,Segundo,Prueba123*,3001112233,OPERATOR\n"
                + "no-es-un-correo,Malo,Tercero,Prueba123*,3001112233,OPERATOR\n";

        ImportResultResponse resultado = importarUsuarios(csv, "operarios.csv");

        assertThat(resultado.totalRows()).isEqualTo(3);
        assertThat(resultado.createdRows()).isEqualTo(2);
        assertThat(resultado.rejectedRows()).isEqualTo(1);
        assertThat(resultado.status()).isEqualTo(BatchStatus.CON_ERRORES);
        assertThat(resultado.errors()).hasSize(1);
        // El numero de fila es la linea del archivo, contando la cabecera: asi el ADMIN abre
        // el archivo y va directo a la fila que fallo.
        assertThat(resultado.errors().get(0).rowNumber()).isEqualTo(4);
        assertThat(resultado.errors().get(0).reason()).isNotBlank();
        assertThat(userRepository.findByEmail("uno@certisafe.test")).isPresent();
        assertThat(userRepository.findByEmail("dos@certisafe.test")).isPresent();
        assertThat(userRepository.findByEmail("no-es-un-correo")).isEmpty();
    }

    @Test
    void unArchivoSinErroresQuedaCompletado() {
        String csv = "correo,nombres,apellidos,contrasena,telefono,rol\n"
                + "limpio@certisafe.test,Limpio,Completo,Prueba123*,3001112233,OPERATOR\n";

        ImportResultResponse resultado = importarUsuarios(csv, "uno.csv");

        assertThat(resultado.status()).isEqualTo(BatchStatus.COMPLETADO);
        assertThat(resultado.errors()).isEmpty();
        assertThat(resultado.createdRows()).isEqualTo(1);
    }

    @Test
    void unCorreoRepetidoDentroDelMismoArchivoSoloEntraUnaVez() {
        String csv = "correo,nombres,apellidos,contrasena,telefono,rol\n"
                + "duplicado@certisafe.test,Primero,Uno,Prueba123*,3001112233,OPERATOR\n"
                + "duplicado@certisafe.test,Segundo,Dos,Prueba123*,3001112233,OPERATOR\n";

        ImportResultResponse resultado = importarUsuarios(csv, "duplicados.csv");

        assertThat(resultado.createdRows()).isEqualTo(1);
        assertThat(resultado.rejectedRows()).isEqualTo(1);
        assertThat(resultado.status()).isEqualTo(BatchStatus.CON_ERRORES);
    }

    @Test
    void unCorreoQueYaExisteEnLaBaseSeReportaSinBorrarAlUsuario() {
        User existente = crearOperario(sufijo());
        String csv = "correo,nombres,apellidos,contrasena,telefono,rol\n"
                + existente.getEmail() + ",Otro,Nombre,Prueba123*,3001112233,OPERATOR\n";

        ImportResultResponse resultado = importarUsuarios(csv, "repetidos.csv");

        assertThat(resultado.createdRows()).isZero();
        assertThat(resultado.rejectedRows()).isEqualTo(1);
        assertThat(resultado.status()).isEqualTo(BatchStatus.CON_ERRORES);
        assertThat(userRepository.findById(existente.getId())).isPresent();
    }

    @Test
    void faltanColumnasObligatoriasSeRechazaElArchivo() {
        String csv = "correo,rol\nuno@certisafe.test,OPERATOR\n";

        assertThatThrownBy(() -> importarUsuarios(csv, "incompleto.csv"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Faltan columnas obligatorias");
    }

    @Test
    void losTalleresImportadosQuedanProgramadosYConSuInstructor() {
        User instructor = crearInstructor(sufijo());
        String csv = "nombre,descripcion,fecha,duracion_minutos,aforo,lugar,correo_instructor\n"
                + TALLER_IMPORTADO + ",Descripcion,31/12/2026 10:00,90,20,Sala importada,"
                + instructor.getEmail() + "\n";

        ImportResultResponse resultado = importarTalleres(csv, "talleres.csv");

        assertThat(resultado.status()).isEqualTo(BatchStatus.COMPLETADO);
        assertThat(resultado.createdRows()).isEqualTo(1);

        var importado = workshopService.list(null, null, null, null, null, Pageable.ofSize(10), admin)
                .content()
                .stream()
                .filter(t -> t.name().equals(TALLER_IMPORTADO))
                .findFirst()
                .orElseThrow();

        assertThat(importado.status()).isEqualTo("PROGRAMADO");
        assertThat(importado.instructor()).isNotNull();
        assertThat(importado.instructor().email()).isEqualTo(instructor.getEmail());
    }
}
