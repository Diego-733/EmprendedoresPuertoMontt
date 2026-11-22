# Entrega 7 · Ferias, seguidores y notificaciones (y revisión de la entrega 6)

Rama `entrega7/ferias-seguidores`, creada desde `main` en `f519e60` (entrega 6 integrada). Sin push ni integración a `main`. Revisión del 19-nov-2026.

Pautas: `checklist-revision-entrega-6.md` y `checklist-revision-entrega-7.md` (35 criterios cada una, identificados aquí como **E6-…** y **E7-…**). Requisitos: `requerimientos-base.md` y `planificacion.md` del repositorio oficial del curso.

## 1. Estado (22-nov-2026)

| Tipo de resultado | Qué incluye | Resultado |
|---|---|---|
| **Probado localmente, con motores reales** | SQL en PostgreSQL 15 y 17 (permisos, funciones, migración con datos de la entrega 6, respaldo, reversión, transacción) · reglas web y Android · compilación | 758/758 SQL · 35/35 web unitarias · 61/61 Android · APK `1.5-entrega7` provisional |
| **Probado en el emulador** | Botón «Seguir» sin sesión → «Continuar con Google» (sustituido) en tienda y asociación; error de Google y reintento | 3/3 pruebas de interfaz (Pixel 7) |
| **Simulado** | Recorridos web con un Supabase sustituto (no prueba RLS) · envío con un fetch sustituto | 43/43 recorridos (Edge) · 10/10 envío |
| **Verificado en el entorno real** | Solo la parte pública de la entrega 6 en la web publicada | Ningún criterio E7: la migración 07 no está aplicada |

**Bloqueos para presentar la entrega 7** (detalle en 3b):
- Aplicar la migración 07: sin ella no funciona ningún criterio E7 en el servidor.
- Firebase (app Android y web) y la cuenta del notificador: sin ellos no hay push (E7-A01, A13, W18 con la página cerrada).
- Google activo en el servidor: sin él, E7-A12 no se puede completar. Hoy `/auth/v1/settings` lo muestra desactivado.

## 2. Qué se agregó, corrigió o cambió

**Base de datos (`supabase/07_ferias_seguidores.sql`, nueva, incremental y reejecutable)**
- Estados de feria validados (`BORRADOR`, `PROXIMA`, `EN_CURSO`, `FINALIZADA`, `CANCELADA`). El público ya no ve borradores.
- `publicar_feria` y `cancelar_feria`. Cancelar avisa a invitadas y confirmadas.
- Participaciones con estados `PENDIENTE` (invitada), `CONFIRMADA`, `RECHAZADA` y `AGREGADA` (por administración). Se registran el origen (directa o vía asociación), las fechas y la asistencia (`ASISTIO`/`NO_ASISTIO`). La asistencia es un dato aparte de la confirmación.
- `invitar_a_feria` (invitar o agregar directamente tiendas y asociaciones, sin duplicar), `responder_invitacion` (solo la dueña y solo su fila; repetir no cambia nada), `registrar_asistencia` (desde el día de la feria hasta 3 días después) y `enviar_aviso_feria` (recordatorio a pendientes o aviso a confirmadas, idempotente por solicitud).
- `checklist_feria` (solo administración), `participantes_feria` (pública: solo confirmadas o agregadas) y `mis_ferias` (de la emprendedora).
- Varias asociaciones por tienda: tabla `tienda_asociaciones`. Las asignaciones existentes se migran sin cambiar las tiendas, y `tiendas.asociacion_id` queda como «principal» para los clientes anteriores.
- Seguidores (`seguir` y `dejar_de_seguir`, sin duplicar), consentimiento (`guardar_consentimiento`; al retirarlo se omite lo pendiente), dispositivos por cuenta (`registrar_dispositivo`/`quitar_dispositivo`; si otra cuenta entra en el mismo celular, el token pasa a ella).
- Bandeja y cola de **avisos**: estados `PENDIENTE` → `ENVIANDO` → `ENVIADO` (FCM aceptó), `SIN_DISPOSITIVO`, `OMITIDO` o `FALLIDO`, con reintentos limitados. Por destino (`entregas_aviso`): aceptado por Firebase, mostrado o abierto en cada navegador o celular, con un solo evento de confirmación (`confirmar_aviso`).
- Los avisos los crean solo triggers y funciones del servidor:
  - invitación o incorporación;
  - confirmación de participación, solo a seguidores con consentimiento y una vez por feria (editar la feria no avisa);
  - contacto del formulario: dentro de horario genera aviso; fuera de horario o con los avisos apagados queda `OMITIDO` y el mensaje igual llega a la bandeja.
- Preferencias privadas de la tienda (`preferencias_tienda`: avisos de contacto y horario) y `tiendas.calificaciones_activas` (pública).
- Calificaciones con `calificar`: el servidor exige que estén activas, no permite calificar la propia tienda ni productos no públicos, y calificar de nuevo actualiza. `resumen_calificaciones` es público y anónimo.
- `bitacora_admin` (quién, qué y cuándo) y `notificadores`: la cuenta del servicio de envío, sin `service_role`.
- Se mantiene el bloqueo de `TRUNCATE`, `REFERENCES` y `TRIGGER` para los clientes. Las tablas nuevas no admiten escritura directa: todo pasa por funciones.

