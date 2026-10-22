package com.certisafe.enrollment.dto;

import com.certisafe.domain.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code PATCH /api/enrollments/{id}/attendance} (RF-012). */
public record AttendanceUpdateRequest(

        @NotNull(message = "El estado de asistencia es obligatorio")
        AttendanceStatus attendanceStatus
) {
}
