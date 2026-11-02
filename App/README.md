# Safe (Android)

Aplicación móvil en Kotlin y Jetpack Compose para el participante del estudio GSTI. Se integra con el backend de `gsti_back_datos` mediante `/api/v1`.

## Estructura MVC

- `model/`: contratos y cliente de la API, reglas de disponibilidad EMA y sesión cifrada.
- `controller/GstiController.kt`: estado de pantalla, navegación y coordinación de operaciones.
- `ui/GstiView.kt`: pantallas Compose y controles visuales; recibe el estado y envía acciones al controlador.
- `MainActivity.kt`: crea el controlador y monta la vista.

## Implementado

- Registro del teléfono con código de enrolamiento (`POST /devices/register`).
- Token de dispositivo cifrado mediante Android Keystore; los datos de la aplicación quedan fuera del respaldo automático.
- Comprobación de sesión y consulta de avisos EMA (`GET /devices/me`, `GET /ema/prompts`).
- Check-in EMA_DAILY con los cuatro ítems del catálogo (0–10), vinculado a un aviso o enviado en contexto `ema` sin aviso.
- Envío idempotente de respuestas: el cuerpo queda cifrado localmente hasta recibir confirmación, y puede reintentarse con el mismo `client_response_id`.
- Pantalla de apoyo cuando el backend devuelve `support_resources: true`, accesible también desde el inicio.
- Pantalla «Qué incluye el estudio» alimentada por los catálogos reales (`GET /catalog/instruments` y `GET /catalog/metrics`). Distingue capacidades del backend de funciones disponibles en el teléfono.
- PHQ-9 y GAD-7 en español con respuestas 0–3; UCLA-3 con tres respuestas 1–3; MSPSS con doce respuestas 1–7; y SUS en español con diez respuestas 1–5. Los cinco se envían como `adhoc` con idempotencia. No se muestran resultados o diagnósticos. Si el ítem 9 del PHQ-9 es positivo, se muestran recursos de ayuda de inmediato, incluso sin red. SUS evalúa la usabilidad de la app, no la salud.
- Cada instrumento que el backend enumera pero que aún no está listo muestra en el catálogo el motivo concreto: derechos de reproducción, texto faltante o protocolo clínico pendiente. No aparece un botón que permita enviar una escala incompleta.
- Sincronización **manual y voluntaria** con Health Connect: pasos y pulso por bloque de 30 minutos y duración de la última noche completa. Solicita solo permisos de lectura de estos tres tipos; no lee rutas, coordenadas ni nombres de aplicaciones. Solo envía valores existentes y guarda cifrado el lote pendiente para reintentarlo con el mismo `batch_id`.
- Interfaz Compose de superficies translúcidas, bordes luminosos y botones con relieve suave.

## Ejecutar

1. Levanta el backend y crea un participante desde su API de personal.
2. Abre este proyecto en Android Studio y ejecuta la variante `debug` en un emulador.
3. En la pantalla inicial, usa el código de enrolamiento. La URL precargada `http://10.0.2.2:8000` apunta al backend del equipo anfitrión desde el emulador.
4. En un teléfono físico o compilación de producción, usa una URL HTTPS accesible desde ese dispositivo.
5. Para sincronizar datos de salud, abre «Datos de salud», autoriza Health Connect y toca «Autorizar y sincronizar». No hay lectura en segundo plano. Esta integración requiere Android 8 (API 26) como mínimo; Health Connect debe estar disponible en el dispositivo.

La compilación `debug` y las pruebas unitarias terminaron correctamente. La integración local ya se probó con una instancia de la API: se vinculó un dispositivo y se envió una respuesta EMA. Los catálogos del servidor responden con 12 instrumentos y 8 métricas. La navegación de PHQ-9 y Health Connect se comprobó en el emulador; no se otorgaron permisos de salud ni se enviaron datos reales durante esa prueba. UCLA-3, MSPSS y SUS cuentan con pruebas de número de ítems y rango, pero aún no con una prueba de envío de extremo a extremo.

## Fuentes de los cuestionarios

