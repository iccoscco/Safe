# Safe

Safe es un proyecto de seguimiento del bienestar universitario para el estudio GSTI de la Escuela Profesional de Ingeniería de Sistemas de la UNSA. Reúne una aplicación Android para participantes, una API para almacenar y procesar información y un tablero web para el personal del estudio.

La app permite responder cuestionarios y un check-in diario, consultar recursos de apoyo y sincronizar voluntariamente datos de Health Connect. El personal utiliza la web para consultar participantes, revisar resultados y dar seguimiento a las alertas que genera el servidor.

**Estado documentado: 2 de noviembre de 2026.** Los cuestionarios habilitados en Android y web son **PHQ-9, GAD-7, ULS-6 y C-SSRS**. EMA diaria se conserva como función independiente de la app. El chatbot sigue siendo una interfaz de demostración, sin servicio de conversación conectado.

Las alertas y los puntajes son información para revisión profesional; no constituyen diagnósticos. La integración técnica y los datos sintéticos de prueba no acreditan una validación clínica del sistema.

## Contenido

- [Arquitectura y estructura](#arquitectura-y-estructura)
- [Funciones actuales](#funciones-actuales)
- [Requisitos](#requisitos)
- [Ejecutar el proyecto](#ejecutar-el-proyecto)
- [Acceso y vinculación](#acceso-y-vinculación)
- [Cuestionarios y API](#cuestionarios-y-api)
- [Configuración](#configuración)
- [Compilación y pruebas](#compilación-y-pruebas)
- [Modelo predictivo](#modelo-predictivo)
- [Privacidad y almacenamiento](#privacidad-y-almacenamiento)
- [Limitaciones y pendientes](#limitaciones-y-pendientes)
- [Problemas frecuentes](#problemas-frecuentes)
- [Documentación por módulo](#documentación-por-módulo)

## Arquitectura y estructura

```mermaid
flowchart LR
    HC[Health Connect] -->|Lectura manual autorizada| APP[App Android]
    APP -->|Catálogos, respuestas, EMA y sensores| API[API FastAPI]
    WEB[Tablero web React] -->|JWT de personal| API
    API -->|Alertas y eventos por WebSocket| WEB
    API --> DB[(PostgreSQL)]
    ML[Proceso externo de ML] -->|Consulta de características y envío de predicciones| API
```

```text
Safe/
├── App/                         Aplicación Android
│   ├── app/src/main/            Pantallas, controlador, modelos y recursos
│   ├── app/src/test/            Pruebas unitarias
│   ├── app/src/androidTest/     Pruebas en dispositivo o emulador
│   └── gradle/                 Versiones, wrapper y configuración de JVM
├── Backend/                     API y persistencia
│   ├── app/                    Rutas, contratos, modelos y servicios
│   ├── questionnaires/         Textos y opciones del catálogo remoto
│   ├── db/init/                Inicialización de roles de PostgreSQL
│   ├── migrations/             Migraciones Alembic y SQL
│   ├── scripts/                Administrador, simulación y módulo de ML
│   ├── tests/                  Pruebas de API, puntuación y ML
│   ├── docs/                   Contrato de API y guía de ML
│   └── docker-compose.yml      API y PostgreSQL
├── Frontend/                    Tablero web
│   ├── src/                    Login, vistas, dominio y cliente HTTP
│   ├── tests/                  Pruebas del dominio y cliente HTTP
│   └── vite.config.js          Desarrollo y proxy hacia la API
└── README.md                    Guía general del proyecto
```

| Componente | Tecnologías y organización |
|---|---|
| Android | Kotlin, Jetpack Compose, Material 3, Android Keystore y Health Connect. Organización en `model`, `controller` y `ui`. |
| Backend | Python 3.12, FastAPI, Pydantic, SQLAlchemy asíncrono, Alembic y PostgreSQL 16. |
| Web | React 19, Vite 8, Tailwind CSS y Lucide. |
| ML | Scripts externos con pandas, scikit-learn y joblib; XGBoost opcional. |
| Entorno local | Docker Compose para API y base de datos; Android Studio y Node.js para los clientes. |

## Funciones actuales

### App Android

- Login local con `admin/admin` para acceder a la interfaz.
- Enlace **Vincular** para registrar el teléfono con un código emitido por el backend.
- Consulta de los cuatro cuestionarios desde el catálogo remoto, con opciones, subpreguntas y saltos condicionales.
- Envío de respuestas con token de dispositivo e identificadores idempotentes para reintentos.
- Check-in EMA diario, consulta de avisos del servidor y registro de entrega de avisos.
- Copia cifrada de formularios descargados y envíos pendientes en el teléfono.
- Sincronización manual de pasos, frecuencia cardiaca y sueño mediante Health Connect, con permisos de lectura.
- Perfil local con foto, nombre, correo opcional y descripción.
- Recursos de apoyo y pantalla de chat de demostración.
- Diseño con base blanca y azul, superficies translúcidas de glassmorphism y relieve suave de neumorphism.

Sin vincular el teléfono se puede recorrer la interfaz y consultar el catálogo público si hay conexión. El envío de cuestionarios, EMA y datos de salud requiere vinculación. Para consultar formularios sin conexión deben haberse descargado antes.

### Tablero web

- Login contra el backend, sesión JWT en memoria y permisos según rol.
- Listado y búsqueda de participantes, filtros por alertas y exportación CSV de la vista.
- Ficha con resumen, resultados de los cuatro instrumentos y dispositivos vinculados.
- Consulta de las preguntas reales del catálogo; la respuesta de cuestionarios se realiza desde Android.
- Registro de participantes, emisión de códigos y revocación de dispositivos para los roles autorizados.
- Revisión, derivación y cierre de alertas con nota de seguimiento.
- Consulta explícita del contacto del participante, auditada por el servidor.
- Creación de cuentas de personal y retiro de participantes desde administración.
- Métricas de latencia, eventos en vivo y actualización periódica cada minuto.

La web consulta hasta 500 alertas recientes. «Sin alerta en esta vista» no equivale a ausencia de riesgo ni a una evaluación clínica. Un fallo de conexión se muestra como error, sin sustituirlo por datos de demostración.

### Backend

Recibe cuestionarios, EMA y lotes de sensores; valida y puntúa las respuestas; guarda datos seudonimizados y genera alertas para revisión humana. Gestiona cuentas de personal, dispositivos, consentimiento, avisos EMA, auditoría, métricas, vistas analíticas y recepción de predicciones.

El catálogo general del backend contiene instrumentos adicionales. Su existencia en la API no implica que estén habilitados en los clientes ni que todos tengan preguntas remotas disponibles.

## Requisitos

| Para ejecutar | Requisitos |
|---|---|
| API y base de datos | Docker Desktop en ejecución, con contenedores Linux y Docker Compose v2. |
| Web | Node.js compatible con el Vite fijado en el lockfile: `^20.19.0` o `>=22.12.0`; npm. |
| Android | Android Studio compatible con AGP 9.2.1, SDK de compilación 37 y emulador o dispositivo desde API 26. El proyecto usa Gradle 9.4.1 y JVM de daemon 21; genera bytecode Java 11 y tiene `targetSdk` 36. |
| Health Connect | Dispositivo compatible, servicio disponible, permisos concedidos y registros de salud existentes. |
| Pruebas de backend o ML fuera de Docker | Python 3.12 y un entorno virtual; PostgreSQL aislado para las pruebas de integración. |

## Ejecutar el proyecto

Los ejemplos usan PowerShell y parten de la raíz del repositorio. El orden habitual es backend, web y app Android.

### 1. Backend y PostgreSQL

```powershell
Set-Location Backend
```

En una instalación nueva, copia la plantilla; si ya existe `.env`, conserva su configuración:

```powershell
Copy-Item .env.example .env
```

Edita `.env` y completa `GSTI_OWNER_PASSWORD`, `GSTI_API_PASSWORD`, `GSTI_ANALYST_PASSWORD`, `JWT_SECRET`, `IDENTITY_ENCRYPTION_KEY` e `IDENTITY_HMAC_KEY`. La plantilla contiene valores de ejemplo y secretos vacíos. No la uses sin completar.

Puedes generar dos secretos y una clave Fernet válida con un contenedor temporal:

```powershell
docker run --rm python:3.12-slim python -c "import secrets, base64, os; print('JWT_SECRET=' + secrets.token_urlsafe(48)); print('IDENTITY_HMAC_KEY=' + secrets.token_urlsafe(48)); print('IDENTITY_ENCRYPTION_KEY=' + base64.urlsafe_b64encode(os.urandom(32)).decode())"
```

En una base existente, conserva las claves y contraseñas originales: la clave Fernet permite recuperar los contactos cifrados y cambiar variables de entorno no cambia las contraseñas de roles ya creados en PostgreSQL.

**Entorno local actual:** `docker-compose.override.yml` establece el proyecto `safe`, publica PostgreSQL en `127.0.0.1:5433` y reutiliza el volumen externo `gsti_tif_app_pgdata`. Ese archivo se ignora en Git y puede no existir en un clon nuevo.

- Para reutilizar los datos actuales, ejecuta Compose normalmente y conserva el volumen externo.
- Para una instalación nueva con volumen propio, usa explícitamente `-f docker-compose.yml` en todos los comandos Compose de esa instalación. Así omites el override local; PostgreSQL se publica en `127.0.0.1:5432`.

Arranque del entorno local existente:

```powershell
docker compose up -d --build
docker compose ps
Invoke-RestMethod http://localhost:8000/health
```

Alternativa para instalación nueva, usando solo la configuración base:

```powershell
docker compose -f docker-compose.yml up -d --build
```

Al iniciar, el contenedor de la API ejecuta `alembic upgrade head` y después Uvicorn. El resultado esperado de `/health` incluye `status: "ok"`.

| Servicio | Dirección |
|---|---|
| API | http://localhost:8000 |
| Swagger | http://localhost:8000/docs |
| Esquema OpenAPI | http://localhost:8000/openapi.json |
| Salud de API y conexión a BD | http://localhost:8000/health |
| PostgreSQL local con override | `127.0.0.1:5433`, base `gsti` |
| PostgreSQL entre contenedores | `db:5432` |

Para ver logs, detener e iniciar sin eliminar los datos:

```powershell
docker compose logs --tail 100 api
docker compose stop
docker compose up -d
```

### 2. Cuenta inicial de personal

La base local preparada ya tiene el acceso web `admin/admin`. En una base nueva no se crea automáticamente esa cuenta. Crea un administrador con:

```powershell
docker compose exec api python -m scripts.create_admin --email admin@example.org --name "Administrador Safe"
```

El script solicita una contraseña de al menos 12 caracteres. La web acepta `admin` como alias de `admin@example.org`; usa la contraseña que acabas de establecer. Si creas otra dirección, inicia sesión con ese correo completo. El script no reemplaza cuentas existentes.

### 3. Tablero web

En otra terminal, desde la raíz:

```powershell
Set-Location Frontend
npm ci
npm run dev
```

Abre http://localhost:5173. El proxy de Vite envía `/api` y `/ws` al backend en el puerto 8000. No hace falta configurar `.env` para este entorno. Si el puerto web está ocupado, consulta la URL que anuncia Vite.

### 4. App Android

1. Abre la carpeta `App` directamente en Android Studio y sincroniza Gradle.
2. Configura el SDK local en Android Studio; `App/local.properties` contiene la ruta propia de cada equipo y no debe versionarse.
3. Ejecuta la variante `debug` en un emulador o dispositivo compatible.
4. Entra con usuario `admin` y contraseña `admin` para explorar la interfaz.
5. Para habilitar envíos, toca **Vincular** e introduce un código de enrolamiento del backend y la URL base del servidor.

En Android Emulator, la dirección es **`http://10.0.2.2:8000`**, sin añadir `/api/v1`; el cliente agrega ese prefijo. `localhost` dentro del emulador apunta al propio emulador. En teléfono físico y en producción se requiere una URL **HTTPS** accesible desde el dispositivo. El cliente solo permite HTTP en `debug` para la dirección exacta del emulador indicada arriba.

## Acceso y vinculación

| Acceso | Credenciales o mecanismo | Alcance |
|---|---|---|
| Login Android | `admin` / `admin`, comprobados localmente | Navegar por la app; no otorga un rol de personal ni un token del servidor. |
| Login web local preparado | `admin` / `admin`; alias de `admin@example.org` | Autenticación real contra la cuenta de administración existente en PostgreSQL. Requiere backend disponible. |
| Otras cuentas web | Correo y contraseña creados para el personal | Operaciones permitidas por su rol. |
| Teléfono vinculado | Código de enrolamiento y posterior token de dispositivo | Enviar datos del participante y consultar sus avisos. |
| Proceso de ML | Cuenta de rol `service` y JWT | Consultar características y enviar predicciones; no accede al tablero web. |

`admin/admin` es el acceso de desarrollo de este entorno. Para un despliegue con datos reales deben sustituirse las credenciales de demostración y el acceso local de Android por el mecanismo definido para el estudio.

### Vincular un participante de principio a fin

1. Inicia sesión en la web como administración o investigación.
2. Selecciona **Nuevo participante**, registra su versión de consentimiento y completa los datos que correspondan. Para 16–17 años se requieren asentimiento y autorización del representante legal.
3. Conserva el código que devuelve el servidor; se muestra en esa respuesta una sola vez y tiene una vigencia de siete días.
4. En Android, abre **Vincular** e ingresa ese código y la URL base.
5. La app llama a `POST /api/v1/devices/register` y guarda cifrado el token recibido.
6. Las respuestas que envíe el teléfono pueden consultarse posteriormente en la ficha web del participante.

La contraseña web no reemplaza al código de enrolamiento. El perfil local de Android tampoco crea ni actualiza la identidad del participante en el backend.

### Roles de personal

| Rol | Permisos principales |
|---|---|
| `admin` | Gestión de participantes, dispositivos, identidad y alertas; creación de cuentas; retiro de participantes; métricas y analítica. |
| `clinician` | Consulta de participantes, respuestas y dispositivos; contacto autorizado, gestión de alertas y métricas. |
| `researcher` | Registro y consulta de participantes, códigos, revocación de dispositivos, resultados, métricas y características analíticas. Sin acceso a identidad ni gestión de alertas clínicas. |
| `service` | Integración de características y predicciones del módulo de ML. |

El backend valida los permisos de cada operación, además de los controles visibles en la interfaz.

## Cuestionarios y API

### Instrumentos habilitados

| Instrumento | Código API | Endpoint de preguntas | Android | Web |
|---|---|---|---|---|
| PHQ-9 | `PHQ9` | `GET /api/v1/catalog/questionnaires/PHQ9` | Consultar y responder | Consultar preguntas y resultados |
| GAD-7 | `GAD7` | `GET /api/v1/catalog/questionnaires/GAD7` | Consultar y responder | Consultar preguntas y resultados |
| ULS-6 | `ULS6` | `GET /api/v1/catalog/questionnaires/ULS6` | Consultar y responder | Consultar preguntas y resultados |
| C-SSRS | `CSSRS_SCREEN` | `GET /api/v1/catalog/questionnaires/CSSRS_SCREEN` | Consultar y responder | Consultar preguntas y resultados |

Las preguntas y opciones de estos cuatro instrumentos provienen del backend. Android filtra el catálogo a esos códigos y conserva una copia cifrada para consulta posterior. PHQ-9 y GAD-7 incluyen impacto funcional opcional; C-SSRS contiene condiciones y un subítem. Los saltos determinan qué respuestas se envían desde el teléfono.

UCLA-3, MSPSS, SUS, NSSI, BSS, BHS, ISI y ADVERSITY quedan fuera de los cuestionarios visibles de los clientes actuales. Esto no elimina sus capacidades o registros existentes del servidor. EMA diaria permanece separada de esta selección; la API dispone de avisos y recepción de respuestas EMA.

### Rutas principales

Las rutas REST siguientes incluyen el prefijo `/api/v1`. Swagger contiene los cuerpos, respuestas y restricciones completas.

| Área | Rutas |
|---|---|
| Personal | `POST /api/v1/auth/login`, `GET /api/v1/auth/me`, `POST /api/v1/auth/staff` |
| Participantes | `GET/POST /api/v1/participants`, `GET /api/v1/participants/{code}` |
| Ficha | `GET /api/v1/participants/{code}/responses`, `GET /api/v1/participants/{code}/devices` |
| Vinculación | `POST /api/v1/participants/{code}/enrollment-code`, `POST /api/v1/devices/register` |
| Dispositivos | `GET /api/v1/devices/me`, `POST /api/v1/devices/{id}/revoke` |
| Identidad y retiro | `GET /api/v1/participants/{code}/identity`, `POST /api/v1/participants/{code}/withdraw` |
| Catálogos públicos | `GET /api/v1/catalog/instruments`, `/api/v1/catalog/metrics`, `/api/v1/catalog/questionnaires` y detalle por código |
| Respuestas | `POST /api/v1/questionnaires/responses` |
| Sensores | `POST /api/v1/ingest/sensors` |
| EMA | `GET /api/v1/ema/prompts`, `POST /api/v1/ema/prompts/{id}/delivered`, `POST /api/v1/participants/{code}/ema-schedule` |
| Alertas | `GET /api/v1/alerts`, `POST /api/v1/alerts/{id}/acknowledge`, `POST /api/v1/alerts/{id}/resolve` |
| Métricas | `GET /api/v1/metrics/latency`, `GET /api/v1/metrics/adherence` |
| ML | `GET /api/v1/features/person-week`, `POST /api/v1/predictions` |

Los WebSockets están fuera del prefijo REST: `/ws/device` para ingesta y `/ws/dashboard` para eventos del tablero. El dashboard autentica enviando el JWT en el primer mensaje, sin incorporarlo a la URL. Las sesiones de personal y los tokens de dispositivo tienen usos distintos.

## Configuración

| Archivo o variable | Función |
|---|---|
| `Backend/.env` | Contraseñas de PostgreSQL, JWT, cifrado, CORS y parámetros del servidor. Plantilla: `Backend/.env.example`. |
| `Backend/docker-compose.override.yml` | Ajustes propios del equipo: nombre de proyecto, puerto 5433 y volumen externo anterior. No se versiona. |
| `Frontend/.env` | Configuración opcional de la API web; plantilla en `.env.example`. |
| `VITE_API_BASE_URL` | Base REST web. Valor por defecto: `/api/v1`; para otro servidor debe incluir el prefijo, por ejemplo `https://api.example.org/api/v1`. |
| `VITE_WS_URL` | URL completa opcional del canal, por ejemplo `wss://api.example.org/ws/dashboard`. |
| `CORS_ORIGINS` | Lista JSON de orígenes permitidos por la API, por ejemplo `["http://localhost:5173"]`. |
| `App/local.properties` | Ubicación local del SDK de Android, gestionada por Android Studio. |
| Dirección en **Vincular** | Base del servidor Android, sin `/api/v1`. |

Las variables `VITE_*` son públicas y se incorporan a la compilación; no deben contener claves privadas. Para servir la web compilada, configura un proxy HTTPS para `/api` y `/ws` o define las URLs antes de compilar y habilita el origen correspondiente en CORS. El proxy de desarrollo de Vite no forma parte de `dist`.

## Compilación y pruebas

### Web

Desde `Frontend`:

```powershell
npm test
npm run build
npm run preview
```

El build genera `Frontend/dist`. `preview` permite inspeccionar los archivos compilados; configura el acceso al backend según la sección anterior. Las pruebas cubren el cliente HTTP, autenticación y errores, selección de los cuatro instrumentos, prioridad por alertas, permisos y exportación CSV.

### Android

Desde `App`:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

La última tarea requiere un emulador o dispositivo conectado. El APK debug se genera en `App/app/build/outputs/apk/debug/app-debug.apk`. Las pruebas incluyen disponibilidad EMA, catálogo de cuestionarios, condiciones de formularios y acceso/perfil local.

### Backend

Desde `Backend`, crea un entorno Python e instala las dependencias de pruebas:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
```

Las pruebas de integración necesitan **una instancia PostgreSQL exclusiva para pruebas**. Las fixtures eliminan y recrean `gsti_test` y cambian la contraseña del rol `gsti_api` a nivel de servidor. No apuntes `TEST_ADMIN_DSN` al PostgreSQL que utiliza la aplicación.

Ejemplo de instancia aislada, en otro puerto:

```powershell
docker run --name safe-postgres-tests -e POSTGRES_PASSWORD=solo-pruebas-locales -p 127.0.0.1:55432:5432 -d postgres:16-alpine
docker exec safe-postgres-tests pg_isready -U postgres
```

Cuando esté lista:

```powershell
$env:TEST_ADMIN_DSN = "postgresql://postgres:solo-pruebas-locales@localhost:55432/postgres"
.\.venv\Scripts\python.exe -m pytest
```

La suite incluye puntuación, permisos, idempotencia, cifrado, cuestionarios, EMA, WebSockets, retiro, analítica e integración de ML. Estos comandos documentan cómo ejecutarla; no implican que se haya ejecutado toda la suite en cada actualización de este README.

## Modelo predictivo

El código de entrenamiento e inferencia se encuentra en `Backend/scripts`; no se inicia automáticamente con Docker Compose.

| Script | Uso |
|---|---|
| `generar_datos_sinteticos.py` | Generar datos persona-semana de prueba. |
| `entrenamiento_riesgo.py` | Entrenar el pipeline con Random Forest o XGBoost opcional. |
| `ml_service_client.py` | Consultar características, ejecutar inferencia y enviar predicciones con una cuenta `service`. |
| `simulate_device.py` | Simular un teléfono y su envío de datos a la API. |

Las dependencias están en `Backend/requirements-ml.txt`; los modelos y datasets generados no se versionan. La ejecución de inferencia y su programación son tareas externas a los dos servicios de Compose.

La guía de [integración de ML](Backend/docs/integracion-ml.md) explica entrenamiento, cuenta de servicio, variables de entorno, inferencia y límites metodológicos. Las métricas obtenidas con datos sintéticos sirven para comprobar el flujo técnico y no deben presentarse como resultados clínicos del estudio.

## Privacidad y almacenamiento

- **PostgreSQL:** `core` contiene registros de investigación seudonimizados; `identity`, contactos cifrados; `analytics`, vistas agregadas; y `admin`, personal y auditoría.
- **Identidad:** los contactos se cifran con Fernet y la clave se mantiene fuera de la base de datos. El acceso a contacto se restringe por rol y queda auditado.
- **Android:** token, perfil y pendientes se almacenan cifrados; la foto se copia al almacenamiento privado. La app desactiva el respaldo automático de sus datos.
- **Web:** el JWT permanece en memoria; cerrar sesión, recargar o vencer la sesión requiere iniciar sesión otra vez.
- **Health Connect:** la lectura es manual y solicita pasos, sueño y frecuencia cardiaca. Esta implementación no lee coordenadas ni nombres de aplicaciones.
- **Retiro:** revoca dispositivos y cancela avisos futuros. La acción de la web elimina contacto y conserva datos de investigación; la API también ofrece la opción `purge_data` según el procedimiento autorizado.
- **Secretos:** no subas `.env`, claves de cifrado, tokens ni configuraciones privadas al repositorio. Respalda de forma segura tanto la base de datos como sus claves.

## Limitaciones y pendientes

| Área | Estado actual o trabajo pendiente |
|---|---|
| Chatbot | Pantalla de ejemplo en Android; no hay endpoint de conversación ni respuestas generadas por un servicio. |
| Cuestionarios adicionales | Fuera de la selección de los clientes. Habilitarlos requiere revisar textos, derechos, periodos, reglas y protocolo correspondiente. |
| Asignaciones | No hay un endpoint general que asigne los cuatro cuestionarios a cada participante. Los envíos actuales son `adhoc`; EMA tiene programación propia. |
| EMA | La app consulta avisos y registra entrega; faltan notificaciones locales programadas y gestión del calendario en la web. |
| Captura de salud | Sincronización manual del último día y última noche completa; pendientes captura en segundo plano y pruebas con dispositivos y datos reales. |
| Otras métricas | El backend acepta más métricas de las que recopila Android. Uso de apps y permanencia en casa no se capturan en la app actual. |
| Perfil | Datos y foto permanecen en el teléfono; no se sincronizan como perfil remoto. |
| Resultados web | El historial de la API expone puntajes y subescalas, sin respuestas individuales por pregunta. |
| Administración web | Creación de cuentas disponible; falta un endpoint para listar cuentas de personal. |
| Predicciones web | La API recibe predicciones, pero no proporciona un endpoint GET de historial para mostrarlas. |
| Alertas web | Hasta 500 alertas recientes; falta paginación para explorar el historial completo. |
| Eventos | La actividad web conserva eventos de la sesión actual, no un historial de auditoría. El bus del backend es en memoria y requiere un solo worker. |
| Despliegue | HTTPS, credenciales finales, límites de intentos de login/vinculación, respaldos y validación del protocolo deben prepararse antes del piloto. |

## Problemas frecuentes

| Problema | Comprobación o solución |
|---|---|
| La API no responde | Verifica Docker Desktop, `docker compose ps`, los logs de `api` y `/health`. Ejecuta los comandos desde `Backend`. |
| Falta `gsti_tif_app_pgdata` | El override local depende de ese volumen externo. Para una instalación nueva usa `docker compose -f docker-compose.yml ...` con volumen propio. |
| PostgreSQL no conecta desde el equipo | Con el override usa `localhost:5433`; la configuración base publica 5432. Entre contenedores siempre es `db:5432`. |
| La web rechaza `admin/admin` | Ese acceso depende de la cuenta preparada en la base local. En una base nueva crea el administrador y usa su contraseña; no existe una semilla automática. |
| Android entra pero no envía | El login local permite navegar; el envío requiere código de enrolamiento y token de dispositivo válidos. |
| El teléfono no llega al servidor | En el emulador usa `http://10.0.2.2:8000`. En un teléfono físico usa HTTPS accesible desde él. No añadas `/api/v1` en **Vincular**. |
| El código de vinculación venció | Administración o investigación puede emitir otro desde la ficha web del participante. |
| Faltan cuestionarios sin conexión | Descarga primero los cuatro formularios desde el backend. La app no inventa preguntas como alternativa al catálogo. |
| Health Connect no envía datos | Revisa disponibilidad, permisos y existencia de registros. Una fuente sin registros no produce valores sintéticos. |
| CORS al usar otro servidor web | Configura `CORS_ORIGINS` con el origen real y revisa `VITE_API_BASE_URL` y `VITE_WS_URL`. |
| La web compilada no conecta | `dist` no incluye el proxy de Vite; configura el proxy del servidor o las URLs públicas antes del build. |
| Error de SDK o JVM en Android | Abre `App`, revisa la ruta del SDK, instala el SDK 37 y usa la JVM 21 configurada para Gradle. |

## Documentación por módulo

- [Aplicación Android](App/README.md): organización, integración y notas históricas de la app. Para la selección actual de cuestionarios y el login, consulta sus apartados finales.
- [Backend](Backend/README.md): arquitectura del servidor, persistencia, alertas y operación.
- [Frontend web](Frontend/README.md): funcionamiento del tablero y configuración del cliente.
- [Contrato de API](Backend/docs/contrato-api.md): contratos de integración.
- [Integración de ML](Backend/docs/integracion-ml.md): entrenamiento e inferencia.
- [Esquema inicial SQL](Backend/migrations/sql/0001_schema.sql): estructura y permisos de la base de datos.
