package com.certisafe.repository;

import com.certisafe.domain.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByWorkshopIdOrderByChangedAtAsc(Long workshopId);

    void deleteByWorkshopId(Long workshopId);
}