**Web**
- `js/ferias7.js` y `css/ferias7.css`, más integración en `main.js`, `vitrina6.js`, `servicio.js`, `catalogo.js` e `index.html`:
  - calendario de próximas ferias;
  - ficha con participantes y muestra de catálogo (excluye ocultos, no disponibles y vendidos);
  - «Seguir» en tiendas y asociaciones, con la acción pendiente que sobrevive al inicio de sesión (también al volver de Google), se ejecuta una vez y se descarta al cancelar;
  - consentimiento, panel «Mis avisos», notificaciones del navegador (push de Firebase si está configurado; si no, mientras la página está abierta);
  - administración: publicar, cancelar con motivo, invitar o agregar tiendas y asociaciones, checklist con búsqueda y 3 filtros que se actualiza solo, recordatorios, avisos y asistencia;
  - «Mis ferias» de la emprendedora con «Participo» / «No participo».
- `firebase-messaging-sw.js` (avisos con la página cerrada, si hay Firebase).
- Se retiró la inscripción libre a ferias locales de la emprendedora (contradecía RN-03) y la mezcla de la lista antigua de ferias con las del servidor.

**Android** (versión `1.5-entrega7`, versionCode 6)
- `data/Ferias7.kt` (modelos y reglas puras), `data/FeriasStore.kt`, `data/remoto/FeriasRepositorio.kt` y `avisos/AvisosCelular.kt`. Avisos por push FCM si existe `firebase.properties`; como respaldo, revisión periódica con WorkManager (intervalo mínimo de 15 min, sin plazo garantizado).
- «Mis ferias»: invitaciones con un toque, estado «pendiente de conexión» sin señal, calendario y permiso de avisos pedido con explicación. Además, la próxima feria real en el inicio.
- Venta: sugerencia de la feria del día donde participa. Con permiso y una ubicación reciente y precisa, la feria en curso donde está. Nunca elige sola y se puede vender sin permiso ni señal.
- Comprador:
  - seguir tiendas y asociaciones, con la acción pendiente al iniciar sesión;
  - consentimiento y baja en Mi perfil → Avisos;
  - participantes y muestra de catálogo en la ficha de feria;
  - calificaciones de tienda y producto.
- Emprendedora: avisos de mensajes con horario y el interruptor de calificaciones (en «Precios, contacto y punto de retiro»).
- Administrador en la app: la lista de ferias ahora viene del servidor y remite a la web para gestionarlas. Se quitó la creación de ferias locales.

**Servicio de envío** (`servicios/notificador/`, Node sin dependencias)
- Entra con una cuenta registrada en `notificadores`, toma lotes con `avisos_por_enviar`, envía por la API HTTP v1 de FCM, informa con `resultado_envio` y desactiva los tokens inválidos.
- Workflow `.github/workflows/notificador.yml`: manual o cada 5 min. Sin secretos termina sin enviar nada y lo avisa.

**Correcciones de la entrega 6**
- `tests/e2e/entrega6.spec.js` ya no omite el mapa: Leaflet es devDependency y, si falta, la prueba falla. Ahora comprueba el contenedor, el marcador y las teselas dibujadas.
- El formulario de ferias ofrece `CANCELADA`.
- Las fechas de «próximas ferias» se calculan en America/Santiago (web y Android); antes usaban UTC o la zona del celular.
- Varias asociaciones por tienda en los filtros y fichas (web y app).
- Se quitó el voseo de «Mis ferias».

## 3. Avisos: una regla por persona y un registro por destino

**¿Debe recibirlo la persona?** Una sola función, `motivo_no_notificar()`, lo decide **ahora**. Revisa:
- consentimiento;
- que siga a la tienda o asociación;
- horario y activación de los avisos de contacto;
- que la feria siga activa;
- que la invitación no se haya respondido;
- que no tenga más de 3 días de atraso.

La usan el push (`avisos_por_enviar`) y los mecanismos alternativos (`avisos_para_mostrar`: web abierta y revisión periódica del celular).

**¿Ya lo recibió ESE destino?** Se lleva por destino en `entregas_aviso`. Un destino es el token FCM de un celular o navegador, o el identificador de una página web abierta sin push:

| Estado del destino | Lo registra | Significa |
|---|---|---|
| `ACEPTADO` | Notificador | Firebase aceptó el mensaje para ese destino; no prueba que se haya visto |
| `FALLA_TEMPORAL` | Notificador | Se reintenta solo en ese destino |
| `INVALIDO` / `RECHAZADO` | Notificador | Token dado de baja (se desactiva) / contenido rechazado (el dispositivo sigue activo) |
| `MOSTRADO` | Cliente, con `confirmar_aviso` | Ese navegador o celular mostró la notificación |
| `ABIERTO` | Cliente, con `confirmar_aviso` | La persona la tocó en ese destino |

Reglas por destino:
- Que la web la muestre no impide el push al celular, y al revés.
- No se repite el push en un destino que ya la aceptó, mostró o abrió. Tampoco se repite el evento: «abierto» no vuelve a «mostrado».
- Leer en la bandeja (`marcar_leido`) no cuenta como notificación mostrada.
- `avisos.recibido_en` solo resume la primera vez que se mostró en algún destino.

Lo que **no** queda registrado: con la app Android en segundo plano, Android muestra el push por su cuenta y la app no se entera. Solo queda `ABIERTO` si la persona la toca. En la web con la página cerrada pasa lo mismo.

