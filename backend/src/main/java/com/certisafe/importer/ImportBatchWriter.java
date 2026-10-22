package com.certisafe.importer;

import com.certisafe.common.error.NotFoundException;
import com.certisafe.domain.StatusHistory;
import com.certisafe.domain.User;
import com.certisafe.domain.Workshop;
import com.certisafe.domain.WorkshopStatus;
import com.certisafe.repository.StatusHistoryRepository;
import com.certisafe.repository.UserRepository;
import com.certisafe.repository.WorkshopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Inserta un lote de filas ya validadas en UNA sola transaccion.
 *
 * <p>Ese "una sola" es el CA3 de la HU-11: si la fila 37 de un lote de 500 choca con una
 * restriccion que la validacion no pudo ver (por ejemplo, otra carga simultanea inserto el
 * mismo correo), se revierte todo el lote y el servicio lo reporta como filas rechazadas. No
 * quedan datos a medias.</p>
 *
 * <p>Igual que {@link ImportBatchStore}, es un bean aparte para que {@code @Transactional}
 * surta efecto: se llama desde otro objeto.</p>
 */
@Service
public class ImportBatchWriter {

    private final UserRepository userRepository;
    private final WorkshopRepository workshopRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    public ImportBatchWriter(UserRepository userRepository,
                             WorkshopRepository workshopRepository,
                             StatusHistoryRepository statusHistoryRepository) {
        this.userRepository = userRepository;
        this.workshopRepository = workshopRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    @Transactional
    public void writeUsers(List<User> users) {
        userRepository.saveAll(users);
    }

    /**
     * Crea los talleres en estado PROGRAMADO (RF-018) y deja su nacimiento en el historial de
     * estados, igual que el alta individual (RNF-016). Sin esa fila, un taller creado por carga
     * masiva apareceria sin origen en su historial.
     */
    @Transactional
    public void writeWorkshops(List<Workshop> workshops, Long actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> NotFoundException.of("Usuario", actorId));

        for (Workshop workshop : workshops) {
            workshop.setStatus(WorkshopStatus.PROGRAMADO);
            workshop.setCreatedBy(actor);
            Workshop saved = workshopRepository.save(workshop);
            statusHistoryRepository.save(StatusHistory.of(saved, null, WorkshopStatus.PROGRAMADO,
                    actor, "Creación desde carga masiva", saved.getStatusChangedAt()));
        }
    }
}
