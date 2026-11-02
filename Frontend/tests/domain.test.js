import test from 'node:test';
import assert from 'node:assert/strict';
import { FORMS, participantPriority, selectedResponses, clinicalRole, enrollRole, csvCell, dateTime } from '../src/domain.js';

test('solo los cuatro instrumentos acordados aparecen en resultados', () => {
  assert.deepEqual(FORMS.map(f => f.code), ['PHQ9', 'GAD7', 'ULS6', 'CSSRS_SCREEN']);
  const rows = ['PHQ9', 'SUS', 'EMA', 'GAD7', 'CSSRS_SCREEN', 'ULS6'].map(instrument_code => ({ instrument_code }));
  assert.deepEqual(selectedResponses(rows).map(r => r.instrument_code), ['PHQ9', 'GAD7', 'CSSRS_SCREEN', 'ULS6']);
});
test('prioridad usa solo alertas activas del participante y respeta acceso clínico', () => {
  const alerts = [
    { participant_code: 'P1', status: 'closed', severity: 'high' },
    { participant_code: 'P1', status: 'acknowledged', severity: 'moderate' },
    { participant_code: 'P2', status: 'open', severity: 'high' },
  ];
  assert.equal(participantPriority('P1', alerts, true).key, 'moderate');
  assert.equal(participantPriority('P1', alerts, false).key, 'unknown');
  assert.equal(participantPriority('P3', alerts, true).tone, 'neutral');
  assert.equal(participantPriority('P3', alerts, true).key, 'none');
  assert.equal(clinicalRole('researcher'), false);
  assert.equal(clinicalRole('clinician'), true);
  assert.equal(enrollRole('clinician'), false);
  assert.equal(enrollRole('researcher'), true);
});
test('CSV escapa fórmulas y comillas; fechas inválidas no se muestran como resultados', () => {
  assert.equal(csvCell('=1+1'), '"\'=1+1"');
  assert.equal(csvCell('Texto "citado"'), '"Texto ""citado"""');
  assert.equal(dateTime(null), '—');
  assert.equal(dateTime('inválida'), '—');
});
