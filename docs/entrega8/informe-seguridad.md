# Entrega 8 · Informe de seguridad (RNF-SEG-04 a RNF-SEG-08)

Revisión del 24-nov-2026 sobre el producto completo:
- app Android `1.6-entrega8`;
- vitrina web;
- backend Supabase (migraciones 01–07, 09 y 10);
- servicio de envío de avisos;
- dependencias.

Incluye los módulos de las entregas 6 y 7: ferias, seguidores, avisos y mapas. Todo se probó en local, sin consultar la instancia real.

## 1. Arquitectura y superficie

| Pieza | Datos que maneja | Control de acceso |
|---|---|---|
| App Android (Kotlin) | Sesión de Supabase, copia local de la tienda, cola de cambios por subir | Solo la clave pública (anon). Cada operación se valida en el servidor con RLS y funciones `security definer`. |
| Web (HTML + JS, GitHub Pages / Firebase Hosting) | Igual que la app | Solo clave anon (`js/config.js` generado, fuera de git). |
| Supabase (PostgreSQL + Auth + Storage) | Cuentas, tiendas, productos, ventas, ferias, seguimientos, avisos, bitácora | RLS por perfil; escrituras sensibles solo por funciones del servidor. |
| Servicio de envío (`servicios/notificador`, Node) | Cola de avisos, tokens FCM | Cuenta propia registrada en `notificadores`; nunca `service_role`. Credenciales en `.env` fuera de git. |
| Firebase | Push (FCM) y, desde esta entrega, App Check sobre Realtime Database | Identificadores públicos en `firebase.properties` (fuera de git). La cuenta de servicio solo la usa el emisor. |

**Perfiles probados:** visitante (anon), compradora, dos emprendedoras, administradora, segunda administradora (entrega 8) y cuenta del emisor.

## 2. Pruebas automatizadas de las reglas (RNF-SEG-04)

Hay dos baterías, porque hay dos tipos de reglas.

**2.1 Reglas de Firebase en el emulador oficial (Firebase Emulator Suite).** La app usa Firebase Realtime Database solo para la ruta de comprobación de App Check. Sus reglas están versionadas en `firebase/database.rules.json`, que es el mismo contenido que se publica en la consola. `tests/reglas/rtdb.test.js` las prueba en el emulador de Realtime Database, con un proyecto de demostración sin conexión a ningún proyecto real:
- se permite escribir una comprobación válida;
- no se puede sobrescribir ni borrar;
- se valida la forma (campos, tipos, sin datos extra);
- nadie lee;
- no se escribe en ninguna otra ruta.

```powershell
npm.cmd run test:reglas
```
**Resultado: 5 de 5.** Si se abren las reglas a propósito, fallan 2 pruebas, lo que muestra que detectan el cambio.

**Lo que el emulador NO prueba:** App Check exigido. El emulador no verifica tokens de App Check. Esa evidencia es de la consola: métricas de solicitudes verificadas y no verificadas, y el rechazo con «Enforce» (sección 5).

**2.2 Reglas reales del backend (Supabase).** Los datos de la plataforma están en Supabase, así que sus reglas son **políticas RLS y funciones de PostgreSQL**. Se prueban en un **PostgreSQL local desechable**:
- se crea en cada ejecución;
- aplica las mismas migraciones que la instancia;
- simula `auth` y `storage` con `supabase/stub_local.sql`;
- ejecuta cada operación con el rol y el `auth.uid()` de cada perfil, igual que Supabase.

Nunca se conecta a la instancia del curso.

```bash
npm install
```
```bash
npm run test:sql
```

**Resultado (24-nov-2026): 570 de 570 casos correctos en PostgreSQL 17.** Para correrlo también en PostgreSQL 15, ver `docs/desarrollo-y-pruebas.md`.

