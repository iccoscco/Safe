export const FORMS = [
  { code: 'PHQ9', label: 'PHQ-9', description: 'Síntomas depresivos', color: 'blue' },
  { code: 'GAD7', label: 'GAD-7', description: 'Síntomas de ansiedad', color: 'teal' },
  { code: 'ULS6', label: 'ULS-6', description: 'Soledad percibida', color: 'violet' },
  { code: 'CSSRS_SCREEN', label: 'C-SSRS', description: 'Ideación y conducta', color: 'rose' },
];
export const formLabel = code => FORMS.find(x => x.code === code)?.label || code;
export const roleLabel = role => ({ admin: 'Administración', clinician: 'Equipo clínico', researcher: 'Investigación' }[role] || role);
export const clinicalRole = role => ['admin', 'clinician'].includes(role);
export const enrollRole = role => ['admin', 'researcher'].includes(role);
export const activeAlert = a => ['open', 'acknowledged'].includes(a.status);
export const statuses = { open: 'Abierta', acknowledged: 'En revisión', referred: 'Derivada', closed: 'Cerrada' };
export const severity = {
  high: { label: 'Alta', tone: 'red', order: 3 },
  moderate: { label: 'Moderada', tone: 'amber', order: 2 },
  low: { label: 'Baja', tone: 'green', order: 1 },
};
export function participantPriority(code, alerts, allowed) {
  if (!allowed) return { label: 'Sin acceso clínico', tone: 'neutral', key: 'unknown' };
  const found = alerts.filter(a => a.participant_code === code && activeAlert(a))
    .sort((a,b) => (severity[b.severity]?.order || 0) - (severity[a.severity]?.order || 0))[0];
  return found ? { ...severity[found.severity], key: found.severity }
    : { label: 'Sin alerta en esta vista', tone: 'neutral', key: 'none' };
}
export function selectedResponses(rows) { return rows.filter(r => FORMS.some(f => f.code === r.instrument_code)); }
export function dateTime(value) {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? '—' : new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
  }).format(d);
}
export function csvCell(value) {
  let text = String(value ?? '');
  if (/^[=+@\-\t\r]/.test(text)) text = "'" + text;
  return `"${text.replaceAll('"', '""')}"`;
}
export function errorText(e) { return e instanceof TypeError ? 'No se pudo conectar con la API. Comprueba que el backend esté encendido.' : e.message || 'Ocurrió un error.'; }
