# Safe · Tablero web

Frontend React/Vite conectado con la API FastAPI de `../Backend`. Muestra datos del servidor y errores de conexión; no utiliza resultados de demostración como reemplazo.

## Ejecución local

Con el backend y PostgreSQL encendidos en Docker, desde esta carpeta:

```powershell
npm install
npm run dev
```

Abre http://localhost:5173. El proxy de Vite reenvía `/api` y `/ws` a `127.0.0.1:8000`.

En la base local preparada para este proyecto, inicia sesión con **usuario `admin` y contraseña `admin`**. La web traduce ese usuario a `admin@example.org` y autentica mediante `POST /api/v1/auth/login`. La cuenta vive en PostgreSQL: si se elimina el volumen, deberá aprovisionarse de nuevo. Las demás cuentas ingresan con su correo y contraseña.

El JWT se conserva en memoria y se elimina al cerrar sesión, recargar la página o vencer. La API decide los permisos: administración, equipo clínico e investigación. Las cuentas de servicio no acceden al tablero.

## Funciones conectadas

- Participantes: listado, búsqueda, filtro por alertas, CSV, resumen, resultados y dispositivos.
- Evaluaciones: únicamente PHQ-9 (`PHQ9`), GAD-7 (`GAD7`), ULS-6 (`ULS6`) y C-SSRS (`CSSRS_SCREEN`), leídas de `/api/v1/catalog/questionnaires/{code}` con sus opciones y condiciones. La ficha filtra resultados a estos cuatro instrumentos.
- Administración/investigación: registro de participantes, código de vinculación y revocación de dispositivos.
- Administración/equipo clínico: alertas, inicio de revisión, resolución y consulta explícita de contacto (auditada por la API).
- Administración: creación de cuentas de personal y retiro de participantes.
- Métricas de latencia y eventos por WebSocket autenticado. Los datos se actualizan también cada minuto.

Las alertas son información para revisión profesional. La ausencia de alertas en las 500 filas cargadas no equivale a una evaluación clínica. Los puntajes se muestran tal como los devuelve el backend.

## Límites actuales de la API

El historial de respuestas ofrece puntajes y subescalas, pero no respuestas individuales a cada pregunta. No hay listado de cuentas de personal ni consulta de historial de predicciones. El canal en vivo muestra eventos desde que se abre esta sesión; no es un historial de auditoría. Evaluaciones web consulta el catálogo; la aplicación móvil conserva el flujo para responder cuestionarios.

## Configuración y verificación

`.env.example` documenta `VITE_API_BASE_URL` y `VITE_WS_URL` para otro servidor. En producción, configura un proxy HTTPS para `/api` y `/ws` o define esas variables antes de compilar y permite el origen web en el CORS del backend. El proxy de Vite se aplica solo al desarrollo.

```powershell
npm test
npm run build
```

Las pruebas cubren el filtro de instrumentos, prioridades por alertas, permisos de rol, protección CSV y errores/autenticación del cliente HTTP.
