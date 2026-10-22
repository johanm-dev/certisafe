/**
 * Utilidades de fecha y duracion.
 *
 * El backend trabaja siempre con instantes en ISO-8601 CON desplazamiento horario
 * (`OffsetDateTime`). El navegador, en cambio, tiene dos formatos locales distintos:
 * el del selector de fecha (`Date`) y el de `<input type="datetime-local">`
 * (`YYYY-MM-DDTHH:mm`). Aqui viven las conversiones entre los tres, en un solo sitio,
 * porque equivocarse de zona horaria es un error muy facil de cometer y muy dificil de ver.
 */

function dosDigitos(valor: number): string {
  return String(valor).padStart(2, '0');
}

/** Inicio del dia LOCAL de `fecha`, expresado en ISO con desplazamiento. */
export function startOfDayIso(fecha: Date): string {
  const inicio = new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate(), 0, 0, 0, 0);
  return inicio.toISOString();
}

/** Fin del dia LOCAL de `fecha`, expresado en ISO con desplazamiento. */
export function endOfDayIso(fecha: Date): string {
  const fin = new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate(), 23, 59, 59, 999);
  return fin.toISOString();
}

/**
 * Convierte un instante ISO del backend al valor local que espera
 * `<input type="datetime-local">`. Se usa el reloj del navegador, que es lo que el
 * usuario ve en su pantalla.
 */
export function toLocalInputValue(iso: string): string {
  const fecha = new Date(iso);
  const anio = fecha.getFullYear();
  const mes = dosDigitos(fecha.getMonth() + 1);
  const dia = dosDigitos(fecha.getDate());
  const hora = dosDigitos(fecha.getHours());
  const minuto = dosDigitos(fecha.getMinutes());
  return `${anio}-${mes}-${dia}T${hora}:${minuto}`;
}

/** Convierte el valor local de `<input type="datetime-local">` a ISO con desplazamiento. */
export function fromLocalInputValue(valor: string): string {
  return new Date(valor).toISOString();
}

/** Duracion legible: 45 -> "45 min", 120 -> "2 h", 495 -> "8 h 15 min". */
export function formatDuration(minutos: number): string {
  if (minutos < 60) {
    return `${minutos} min`;
  }
  const horas = Math.floor(minutos / 60);
  const resto = minutos % 60;
  return resto === 0 ? `${horas} h` : `${horas} h ${resto} min`;
}
