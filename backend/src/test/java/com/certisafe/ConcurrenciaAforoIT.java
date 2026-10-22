package com.certisafe;

import com.certisafe.common.error.BusinessException;
import com.certisafe.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La regla del aforo contra la carrera real (RF-015, y la que RNF-014 nombra primero).
 *
 * <p>Esta prueba es la única que no va envuelta en la transaccion de prueba: si lo estuviera,
 * las dos inscripciones entrarian en la MISMA transaccion, no se bloquearian entre si y la
 * prueba no demostraria nada. Aqui cada inscripción es una peticion con su propia
 * transaccion, como en produccion.</p>
 *
 * <p>La comprobacion del aforo solo es correcta porque el servicio bloquea la fila del taller
 * con {@code SELECT … FOR UPDATE} ANTES de contar. Validar antes y bloquear despues deja la
 * ventana de carrera exactamente donde esta prueba la cierra.</p>
 */
@SpringBootTest
@DisplayName("RF-015: dos inscripciones simultáneas y un solo cupo")
class ConcurrenciaAforoIT extends ReglasDeNegocioIT {

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Long adminId;
    private Long tallerId;
    private Long primerOperario;
    private Long segundoOperario;
    private UserPrincipal admin;

    @BeforeEach
    void preparar() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        adminId = tx.execute(status -> crearAdministrador(sufijo()).getId());
        tallerId = tx.execute(status -> crearTaller(adminId, sufijo(), 1));
        primerOperario = tx.execute(status -> crearOperario(sufijo()).getId());
        segundoOperario = tx.execute(status -> crearOperario(sufijo()).getId());
        admin = principal(adminId);
    }

    @AfterEach
    void limpiar() {
        // Las inscripciones (y sus certificados) se confirmaron de verdad, asi que se borran
        // con SQL en el orden en que lo exigen las claves foraneas.
        jdbc.update("DELETE FROM certificates WHERE enrollment_id IN "
                + "(SELECT id FROM enrollments WHERE workshop_id = ?)", tallerId);
        jdbc.update("DELETE FROM enrollments WHERE workshop_id = ?", tallerId);
        jdbc.update("DELETE FROM status_history WHERE workshop_id = ?", tallerId);
        jdbc.update("DELETE FROM workshop_assignments WHERE workshop_id = ?", tallerId);
        jdbc.update("DELETE FROM workshops WHERE id = ?", tallerId);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (?, ?, ?)", adminId, primerOperario, segundoOperario);
        jdbc.update("DELETE FROM audit_logs WHERE user_id IN (?, ?, ?)", adminId, primerOperario, segundoOperario);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", adminId, primerOperario, segundoOperario);
    }

    @Test
    void soloUnoDeLosDosSeInscribeYElOtroRecibeAforoCompleto() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier arranque = new CyclicBarrier(2);

            Callable<Resultado> primero = () -> {
                arranque.await(10, TimeUnit.SECONDS);
                return intentar(primerOperario);
            };
            Callable<Resultado> segundo = () -> {
                arranque.await(10, TimeUnit.SECONDS);
                return intentar(segundoOperario);
            };

            Future<Resultado> futuroUno = pool.submit(primero);
            Future<Resultado> futuroDos = pool.submit(segundo);

            List<String> resultados = List.of(futuroUno.get(30, TimeUnit.SECONDS), futuroDos.get(30, TimeUnit.SECONDS))
                    .stream()
                    .map(Resultado::clasificacion)
                    .sorted()
                    .toList();

            assertThat(resultados).containsExactly("inscrito", "lleno");
        } finally {
            pool.shutdownNow();
        }
    }

    private Resultado intentar(Long operarioId) {
        try {
            enrollmentService.enroll(tallerId, operarioId, admin);
            return new Resultado("inscrito", null);
        } catch (BusinessException error) {
            return new Resultado(error.getMessage().contains("Aforo completo") ? "lleno" : "inesperado", error.getMessage());
        }
    }

    /** Qué pasó con cada intento: sirve para ordenar y comparar sin depender del orden de llegada. */
    private record Resultado(String clasificacion, String detalle) {
    }
}
