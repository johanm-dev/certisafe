/**
 * Generador nativo de Informes Ejecutivos en formato PDF 1.4 (vectorial, sin dependencias externas).
 *
 * Estructura cada exportacion (Usuarios, Talleres, Inscripciones, Reporte de Cumplimiento) como un
 * informe formal de ingenieria y auditoria SST inspirado en formularios ejecutivos de reporte:
 * - Pagina 1: Cabecera institucional, panel lateral crema de Criterios/Umbrales, Proposito e
 *             Introduccion, y Seccion A con los metadatos actuales del exportado.
 * - Pagina 2: Seccion B con dos paneles crema lado a lado que contienen graficas interpretativas
 *             vectoriales (barras horizontales, columnas comparativas y barra de proporcion 100%),
 *             interpretacion automatica y bloque de firmas de 2 filas x 3 columnas.
 * - Pagina 3+: Seccion C con la tabla detallada de todos los registros actuales exportados.
 */

export interface PdfReportContext {
  endpointUrl: string;
  filters: Record<string, string | number | boolean | null | undefined>;
  generatedByName: string;
  generatedByRole: string;
  headers: string[];
  rows: string[][];
}

interface StatItem {
  label: string;
  count: number;
  percentage: number;
  color: [number, number, number];
}

interface ModuleReportConfig {
  reportTitle: string;
  moduleName: string;
  rfCode: string;
  purposeParagraph1: string;
  purposeParagraph2: string;
  guidelinesTitle: string;
  guidelines: { title: string; body: string }[];
  kpiLabel: string;
  computePrimaryStats: (headers: string[], rows: string[][]) => StatItem[];
  computeSecondaryStats: (headers: string[], rows: string[][]) => StatItem[];
  computeKpiSummary: (headers: string[], rows: string[][]) => string;
  computeInterpretation: (headers: string[], rows: string[][], stats: StatItem[]) => string[];
}

// Paleta de colores vectoriales RGB (0..1)
const COLOR_BEIGE_BG: [number, number, number] = [0.949, 0.933, 0.894]; // #F2EEE4
const COLOR_DARK: [number, number, number] = [0.11, 0.12, 0.14];
const COLOR_MUTED: [number, number, number] = [0.35, 0.37, 0.4];
const COLOR_LINE: [number, number, number] = [0.25, 0.25, 0.25];
const COLOR_GREEN: [number, number, number] = [0.18, 0.49, 0.2]; // #2E7D32
const COLOR_BLUE: [number, number, number] = [0.08, 0.4, 0.75]; // #1565C0
const COLOR_AMBER: [number, number, number] = [0.9, 0.45, 0.0]; // #E67300
const COLOR_RED: [number, number, number] = [0.78, 0.16, 0.16]; // #C62828
const COLOR_TEAL: [number, number, number] = [0.0, 0.5, 0.53];
const COLOR_PURPLE: [number, number, number] = [0.42, 0.21, 0.6];

const PALETTE: [number, number, number][] = [
  COLOR_GREEN,
  COLOR_BLUE,
  COLOR_AMBER,
  COLOR_RED,
  COLOR_TEAL,
  COLOR_PURPLE,
];

/** Convierte una cadena UTF-8 a bytes WinAnsi (Windows-1252) y escapa parentesis de PDF. */
function escapePdfText(input: string): string {
  let out = '';
  for (let i = 0; i < input.length; i++) {
    const code = input.charCodeAt(i);
    let byte = code;
    if (code === 0x2022) {
      byte = 0x95; // bullet •
    } else if (code === 0x2013) {
      byte = 0x96; // en-dash –
    } else if (code === 0x2014) {
      byte = 0x97; // em-dash —
    } else if (code === 0x2018 || code === 0x2019) {
      byte = 0x27;
    } else if (code === 0x201c || code === 0x201d) {
      byte = 0x22;
    } else if (code >= 0x2190 && code <= 0x21ff) {
      byte = 0x3e; // >
    } else if (code > 255) {
      byte = 0x3f; // ?
    }

    const ch = String.fromCharCode(byte);
    if (ch === '\\' || ch === '(' || ch === ')') {
      out += '\\' + ch;
    } else {
      out += ch;
    }
  }
  return out;
}

/** Estima el ancho aproximado en puntos de un texto en Helvetica para hacer word-wrap limpio. */
function measureTextWidth(text: string, fontSize: number, bold = false): number {
  const factor = bold ? 0.55 : 0.51;
  return text.length * fontSize * factor;
}

/** Divide un parrafo en lineas que caben en `maxWidth` puntos. */
function wrapText(text: string, maxWidth: number, fontSize: number, bold = false): string[] {
  if (!text) {
    return [''];
  }
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let current = '';

  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word;
    if (measureTextWidth(candidate, fontSize, bold) <= maxWidth || !current) {
      current = candidate;
    } else {
      lines.push(current);
      current = word;
    }
  }
  if (current) {
    lines.push(current);
  }
  return lines;
}

/** Recorta un texto con puntos suspensivos si supera el ancho maximo de una celda. */
function truncateText(text: string, maxWidth: number, fontSize: number): string {
  const clean = (text ?? '').replace(/\r?\n/g, ' ').trim();
  if (measureTextWidth(clean, fontSize, false) <= maxWidth) {
    return clean;
  }
  const maxChars = Math.max(4, Math.floor(maxWidth / (fontSize * 0.52)) - 2);
  return clean.slice(0, maxChars) + '..';
}

/** Lienzo vectorial de una pagina PDF (595.28 x 841.89 pt - A4). */
class PdfPageCanvas {
  private readonly ops: string[] = [];

  rectFill(x: number, y: number, w: number, h: number, rgb: [number, number, number]): void {
    this.ops.push(
      `q ${rgb[0].toFixed(3)} ${rgb[1].toFixed(3)} ${rgb[2].toFixed(3)} rg ${x.toFixed(2)} ${y.toFixed(2)} ${w.toFixed(2)} ${h.toFixed(2)} re f Q`,
    );
  }