**Errores de FCM:**
- `UNREGISTERED`, `SENDER_ID_MISMATCH` o un `INVALID_ARGUMENT` que señala el campo `token`: token inválido.
- Otro `INVALID_ARGUMENT` (contenido, tamaño o formato): mensaje rechazado.
- 429, 5xx o autenticación: falla temporal.

WorkManager (Android) es un respaldo periódico **solo sin Firebase** y con permiso de notificar. Corre cada 15 min como mínimo y Android puede postergarlo: no es push.

## 3a. Consultas automáticas (inventario)

Sin novedades, cada mecanismo hace aproximadamente:

| Dónde | Cuándo corre | Solicitudes | Cuándo se detiene |
|---|---|---|---|
| Web: revisión de avisos | Con sesión, con el permiso de notificaciones concedido y la pestaña **visible** | 1 cada 60 s (máximo 20 avisos) | Pestaña oculta, cierre de sesión o sin permiso. Un solo temporizador |
| Web: checklist de una feria (admin) | Solo con el checklist abierto y la pestaña visible (para que se vea «Confirmada», E7-W07) | 1 cada 20 s | Al cerrarlo por cualquier vía, al abrir otro o al cerrar sesión. Además, el botón «Actualizar» |
| Android: revisión periódica | Con sesión, **sin Firebase** y con permiso de notificar | 1 por ejecución, mínimo cada 15 min, con red (Android la puede postergar) | Al cerrar sesión, con Firebase configurado o sin permiso |
| Android: sincronización de la entrega 5 | Solo si quedan cambios por subir | Reintentos con espera creciente | Al quedar todo subido (sin consultas en reposo) |
| Notificador `npm run notificar:vigilar` | Solo mientras alguien lo deja corriendo (demostración) | 1 cada 15 s (≈240/h) + 1 inicio de sesión por hora; con errores, espera más (hasta 5 min) | Ctrl+C |
| Notificador por defecto (`--vigilar` sin número) | Igual | 1 cada 30 s | Ctrl+C |
| GitHub «Enviar avisos» | **Desactivado:** solo «Run workflow» manual | 2 por ejecución (+1 por aviso enviado) | El cron queda comentado hasta configurarlo y acordarlo |

El resto se consulta **al abrir** cada pantalla, sin repetición:
- mis ferias, participantes de una feria, seguimientos (máximo 500) y calificaciones;
- la bandeja, paginada de 20 en 20 con «Ver más».

No se hicieron pruebas de carga ni consultas contra la instancia del curso.

## 3b. Configuración externa (sin credenciales en este informe)

**A. Google en Supabase autoalojado** — en esta instancia no se activa desde Studio: lo configura el servicio de autenticación (GoTrue) con variables de entorno **en el servidor**. Así lo trae la configuración oficial de Supabase para autoalojar: `docker/docker-compose.yml` y `.env.example`, donde las líneas de Google vienen comentadas.
- **El grupo puede** (sin acceso al servidor):
  1. Crear en Google Cloud la pantalla de consentimiento y un cliente OAuth de tipo «Aplicación web», con la URI de redirección autorizada `https://<dominio-de-la-instancia>/auth/v1/callback`.
  2. Entregar al administrador el Client ID y el secreto por un canal privado (nunca por el repositorio ni el chat del grupo).
  3. Probar el resultado.
- **Necesita acceso al servidor** (administrador):
  1. En el `.env` del despliegue: `GOOGLE_ENABLED=true`, `GOOGLE_CLIENT_ID`, `GOOGLE_SECRET`. En `ADDITIONAL_REDIRECT_URLS`, agregar `com.company.ultrapp://login-callback` y la URL pública de la web. `SITE_URL` debe ser la web.
  2. En `docker-compose.yml`, servicio `auth`: descomentar `GOTRUE_EXTERNAL_GOOGLE_ENABLED`, `GOTRUE_EXTERNAL_GOOGLE_CLIENT_ID`, `GOTRUE_EXTERNAL_GOOGLE_SECRET` y `GOTRUE_EXTERNAL_GOOGLE_REDIRECT_URI: ${API_EXTERNAL_URL}/callback` (`API_EXTERNAL_URL` termina en `/auth/v1`).
  3. Reiniciar solo el servicio de autenticación (`docker compose up -d auth`).
- **Verificación** (una sola lectura):
  - `GET /auth/v1/settings` muestra `"google": true`;
  - en la app, «Continuar con Google» abre el navegador y **vuelve con sesión**. Abrir el navegador no basta;
  - cancelar en Google deja la app sin sesión y descarta la acción pendiente;
  - en la web, lo mismo con el botón de Google.

**B. Firebase Android y web** — lo hace el grupo (dueño de una cuenta Google):
1. Crea un proyecto Firebase y activa Cloud Messaging.
2. App Android con el paquete `com.company.ultrapp`: copia `appId`, `apiKey`, `projectId` y `senderId` (públicos) a `App para el celular/firebase.properties` (ignorado; modelo en `firebase.properties.ejemplo`) y vuelve a compilar.
3. App web: copia `apiKey`, `projectId`, `messagingSenderId` y `appId`, y genera el par de claves Web Push (la `vapidKey` pública). Guárdalos como **variable** de repositorio `FIREBASE_WEB_CONFIG` (JSON); el workflow de Pages rechaza una cuenta de servicio ahí.
4. Verificación: la app muestra el permiso y registra su dispositivo (fila en `dispositivos` con plataforma ANDROID); la web publicada registra uno WEB al aceptar el permiso.

