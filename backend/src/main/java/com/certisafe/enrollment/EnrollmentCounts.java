package com.certisafe.enrollment;

import com.certisafe.repository.EnrollmentRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cuenta inscripciones por taller.
 *
 * <p>Existe como componente propio porque lo necesitan dos modulos: el listado de talleres,
 * que muestra los cupos disponibles, y el de inscripciones. La alternativa era repetir la
 * misma consulta agrupada en los dos servicios.</p>
 *
 * <p>Recibe ids en lugar de entidades para que quien la use no tenga que extraerlos antes: el
 * listado de talleres ya los tiene a mano.</p>
 */
@Component
public class EnrollmentCounts {

    private final EnrollmentRepository enrollmentRepository;

    public EnrollmentCounts(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * @return inscripciones por id de taller, en UNA sola consulta. Los talleres sin ninguna
     *         inscripcion no aparecen en el mapa.
     */
    public Map<Long, Long> byWorkshopIds(Collection<Long> workshopIds) {
        Set<Long> ids = new LinkedHashSet<>(workshopIds);
        if (ids.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, Long> counts = new HashMap<>();
        for (Object[] fila : enrollmentRepository.countGroupedByWorkshopId(ids)) {
            counts.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }
        return counts;
    }
}
