import React, { useEffect, useId, useRef, useState } from 'react';
import { ArrowRight, CheckCircle2, ChevronRight, ClipboardList, Download, Eye, EyeOff, LockKeyhole, RefreshCw, Search, ShieldCheck, X } from 'lucide-react';
import { createApi } from './api';
import { FORMS, csvCell, errorText, participantPriority } from './domain';

export function ErrorBox({ children }) { return children ? <div className="error-box" role="alert">{children}</div> : null; }
export function Empty({ children }) { return <div className="empty">{children}</div>; }
export function Badge({ tone = 'neutral', children }) { return <span className={`status-chip ${tone}`}>{children}</span>; }
export function Button({ children, onClick, disabled, primary = false, type = 'button', ...rest }) {
  return <button type={type} className={primary ? 'btn-primary' : 'btn-secondary'} disabled={disabled} onClick={onClick} {...rest}>{children}</button>;
}
export function Field({ label, children, ...props }) {
  const id = useId();
  return <label className="field" htmlFor={id}><span>{label}</span>{children ? React.cloneElement(children, { id }) : <input id={id} {...props}/>}</label>;
}
export function Modal({ title, onClose, children, wide = false }) {
  const ref = useRef(); const id = useId();
  useEffect(() => {
    const dialog = ref.current; const before = document.body.style.overflow;
    dialog.showModal(); document.body.style.overflow = 'hidden';
    return () => { dialog.close(); document.body.style.overflow = before; };
  }, []);
  return <dialog ref={ref} className={`real-dialog ${wide ? 'wide' : ''}`} aria-labelledby={id}
    onCancel={e => { e.preventDefault(); onClose(); }} onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
    <div className="dialog-head"><h2 id={id}>{title}</h2><button className="icon-button" aria-label="Cerrar" onClick={onClose}><X size={21}/></button></div>
    <div className="dialog-body">{children}</div>
  </dialog>;
}
export function Metric({ icon: Icon, label, value, note, tone = 'blue' }) {
  return <div className="metric card"><div className={`metric-icon ${tone}`}><Icon size={19}/></div><div className="metric-label">{label}</div><div className="metric-value">{value}</div><div className="metric-note">{note}</div></div>;
}