**C. Cuenta del notificador y permisos mínimos**:
1. El grupo crea en Firebase/Google Cloud una **cuenta de servicio** solo con el rol «Firebase Cloud Messaging API Admin» (`roles/firebasecloudmessaging.admin`) y descarga su clave JSON. Se guarda fuera del repositorio, nunca en la app ni en la web.
2. El grupo registra una cuenta común en la web (por ejemplo `notificador@…`), con contraseña larga y exclusiva.
3. El administrador de Supabase obtiene su uid (Authentication → Users) y ejecuta `insert into public.notificadores (usuario_id, nota) values ('<uid>', 'servicio de envío');`.
   - Esa cuenta no es ADMIN ni tiene tienda: solo puede llamar `avisos_por_enviar` y `resultado_envio`.
   - No lee la bitácora, no administra ferias y no puede leer las tablas de avisos directamente.
4. Verificación: `npm run notificar` muestra «tomados N» sin error. Con otra cuenta, `avisos_por_enviar` responde `SOLO_NOTIFICADOR`.

**D. Secretos y variables**:

| Dónde | Nombre | Tipo | Contenido |
|---|---|---|---|
| GitHub Actions (secreto) | `SUPABASE_URL`, `SUPABASE_ANON_KEY` | ya existen | URL y clave **anon** |
| GitHub Actions (secreto) | `NOTIFICADOR_EMAIL`, `NOTIFICADOR_CLAVE` | nuevos | Cuenta del punto C.2 |
| GitHub Actions (secreto) | `FIREBASE_CUENTA_SERVICIO` | nuevo | JSON completo de la cuenta de servicio (C.1) |
| GitHub Actions (variable) | `FIREBASE_WEB_CONFIG` | nueva | JSON público de la app web con la `vapidKey` (B.3) |
| Computador del grupo (archivo ignorado) | `App para el celular/supabase.properties`, `firebase.properties` | locales | URL/anon y los identificadores públicos de Firebase |
| Computador o servidor del notificador (archivo ignorado) | `servicios/notificador/.env` | local | Las mismas variables que los secretos, con la ruta del JSON |

Nunca se usa la `service_role`: el notificador rechaza una clave que no sea anon.

## 4. Migración 07: procedimiento seguro (preparado, NO aplicado)

Archivos: `07_diagnostico.sql` (solo lectura) · `07_respaldo.sql` (solo lectura: datos + políticas, permisos, triggers, restricciones y funciones de triggers) · `07_ferias_seguidores.sql` · `07_revertir.sql` · `07_restaurar_datos.sql`. Todo el procedimiento está ensayado en `tests/sql` en PostgreSQL 15 y 17.

**Efecto sobre los datos actuales:**
- Crea tablas, columnas, funciones y triggers; no borra datos.
- Copia la asociación de cada tienda a `tienda_asociaciones` sin tocar las tiendas.
- Normaliza estados de feria y asistencias antiguas. Según la lectura del 19-nov, el servidor tiene 2 ferias en `PROXIMA` y 0 participaciones: no cambiaría nada; el diagnóstico lo confirma.
- Cambios de comportamiento:
  - el público ya no lee participaciones ni borradores;
  - la emprendedora responde solo con `responder_invitacion`;
  - las tablas nuevas no admiten escritura directa.
  - permisos: el visitante (`anon`) queda sin privilegios en las tablas privadas nuevas (Supabase se los concede por defecto) y en `contactos` solo puede enviar. El respaldo del 22-nov mostró que además podía leer, editar y borrar ahí; RLS ya le devolvía 0 filas. `07_revertir.sql` no devuelve esos permisos de más.

**Pasos** (los ejecuta el administrador; lo que dice «solo lectura» no cambia nada):
1. **Diagnóstico** (solo lectura): ejecutar `07_diagnostico.sql` y guardar el JSON.
   - Esperado: `07_aplicada: false`, `funciones_07_existentes: []`, `tablas_07_existentes: []`, `*_que_se_normalizarian` en 0 y `zona_america_santiago_disponible: true`.
   - Si algo difiere, detenerse y revisar.
2. **Respaldo** (solo lectura): ejecutar `07_respaldo.sql` y guardar la fila como `respaldo-07-AAAAMMDD.json` **fuera del repositorio** (contiene datos de la instancia).
   - Además, el respaldo general del host (base, Auth y Storage) con el mecanismo del servidor, como en la entrega 5.
3. **Restauración y ensayo local**:
   - restaurar el respaldo general en un PostgreSQL local;
   - ejecutar ahí 07 dos veces, `07_diagnostico.sql`, `07_revertir.sql`, `07_restaurar_datos.sql` (con el JSON del paso 2) y 07 otra vez;
   - comparar que los datos queden iguales.

   Es lo que hace `npm run test:sql` con datos de la entrega 6 (casos «Diagnóstico 07», «Respaldo 07», «Tras revertir 07…», «Restaurar desde el respaldo…»).