- PHQ-9 español (US): https://www.phqscreeners.com/images/sites/g/files/g10060481/f/201412/PHQ9_Spanish%20for%20the%20USA.pdf
- GAD-7 español (US): https://www.phqscreeners.com/images/sites/g/files/g10060481/f/201412/GAD7_Spanish%20for%20the%20USA.pdf
- UCLA-3: estructura de tres ítems y respuestas de tres niveles según la guía del Health and Retirement Study: https://hrsonline.isr.umich.edu/sitedocs/userg/HRS2006LBQscale.pdf ; redacción española de las preguntas contrastada con la encuesta MEPS/AHRQ: https://meps.ahrq.gov/survey_comp/hc_survey/paper_quest/2024/2024_PSAQ_SP.shtml . Antes del piloto, el equipo de investigación debe aprobar esta combinación de redacción y opciones de respuesta.
- MSPSS: Zimet GD, Dahlem NW, Zimet SG y Farley GK. *The Multidimensional Scale of Perceived Social Support*. Journal of Personality Assessment. 1988;52:30–41. El autor declara libre su uso con atribución: https://gzimet.wixsite.com/mspss . Se contrastó una redacción española de 12 ítems con un formulario del repositorio de la Universidad Privada San Juan Bautista que usa la escala 1–7: https://repositorio.upsjb.edu.pe/backend/api/core/bitstreams/1dd51b23-a2a2-4c0b-9e0d-3704f2c431e2/content . La redacción incorporada es una versión de trabajo; no se afirma que sea la versión validada para la población GSTI. Requiere aprobación metodológica antes de un piloto.
- SUS: Sevilla-González MDR, Moreno Loaeza L, Lázaro-Carrera LS, Bourguet Ramírez B, Vázquez Rodríguez A, Peralta-Pedrero ML y Almeda-Valdés P. *Spanish Version of the System Usability Scale for the Assessment of Electronic Tools: Development and Validation*. JMIR Human Factors. 2020;7(4):e21161. Cuestionario en el apéndice 2 de https://humanfactors.jmir.org/2020/4/e21161/ . © Autores, disponible bajo Creative Commons Attribution 4.0: https://creativecommons.org/licenses/by/4.0/ . La app muestra la atribución junto al formulario.

Los formularios PHQ-9 y GAD-7 citados indican que no se requiere permiso para reproducirlos. El equipo clínico debe aprobar que las variantes de español, los contextos de aplicación y las frecuencias sean adecuados para el protocolo local antes de un piloto con personas.

## Pendiente para el piloto