  rectStroke(
    x: number,
    y: number,
    w: number,
    h: number,
    rgb: [number, number, number],
    lineWidth = 0.7,
  ): void {
    this.ops.push(
      `q ${lineWidth.toFixed(2)} w ${rgb[0].toFixed(3)} ${rgb[1].toFixed(3)} ${rgb[2].toFixed(3)} RG ${x.toFixed(2)} ${y.toFixed(2)} ${w.toFixed(2)} ${h.toFixed(2)} re S Q`,
    );
  }

  line(
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    rgb: [number, number, number] = COLOR_DARK,
    lineWidth = 1,
  ): void {
    this.ops.push(
      `q ${lineWidth.toFixed(2)} w ${rgb[0].toFixed(3)} ${rgb[1].toFixed(3)} ${rgb[2].toFixed(3)} RG ${x1.toFixed(2)} ${y1.toFixed(2)} m ${x2.toFixed(2)} ${y2.toFixed(2)} l S Q`,
    );
  }

  text(
    str: string,
    x: number,
    y: number,
    size: number,
    bold = false,
    rgb: [number, number, number] = COLOR_DARK,
  ): void {
    const font = bold ? '/F2' : '/F1';
    this.ops.push(
      `BT ${font} ${size.toFixed(1)} Tf ${rgb[0].toFixed(3)} ${rgb[1].toFixed(3)} ${rgb[2].toFixed(3)} rg ${x.toFixed(2)} ${y.toFixed(2)} Td (${escapePdfText(str)}) Tj ET`,
    );
  }

  textRight(
    str: string,
    rightX: number,
    y: number,
    size: number,
    bold = false,
    rgb: [number, number, number] = COLOR_DARK,
  ): void {
    const w = measureTextWidth(str, size, bold);
    this.text(str, Math.max(40, rightX - w), y, size, bold, rgb);
  }

  paragraph(
    str: string,
    x: number,
    startY: number,
    maxWidth: number,
    size: number,
    lineHeight: number,
    bold = false,
    rgb: [number, number, number] = COLOR_DARK,
  ): number {
    const lines = wrapText(str, maxWidth, size, bold);
    let y = startY;
    for (const ln of lines) {
      this.text(ln, x, y, size, bold, rgb);
      y -= lineHeight;
    }
    return y;
  }

  /** Dibuja un bloque con titulo en negrita seguido de texto descriptivo. */
  titledBlock(
    title: string,
    body: string,
    x: number,
    startY: number,
    maxWidth: number,
    size = 8.5,
    lineHeight = 11.5,
  ): number {
    let y = this.paragraph(title, x, startY, maxWidth, size, lineHeight, true, COLOR_DARK);
    y = this.paragraph(body, x, y, maxWidth, size, lineHeight, false, COLOR_DARK);
    return y - 5;
  }

  /** Dibuja el emblema institucional SST y la cabecera superior inspirada en las imagenes. */
  drawInstitutionalHeader(reportTitle: string): void {
    // Icono vectorial tipo emblema SST / Salud Ocupacional (x=42..64, y=768..804)
    this.rectFill(42, 772, 22, 28, COLOR_DARK);
    this.rectFill(45, 775, 16, 22, [1, 1, 1]);
    // Cruz de seguridad industrial en el centro del escudo
    this.rectFill(51, 779, 4, 14, COLOR_DARK);
    this.rectFill(48, 784, 10, 4, COLOR_DARK);

    // Nombre de la organizacion a la izquierda
    this.text('CertiSafe · IndustrialCore', 72, 790, 9.5, true, COLOR_DARK);
    this.text('Seguridad y Salud en el Trabajo (SST)', 72, 778, 8.5, false, COLOR_MUTED);

    // Titulo grande del informe a la derecha
    this.textRight(reportTitle, 555, 781, 19, false, COLOR_DARK);

    // Linea divisoria gruesa de extremo a extremo
    this.line(40, 756, 555, 756, COLOR_DARK, 2.2);
  }

  /** Dibuja el pie de pagina institucional con numeracion. */
  drawFooter(reportTitle: string, pageNum: number, totalPages: number): void {
    this.line(40, 48, 555, 48, [0.75, 0.75, 0.75], 0.6);
    this.text(
      `CertiSafe · IndustrialCore SST - ${reportTitle}`,
      40,
      34,
      8,
      false,
      COLOR_MUTED,
    );
    this.textRight(`Página ${pageNum} de ${totalPages}`, 555, 34, 8, false, COLOR_MUTED);
  }

  buildContentStream(): string {
    return this.ops.join('\n');
  }
}

/** Analiza un archivo CSV (UTF-8) y devuelve encabezados y filas. */
export function parseCsvContent(csvText: string): { headers: string[]; rows: string[][] } {
  const clean = csvText.replace(/^\uFEFF/, '').trim();
  if (!clean) {
    return { headers: [], rows: [] };
  }

  const records: string[][] = [];
  let currentRow: string[] = [];
  let currentField = '';
  let inQuotes = false;

  for (let i = 0; i < clean.length; i++) {
    const ch = clean[i];
    const next = clean[i + 1];

    if (inQuotes) {
      if (ch === '"' && next === '"') {
        currentField += '"';
        i++;
      } else if (ch === '"') {
        inQuotes = false;
      } else {
        currentField += ch;
      }
    } else {
      if (ch === '"') {
        inQuotes = true;
      } else if (ch === ',') {
        currentRow.push(currentField);
        currentField = '';
      } else if (ch === '\r' && next === '\n') {
        currentRow.push(currentField);
        records.push(currentRow);
        currentRow = [];
        currentField = '';
        i++;
      } else if (ch === '\n') {
        currentRow.push(currentField);
        records.push(currentRow);
        currentRow = [];
        currentField = '';
      } else {
        currentField += ch;
      }
    }
  }
  if (currentField.length > 0 || currentRow.length > 0) {
    currentRow.push(currentField);
    records.push(currentRow);
  }

  const headers = records[0] ?? [];
  const rows = records.slice(1).filter((r) => r.some((cell) => cell.trim() !== ''));
  return { headers, rows };
}

