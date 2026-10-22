package com.certisafe.repository;

import com.certisafe.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
}