- Captura automática en segundo plano y política de sincronización a largo plazo. La versión actual solo sincroniza manualmente el último día y la última noche completa.
- Indicadores `app_use` y `at_home`. El segundo requiere un protocolo de consentimiento y una inferencia local de domicilio; no se recopilan coordenadas sin definirlo.
- Notificaciones locales programadas a partir de los avisos EMA.
- Seis de los doce instrumentos del catálogo aún no tienen formulario: `CSSRS_SCREEN`, `NSSI`, `BSS`, `BHS`, `ISI` y `ADVERSITY`. BHS/BSS son pruebas de Pearson (https://www.pearsonassessments.com/professional-assessments/products/products-a-z.html) y requieren derechos de reproducción; ISI se distribuye para uso con permiso de su desarrollador (https://site.thoracic.org/assemblies/srn/sleep-related-questionnaires/isi). El backend ya se alineó con los seis valores y saltos del screener oficial español «Desde la última visita» (https://cssrs.columbia.edu/wp-content/uploads/C-SSRS-Screener-since-last-visit-US-Spanish-2026.pdf), pero el formulario de la app sigue pendiente de elegir una versión española de autorreporte y aprobar la respuesta humana ante alertas. Para `NSSI` y `ADVERSITY` se documentaron fuentes públicas en el backend, pero sus periodos y escalas no coinciden directamente con el catálogo actual y necesitan decisión metodológica. Un PDF disponible públicamente no concede derechos de reproducción. No se deben inventar ítems ni mostrar puntajes al estudiante.
- Un endpoint de asignaciones de cuestionarios por participante. El catálogo solo enumera instrumentos; actualmente el backend únicamente programa avisos EMA. Los cinco formularios adicionales quedan como `adhoc`, para responder cuando lo indique el equipo.
- Pruebas integradas con backend desplegado, red HTTPS y dispositivos reales.

## Integración con GSTI_TIF_App · 2026-11-01

Backend de referencia: `../Backend` (`C:/Users/User/Documents/GitHub/Safe/Backend`), API local en `http://localhost:8000` (en Android Emulator, `http://10.0.2.2:8000`).

Safe consume `/catalog/questionnaires` y su detalle para PHQ-9, GAD-7, ULS-6 y C-SSRS. Respeta las opciones explícitas, el `backend_item`, las dependencias y los subítems; los ítems descartados por salto no se envían. PHQ-9 y GAD-7 incluyen impacto funcional opcional y condicional. Se conserva una copia cifrada del contenido para su consulta sin conexión. UCLA-3, MSPSS y SUS siguen disponibles localmente y son aceptados por la API. Esta sección actualiza las descripciones previas del README sobre la disponibilidad de C-SSRS y ULS-6.

Se verificaron registro real, consultas de catálogos y sesión, respuestas Android para C-SSRS/PHQ-9/ULS-6/EMA, estado delivered, persistencia/reintento sin conexión, y recepción idempotente de formularios y sensores sintéticos. `assembleDebug`, las 3 pruebas unitarias y las 7 pruebas instrumentadas terminaron correctamente. La colección desde un reloj/dispositivo con datos reales sigue pendiente.

El chat continúa como ejemplo visual: esta API no proporciona un endpoint de conversación. Siguen pendientes las notificaciones EMA, captura en segundo plano, cinco métricas adicionales y los cinco formularios sin texto remoto (NSSI, BSS, BHS, ISI, ADVERSITY). Los endpoints de personal, alertas y analítica corresponden a un tablero separado.

## Ubicación del proyecto Android · 2026-11-02

Abre esta carpeta directamente en Android Studio: `C:/Users/User/Documents/GitHub/Safe/App`. `settings.gradle.kts` define el proyecto `Safe` y solo incluye el módulo `:app`. La carpeta anidada `gsti_back_datos` es una copia anterior y no participa en la compilación Android; el backend activo del repositorio está en `../Backend`.

El SDK se configura en `local.properties` (archivo local ignorado por Git). Mover el proyecto no cambia la URL del backend: en el emulador se utiliza `http://10.0.2.2:8000`.

## Cuestionarios habilitados actualmente

La app muestra únicamente PHQ-9, GAD-7, ULS-6 y C-SSRS, en ese orden, consumiendo el texto y las opciones de `/api/v1/catalog/questionnaires`. Los demás instrumentos quedan fuera del listado de la app, incluso de su sección de pendientes. Este apartado reemplaza las listas anteriores del README.

No se agregan formularios locales como alternativa. Sin conexión se utiliza la copia cifrada de los cuatro formularios ya descargados. La vista previa también puede consultar este catálogo público sin vincular el teléfono, manteniendo desactivado el envío de respuestas. El check-in EMA diario se conserva como función independiente. El backend no se modifica.

## Acceso local y perfil

La pantalla inicial es el login local: usuario `admin`, contraseña `admin`. Este acceso no es una cuenta de personal del backend. El enlace pequeño «Vincular» de la cabecera abre el formulario de enrolamiento; se puede volver al login sin vincular. Sin token de dispositivo, el usuario local consulta el catálogo público y recorre la interfaz, sin enviar respuestas ni datos de salud. Una vinculación vencida no bloquea el acceso local.

El perfil permite elegir una foto de la galería y editar nombre, correo opcional y una descripción. La foto se copia al almacenamiento privado de la app y los datos de perfil se guardan cifrados en el teléfono. Cerrar sesión vuelve al login conservando la vinculación, los envíos pendientes y el perfil. No se modificó la API ni se agregó sincronización de perfiles con el backend.