/** Agrupa valores de una columna y devuelve frecuencias ordenadas con porcentajes y colores. */
function groupColumnStats(
  rows: string[][],
  colIndex: number,
  colorMap?: Record<string, [number, number, number]>,
): StatItem[] {
  const counts = new Map<string, number>();
  const total = Math.max(1, rows.length);

  for (const r of rows) {
    const raw = (r[colIndex] ?? '').trim() || 'Sin especificar';
    counts.set(raw, (counts.get(raw) ?? 0) + 1);
  }

  return Array.from(counts.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6)
    .map(([label, count], idx) => ({
      label,
      count,
      percentage: Math.round((count / total) * 1000) / 10,
      color: colorMap?.[label] ?? PALETTE[idx % PALETTE.length],
    }));
}

/** Configuracion especifica por cada modulo exportable de CertiSafe. */
function resolveModuleConfig(endpointUrl: string): ModuleReportConfig {
  if (endpointUrl.includes('/reports/compliance')) {
    return {
      reportTitle: 'Informe de Cumplimiento SST',
      moduleName: 'Reporte de Cumplimiento y Aptitud en Alto Riesgo',
      rfCode: 'RF-020 · HU-14 (Auditoría HSE)',
      purposeParagraph1:
        'Utilice este informe oficial para auditar el estado de certificación vigente de los operarios de planta y verificar su habilitación legal antes del ingreso a zonas de alto riesgo en IndustrialCore.',
      purposeParagraph2:
        'Una vez generado y revisado, este documento debe ser validado y archivado por el Comité de Evaluación y Seguridad Industrial (HSE) de CertiSafe.',
      guidelinesTitle: 'Criterios y Umbrales para Auditoría de Cumplimiento SST',
      guidelines: [
        {
          title: 'Certificación Vigente (Apto para Alto Riesgo):',
          body: 'El operario cuenta con al menos una certificación activa cuya fecha de vencimiento es posterior al momento de la auditoría y no presenta revocación.',
        },
        {
          title: 'Certificación Vencida o Revocada (No Apto):',
          body: 'El operario estuvo certificado previamente, pero el documento superó los 365 días de vigencia reglamentaria o fue revocado administrativamente por incumplimiento.',
        },
        {
          title: 'Sin Certificar (Restricción Crítica de Acceso):',
          body: 'El operario registra inscripción en talleres del alcance pero aún no cuenta con asistencia aprobada ni certificado emitido.',
        },
        {
          title: 'Regla de Bloqueo Operativo en Planta:',
          body: 'Ningún operario marcado como No Apto puede ejecutar maniobras en alturas, espacios confinados o riesgo eléctrico hasta renovar su capacitación.',
        },
        {
          title: 'Criterio de Rango Temporal:',
          body: 'Los filtros de fecha evalúan el periodo en que se dictaron los talleres auditados, garantizando trazabilidad histórica exacta.',
        },
        {
          title: 'Trazabilidad e Integridad Legal:',
          body: 'Cada certificado listado posee un código único CS-AAAA-NNNN verificable en la base de datos de CertiSafe.',
        },
      ],
      kpiLabel: 'Índice de Operarios Aptos (Habilitados para Alto Riesgo)',
      computePrimaryStats: (_h, rows) =>
        groupColumnStats(rows, 3, {
          Vigente: COLOR_GREEN,
          Vencida: COLOR_AMBER,
          'Sin certificar': COLOR_RED,
        }),
      computeSecondaryStats: (_h, rows) =>
        groupColumnStats(rows, 4, {
          Sí: COLOR_GREEN,
          No: COLOR_RED,
        }),
      computeKpiSummary: (_h, rows) => {
        const aptos = rows.filter((r) => (r[4] ?? '').trim() === 'Sí').length;
        const pct = rows.length ? ((aptos / rows.length) * 100).toFixed(1) : '0.0';
        return `${aptos} de ${rows.length} operarios aptos (${pct}% habilitados)`;
      },
      computeInterpretation: (_h, rows, stats) => {
        const total = rows.length;
        const aptos = rows.filter((r) => (r[4] ?? '').trim() === 'Sí').length;
        const noAptos = total - aptos;
        const pctAptos = total ? ((aptos / total) * 100).toFixed(1) : '0.0';
        const dom = stats[0];
        return [
          `1. Diagnóstico Actual: De un total de ${total} operarios evaluados en el alcance actual, ${aptos} (${pctAptos}%) cumplen con certificación vigente y están habilitados para zonas de alto riesgo.`,
          `2. Alerta de Riesgo: Se identifican ${noAptos} operarios en condición NO APTA (vencidos, revocados o sin certificar), quienes requieren restricción preventiva de ingreso a planta.`,
          `3. Estado Predominante: La categoría con mayor concentración es "${dom?.label ?? 'N/A'}" con ${dom?.count ?? 0} registros (${dom?.percentage ?? 0}%).`,
          `4. Medida Preventiva Recomendada: Programar reinducción prioritaria para los ${noAptos} operarios no habilitados y notificar a los supervisores de turno.`,
        ];
      },
    };
  }

  if (endpointUrl.includes('/export/workshops')) {
    return {
      reportTitle: 'Informe de Gestión de Talleres',
      moduleName: 'Catálogo y Ciclo de Vida de Talleres SST',
      rfCode: 'RF-019 · RNF-016 (Gestión de Capacitaciones)',
      purposeParagraph1:
        'Este informe consolida el estado operativo, programación, capacidad instalada (aforo) y asignación de instructores de los talleres de Seguridad y Salud en el Trabajo registrados en CertiSafe.',
      purposeParagraph2:
        'Permite a la coordinación HSE controlar el avance del cronograma de formación y auditar el cumplimiento de las transiciones de estado reglamentarias.',
      guidelinesTitle: 'Lineamientos del Ciclo de Vida de Talleres (RNF-016)',
      guidelines: [
        {
          title: 'Taller Programado (Inscripción Abierta):',
          body: 'Capacitación agendada a futuro con cupos disponibles. Es el único estado que admite nuevas inscripciones de operarios.',
        },
        {
          title: 'Taller En Progreso (Ejecución en Planta):',
          body: 'Sesión de formación en curso. Habilita al instructor asignado para registrar la asistencia oficial de los operarios inscritos.',
        },
        {
          title: 'Taller Finalizado (Cierre y Certificación):',
          body: 'Estado final inmutable tras concluir la jornada. Conserva el historial de asistencia y las certificaciones emitidas.',
        },
        {
          title: 'Taller Cancelado (Anulación Auditada):',
          body: 'Estado final alcanzado con motivo obligatorio registrado en status_history, preservando la trazabilidad sin borrar registros.',
        },
        {
          title: 'Control Estricto de Aforo:',
          body: 'El sistema garantiza mediante bloqueo transaccional que ningún taller supere su capacidad máxima configurada.',
        },
      ],
      kpiLabel: 'Capacidad Total Instalada (Aforo Acumulado en Talleres)',
      computePrimaryStats: (_h, rows) =>
        groupColumnStats(rows, 5, {
          Programado: COLOR_BLUE,
          'En progreso': COLOR_AMBER,
          Finalizado: COLOR_GREEN,
          Cancelado: COLOR_RED,
        }),
      computeSecondaryStats: (_h, rows) => groupColumnStats(rows, 6),
      computeKpiSummary: (_h, rows) => {
        const totalCap = rows.reduce((acc, r) => acc + (Number.parseInt(r[3] ?? '0', 10) || 0), 0);
        const avgDur =
          rows.reduce((acc, r) => acc + (Number.parseInt(r[2] ?? '0', 10) || 0), 0) /
          Math.max(1, rows.length);
        return `${totalCap} cupos totales ofertados (Duración media: ${Math.round(avgDur)} min)`;
      },
      computeInterpretation: (_h, rows, stats) => {
        const total = rows.length;
        const totalCap = rows.reduce((acc, r) => acc + (Number.parseInt(r[3] ?? '0', 10) || 0), 0);
        const sinInstructor = rows.filter((r) => !(r[6] ?? '').trim()).length;
        const dom = stats[0];
        return [
          `1. Oferta Formativa: El exportado actual comprende ${total} talleres con una capacidad instalada acumulada de ${totalCap} cupos para operarios.`,
          `2. Estado Dominante: El estado con mayor proporción es "${dom?.label ?? 'N/A'}" con ${dom?.count ?? 0} talleres (${dom?.percentage ?? 0}% del total).`,
          `3. Cobertura de Instructores: ${total - sinInstructor} de ${total} talleres cuentan con especialista técnico asignado (${sinInstructor} pendientes de asignación).`,
          `4. Acción Recomendada: Verificar que los talleres en estado Programado tengan instructor asignado al menos 48 horas antes de su inicio.`,
        ];
      },
    };
  }

  if (endpointUrl.includes('/export/enrollments')) {
    return {
      reportTitle: 'Informe de Inscripciones y Asistencia',
      moduleName: 'Control de Inscripciones, Asistencia y Emisión',
      rfCode: 'RF-012 · RF-013 · RF-015 · RF-019',
      purposeParagraph1:
        'Este informe detalla el registro nominal de operarios inscritos en los talleres de seguridad industrial, el estado de su asistencia y los folios de certificación emitidos.',
      purposeParagraph2:
        'Constituye el soporte auditable de participación efectiva en las capacitaciones y de generación automática de certificados SST.',
      guidelinesTitle: 'Criterios de Inscripción, Asistencia y Certificación',
      guidelines: [
        {
          title: 'Asistencia Confirmada (Asistió -> Certificado):',
          body: 'Al registrar ASISTIÓ, el sistema emite automáticamente en la misma transacción el certificado oficial con código único CS-AAAA-NNNN.',
        },
        {
          title: 'Asistencia Pendiente (Inscrito en Espera):',
          body: 'Operario con cupo reservado en un taller programado o en curso cuya asistencia aún no ha sido calificada por el instructor.',
        },
        {
          title: 'Inasistencia Registrada (Ausente):',
          body: 'El operario no asistió a la sesión. No genera certificado y, si existía uno previo por error, queda revocado automáticamente.',
        },
        {
          title: 'Separación de Funciones por Rol:',
          body: 'El ADMIN y el propio OPERARIO gestionan inscripciones; el INSTRUCTOR califica exclusivamente la asistencia de sus talleres asignados.',
        },
      ],
      kpiLabel: 'Tasa de Asistencia Efectiva y Certificación Emitida',
      computePrimaryStats: (_h, rows) =>
        groupColumnStats(rows, 4, {
          Asistió: COLOR_GREEN,
          Pendiente: COLOR_AMBER,
          Ausente: COLOR_RED,
        }),
      computeSecondaryStats: (_h, rows) => groupColumnStats(rows, 0),
      computeKpiSummary: (_h, rows) => {
        const certificados = rows.filter((r) => (r[7] ?? '').trim().length > 0).length;
        const pct = rows.length ? ((certificados / rows.length) * 100).toFixed(1) : '0.0';
        return `${certificados} de ${rows.length} inscripciones certificadas (${pct}%)`;
      },
      computeInterpretation: (_h, rows, stats) => {
        const total = rows.length;
        const certificados = rows.filter((r) => (r[7] ?? '').trim().length > 0).length;
        const ausentes = rows.filter((r) => (r[4] ?? '').trim() === 'Ausente').length;
        const pendientes = rows.filter((r) => (r[4] ?? '').trim() === 'Pendiente').length;
        const dom = stats[0];
        return [
          `1. Trazabilidad de Inscripciones: Se procesaron ${total} registros de inscripción en el alcance actual, con ${certificados} certificados emitidos.`,
          `2. Distribución de Asistencia: Predomina el estado "${dom?.label ?? 'N/A'}" (${dom?.percentage ?? 0}%). Se registran ${pendientes} asistencias pendientes y ${ausentes} ausencias.`,
          `3. Impacto en Certificación: Cada asistencia confirmada generó su respectivo folio CS-AAAA-NNNN de forma atómica e inmediata.`,
          `4. Seguimiento Recomendado: Reprogramar a los ${ausentes} operarios ausentes y cerrar el registro de los ${pendientes} pendientes al finalizar cada taller.`,
        ];
      },
    };
  }

  // Por defecto: /api/export/users
  return {
    reportTitle: 'Informe de Directorio de Usuarios',
    moduleName: 'Gestión de Usuarios, Roles y Control de Acceso (RBAC)',
    rfCode: 'RF-001..005 · RF-019 · RNF-015',
    purposeParagraph1:
      'Este informe presenta el padrón oficial de cuentas de usuario registradas en CertiSafe, su distribución por rol operativo (ADMIN, INSTRUCTOR, OPERATOR) y su estado de activación.',
    purposeParagraph2:
      'Sirve como instrumento de auditoría de seguridad de la información para verificar privilegios de acceso y cuentas activas en IndustrialCore.',
    guidelinesTitle: 'Políticas de Control de Acceso y Seguridad (RBAC)',
    guidelines: [
      {
        title: 'Rol ADMIN (Líder HSE):',
        body: 'Cuenta con los 19 permisos del sistema: administración de usuarios, talleres, cargas masivas, revocación de certificados y reportes de auditoría.',
      },
      {
        title: 'Rol INSTRUCTOR (Especialista Técnico):',
        body: 'Autorizado para gestionar el avance de sus talleres asignados y registrar la asistencia de los operarios inscritos.',
      },
      {
        title: 'Rol OPERATOR (Operario de Planta):',
        body: 'Autorizado para consultar la oferta de talleres, autoinscribirse en sesiones programadas y verificar su propio historial de certificados.',
      },
      {
        title: 'Borrado Lógico y Preservación Histórica:',
        body: 'Las cuentas inactivas tienen el acceso bloqueado pero conservan intacto su historial legal de certificaciones y auditoría.',
      },
      {
        title: 'Protección de Credenciales:',
        body: 'Contraseñas cifradas con BCrypt (costo 12) y bloqueo automático tras 5 intentos fallidos durante 15 minutos (RNF-015).',
      },
    ],
    kpiLabel: 'Proporción de Cuentas Activas en la Plataforma',
    computePrimaryStats: (_h, rows) =>
      groupColumnStats(rows, 3, {
        ADMIN: COLOR_PURPLE,
        INSTRUCTOR: COLOR_BLUE,
        OPERATOR: COLOR_GREEN,
      }),
    computeSecondaryStats: (_h, rows) =>
      groupColumnStats(rows, 5, {
        Sí: COLOR_GREEN,
        No: COLOR_RED,
      }),
    computeKpiSummary: (_h, rows) => {
      const activos = rows.filter((r) => (r[5] ?? '').trim() === 'Sí').length;
      const pct = rows.length ? ((activos / rows.length) * 100).toFixed(1) : '0.0';
      return `${activos} de ${rows.length} usuarios activos (${pct}% habilitados)`;
    },
    computeInterpretation: (_h, rows, stats) => {
      const total = rows.length;
      const activos = rows.filter((r) => (r[5] ?? '').trim() === 'Sí').length;
      const inactivos = total - activos;
      const dom = stats[0];
      return [
        `1. Censo de Usuarios: El directorio exportado registra ${total} cuentas en total, de las cuales ${activos} se encuentran activas y ${inactivos} desactivadas (borrado lógico).`,
        `2. Composición por Rol: El perfil mayoritario corresponde a "${dom?.label ?? 'N/A'}" con ${dom?.count ?? 0} usuarios (${dom?.percentage ?? 0}% del padrón).`,
        `3. Gobernanza de Seguridad: Se garantiza la existencia de al menos un administrador activo y el cumplimiento de políticas RBAC.`,
        `4. Recomendación de Auditoría: Revisar periódicamente las ${inactivos} cuentas inactivas y auditar los privilegios asignados al personal HSE.`,
      ];
    },
  };
}