| Área | Qué se comprueba (se permite / se rechaza) | Archivo |
|---|---|---|
| Base (RLS) | El visitante solo lee lo público. Una emprendedora no edita otra tienda ni ve ventas ajenas. El administrador no ve ventas (RN-10). Storage solo en la carpeta propia. El rol ADMIN no se acepta desde el cliente. | `supabase/pruebas_rls.sql` |
| Catálogo y ventas | Cantidades solo por `guardar_producto`. Ventas solo por `registrar_venta`: atómica, idempotente y sin sobreventa (también con dos conexiones a la vez). Pieza única. | `tests/sql/pruebas_entrega5.js`, `concurrencia.js` |
| Vitrina y mapas (E6) | `proteger_tienda` y `proteger_contacto`. Los contactos los lee solo la dueña. Coordenadas válidas. Configuración de navegación solo ADMIN. | `tests/sql/pruebas_entrega6.js` |
| Ferias y seguidores (E7) | Solo ADMIN publica, cancela e invita. Solo la dueña responde y solo su fila. La ficha pública muestra solo confirmadas y productos públicos. `seguir` exige sesión y no duplica. Sin consentimiento no hay aviso. Los dispositivos pertenecen a una cuenta. El emisor solo lee lo que debe enviar. Calificaciones solo si están activas y no la propia tienda. | `tests/sql/pruebas_entrega7.js` |
| Administración (E8) | Solo ADMIN gestiona administradores. No se desactiva la propia cuenta ni la última. Desactivar quita el rol en el servidor. Nadie escribe ni borra la bitácora desde un cliente. Las acciones de no administradores no se anotan. | `tests/sql/pruebas_administracion.js` |
| Emprendedoras y tipos (E8, 10) | Solo ADMIN edita y desactiva. Una tienda desactivada no se ve y su dueña no guarda productos, ventas, cambios ni respuestas; tampoco puede reactivarse sola. Tipos: solo ADMIN; no se elimina uno en uso; desactivar conserva el tipo en los productos. Actualización desde el esquema anterior y reversión. | `tests/sql/pruebas_emprendedoras_tipos.js` |
| Migraciones | Instalación limpia, actualización con datos antiguos, aplicar dos veces, revertir y volver a aplicar, transacción. Sin `TRUNCATE`, `TRIGGER` ni `REFERENCES` para los clientes. | `tests/sql/ejecutar.js` |

**Otras baterías:**
- Web unitarias, `npm run test:web`: 58/58. Incluyen Hosting en carpeta temporal (rechaza claves secretas y APK no aptos), el verificador de APK y el emisor (no muestra credenciales ni cuerpos).
- Recorridos web con Supabase sustituto, `npm run test:e2e`: 60/60. Incluyen XSS en nombres, errores y la administración de la entrega 8.
- Ensayo en el emulador de Hosting, `npm run test:hosting`: 5/5 (cabeceras, APK, enlaces, recuperación).
- Android, `gradlew testDebugUnitTest`: 62/62.

## 3. Análisis de vulnerabilidades (RNF-SEG-05)

| Herramienta / revisión | Alcance | Resultado |
|---|---|---|
| Android Lint (`gradlew lintDebug`) | App completa | 1 error (corregido, H-05), 0 hallazgos en la categoría *Security*. Advertencias de productividad y versiones (H-10). |
| `npm audit` | Dependencias de desarrollo y pruebas (la web no usa npm en producción) | 0 vulnerabilidades. |
| Revisión de dependencias por CDN | supabase-js, Leaflet, Firebase JS | H-01 corregido; H-12 justificado. |
| Revisión del manifiesto Android | Componentes exportados, respaldo, tráfico en claro | Solo la actividad principal (lanzador y retorno de Google) está exportada. Sin tráfico en claro. H-02 corregido. |
| Búsqueda de secretos en git | Todo `git ls-files` (llaves privadas, `service_role`, `sb_secret_`, JWT, claves de Google, tokens de GitHub) | Sin secretos. Las coincidencias son código de validación y valores falsos de prueba. Ningún `.properties`, `.jks`, `.env` ni `config.js` versionado. |
| Revisión del panel web | Origen de los datos, escapes HTML | H-03 y H-06 corregidos. |
| MobSF | — | No se ejecutó (H-11). Lo reemplazaron Lint y la revisión manual del manifiesto, permisos y almacenamiento. |

## 4. Hallazgos