4. **Aplicación transaccional** en una ventana tranquila (avisar al grupo). En el editor SQL, en una sola ejecución:
   ```sql
   begin;
   set local lock_timeout = '5s';
   set local statement_timeout = '120s';
   -- pegar aquí el contenido completo de supabase/07_ferias_seguidores.sql
   select nombre from public.migraciones_aplicadas where nombre = '07_ferias_seguidores';
   commit;
   ```
   Si aparece cualquier error, ejecutar `rollback;`: no queda nada a medias (ensayado: un error al final deshace todo).
5. **Verificación posterior**:
   - `07_diagnostico.sql` debe mostrar `07_aplicada: true`, `clientes_con_truncate: 0`, las nuevas políticas y triggers, y en `despues` las pertenencias migradas;
   - la web sin sesión sigue mostrando las ferias, y `rpc/participantes_feria` responde;
   - sin sesión, `/rest/v1/participaciones` devuelve 0 filas;
   - con la cuenta del notificador, `npm run notificar` funciona.
6. **Recuperación si algo falla después del commit**:
   - Si falla la web o la app nuevas, sin datos dañados: ejecutar `07_revertir.sql`. Restituye las políticas, los permisos y las restricciones de la entrega 6, quita los triggers de 07 y retira el permiso de usar sus funciones, sin borrar datos nuevos. Volver a la web y al APK anteriores.
   - Si 07 normalizó datos (el paso 1 dio más de 0): después de revertir, ejecutar `07_restaurar_datos.sql` pegando el JSON del paso 2. Deja ferias, participaciones y asociaciones exactamente como estaban (ensayado).
   - Si hubo daño mayor: restaurar el respaldo general del paso 2.
   - Para reintentar: corregir y ejecutar 07 de nuevo (ensayado sobre la base revertida y restaurada).

Si alguien vuelve a ejecutar `01_esquema.sql`, después hay que ejecutar 05, 06 y 07.

## 5. Matriz de la entrega 6 (35)

Leyenda: **Verificado** = usado en un entorno real · **Implementado sin verificar** = código y pruebas locales o simuladas · **Bloqueado** = depende de una configuración externa.

| ID | Requisito | Prueba realizada | Estado | Pendiente |
|---|---|---|---|---|
| E6-W01 | Directorio de emprendedoras y asociaciones sin sesión | Web publicada, sin sesión (19-nov): 7 tiendas y 3 asociaciones | Verificado | — |
| E6-W02 | Búsqueda y 3 filtros | Publicada: los 4 controles existen y el filtro por asociación reduce de 7 a 2; e2e de los tres | Verificado | Con 07: varias asociaciones (e2e) |
| E6-W03 | Tienda y catálogo público | e2e; publicada con catálogo | Implementado sin verificar | Recorrer en la publicada |
| E6-W04 | Foto y precio por opción | e2e | Implementado sin verificar | Producto real con opciones con foto |
| E6-W05 | Asociación con integrantes y acceso a tiendas | e2e; publicada: 3 asociaciones | Implementado sin verificar | Abrir integrantes en la publicada |
| E6-W06 | Contacto según la configuración | e2e + SQL (RLS de contactos) | Implementado sin verificar | — |
| E6-W07 | «Consultar precio» | e2e | Implementado sin verificar | — |
| E6-W08 | Usable en teléfono | Publicada a 375 px sin desborde; e2e de 320 a 1280 px | Verificado (ancho de teléfono en el navegador) | Probar en un teléfono físico |
| E6-W09 | Editar la ficha de la tienda en la web | e2e | Implementado sin verificar | Cuenta de prueba |
| E6-W10 | Crear o editar un producto en la web | e2e (entrega 5) | Implementado sin verificar | Cuenta de prueba |
| E6-W11 | Venta en la web que descuenta | e2e + SQL de ventas | Implementado sin verificar | Cuenta de prueba |
| E6-W12 | Navegación anónima guardada | SQL real (3 valores persisten) + e2e; servidor con `PERMITIR` | Implementado sin verificar | Cuenta administradora |
| E6-W13 | Crear una feria con todos sus datos | e2e | Implementado sin verificar | Cuenta administradora |
| E6-W14 | Mapa o búsqueda de dirección | e2e con Leaflet dibujado | Implementado sin verificar | — |
| E6-W15 | Editar la feria y que persista | e2e | Implementado sin verificar | — |
| E6-W16 | Ficha pública con mapa | Publicada: mapa dibujado con 1 marcador | Verificado | — |
| E6-W17 | «Cómo llegar» abre la app de mapas | Publicada: el enlace lleva las coordenadas de la feria | Implementado sin verificar | Abrirlo en un celular |
| E6-A01 – A07 | Modo comprador: directorio, filtros, tienda, opción, asociación, contacto, precios ocultos | Unitarias (`Entrega6Test`), emprendedoras anteriores en emulador | Implementado sin verificar | Recorrido en emulador o celular con servidor |
| E6-A08 | PERMITIR / INVITAR / OBLIGAR al abrir | Código y lectura de la opción; Google desactivado | **Bloqueado** (Google) en INVITAR y OBLIGAR | Activar Google |
| E6-A09 | Compartir tienda, producto y asociación | Código (menú nativo) | Implementado sin verificar | Emulador |
| E6-A10 | QR de tienda, asociación y feria | Código | Implementado sin verificar | Emulador |
| E6-A11 – A15 | Mapa de feria, mapa de ferias, con y sin permiso, permiso al abrir el mapa | Unitarias del orden por distancia o fecha | Implementado sin verificar | Emulador con ubicación simulada |
| E6-A16 – A18 | Precios y contacto, bandeja, punto de retiro | SQL real + código | Implementado sin verificar | Cuenta de prueba |

