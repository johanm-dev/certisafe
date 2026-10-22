package com.certisafe.importer;

import com.certisafe.common.error.NotFoundException;
import com.certisafe.domain.BatchStatus;
import com.certisafe.domain.ImportBatch;
import com.certisafe.domain.ImportError;
import com.certisafe.repository.ImportBatchRepository;
import com.certisafe.repository.ImportErrorRepository;
import com.certisafe.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persistencia del lote y de sus errores en transacciones INDEPENDIENTES.
 *
 * <p>Va en un bean aparte a proposito: si estos metodos vivieran en {@code ImportService} y se
 * llamaran desde el mismo objeto, {@code @Transactional(REQUIRES_NEW)} no se aplicaria (Spring
 * usa un proxy, y una llamada interna no pasa por el).</p>
 *
 * <p>{@code REQUIRES_NEW} es lo que permite cumplir el CA3 de la HU-11: si un lote de filas se
 * revierte, el registro del lote y el reporte de errores NO se pierden, porque ya estan
 * confirmados en otra transaccion.</p>
 */
@Service
public class ImportBatchStore {

    private final ImportBatchRepository batchRepository;
    private final ImportErrorRepository errorRepository;
    private final UserRepository userRepository;

    public ImportBatchStore(ImportBatchRepository batchRepository,
                            ImportErrorRepository errorRepository,
                            UserRepository userRepository) {
        this.batchRepository = batchRepository;
        this.errorRepository = errorRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportBatch start(String fileName, String fileType, String entityType,
                             int totalRows, Long actorId) {
        ImportBatch batch = new ImportBatch();
        batch.setFileName(fileName);
        batch.setFileType(fileType);
        batch.setEntityType(entityType);
        batch.setTotalRows(totalRows);
        batch.setProcessedRows(0);
        batch.setRejectedRows(0);
        batch.setUploadedBy(userRepository.findById(actorId)
                .orElseThrow(() -> NotFoundException.of("Usuario", actorId)));
        batch.setStatus(BatchStatus.PROCESANDO);
        return batchRepository.save(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addErrors(Long batchId, List<ImportFailure> failures) {
        if (failures.isEmpty()) {
            return;
        }
        ImportBatch batch = batchRepository.getReferenceById(batchId);
        List<ImportError> errors = failures.stream().map(failure -> {
            ImportError error = new ImportError();
            error.setBatch(batch);
            error.setRowNumber(failure.rowNumber());
            error.setErrorReason(failure.reason());
            error.setRowData(failure.data());
            return error;
        }).toList();
        errorRepository.saveAll(errors);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long batchId, int processedRows, int rejectedRows, BatchStatus status) {
        ImportBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> NotFoundException.of("Lote de importación", batchId));
        batch.setProcessedRows(processedRows);
        batch.setRejectedRows(rejectedRows);
        batch.setStatus(status);
        batchRepository.save(batch);
    }
}
