package com.certisafe.enrollment.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code POST /api/enrollments} (RF-015).
 *
 * <p>{@code userId} es opcional a proposito: si llega nulo se inscribe el propio usuario
 * autenticado, que es el caso normal del OPERATOR. Un ADMIN puede inscribir a otro
 * indicandolo.</p>
 */
public record EnrollmentCreateRequest(

        @NotNull(message = "El taller es obligatorio")
        Long workshopId,

        Long userId
) {
}