| ID | Componente | Hallazgo | Gravedad | Estado | Corrección o justificación |
|---|---|---|---|---|---|
| H-01 | Web | supabase-js se cargaba con la etiqueta flotante `@2` y sin verificación de integridad. Un cambio en el CDN podía ejecutar otro código con la sesión de la persona. | Media | **Corregido** | Versión fija `2.119.2` con `integrity="sha256-…"` y `crossorigin`. Se verificó que el hash coincide con el archivo y que carga en Edge. |
| H-02 | App | `android:allowBackup="true"` respaldaba todas las SharedPreferences, incluida la sesión de Supabase (token de actualización) y la cola de cambios. | Media | **Corregido** | `allowBackup="false"` y reglas que excluyen todo del respaldo en la nube y de la transferencia entre equipos (`backup_rules.xml`, `data_extraction_rules.xml`). Los datos reales están en el servidor. |
| H-03 | Web | Con servidor, el panel de administración mostraba cuentas emprendedoras, administradores y bitácora **de ejemplo guardados en el navegador**. Además «creaba» cuentas con una contraseña que nunca llegaba al servidor y una tienda falsa visible en la vitrina. | Alta (integridad e información engañosa) | **Corregido** | Administradores y bitácora vienen del servidor (09). La lista de emprendedoras viene de `perfiles` y `cuentas_autorizadas`. Crear solo autoriza el correo. Las pestañas locales de asociaciones y catálogos se ocultan con servidor. |
| H-04 | Backend | Las acciones del administrador sobre cuentas, asociaciones y ferias no quedaban registradas (RNF-SEG-07). Solo se anotaban los avisos de feria. | Media | **Corregido** | `09_administracion.sql`: disparadores sobre ferias, participaciones, asociaciones, pertenencias, cuentas autorizadas, configuración y tipos. Funciones de administradores que anotan quién, qué y cuándo. Solo lectura para ADMIN; nadie escribe ni borra desde un cliente. Se anotan los nombres de los campos cambiados, no sus valores. |
| H-05 | App | Lint `ContextCastToActivity`: convertir `LocalContext` en `Activity` puede fallar y dejar la huella sin funcionar. | Baja | **Corregido** | `LocalActivity` en el inicio de sesión y Ajustes. |
| H-06 | Web | Nombres y correos del panel de administración se insertaban en HTML sin escapar (posible XSS almacenado si alguien guarda un nombre con etiquetas). | Media | **Corregido** | Las vistas de administradores, bitácora y emprendedoras usan `escaparHtml`. Las vistas públicas ya escapaban (prueba XSS en `web.spec.js`). |
| H-07 | App | El APK publicado era el de depuración, firmado con la clave de depuración, que es pública y compartida por todos los equipos. | Baja | **Corregido en la configuración** · falta generar la clave | `signingConfigs` lee `keystore.properties` (fuera de git, igual que `*.jks`). La clave la genera y guarda el grupo (ver README de la entrega). |
| H-08 | App y web | La URL de Supabase y la clave anon están en el APK y en la web. | Informativa | **Justificado** | Son públicas por diseño. El control está en RLS y en las funciones. Se valida que nunca se use una clave `service_role` o `sb_secret_` (workflow, emisor y preparación de Hosting). |
| H-09 | App y backend | Sin App Check, cualquier cliente con la clave anon puede llamar a la API. | Media | **Parcial** | App Check en la app (Play Integrity o depuración) protege la ruta de comprobación en Realtime Database. Supabase no verifica tokens de App Check. El riesgo residual se limita con RLS, funciones con validación, idempotencia y límites. Activación en la consola: README de la entrega. |
| H-10 | App | Dependencias con versiones nuevas disponibles (Lint `GradleDependency`, `NewerVersionAvailable`). | Baja | **Justificado** | Versiones estables 2024–2025. No se pudo correr un escáner de CVE para Gradle (OWASP Dependency-Check o MobSF) en esta etapa. Actualizar el día de la entrega arriesga la demo. Plan: actualizar y repetir las pruebas después del 26-nov. |
| H-11 | App | MobSF no ejecutado. | Informativa | **Justificado** | Requiere instalar el servidor de MobSF. Se cubrió con Lint, la revisión del manifiesto (componentes exportados, permisos, respaldo, tráfico en claro) y la revisión del almacenamiento local. |
| H-12 | Web | El service worker carga Firebase JS 10.14.1 con `importScripts` desde `gstatic.com`, sin SRI. | Baja | **Justificado** | `importScripts` no admite SRI. La versión es fija y el dominio es de Google. Solo se usa si se configura el push del navegador. |
| H-13 | Backend | Alta abierta de emprendedora (RN-01 ajustado): cualquiera puede crear una cuenta con tienda. | Baja | **Justificado** (decisión del grupo) | La tienda nueva nace sin productos públicos y el rol lo asigna el servidor (ADMIN nunca desde el cliente). Para cerrarlo basta con que `al_crear_usuario` ignore `tipo_cuenta`; ver la matriz (RN-01). |
| H-14 | Backend | (Entrega 7) El visitante tenía permisos de más sobre contactos y tablas privadas, heredados de los permisos por defecto de Supabase. | Alta | **Corregido** (commit `e8b0775`) | La 07 retira esos permisos. Lo comprueban las pruebas SQL con los mismos permisos por defecto de la instancia. |
| H-15 | Web | GitHub Pages no permite cabeceras de seguridad. | Baja | **Corregido en Hosting** | `firebase.json` agrega `X-Content-Type-Options`, `Referrer-Policy` y `X-Frame-Options`. Pages se conserva mientras se verifica Hosting. |
| H-17 | App | El APK público incluía el proveedor de depuración de App Check y la pantalla para ver y copiar su token. Firebase pide no distribuirlo. | Media | **Corregido** | El proveedor depende del tipo de APK: `src/release` usa Play Integrity y `src/debug` el de depuración (`debugImplementation`). El botón del token solo existe en debug. `scripts/verificar-apk.js` rechaza un APK con ese proveedor, depurable, sin firma o firmado con la clave de depuración. La preparación de Hosting lo exige. |
| H-18 | Herramientas | Las pruebas de Hosting reemplazaban `dist/hosting` por una web con configuración ficticia: se podía publicar por error. | Media | **Corregido** | Las pruebas usan una carpeta temporal (`--destino`) y comprueban al final que `dist/hosting` no cambió. |
| H-19 | Web (Hosting) | Las cabeceras de seguridad con el patrón `**` no se aplicaban a la raíz `/` (lo mostró el emulador). | Baja | **Corregido** | Se usa `regex: ".*"`. El emulador comprueba `nosniff` y `SAMEORIGIN` en `/`. |
| H-16 | Servicio de envío | Riesgo de exponer credenciales o cuerpos de respuesta en los registros de GitHub Actions. | Media | **Corregido** (entrega 7) | El emisor muestra solo conteos y códigos reconocidos. Las pruebas usan valores centinela que nunca deben aparecer en la salida. |