Reglas de la entrega 5 (opciones, cantidades, pieza única, disponibilidad, venta atómica, reintentos y sincronización por cuenta): siguen pasando en la batería SQL completa.

## 6. Pauta de prueba de la entrega 7 (35 criterios)

**Preparación (una vez, antes de la demo):**
- Migración 07 aplicada (sección 4), Firebase y notificador configurados, Google activo (sección 3b).
- APK final instalado en un **celular físico**.
- Cuentas identificables:
  - administrador;
  - emprendedora «PRUEBA E7 Uno», integrante de la asociación «PRUEBA E7 Asociación»;
  - emprendedora «PRUEBA E7 Dos», para el rechazo y el recordatorio;
  - comprador «PRUEBA E7 Comprador», que entra con Google.
- Feria «PRUEBA E7 Feria» en borrador, con fecha del día de la demo, ubicación y un horario que incluya la hora de la demo.
- Cada «procesar un lote» significa ejecutar `npm run notificar` **una vez**. Nunca modo continuo ni cron.

Estados: **probado localmente** (SQL real local, pruebas unitarias o emulador con sustitutos) · **simulado** (recorrido web con un Supabase sustituto) · **pendiente de prueba física** · **bloqueado por configuración**.

Una prueba local o simulada **no** equivale a ver la notificación en el celular: cada fila dice qué evidencia hay.

Ningún criterio está «verificado en el entorno real» todavía.

