package com.certisafe.repository;

import com.certisafe.domain.ImportError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportErrorRepository extends JpaRepository<ImportError, Long> {

    List<ImportError> findByBatchIdOrderByRowNumberAsc(Long batchId);
}