export function Login({ onLogin, message }) {
  const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const [visible, setVisible] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  async function submit(e) {
    e.preventDefault(); setBusy(true); setError('');
    try {
      const token = await createApi()('/auth/login', { method: 'POST', body: { email: email.trim() === 'admin' ? 'admin@example.org' : email.trim(), password } });
      const user = await createApi(token.access_token)('/auth/me');
      if (!['admin','clinician','researcher'].includes(user.role)) throw new Error('Esta cuenta de servicio no tiene acceso al tablero de personal.');
      setPassword(''); onLogin({ token: token.access_token, user, expires: Date.now() + token.expires_in_minutes * 60000 });
    } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  return <main className="login-page"><section className="login-story"><div className="login-brand"><span className="brand-mark">S</span><b>Safe<span>Bienestar universitario</span></b></div>
    <div className="login-story-copy"><span className="eyebrow">EPIS · UNSA</span><h1>La información que necesitas.<br/>El cuidado que importa.</h1><p>Un espacio de trabajo para revisar evaluaciones, acompañar a participantes y dar seguimiento a las alertas del estudio.</p>
      <div className="login-form-list">{FORMS.map(f => <span key={f.code}>{f.label}</span>)}</div></div><div className="login-story-foot"><ShieldCheck size={17}/> Acceso de personal autorizado</div></section>
    <section className="login-side"><form className="login-card" onSubmit={submit}><div className="login-lock"><LockKeyhole size={25}/></div><div className="eyebrow">TABLERO DE SEGUIMIENTO</div><h2>Bienvenido de nuevo</h2><p>Inicia sesión con tu cuenta del estudio.</p>
      {message && <div className="inline-note" role="status">{message}</div>}<ErrorBox>{error}</ErrorBox>
      <Field label="Usuario" type="text" autoComplete="username" placeholder="admin" required value={email} onChange={e=>setEmail(e.target.value)} disabled={busy}/>
      <label className="field"><span>Contraseña</span><div className="password-input"><input aria-label="Contraseña" type={visible ? 'text' : 'password'} autoComplete="current-password" required value={password} onChange={e=>setPassword(e.target.value)} disabled={busy}/><button type="button" aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'} onClick={()=>setVisible(!visible)}>{visible ? <EyeOff size={18}/> : <Eye size={18}/>}</button></div></label>
      <Button primary type="submit" disabled={busy}>{busy ? 'Verificando acceso…' : 'Iniciar sesión'}<ArrowRight size={17}/></Button>
      <p className="login-help">Ingresa con tu usuario o con el correo de tu cuenta de personal.</p></form><p className="login-bottom">Safe · Sistema de seguimiento del estudio</p></section></main>;
}

export function QuestionnaireModal({ document: q, onClose }) {
  const items = q.items.flatMap(item => [item, ...(item.sub_items || []).map(sub => ({ ...sub, options: sub.options || item.options }))]);
  if (q.functional_impact_item) items.push({ ...q.functional_impact_item, optional: true });
  return <Modal title={q.name} onClose={onClose} wide><div className="inline-note">Contenido del backend · versión {q.version} · Vista de consulta</div>
    <p className="muted">{q.time_frame || q.time_frames?.join(' · ')}</p>{q.translation_note && <p className="small-note">{q.translation_note}</p>}
    {items.map(item => <section className="question-preview" key={item.backend_item}><div className="question-meta">Ítem {item.backend_item}{item.optional ? ' · Impacto funcional opcional' : item.is_conditional ? ' · Condicional' : ''}</div><h3>{item.text}</h3>{item.time_frame && <p>{item.time_frame}</p>}
      {item.depends_on && <p className="question-condition">Se muestra cuando {item.depends_on.item_code} = {item.depends_on.expected_value === true ? 'Sí' : item.depends_on.expected_value === false ? 'No' : String(item.depends_on.expected_value)}</p>}
      <div className="preview-options">{(item.options || q.scale?.options || []).map(option => <span key={String(option.value)}>{option.label}</span>)}</div></section>)}
    {q.branching_summary && <div className="inline-note">{q.branching_summary}</div>}</Modal>;
}

export function Evaluations({ forms, loading, onRefresh }) {
  const [selected, setSelected] = useState(null);
  return <><div className="page-heading"><div><div className="eyebrow">CUATRO INSTRUMENTOS · UN MISMO SEGUIMIENTO</div><h1>Evaluaciones</h1><p>Preguntas y opciones obtenidas del catálogo de la API.</p></div><Button onClick={onRefresh} disabled={loading}><RefreshCw size={16}/>Actualizar</Button></div>
    <div className="questionnaire-grid">{FORMS.map(f => { const q = forms.find(q=>q.backend.instrument===f.code); return <article className={`card questionnaire-card ${f.color}`} key={f.code}><div className="questionnaire-top"><div className="info-icon"><ClipboardList size={22}/></div><Badge>{q ? `Versión ${q.version}` : loading ? 'Cargando' : 'No disponible'}</Badge></div><h2>{f.label}</h2><p>{f.description}</p><div className="form-time">{q ? `${q.items.length} preguntas principales${q.functional_impact_item ? ' + impacto opcional' : ''}` : 'No se recibió el contenido del servidor.'}</div><Button onClick={()=>setSelected(q)} disabled={!q}>Ver preguntas<ChevronRight size={16}/></Button></article>; })}</div>
    {selected && <QuestionnaireModal document={selected} onClose={()=>setSelected(null)}/>}</>;
}

export function ParticipantTable({ rows, alerts, clinical, onSelect, query, setQuery, filter, setFilter, loading, unavailable }) {
  const visible = rows.filter(p => `${p.code} ${p.cohort || ''}`.toLowerCase().includes(query.toLowerCase()) &&
    (filter === 'all' || participantPriority(p.code, alerts, clinical).key === filter));
  function exportCsv() {
    const lines = [['Código','Cohorte','Semestre','Estado','Prioridad según alertas cargadas'], ...visible.map(p=>[p.code,p.cohort,p.semester,p.status,participantPriority(p.code,alerts,clinical).label])];
    const url = URL.createObjectURL(new Blob(['\ufeff'+lines.map(r=>r.map(csvCell).join(',')).join('\n')], { type:'text/csv;charset=utf-8' }));
    const a = document.createElement('a'); a.href=url; a.download='safe-participantes.csv'; a.click(); setTimeout(()=>URL.revokeObjectURL(url),1000);
  }
  return <section className="card case-card"><div className="section-head"><div><h2>Participantes del estudio</h2><p>Códigos seudonimizados · datos recibidos de la API</p></div><Button onClick={exportCsv} disabled={!visible.length}><Download size={14}/>CSV</Button></div>
    <div className="toolbar page-toolbar"><div className="searchbox"><Search size={17}/><input aria-label="Buscar participante" placeholder="Buscar por código o cohorte…" value={query} onChange={e=>setQuery(e.target.value)}/></div>
      {clinical && <select aria-label="Filtrar por alerta" value={filter} onChange={e=>setFilter(e.target.value)}><option value="all">Todas las alertas</option><option value="high">Alta</option><option value="moderate">Moderada</option><option value="low">Baja</option><option value="none">Sin alerta en esta vista</option></select>}</div>
    <div className="table-wrap"><table><thead><tr><th>PARTICIPANTE</th><th>ALERTA EN ESTA VISTA</th><th>ESTADO</th><th/></tr></thead><tbody>{visible.map(p=>{const priority=participantPriority(p.code,alerts,clinical);return <tr key={p.code}><td><div className="person"><div className={`status-bar ${priority.tone}`}/><div><b>{p.code}</b><small>{p.cohort || 'Sin cohorte'}{p.semester ? ` · Semestre ${p.semester}` : ''}</small></div></div></td><td><Badge tone={priority.tone}>{priority.label}</Badge></td><td>{p.status==='withdrawn'?'Retirado':p.status==='active'?'Activo':'Inscrito'}</td><td><button className="view-btn" onClick={()=>onSelect(p.code)}>Ver ficha</button></td></tr>;})}</tbody></table></div>
    {!visible.length && <Empty>{loading ? 'Cargando participantes…' : unavailable ? 'No se pudo consultar el listado. Actualiza los datos para reintentar.' : 'No hay participantes que coincidan con la vista.'}</Empty>}<div className="table-foot">{unavailable ? 'Listado no disponible' : `${visible.length} participantes`} · Una ausencia de alertas no equivale a una evaluación clínica.</div></section>;
}