| Orden | ID | Quién | Pasos | Resultado que debemos ver | Evidencia disponible | Estado |
|---|---|---|---|---|---|---|
| 1 | W01 | Admin (web) | Panel Admin → Ferias → «📣 Publicar» en «PRUEBA E7 Feria»; abrir la web en ventana privada | La feria aparece en el calendario y en «Próximas ferias» sin sesión | SQL (borrador oculto → visible) + recorrido | Bloqueado por configuración (07) |
| 2 | W14 | Visitante (web) | En la ventana privada: «Calendario de próximas ferias» → tocar la feria | Se abre su ficha con fecha, lugar y mapa | Recorrido + unitarias | Bloqueado por configuración (07) |
| 3 | W16 | Comprador (web) | Sin sesión: abrir la tienda «PRUEBA E7 Uno» → «＋ Seguir» → iniciar sesión | Al volver dice «✓ Siguiendo» sin volver a tocar | Recorrido (con correo y cancelación) | Bloqueado por configuración (07; con Google, además Google) |
| 4 | A12 | Comprador (celular) | Sin sesión: abrir la tienda «PRUEBA E7 Uno» → «Seguir» → «Continuar con Google» → elegir la cuenta | Vuelve a la ficha de la tienda con «Siguiendo». Repetir con la asociación | Emulador: el botón abre Google y no el correo (tienda y asociación); unitarias de la ficha de vuelta | Bloqueado por configuración (Google y 07) |
| 5 | A14 | Comprador (celular) | Al volver de A12 aparece «¿Quieres recibir avisos?» → «Sí, avisarme» → permitir notificaciones | Mi perfil → Avisos muestra «Recibir avisos» activo | SQL (sin consentimiento no hay aviso) | Pendiente de prueba física |
| 6 | W15 | Comprador (web) | Con sesión: abrir la asociación «PRUEBA E7 Asociación» → «＋ Seguir» → «Sí, avisarme» | «✓ Siguiendo» y consentimiento guardado | SQL + recorrido | Bloqueado por configuración (07) |
| 7 | A10 | Comprador (celular) | Abrir la asociación → «Seguir» | «Siguiendo» | SQL | Pendiente de prueba física |
| 8 | A09 | Comprador (celular) | Si se siguió por la web, comprobar «Siguiendo» en la tienda; si no, seguirla | «Siguiendo» | SQL | Pendiente de prueba física |
| 9 | W03 | Admin (web) | «📋 Invitaciones y checklist» → marcar «PRUEBA E7 Uno» → «Invitar seleccionadas» | Fila «Invitada» | SQL + recorrido | Bloqueado por configuración (07) |
| 10 | A01 | Emprendedora (celular) | Con sesión y «Activar avisos» tocado, enviar la app a segundo plano (sin detenerla). Procesar un lote | Llega «Te invitaron a PRUEBA E7 Feria» con la app en segundo plano | SQL (crea el aviso) + envío simulado; emulador: el permiso se pide al tocar | Bloqueado por configuración (Firebase, notificador, 07) |
| 11 | A04 | Emprendedora (celular) | Tocar la notificación → «Mis ferias» | La feria aparece en «Invitaciones» y en el calendario del mes | Unitarias del calendario | Pendiente de prueba física |
| 12 | A02 | Emprendedora (celular) | Tocar «Participo» | «Confirmaste tu participación…» (si dice «pendiente de conexión», falta señal) | SQL + unitarias | Pendiente de prueba física |
| 13 | W07 | Admin (web) | Mirar el checklist abierto (se actualiza cada 20 s) o tocar «Actualizar» | «PRUEBA E7 Uno» pasa a «Confirmada» | SQL real; recorrido simulado | Bloqueado por configuración (07) |
| 14 | A13 | Comprador (celular) | Procesar un lote con la app del comprador en segundo plano | Push «PRUEBA E7 Uno estará en PRUEBA E7 Feria» | SQL (destinos y título) + envío simulado | Bloqueado por configuración (Firebase, notificador) |
| 15 | W18 | Comprador (web) | Con la web abierta y el permiso del navegador concedido (o cerrada, si hay Firebase web) | Notificación del navegador con el nombre de la feria. La web y el celular la reciben cada uno | SQL (web primero/celular primero) + recorrido | Bloqueado por configuración (07; con la página cerrada, además Firebase web) |
| 16 | W04 | Admin (web) | Invitar «PRUEBA E7 Asociación» | Sus integrantes aparecen; quien ya estaba no se duplica | SQL + recorrido | Bloqueado por configuración (07) |
| 17 | W05 | Admin (web) | Marcar «PRUEBA E7 Dos» → «Agregar directamente» | Fila «Agregada por administración» | SQL + recorrido | Bloqueado por configuración (07) |
| 18 | W06 | Admin (web) | Revisar el checklist | Se ven «Invitada», «Confirmada», «Rechazada» y «Agregada por administración» (la rechazada aparece en la fila 21) | SQL + recorrido | Bloqueado por configuración (07) |
| 19 | W08 | Admin (web) | Usar la búsqueda y los filtros de estado, tipo y asociación | La lista se reduce según cada filtro | Unitarias + recorrido | Bloqueado por configuración (07) |
| 20 | W09 | Admin (web) | Invitar a otra participante de prueba sin responder → «⏰ Recordar…» → procesar un lote | Solo la pendiente recibe el recordatorio; el contador coincide | SQL (solo pendientes, sin duplicar) + recorrido | Bloqueado por configuración (07, Firebase) |
| 21 | A03 | Otra emprendedora (celular) | Esa participante toca «No participo» | En la web, su fila pasa a «Rechazada» | SQL | Pendiente de prueba física |
| 22 | W10 | Admin (web) | Escribir un mensaje → «📢 Enviar aviso a las confirmadas» → procesar un lote | Llega a confirmadas y agregadas, no a las rechazadas | SQL + recorrido | Bloqueado por configuración (07, Firebase) |
| 23 | W12 | Visitante (web) | Abrir la ficha de la feria | Lista «PRUEBA E7 Uno» y las agregadas; no la rechazada | SQL + recorrido | Bloqueado por configuración (07) |
| 24 | W13 | Visitante (web) | En la ficha, revisar la muestra de productos | Solo productos públicos (un producto «Solo yo lo veo» o no disponible no aparece) | Unitarias + recorrido | Bloqueado por configuración (07) |
| 25 | A07 | Comprador (celular) | Abrir la ficha de la feria en la app | «Participan» con la muestra de productos públicos | SQL de la consulta pública | Pendiente de prueba física |
| 26 | A08 | Comprador (celular) | «Compartir» y «Código QR» → abrir el enlace o QR en un navegador sin sesión | Se abre la ficha web de la feria | Recorrido del enlace `?feria=` | Pendiente de prueba física |
| 27 | A05 | Emprendedora (celular) | Venta → «¿Dónde vendiste?» | «Hoy participas en PRUEBA E7 Feria» → «Usar esta feria» | Unitarias (solo confirmadas, varios días, canceladas) | Pendiente de prueba física |
| 28 | A06 | Emprendedora (celular) | En el punto de la feria, con la ubicación activada: «Confirmar con mi ubicación» | «Estás en PRUEBA E7 Feria (a N m)» | Unitarias (lejos, imprecisa, antigua, fuera de horario) | Pendiente de prueba física |
| 29 | W11 | Admin (web) | En el checklist, marcar «Asistió» para «PRUEBA E7 Uno» | Se guarda; para la rechazada el control está desactivado | SQL + recorrido | Bloqueado por configuración (07) |
| 30 | W17 | Comprador (web) | «✓ Siguiendo» → dejar de seguir | Vuelve a «＋ Seguir» | SQL + recorrido | Bloqueado por configuración (07) |
| 31 | A11 | Comprador (celular) | Mi perfil → Avisos → «Dejar de seguir». Luego la emprendedora dice «Ya no puedo participar», el admin la agrega de nuevo y se procesa un lote | No llega aviso nuevo; en la base queda omitido por «ya no sigue» | SQL (omitido y no enviado) | Pendiente de prueba física |
| 32 | A15 | Emprendedora (celular) | Tienda → «Precios, contacto…» → Avisos de mensajes: activar con un horario que excluya la hora actual. Enviar un mensaje desde la web. Luego incluir la hora actual y enviar otro | El primero llega a «Mensajes recibidos» sin notificación; el segundo notifica (tras procesar un lote) | SQL (dentro, fuera, apagado; también al enviar) | Pendiente de prueba física |
| 33 | A16 | Emprendedora (celular) | Mismo lugar → «Permitir que me califiquen» | Interruptor activo (y luego apagado) | SQL (otra emprendedora no puede cambiarlo) | Pendiente de prueba física |
| 34 | A17 | Comprador (celular) | Con calificaciones activas: calificar la tienda y un producto. Apagarlas y volver a abrir la tienda | Activas: se guarda y se ve el promedio. Apagadas: desaparece el control y el servidor rechaza | SQL (lo exige el servidor) | Pendiente de prueba física |
| 35 | W02 | Admin (web) | Al final: «Cancelar feria» con motivo | Sale del calendario; su ficha dice «cancelada»; las participantes reciben el aviso (procesar un lote) | SQL + recorrido | Bloqueado por configuración (07) |