## 5. Detección (RNF-SEG-06, RNF-SEG-07)

**App Check (A11).** La app instala App Check al iniciar Firebase:
- **APK público:** Play Integrity.
- **APK privado de pruebas:** proveedor de depuración.
- En Ajustes → «Protección de la app», «Comprobar App Check» escribe una comprobación sin datos personales en Realtime Database.
- Con «Enforce», Firebase acepta las solicitudes atestiguadas y rechaza las demás (curl).

Según la documentación oficial, Play Integrity se configura vinculando el proyecto en Google Play Console. Para un APK distribuido fuera de Google Play hay que elegir «Exclusivamente fuera de Google Play», porque esas apps no reciben `PLAY_RECOGNIZED`. **Decisión del grupo: camino sin costo.** Sin esa vinculación, el APK público queda no verificado en las métricas: marcado, no aceptado. La evidencia de A11 sale de la consola:
- **verificada frente a no verificada** en las métricas;
- con «Enforce», **aceptada frente a rechazada**: el APK privado de pruebas, con su token de depuración, contra el mismo `curl` desde un PC.

Los pasos de consola y la prueba de rechazo están en [README.md](README.md#3-lo-que-te-toca-hacer).

**Bitácora (W08).** Web → Panel Admin → «📋 Bitácora». Muestra fecha y hora de Chile, quién, acción y objeto. La alimenta el servidor (09).

## 6. Riesgos residuales y plan

1. **APK público sin Play Console** (decisión del grupo: camino sin costo). Play Integrity no reconoce el APK instalado a mano y sus solicitudes quedan marcadas como no verificadas. La evidencia de rechazo de A11 se obtiene con el APK privado de pruebas frente a una solicitud externa equivalente. Solo afecta a la ruta de comprobación, no a la plataforma.
2. **Supabase no verifica App Check.** Se mitiga con RLS. Una función en el borde (Edge Function) que verifique el token antes de las RPC sensibles podría cerrarlo, pero requiere capacidades del servidor que el curso no ha confirmado.
3. **Dependencias Android** por actualizar (H-10) y MobSF (H-11) después de la presentación.
4. **RN-01** (H-13), si la contraparte exige registro solo por el administrador.
5. **Prueba física** de App Check y de la clave de firma con el APK final (README de la entrega).