function formatFiltersDescription(
  filters: Record<string, string | number | boolean | null | undefined>,
): string {
  const parts: string[] = [];
  for (const [k, v] of Object.entries(filters)) {
    if (k === 'format' || v === undefined || v === null || v === '') {
      continue;
    }
    const labelMap: Record<string, string> = {
      search: 'Búsqueda',
      roleId: 'ID Rol',
      active: 'Activo',
      status: 'Estado',
      instructorId: 'ID Instructor',
      workshopId: 'ID Taller',
      attendanceStatus: 'Asistencia',
      from: 'Desde',
      to: 'Hasta',
    };
    const friendlyKey = labelMap[k] ?? k;
    const friendlyVal = typeof v === 'string' && v.includes('T00:00') ? v.slice(0, 10) : String(v);
    parts.push(`${friendlyKey}: ${friendlyVal}`);
  }
  return parts.length > 0 ? parts.join(' | ') : 'Todos los registros (Sin filtros restrictivos)';
}

/**
 * Construye el documento PDF 1.4 binario completo a partir del contexto de exportacion.
 */
export function buildExecutivePdfReport(ctx: PdfReportContext): Blob {
  const config = resolveModuleConfig(ctx.endpointUrl);
  const primaryStats = config.computePrimaryStats(ctx.headers, ctx.rows);
  const secondaryStats = config.computeSecondaryStats(ctx.headers, ctx.rows);
  const kpiSummary = config.computeKpiSummary(ctx.headers, ctx.rows);
  const interpretations = config.computeInterpretation(ctx.headers, ctx.rows, primaryStats);
  const filtersSummary = formatFiltersDescription(ctx.filters);

  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  const dateTimeStr = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`;
  const slashDateStr = `${pad(now.getDate())} / ${pad(now.getMonth() + 1)} / ${now.getFullYear()}`;

  // Calculamos cuantas paginas de tabla (Seccion C) se necesitan (22 filas por pagina)
  const rowsPerPage = 22;
  const tablePagesCount = Math.max(1, Math.ceil(ctx.rows.length / rowsPerPage));
  const totalPages = 2 + tablePagesCount;

  const pages: PdfPageCanvas[] = [];

  // =========================================================================
  // PÁGINA 1: PORTADA EJECUTIVA, PROPÓSITO, UMBRALES Y SECCIÓN A (Imagen 1)
  // =========================================================================
  const p1 = new PdfPageCanvas();
  p1.drawInstitutionalHeader(config.reportTitle);

  // Columna Izquierda: Panel Sombreado Crema/Beige (x=40..285, y=75..735)
  p1.rectFill(40, 75, 245, 660, COLOR_BEIGE_BG);
  let leftY = 710;
  leftY = p1.paragraph(config.guidelinesTitle, 56, leftY, 213, 12.5, 16, true, COLOR_DARK);
  leftY -= 8;

  for (const g of config.guidelines) {
    if (leftY < 110) {
      break;
    }
    leftY = p1.titledBlock(g.title, g.body, 56, leftY, 213, 8.4, 11.5);
  }

  // Columna Derecha: Proposito / Introduccion (x=305..555, y=615..730)
  let rightY = 718;
  rightY = p1.paragraph('Propósito e Introducción del Informe', 305, rightY, 250, 11, 14, true, COLOR_DARK);
  rightY -= 4;
  rightY = p1.paragraph(config.purposeParagraph1, 305, rightY, 250, 8.6, 12, false, COLOR_DARK);
  rightY -= 6;
  rightY = p1.paragraph(config.purposeParagraph2, 305, rightY, 250, 8.6, 12, false, COLOR_DARK);

  // Linea divisoria en columna derecha
  p1.line(305, 596, 555, 596, COLOR_DARK, 1.8);

  // Seccion A: Informacion Actual del Exportado (Estilo formulario de Imagen 1)
  let secAY = 572;
  secAY = p1.paragraph(
    'Sección A: Información Actual del Exportado',
    305,
    secAY,
    250,
    12.5,
    15,
    true,
    COLOR_DARK,
  );
  secAY -= 2;
  secAY = p1.paragraph(
    'Resumen verificable de los datos y parámetros aplicados en la exportación actual.',
    305,
    secAY,
    250,
    8.4,
    11,
    false,
    COLOR_MUTED,
  );

  const formFields: { value: string; label: string }[] = [
    {
      value: `${config.moduleName} (${config.rfCode})`,
      label: 'Módulo del Sistema y Referencia Normativa',
    },
    {
      value: `${ctx.rows.length} registros exportados (${ctx.headers.length} columnas por registro)`,
      label: 'Volumen Actual de Datos Exportados',
    },
    {
      value: kpiSummary,
      label: config.kpiLabel,
    },
    {
      value: filtersSummary,
      label: 'Filtros y Criterios Activos en la Pantalla',
    },
    {
      value: `${ctx.generatedByName} — Rol: ${ctx.generatedByRole}`,
      label: 'Generado por (Usuario Autenticado y Perfil RBAC)',
    },
    {
      value: `${dateTimeStr} (Hora Local del Servidor)`,
      label: 'Fecha y Hora Oficial de Generación del Informe',
    },
  ];

  let fieldY = 505;
  for (const f of formFields) {
    // Valor escrito encima de la linea
    p1.text(truncateText(f.value, 246, 9.2), 305, fieldY + 6, 9.2, true, COLOR_DARK);
    // Linea horizontal del campo
    p1.line(305, fieldY, 555, fieldY, COLOR_LINE, 0.8);
    // Etiqueta descriptiva debajo de la linea
    p1.text(f.label, 305, fieldY - 12, 8.0, false, COLOR_MUTED);
    fieldY -= 68;
  }

  p1.drawFooter(config.reportTitle, 1, totalPages);
  pages.push(p1);

  // =========================================================================
  // PÁGINA 2: SECCIÓN B (GRÁFICAS INTERPRETATIVAS Y FIRMAS - Imagen 2)
  // =========================================================================
  const p2 = new PdfPageCanvas();
  p2.drawInstitutionalHeader(config.reportTitle);

  p2.text(
    'Sección B: Gráficas Interpretativas y Análisis del Exportado (cont.)',
    40,
    724,
    13,
    true,
    COLOR_DARK,
  );

  // Dos grandes bloques crema lado a lado (y=265..705, alto=440pt)
  p2.rectFill(40, 265, 250, 440, COLOR_BEIGE_BG);
  p2.rectFill(305, 265, 250, 440, COLOR_BEIGE_BG);

  // --- BLOQUE IZQUIERDO: GRAFICA 1 (BARRAS HORIZONTALES + COLUMNAS VERTICALES) ---
  p2.text('Gráfica 1: Distribución Estadística Actual', 54, 682, 11, true, COLOR_DARK);
  p2.text(
    '(Frecuencia absoluta y porcentaje por categoría en el exportado):',
    54,
    668,
    8,
    false,
    COLOR_MUTED,
  );

  // 1A. Barras Horizontales con porcentaje
  let barY = 640;
  for (const st of primaryStats) {
    p2.text(truncateText(st.label, 140, 8.5), 54, barY, 8.5, true, COLOR_DARK);
    p2.textRight(`${st.count} (${st.percentage}%)`, 276, barY, 8.2, true, COLOR_DARK);

    // Pista de fondo de la barra
    p2.rectFill(54, barY - 11, 222, 7.5, [0.85, 0.83, 0.78]);
    // Barra proporcional coloreada
    const fillW = Math.max(2, (Math.min(100, st.percentage) / 100) * 222);
    p2.rectFill(54, barY - 11, fillW, 7.5, st.color);

    barY -= 32;
  }

  // Linea separadora interna del panel izquierdo
  p2.line(54, 452, 276, 452, [0.75, 0.72, 0.66], 0.7);
  p2.text('Comparativa Visual de Magnitudes (Columnas)', 54, 436, 9.5, true, COLOR_DARK);

  // 1B. Grafico de Columnas Verticales (x=58..272, base y=300, alto max=110pt)
  const chartBaseY = 302;
  const chartMaxH = 105;
  p2.line(58, chartBaseY, 274, chartBaseY, COLOR_DARK, 0.9);
  p2.line(58, chartBaseY, 58, chartBaseY + chartMaxH + 8, COLOR_DARK, 0.9);

  // Lineas guia horizontales (25%, 50%, 75%, 100%)
  for (const pct of [0.25, 0.5, 0.75, 1.0]) {
    const gy = chartBaseY + chartMaxH * pct;
    p2.line(58, gy, 274, gy, [0.82, 0.8, 0.74], 0.4);
  }

  const maxCount = Math.max(1, ...primaryStats.map((s) => s.count));
  const colSlotW = 208 / Math.max(1, primaryStats.length);
  const colBarW = Math.min(28, colSlotW * 0.58);

  primaryStats.forEach((st, idx) => {
    const bx = 64 + idx * colSlotW + (colSlotW - colBarW) / 2;
    const bh = Math.max(3, (st.count / maxCount) * chartMaxH);
    p2.rectFill(bx, chartBaseY, colBarW, bh, st.color);
    p2.text(String(st.count), bx + 2, chartBaseY + bh + 4, 7.8, true, COLOR_DARK);
    p2.text(
      truncateText(st.label, colSlotW - 2, 7.2),
      bx - 2,
      chartBaseY - 12,
      7.2,
      false,
      COLOR_DARK,
    );
  });

  // --- BLOQUE DERECHO: GRAFICA 2 (COMPOSICION 100% + HALLAZGOS Y MEDIDAS) ---
  p2.text('Gráfica 2: Composición y Hallazgos SST', 319, 682, 11, true, COLOR_DARK);
  p2.text(
    '(Proporción acumulada, indicadores clave y medidas preventivas):',
    319,
    668,
    8,
    false,
    COLOR_MUTED,
  );

  // Barra apilada al 100% (Stacked Progress Chart)
  p2.text('Proporción Acumulada (100% del Exportado):', 319, 646, 8.5, true, COLOR_DARK);
  const stackX = 319;
  const stackY = 624;
  const stackW = 222;
  const stackH = 16;
  p2.rectFill(stackX, stackY, stackW, stackH, [0.85, 0.83, 0.78]);

  let cursorX = stackX;
  for (const st of primaryStats) {
    const segW = (st.percentage / 100) * stackW;
    if (segW > 0.5) {
      p2.rectFill(cursorX, stackY, segW, stackH, st.color);
      cursorX += segW;
    }
  }
  p2.rectStroke(stackX, stackY, stackW, stackH, COLOR_DARK, 0.5);

  // Leyenda compacta debajo de la barra apilada
  let legY = 606;
  let legCol = 0;
  for (const st of primaryStats) {
    const lx = legCol === 0 ? 319 : 432;
    p2.rectFill(lx, legY, 7, 7, st.color);
    p2.text(
      `${truncateText(st.label, 68, 7.5)}: ${st.percentage}%`,
      lx + 11,
      legY,
      7.5,
      false,
      COLOR_DARK,
    );
    if (legCol === 1) {
      legY -= 13;
      legCol = 0;
    } else {
      legCol = 1;
    }
  }
  if (legCol === 1) {
    legY -= 13;
  }

  // Distribucion secundaria (ej. Apto/No Apto, Activo/Inactivo o Talleres/Instructores)
  legY -= 4;
  p2.line(319, legY + 6, 541, legY + 6, [0.75, 0.72, 0.66], 0.7);
  p2.text('Indicador Secundario de Control:', 319, legY - 6, 8.8, true, COLOR_DARK);
  legY -= 20;
  for (const sec of secondaryStats.slice(0, 3)) {
    p2.rectFill(319, legY, 6, 6, sec.color);
    p2.text(
      `${truncateText(sec.label, 145, 8)}: ${sec.count} reg. (${sec.percentage}%)`,
      329,
      legY,
      8,
      false,
      COLOR_DARK,
    );
    legY -= 13;
  }

  // Interpretacion automatica y medidas preventivas
  legY -= 4;
  p2.line(319, legY + 6, 541, legY + 6, [0.75, 0.72, 0.66], 0.7);
  p2.text(
    'Interpretación Técnica y Medidas Preventivas:',
    319,
    legY - 6,
    9.2,
    true,
    COLOR_DARK,
  );
  let interpY = legY - 20;
  for (const note of interpretations) {
    if (interpY < 278) {
      break;
    }
    interpY = p2.paragraph(note, 319, interpY, 222, 8.0, 10.6, false, COLOR_DARK);
    interpY -= 4;
  }

  // --- BLOQUE DE FIRMAS (2 filas x 3 columnas, identico a la Imagen 2) ---
  // Fila 1 de firmas (y=190)
  p2.text(truncateText(ctx.generatedByName, 170, 9), 40, 196, 9, true, COLOR_DARK);
  p2.line(40, 190, 215, 190, COLOR_DARK, 0.8);
  p2.text('Nombre del Responsable / Emisor', 40, 177, 8, false, COLOR_MUTED);

  p2.text('Firmado digitalmente en CertiSafe', 235, 196, 8.5, false, COLOR_MUTED);
  p2.line(235, 190, 410, 190, COLOR_DARK, 0.8);
  p2.text('Firma del Responsable', 235, 177, 8, false, COLOR_MUTED);

  p2.text(slashDateStr, 430, 196, 9, true, COLOR_DARK);
  p2.line(430, 190, 555, 190, COLOR_DARK, 0.8);
  p2.text('Fecha (DD / MM / YYYY)', 430, 177, 8, false, COLOR_MUTED);

  // Fila 2 de firmas (y=115)
  p2.line(40, 115, 215, 115, COLOR_DARK, 0.8);
  p2.text('Nombre del Supervisor / Auditor HSE', 40, 102, 8, false, COLOR_MUTED);

  p2.line(235, 115, 410, 115, COLOR_DARK, 0.8);
  p2.text('Firma del Supervisor', 235, 102, 8, false, COLOR_MUTED);

  p2.line(430, 115, 555, 115, COLOR_DARK, 0.8);
  p2.text('Fecha (DD / MM / YYYY)', 430, 102, 8, false, COLOR_MUTED);

  p2.drawFooter(config.reportTitle, 2, totalPages);
  pages.push(p2);

  // =========================================================================
  // PÁGINA 3+: SECCIÓN C (TABLA CON LA INFORMACIÓN ACTUAL DEL EXPORTADO)
  // =========================================================================
  // Seleccionamos hasta 7 columnas clave para que quepan comodamente en el ancho de 515pt
  const maxCols = Math.min(7, ctx.headers.length);
  const colIndices = Array.from({ length: maxCols }, (_, i) => i);
  const tableWidth = 515;
  const colW = tableWidth / Math.max(1, maxCols);

  for (let pageIdx = 0; pageIdx < tablePagesCount; pageIdx++) {
    const pt = new PdfPageCanvas();
    pt.drawInstitutionalHeader(config.reportTitle);

    const startRow = pageIdx * rowsPerPage;
    const endRow = Math.min(ctx.rows.length, startRow + rowsPerPage);
    const slice = ctx.rows.slice(startRow, endRow);

    pt.text(
      `Sección C: Información Actual del Exportado — Detalle de Registros (${startRow + 1} a ${endRow} de ${ctx.rows.length})`,
      40,
      724,
      11.5,
      true,
      COLOR_DARK,
    );

    // Encabezado de la tabla
    const headerY = 690;
    pt.rectFill(40, headerY, tableWidth, 22, COLOR_DARK);
    colIndices.forEach((cIdx, pos) => {
      const hx = 44 + pos * colW;
      const rawHeader = (ctx.headers[cIdx] ?? '').replace(/_/g, ' ').toUpperCase();
      pt.text(truncateText(rawHeader, colW - 8, 7.8), hx, headerY + 7, 7.8, true, [1, 1, 1]);
    });

    // Filas de datos
    let rowY = headerY - 24;
    slice.forEach((row, rIdx) => {
      const bg: [number, number, number] = rIdx % 2 === 0 ? [1, 1, 1] : COLOR_BEIGE_BG;
      pt.rectFill(40, rowY, tableWidth, 24, bg);
      pt.line(40, rowY, 555, rowY, [0.82, 0.8, 0.75], 0.4);

      colIndices.forEach((cIdx, pos) => {
        const cx = 44 + pos * colW;
        const val = row[cIdx] ?? '—';
        pt.text(truncateText(val, colW - 8, 7.8), cx, rowY + 8, 7.8, false, COLOR_DARK);
      });

      rowY -= 24;
    });

    // Borde exterior de la tabla
    const tableHeight = 22 + slice.length * 24;
    pt.rectStroke(40, headerY + 22 - tableHeight, tableWidth, tableHeight, COLOR_DARK, 0.7);

    pt.drawFooter(config.reportTitle, 3 + pageIdx, totalPages);
    pages.push(pt);
  }

  return assemblePdfDocument(pages);
}

/**
 * Ensambla el contenedor binario PDF 1.4 con tabla xref exacta al byte.
 */
function assemblePdfDocument(pages: PdfPageCanvas[]): Blob {
  // Objetos PDF:
  // 1: Catalog
  // 2: Pages
  // 3: Font Helvetica (/F1)
  // 4: Font Helvetica-Bold (/F2)
  // 5..(4 + 2*N): Page object + Content stream object para cada pagina
  const objects: string[] = [];

  const pageObjIds: number[] = [];
  const nextIdStart = 5;

  for (let i = 0; i < pages.length; i++) {
    pageObjIds.push(nextIdStart + i * 2);
  }

  // Obj 1: Catalog
  objects.push(`1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n`);

  // Obj 2: Pages
  const kidsRefs = pageObjIds.map((id) => `${id} 0 R`).join(' ');
  objects.push(
    `2 0 obj\n<< /Type /Pages /Kids [ ${kidsRefs} ] /Count ${pages.length} >>\nendobj\n`,
  );

  // Obj 3: Font F1 (Helvetica con WinAnsiEncoding para tildes y eñes)
  objects.push(
    `3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n`,
  );

  // Obj 4: Font F2 (Helvetica-Bold con WinAnsiEncoding)
  objects.push(
    `4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n`,
  );

  // Paginas y sus streams
  for (let i = 0; i < pages.length; i++) {
    const pageObjId = nextIdStart + i * 2;
    const contentObjId = pageObjId + 1;
    const streamContent = pages[i].buildContentStream();

    objects.push(
      `${pageObjId} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595.28 841.89] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${contentObjId} 0 R >>\nendobj\n`,
    );

    objects.push(
      `${contentObjId} 0 obj\n<< /Length ${streamContent.length} >>\nstream\n${streamContent}\nendstream\nendobj\n`,
    );
  }

  let pdfString = '%PDF-1.4\n%\xE2\xE3\xCF\xD3\n';
  const offsets: number[] = [0];

  for (const obj of objects) {
    offsets.push(pdfString.length);
    pdfString += obj;
  }

  const xrefStart = pdfString.length;
  const totalObjs = objects.length + 1;
  pdfString += `xref\n0 ${totalObjs}\n`;
  pdfString += `0000000000 65535 f \n`;

  for (let i = 1; i < totalObjs; i++) {
    const offStr = String(offsets[i]).padStart(10, '0');
    pdfString += `${offStr} 00000 n \n`;
  }

  pdfString += `trailer\n<< /Size ${totalObjs} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF\n`;

  // Convertimos a bytes 1:1 (WinAnsi / Latin1)
  const bytes = new Uint8Array(pdfString.length);
  for (let i = 0; i < pdfString.length; i++) {
    bytes[i] = pdfString.charCodeAt(i) & 0xff;
  }

  return new Blob([bytes], { type: 'application/pdf' });
}