## 7. Pruebas ejecutadas (22-nov-2026)

| Batería | Resultado | Tipo |
|---|---|---|
| `npm run test:sql` | 758 de 758 en PostgreSQL 15 y 17. Incluye la entrega por destino (web primero, celular primero, reintento al destino pendiente, dejar de seguir, retirar consentimiento, cierre de sesión en celular compartido, navegador sin confirmar) y la migración con datos de la entrega 6 | Probado localmente |
| `npm run test:web` | 35 de 35 | Probado localmente / simulado |
| `npm run test:e2e` | 43 de 43 en Edge (incluye: la web confirma «mostrado» con su propio destino y no lo hace si la notificación falla) | Simulado |
| `./gradlew testDebugUnitTest assembleDebug` | 61 de 61 (incluye la regla del botón «Seguir» y la ficha de vuelta) | Probado localmente |
| Emulador (`BotonSeguirTest`) | 3 de 3: tienda y asociación abren Google y no el correo; un error se informa y se puede reintentar | Probado en el emulador (Google sustituido) |

No se hicieron pruebas contra la instancia del curso, salvo una lectura de `/auth/v1/settings` para ver si Google está activo.

## 7b. Archivos privados publicados por error

El commit `28b65a0` versionó herramientas locales con un respaldo que contiene datos personales, además de dependencias, capturas y skills personales.

En esta rama se retiraron del seguimiento (`git rm --cached`) sin reescribir el historial:
- `.herramientas/`, `node_modules/` y `test-results/`;
- `.agents/`, `.claude/` y `skills-lock.json`.

Siguen en el disco y en `.gitignore`. Se comprobó con `git check-ignore` que los archivos privados (`supabase.properties`, `firebase.properties`, `js/config.js`, `servicios/notificador/.env` y los JSON de cuentas de servicio) quedan fuera.

El retiro del historial ya publicado requiere una decisión del grupo y está en [retiro-respaldos-publicados.md](retiro-respaldos-publicados.md). Ahí no se copia nada de esos respaldos.

## 8. Recorrido mínimo de prueba real (después de aplicar 07 y la sección 3b)

Necesitas:
- cuenta administradora y una emprendedora de prueba «PRUEBA E7» en un **celular físico**, con el APK compilado con `firebase.properties`;
- la feria «PRUEBA E7 Feria», publicada;
- el notificador corriendo con `npm run notificar:vigilar` (solo durante la prueba).

1. **Invitar (web, admin):** «📋 Invitaciones y checklist» → marcar «PRUEBA E7» → «Invitar seleccionadas». La fila aparece como «Invitada».
2. **Recibir el push (celular):**
   - la emprendedora inició sesión antes y tocó «Activar avisos»;
   - con la app en segundo plano (sin detenerla a la fuerza) llega «Te invitaron a PRUEBA E7 Feria»;
   - el notificador muestra «aceptados por FCM 1»: eso es la aceptación del proveedor;
   - la evidencia de recepción es ver la notificación en el celular. Al tocarla, ese celular queda `ABIERTO` en `entregas_aviso`.
3. **Aceptar:** la notificación abre «Mis ferias» → «Participo». La app confirma «Confirmaste tu participación…»; si dice «pendiente de conexión», falta señal.
4. **Ver la confirmación (web):** con el checklist abierto, en hasta 20 s (o con «Actualizar») la fila pasa a «Confirmada».

Si el paso 2 no llega:
- revisar que el dispositivo esté registrado (`dispositivos` con plataforma ANDROID) y la salida del notificador;
- `SIN_DISPOSITIVO` = sin token;
- `FALLIDO` con «mensaje rechazado» = el contenido no lo aceptó FCM.

Al terminar, borrar la cuenta y la feria de prueba. El resto de los criterios (seguir, dejar de seguir, horario de contactos, calificaciones, sugerencia de venta) se prueba después con el mismo esquema.

## 9. APK

| Archivo | Versión | Uso |
|---|---|---|
| `app-debug.apk` (raíz, enlace del README) y `LA PAgina (segun)/proyectode10/app-debug.apk` (botón de la web) | `1.4-entrega6` (versionCode 5). Ambos tienen el mismo hash | Descargas publicadas hoy: **no se reemplazaron** |
| `App para el celular/app/build/outputs/apk/debug/app-debug.apk` | `1.5-entrega7` (versionCode 6) | **Provisional**: compilado sin `firebase.properties` (sin push). No se publica |

Para el APK final:
1. Crear `firebase.properties` y compilar.
2. Comprobar la versión con `aapt dump badging`.
3. Probarlo en el celular físico.
4. Copiar **ese mismo archivo** a las dos rutas de descarga.
5. Actualizar la etiqueta del README y del botón de la web («v1.5 · Entrega 7»).
6. Comprobar con `sha256sum` que las tres copias son idénticas.

No se suben `supabase.properties`, `firebase.properties`, `js/config.js`, credenciales del notificador ni la cuenta de servicio de Firebase.
