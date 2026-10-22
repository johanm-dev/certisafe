package com.certisafe.repository;

import com.certisafe.domain.WorkshopAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkshopAssignmentRepository extends JpaRepository<WorkshopAssignment, Long> {

    Optional<WorkshopAssignment> findByWorkshopIdAndIsCurrentTrue(Long workshopId);

    List<WorkshopAssignment> findByWorkshopIdOrderByAssignedAtDesc(Long workshopId);

    long countByInstructorIdAndIsCurrentTrue(Long instructorId);
}
