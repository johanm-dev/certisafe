package com.certisafe.enrollment.dto;

import com.certisafe.domain.AttendanceStatus;
import com.certisafe.domain.Enrollment;

import java.time.OffsetDateTime;

/**
 * Representacion publica de una inscripcion.
 *
 * <p>Trae tambien los datos del taller y del operario para que el listado se pueda pintar sin
 * pedir nada mas, y los cupos ya calculados: la historia de usuario de RF-015 pide
 * literalmente "calcular los cupos disponibles restando la cantidad de inscritos al aforo
 * maximo del taller".</p>
 */
public record EnrollmentResponse(
        Long id,
        Long userId,
        String userFullName,
        String userEmail,
        Long workshopId,
        String workshopName,
        OffsetDateTime workshopDate,
        String workshopLocation,
        String workshopStatus,
        Integer workshopMaxCapacity,
        long workshopEnrolledCount,
        long availableSeats,
        OffsetDateTime enrolledAt,
        AttendanceStatus attendanceStatus,
        OffsetDateTime assignedAt,
        /**
         * Numero del certificado emitido por esta asistencia, o {@code null} si aun no hay.
         * Se incluye para que la pantalla de inscripciones muestre de un vistazo que la
         * asistencia produjo su certificacion (RF-013).
         */
        String certificateNumber,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /**
     * @param enrolledCount inscripciones que tiene el taller, contadas por el servicio. Se
     *                      recibe en vez de calcularse aqui para que un listado paginado pueda
     *                      resolverlas todas en una sola consulta agrupada en lugar de una por
     *                      fila.
     * @param certificateNumber numero del certificado de esta inscripcion, o {@code null}.
     */
    public static EnrollmentResponse from(Enrollment enrollment, long enrolledCount,
                                          String certificateNumber) {
        int maxCapacity = enrollment.getWorkshop().getMaxCapacity();
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getUser().getId(),
                enrollment.getUser().fullName(),
                enrollment.getUser().getEmail(),
                enrollment.getWorkshop().getId(),
                enrollment.getWorkshop().getName(),
                enrollment.getWorkshop().getWorkshopDate(),
                enrollment.getWorkshop().getLocation(),
                enrollment.getWorkshop().getStatus().name(),
                maxCapacity,
                enrolledCount,
                Math.max(0, maxCapacity - enrolledCount),
                enrollment.getEnrolledAt(),
                enrollment.getAttendanceStatus(),
                enrollment.getAssignedAt(),
                certificateNumber,
                enrollment.getCreatedAt(),
                enrollment.getUpdatedAt()
        );
    }
}
