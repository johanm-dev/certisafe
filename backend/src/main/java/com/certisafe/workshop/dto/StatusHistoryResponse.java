package com.certisafe.workshop.dto;

import com.certisafe.domain.StatusHistory;

import java.time.OffsetDateTime;

/** Una fila del historial de cambios de estado de un taller (RNF-016). */
public record StatusHistoryResponse(
        Long id,
        String previousStatus,
        String newStatus,
        PersonRef changedBy,
        OffsetDateTime changedAt,
        String reason
) {

    public static StatusHistoryResponse from(StatusHistory history) {
        return new StatusHistoryResponse(
                history.getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                PersonRef.from(history.getChangedBy()),
                history.getChangedAt(),
                history.getReason()
        );
    }
}
