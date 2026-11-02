import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Activity, Bell, ChevronRight, ClipboardList, HeartPulse, LayoutDashboard, LockKeyhole, LogOut, Menu, Plus, RefreshCw, ShieldCheck, Users } from 'lucide-react';
import { createApi, dashboardSocketUrl } from './api';
import { FORMS, activeAlert, clinicalRole, dateTime, enrollRole, errorText, roleLabel } from './domain';
import { Badge, Button, Empty, ErrorBox, Evaluations, Login, Metric, ParticipantTable } from './components';
import { AlertList, CreateParticipant, ParticipantModal, Team } from './views';
import './styles.css';

const emptyData = { participants: [], alerts: [], forms: [], latency: [] };
function App() {
 const [session,setSession]=useState(null);const [loginMessage,setLoginMessage]=useState('');const [page,setPage]=useState('dashboard');const [menu,setMenu]=useState(false);
 const [data,setData]=useState(emptyData);const [errors,setErrors]=useState([]);const [loading,setLoading]=useState(false);const [revision,setRevision]=useState(0);const [updated,setUpdated]=useState(null);
 const [query,setQuery]=useState('');const [filter,setFilter]=useState('all');const [selected,setSelected]=useState(null);const [creating,setCreating]=useState(false);const [events,setEvents]=useState([]);const [live,setLive]=useState('Desconectado');
 const logout=useCallback((message='')=>{setSession(null);setLoginMessage(message);setData(emptyData);setSelected(null);setCreating(false);setEvents([]);setPage('dashboard');setQuery('');setFilter('all');setErrors([]);},[]);
 const expire=useCallback(()=>logout('Tu sesión terminó. Inicia sesión de nuevo.'),[logout]);
 const api=useMemo(()=>createApi(session?.token,expire),[session?.token,expire]);
 const reload=useCallback(()=>setRevision(x=>x+1),[]);
 useEffect(()=>{if(!session)return;const timer=setTimeout(expire,Math.max(0,session.expires-Date.now()));return()=>clearTimeout(timer);},[session,expire]);
 useEffect(()=>{
  if(!session)return;const controller=new AbortController();setLoading(true);setErrors([]);
  const tasks=[['participants',()=>api('/participants',{signal:controller.signal})],['forms',()=>Promise.all(FORMS.map(f=>api(`/catalog/questionnaires/${f.code}`,{signal:controller.signal})))],
    ['latency',()=>api('/metrics/latency',{signal:controller.signal})]];
  if(clinicalRole(session.user.role))tasks.push(['alerts',()=>api('/alerts?status=all&limit=500',{signal:controller.signal})]);
  Promise.allSettled(tasks.map(([,fn])=>fn())).then(results=>{
    if(controller.signal.aborted)return;const next={...emptyData};const failures=[];
    results.forEach((result,i)=>{if(result.status==='fulfilled')next[tasks[i][0]]=result.value;else failures.push(`${({participants:'Participantes',forms:'Evaluaciones',adherence:'Adherencia',latency:'Latencia',alerts:'Alertas'})[tasks[i][0]]}: ${errorText(result.reason)}`);});
    setData(next);setErrors(failures);setUpdated(new Date().toISOString());setLoading(false);
  });return()=>controller.abort();
 },[session,api,revision]);
 useEffect(()=>{if(!session)return;const t=setInterval(reload,60000);return()=>clearInterval(t);},[session,reload]);
 useEffect(()=>{
  if(!session||!clinicalRole(session.user.role)){setLive('Según rol');return;}
  let stopped=false;let socket;let reconnect;let ping;let debounce;let attempt=0;
  function connect(){
    if(stopped)return;setLive('Conectando');socket=new WebSocket(dashboardSocketUrl());
    socket.onopen=()=>socket.send(JSON.stringify({type:'auth',token:session.token}));
    socket.onmessage=e=>{let event;try{event=JSON.parse(e.data);}catch{return;}
      if(event.type==='auth_ok'){setLive('En vivo');attempt=0;ping=setInterval(()=>{if(socket.readyState===WebSocket.OPEN)socket.send(JSON.stringify({type:'ping'}));},25000);}
      else if(['alert.created','alert.updated','ingest.batch'].includes(event.type)){setEvents(prev=>[event,...prev].slice(0,50));clearTimeout(debounce);debounce=setTimeout(reload,500);}
    };
    socket.onerror=()=>{if(!stopped)setLive('Reconectando');};
    socket.onclose=e=>{clearInterval(ping);if(stopped)return;if(e.code===1008){expire();return;}setLive('Reconectando');reconnect=setTimeout(connect,Math.min(30000,1000*2**attempt++));};
  }
  connect();return()=>{stopped=true;clearTimeout(reconnect);clearTimeout(debounce);clearInterval(ping);socket?.close();};
 },[session,reload,expire]);
 if(!session)return <Login message={loginMessage} onLogin={value=>{setLoginMessage('');setSession(value);}}/>;
 const user=session.user;const clinical=clinicalRole(user.role);const clinicalReady=clinical&&!errors.some(x=>x.startsWith('Alertas:'));
 const openAlerts=data.alerts.filter(activeAlert);const high=openAlerts.filter(a=>a.severity==='high').length;
 const nav=[['dashboard','Tablero general',LayoutDashboard],['participants','Participantes',Users],...(clinical?[['alerts','Alertas',Bell]]:[]),['evaluations','Evaluaciones',ClipboardList],['activity','Actividad en vivo',Activity],...(user.role==='admin'?[['team','Accesos del equipo',ShieldCheck]]:[])];
 const title=nav.find(x=>x[0]===page)?.[1]||'Tablero';
 const participantsUnavailable=errors.some(x=>x.startsWith('Participantes:'));
 const formsUnavailable=errors.some(x=>x.startsWith('Evaluaciones:'));
 const participantProps={loading,unavailable:participantsUnavailable,rows:data.participants,alerts:data.alerts,clinical:clinicalReady,onSelect:setSelected,query,setQuery,filter,setFilter};
 return <div className="app-shell"><aside className={`sidebar ${menu?'sidebar-open':''}`}><div className="brand"><div className="brand-mark">S</div><div><div className="brand-title">Safe</div><div className="brand-sub">Bienestar · EPIS-UNSA</div></div></div><div className="side-label">SEGUIMIENTO</div>{nav.map(([id,label,Icon])=><button key={id} className={`nav ${page===id?'active':''}`} onClick={()=>{setPage(id);setMenu(false);}}><Icon size={18}/>{label}{id==='alerts'&&openAlerts.length>0&&<span className="nav-count">{openAlerts.length}</span>}</button>)}
  <div className="sidebar-bottom"><div className="secure"><ShieldCheck size={17}/><span>Sesión autenticada<br/><b>{roleLabel(user.role)}</b></span></div><div className="user-row"><div className="avatar">{user.full_name.slice(0,2).toUpperCase()}</div><div><b>{user.full_name}</b><small>{user.email}</small></div></div><button className="nav logout" onClick={()=>logout()}><LogOut size={17}/>Cerrar sesión</button></div></aside>
  <main className="main-area"><header className="topbar"><button className="mobile-menu" onClick={()=>setMenu(!menu)} aria-label="Abrir menú"><Menu/></button><div className="crumb">Safe <span>/</span><b>{title}</b></div><div className="top-actions"><span className="demo-pill"><span/>{clinical?live:'API conectada'}</span><button className="icon-button" aria-label="Actualizar datos" disabled={loading} onClick={reload}><RefreshCw size={18} className={loading?'spinning':''}/></button><div className="avatar">{user.full_name.slice(0,2).toUpperCase()}</div></div></header>
  <div className="content">{errors.map((e,i)=><ErrorBox key={i}>{e}</ErrorBox>)}{page==='dashboard'&&<><div className="page-heading"><div><div className="eyebrow">SEGUIMIENTO DEL ESTUDIO · DATOS DE LA API</div><h1>Tablero de bienestar</h1><p>Una vista compartida para revisar la información y dar seguimiento.</p></div>{enrollRole(user.role)&&<Button primary onClick={()=>setCreating(true)}><Plus size={16}/>Nuevo participante</Button>}</div>
   <div className="notice"><ShieldCheck size={20}/><div><b>Información para revisión profesional</b><p>La prioridad se obtiene de las alertas cargadas del servidor. Los indicadores no constituyen diagnósticos.</p></div></div>
   <section className="metrics-grid"><Metric icon={Users} label="Participantes registrados" value={loading?'…':participantsUnavailable?'—':data.participants.length} note="Listado actual del estudio"/><Metric icon={Bell} label="Alertas abiertas / en revisión" value={!clinicalReady?'—':loading?'…':openAlerts.length} note={clinical?'Dentro de las 500 alertas cargadas':'Disponible para el equipo clínico'} tone="red"/><Metric icon={HeartPulse} label="Alertas de prioridad alta" value={!clinicalReady?'—':loading?'…':high} note="Abiertas o en revisión" tone="amber"/><Metric icon={ClipboardList} label="Cuestionarios disponibles" value={loading?'…':formsUnavailable?'—':data.forms.length} note="PHQ-9 · GAD-7 · ULS-6 · C-SSRS" tone="green"/></section>
   <div className="lower-grid"><ParticipantTable {...participantProps}/><div className="right-col"><section className="card summary-card"><h2>Evaluaciones del estudio</h2><p className="muted">Los cuatro instrumentos conectados con la app.</p>{FORMS.map(f=><div className="instrument-line" key={f.code}><span>{f.label}</span><Badge tone={data.forms.some(q=>q.backend.instrument===f.code)?'green':'neutral'}>{data.forms.some(q=>q.backend.instrument===f.code)?'Disponible':'Sin cargar'}</Badge></div>)}<button className="text-link" onClick={()=>setPage('evaluations')}>Consultar preguntas <ChevronRight size={14}/></button></section><section className="card summary-card"><h2>Procesamiento del sistema</h2>{data.latency.length?data.latency.map(l=><div className="instrument-line" key={l.kind}><span>{l.kind}</span><b>{l.median_ms??'—'} ms<small>Mediana · {l.n} registros</small></b></div>):<p className="muted">No hay métricas de procesamiento disponibles.</p>}<p className="small-note">Última consulta: {dateTime(updated)} · Hora de Lima.</p></section></div></div></>}
   {page==='participants'&&<><div className="page-heading"><div><div className="eyebrow">GESTIÓN DEL ESTUDIO</div><h1>Participantes</h1><p>Consulta las fichas y las evaluaciones recibidas.</p></div>{enrollRole(user.role)&&<Button primary onClick={()=>setCreating(true)}><Plus size={16}/>Nuevo participante</Button>}</div><ParticipantTable {...participantProps}/></>}
   {page==='alerts'&&clinicalReady&&<AlertList alerts={data.alerts} api={api} onChanged={reload} onSelect={setSelected}/>}
   {page==='evaluations'&&<Evaluations forms={data.forms} loading={loading} onRefresh={reload}/>}
   {page==='team'&&user.role==='admin'&&<Team api={api}/>}
   {page==='activity'&&<><div className="page-heading"><div><div className="eyebrow">EVENTOS DEL SERVIDOR</div><h1>Actividad en vivo</h1><p>Eventos recibidos durante esta sesión. No es un historial de auditoría.</p></div><Badge>{live}</Badge></div><section className="card activity-list">{events.map((event,i)=><div className="activity-item" key={`${event.at}-${i}`}><div className="activity-mark"><Activity size={16}/></div><div><b>{({'alert.created':'Nueva alerta','alert.updated':'Alerta actualizada','ingest.batch':'Datos sincronizados'})[event.type]}</b><small>{event.data.participant_code} · {dateTime(event.at)}</small></div></div>)}{!events.length&&<Empty>{clinical?'Todavía no se recibieron eventos en esta sesión.':'El canal de alertas en vivo está disponible para administración y equipo clínico.'}</Empty>}</section></>}
   <footer><span>Safe · EPIS-UNSA</span><span><LockKeyhole size={13}/>{roleLabel(user.role)}</span></footer>
  </div></main>
  {selected&&<ParticipantModal key={selected} code={selected} api={api} user={user} revision={revision} onClose={()=>setSelected(null)} onChanged={reload}/>}
  {creating&&<CreateParticipant api={api} onClose={()=>setCreating(false)} onCreated={reload}/>}
 </div>;
}
const root = createRoot(document.getElementById('root'));
root.render(<React.StrictMode><App/></React.StrictMode>);
if (import.meta.hot) import.meta.hot.dispose(() => root.unmount());

