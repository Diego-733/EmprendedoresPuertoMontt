# Entrega 8 · Producto terminado (ALERCEIS7)

Revisión: miércoles 25-nov-2026. Jurado: jueves 26-nov-2026. Pauta: `checklist-revision-entrega-8.md` (35 criterios).

| Documento | Para |
|---|---|
| Esta guía | Estado y verificación de los 35 criterios, pasos tuyos, recorrido de la demo |
| [matriz-requisitos.md](matriz-requisitos.md) | C07–C10: estado final de cada requisito y caso de prueba de cada Esencial |
| [informe-seguridad.md](informe-seguridad.md) | C04–C06: reglas, vulnerabilidades, hallazgos |
| [manual-emprendedora.md](manual-emprendedora.md) · [manual-administrador.md](manual-administrador.md) | C03, C12, W10 |
| [ia-modulo-desacoplado.md](ia-modulo-desacoplado.md) | C13 |
| [README del repositorio](../../README.md) | C11: ejecutar desde un clon limpio |

## 1. Pruebas ejecutadas (24-nov, todas en local, sin consultar la instancia real)

| Batería (comando en Windows) | Qué prueba | Resultado |
|---|---|---|
| `npm.cmd run test:sql` | RLS y funciones de Supabase en PostgreSQL 17 local desechable: migraciones 01–07, 09 y 10; actualización desde el esquema anterior; reversión | **570/570** |
| `npm.cmd run test:reglas` | Reglas de Realtime Database (`firebase/database.rules.json`) en el **emulador de Firebase** | **5/5** |
| `npm.cmd run test:web` | Reglas web, emisor, preparación de Hosting y verificador de APK, en carpeta temporal | **58/58** |
| `npm.cmd run test:e2e` | Recorridos de la web en Edge con Supabase simulado | **60/60**, más 5 que solo corren con el emulador |
| `npm.cmd run test:hosting` | La web preparada servida por el **emulador de Hosting**: cabeceras, APK, `?tienda=`, `?feria=`, 390 px, retorno de recuperación | **5/5** |
| `gradlew.bat testDebugUnitTest lintDebug` | Android, incluido el proveedor de App Check por tipo de APK | **62/62**, lint con 0 errores |
| `npm.cmd run apk:verificar -- <apk>` | APK de depuración / release sin firmar / release firmado con una clave desechable | No apto / No apto / **Apto**. El release no contiene el proveedor de depuración. |
| Emulador Pixel 7 (APK sin servidor) | Elección de modo, letra al 200 % (acceso, inicio de sesión, tienda), franja sin conexión, Ajustes | Corregido y comprobado (ver A02, A08, A09) |
| Copia limpia sin archivos secretos | `npm ci`, `test:web`, `test:sql`, `test:reglas`, compilación Android | Todo pasa. Sin historial de git, el escenario de actualización avisa y se omite. |

**Migraciones, en este orden exacto:** `09_administracion.sql` → `10_emprendedoras_y_tipos.sql`. No basta con la 09: la 10 agrega RF-ADM-01 y RF-ADM-03 y usa la bitácora de la 09.

Para revertir: `10_revertir.sql` y después `09_revertir.sql`.

La 08 sigue fuera. Si alguna vez se vuelve a ejecutar `05_catalogo_ventas.sql`, hay que repetir la 10, porque la 05 reescribe la política de productos.

## 2. Los 35 criterios: qué funciona, qué se probó, qué haces tú y qué deberías ver

Marca ☐ → ☑ al comprobarlo con el celular y el computador.

**Cuentas de la demo:**
- `admin@alerce.app` (web);
- `donamarta@alerce.app` (emprendedora con tienda, invitada a la feria);
- una **compradora con Google**, con sesión iniciada en el celular y que ya sigue la tienda de Doña Marta;
- tu buzón de prueba (W06).

### App Android

| ☐ | ID | Funciona en teoría | Prueba ejecutada → resultado | Qué haces tú | Qué deberías ver |
|---|---|---|---|---|---|
| ☐ | **A01** | `minSdk 24` (Android 7.0). APK público firmado con tu clave (`keystore.properties`, fuera de git), no depurable y sin proveedor de depuración. | Verificador: el de depuración y el release sin firmar son **no aptos**; el release firmado con una clave desechable es **apto**. | Pasos 1 y 2 de la sección 3 (primero `firebase.databaseUrl`, después firmar una sola vez). Instala el mismo `app-release.apk` que publicas en Hosting. | «Resultado: APTO para publicar». El SHA-256 del archivo es igual en tu PC, en Hosting y en el instalado. Ajustes muestra «versión 1.6-entrega8». |
| ☐ | **A02** | Primera pantalla «¿Cómo quieres usar la app?»: «Quiero comprar» / «Soy emprendedora». Con Invitar u Obligar, Google y «Soy emprendedora… entrar con correo». | Emulador al 100 % y 200 %: ambos botones abren su flujo. | Abre la app sin sesión. | Las dos opciones; cada una entra a su modo. |
| ☐ | **A03** | Edición de tienda con `PATCH`, protegida por RLS. | SQL (`proteger_tienda`). Comprobado en la instancia en entregas anteriores. | `donamarta` → **Tienda** → cambiar la descripción → **Guardar cambios** → cierra y abre la app. | El cambio sigue y también se ve en la web. |
| ☐ | **A04** | Alta guiada con foto (cámara o galería) y «Lo ven todos». | SQL y unitarias (`FormularioProductoTest`). Usado en la instancia en la entrega 5. | **Productos → Añadir producto** → foto → nombre y precio → «Lo ven todos». | El producto aparece en la vitrina web. |
| ☐ | **A05** | `registrar_venta`: atómica, idempotente, descuenta stock; una pieza única sale del catálogo. | SQL más concurrencia (dos conexiones). | **Venta** → ese producto → confirmar. Mira la web. | Baja la cantidad o desaparece, si es pieza única. |
| ☐ | **A06** | Directorio con filtros, tienda y ficha de feria con mapa. | e2e de las entregas 6 y 7. Comprobado en la instancia. | Modo comprador: directorio → una tienda → una feria. | Las tres vistas. |
| ☐ | **A07** | Seguir y, cuando la tienda confirma una feria, push FCM enviado por el emisor local. | SQL (destinatarios, consentimiento) más envío simulado. **Real 22-nov** (invitación) y **24-nov** (tienda seguida). | Recorrido de la sección 4 (pasos 7 a 9) con `npm.cmd run notificar:vigilar` encendido. | Terminal: «aceptados por FCM 1» = **Firebase aceptó**. Celular de la compradora: «Minimarket Doña Marta estará en …» = **recepción observada**. |
| ☐ | **A08** | Botones con alto mínimo de 48 dp que crece con la letra. Selector de inicio de sesión apilado. Barra inferior en una línea. | Emulador al **200 %**: acceso, inicio de sesión y tienda se leen. Antes el texto de los botones se cortaba. | Ajustes del celular → tamaño de letra al máximo → abre acceso, inicio de sesión y Tienda. | Nada se corta. Las etiquetas largas de la barra se acortan con «…». |
| ☐ | **A09** | Franja «Sin conexión», cola de cambios por subir y borradores locales. | Emulador en modo avión: aparece la franja y se va al volver la red. SQL de reintento idempotente. | Modo avión → crea un producto o una venta → quita el modo avión. | La franja aparece y se va. El producto o la venta llega a la web. |
| ☐ | **A10** | Español de Chile en toda la app. | Búsqueda de voseo e inglés; 5 textos corregidos. | Recorre la app. | Sin voseo ni inglés. |
| ☐ | **A11** | **Pendiente de evidencia real en la consola.** App Check sobre Realtime Database. El APK público usa Play Integrity, sin proveedor ni token de depuración. El APK privado de pruebas usa el proveedor de depuración. Reglas versionadas. Camino sin costo: sin Play Console. | Emulador de reglas: 5/5. Verificador: el público no contiene el proveedor de depuración; el privado sale no apto. Unitaria del proveedor por tipo de APK. App Check exigido **no se puede emular**. | Pasos 1 a 3 de la sección 3. | **Marca:** en las métricas, la del APK privado sale verificada y la del `curl`, no verificada. **Rechaza (con Enforce):** el APK privado es aceptado («Firebase aceptó…») y el `curl` equivalente recibe `Permission denied`. Guarda las capturas. |

### Web

| ☐ | ID | Funciona en teoría | Prueba ejecutada → resultado | Qué haces tú | Qué deberías ver |
|---|---|---|---|---|---|
| ☐ | **W01** | La web en Firebase Hosting, con cabeceras de seguridad y la configuración pública. GitHub Pages se mantiene. | **Emulador de Hosting: 5/5.** Pruebas aisladas: no tocan `dist/hosting`. | Paso 4 de la sección 3. | La web carga en `https://TU-ID.web.app` con el catálogo real. |
| ☐ | **W02** | Adaptable de 320 a 1280 px. | e2e y emulador de Hosting a 390 px, sin desborde. | Abre la URL de Hosting en el navegador del celular. | Directorio y tienda completos, sin cortes. |
| ☐ | **W03** | Publicar, invitar, checklist y asistencia. | SQL de la entrega 7, e2e. **Real 22-nov.** | `admin` → **➕ Nueva feria** → **📣 Publicar** → **📋 Invitaciones y checklist** → invitar a Doña Marta. | «Feria publicada» y una fila «Invitada». |
| ☐ | **W04** | La emprendedora gestiona tienda, productos y ventas en la web. | e2e de la entrega 6. Comprobado en la instancia. | `donamarta` en la web → **Mi tienda** → cambia algo. | Se ve en la vitrina. |
| ☐ | **W05** | Directorio, tienda y feria sin sesión. | RLS anónima (SQL) y e2e. | Ventana privada. | Todo abre sin pedir sesión. |
| ☐ | **W06** | Pedir el enlace, volver con `?recuperar=1` al **mismo dominio** y guardar la clave nueva. | e2e y **emulador de Hosting: el retorno vuelve a Hosting y se guarda la clave nueva**. | Paso 6 de la sección 3, con tu buzón. | Llega el correo; el enlace abre «Nueva contraseña»; entras **con la clave nueva**. |
| ☐ | **W07** | 09: crear, editar, desactivar y reactivar otra administradora, sin la propia ni la última. | SQL 56 casos, e2e. | Aplica la 09 (paso 5) → **🛡️ Administradores** → crear, editar, desactivar → **recarga**. | Los cambios siguen tras recargar. La desactivada ya no entra al panel. |
| ☐ | **W08** | Bitácora del servidor: quién, qué y cuándo (ferias, asociaciones, cuentas, configuración, tipos). | SQL y e2e. | **📋 Bitácora** después de W03 y W07. | Correo, fecha y hora de Chile, acción y objeto. Registra solo lo hecho **después** de aplicar la 09. |
| ☐ | **W09** | Solo lo que la emprendedora publica (RLS). Aviso de privacidad. | SQL (visitante sin contactos, ventas ni ocultos) y e2e del aviso. | Ventana privada → tienda → pie de página → «Aviso de privacidad». | No hay datos privados; el aviso explica qué es público. |
| ☐ | **W10** | `manual-administrador.md`, secciones 1 a 3. | Revisado con los rótulos reales de la pantalla. | Paso 8 de la sección 3. | La persona publica y cancela la feria sin ayuda. |
| ☐ | **W11** | Español de Chile; rótulos del panel sin códigos internos. | Búsqueda en el código. | Recorre la web. | Sin inglés ni códigos. |

### Cierre

| ☐ | ID | Funciona en teoría | Prueba ejecutada → resultado | Qué haces tú | Qué deberías ver |
|---|---|---|---|---|---|
| ☐ | **C01** | — | — | Paso 9 de la sección 3. | La etiqueta `entrega-8` en GitHub, sobre el commit de la demo. |
| ☐ | **C02** | Toda la demo usa cuentas del grupo. | — | Sección 4. | App y web con cuentas de prueba. |
| ☐ | **C03** | Manuales con los rótulos reales. | Revisión contra el código y el emulador. | Sigue la sección 2 de cada manual. | Los pasos calzan con la pantalla. |
| ☐ | **C04** | Reglas de Firebase (RTDB) en el emulador **y** RLS de Supabase en PostgreSQL local. | `test:reglas` **5/5** y `test:sql` **570/570**. | En la demo: `npm.cmd run test:reglas` y `npm.cmd run test:sql`. | «pass 5» y «570 de 570 casos correctos». |
| ☐ | **C05** | 19 hallazgos con gravedad y cierre. | Lint, `npm audit` (0), verificador de APK, búsqueda de secretos. | — | Informe, sección 4. |
| ☐ | **C06** | Cubre ferias, seguidores, mapas, App Check y las migraciones 09 y 10. | — | — | Informe, secciones 2 y 4. |
| ☐ | **C07** | Caso de prueba de cada Esencial con su resultado. | — | — | Matriz, sección 2. |
| ☐ | **C08** | Matriz completa: reglas, funcionales y no funcionales. | — | — | Matriz, sección 1. |
| ☐ | **C09** | 69 de 71 Esenciales cumplidos. **RF-ADM-01 y RF-ADM-03 ya están implementados.** | SQL: 38 casos de la 10, más actualización y reversión. e2e. | Decide sobre RNF-MAN-03 y 04 (sección 5). | Quedan 2 parciales de proceso del repositorio, justificados. |
| ☐ | **C10** | Importantes pendientes o parciales justificados: RNF-USA-05, RNF-MAN-05, RF-CMP-08, RNF-SEG-06. | — | — | Matriz. |
| ☐ | **C11** | README desde un clon limpio, sin secretos. | Copia limpia: `npm ci`, pruebas y compilación pasan. | Paso 8 de la sección 3: persona ajena. | Llega a «58 pass», «570 de 570» y la app compila. |
| ☐ | **C12** | Dos manuales breves. | — | — | Los dos archivos. |
| ☐ | **C13** | IA como módulo desacoplado. | — | — | `ia-modulo-desacoplado.md`. |

## 3. Lo que te toca hacer

Nada aquí activa facturación: Realtime Database, App Check y Hosting son gratuitos (plan Spark).

**Decisión tomada:** camino **sin costo**, sin Google Play Console.
- El APK público usa Play Integrity.
- El proveedor de depuración existe solo en el APK privado de pruebas, que nunca se publica.

**Orden obligatorio:** la dirección de la base (paso 1) va **antes** de compilar el APK definitivo (paso 2). El APK se compila y firma **una sola vez**.

### Paso 1 · Realtime Database y `firebase.databaseUrl` (antes de compilar)

En <https://console.firebase.google.com>, en el proyecto de `firebase.properties`:
1. **Compilación → Realtime Database → Crear base de datos** → Estados Unidos → **modo bloqueado**. Copia la dirección que aparece sobre los datos (`https://…firebasedatabase.app` o `https://…firebaseio.com`).
2. Pestaña **Reglas**: pega el contenido de `firebase/database.rules.json`, el mismo que prueba el emulador, y toca **Publicar**.
3. En `App para el celular\firebase.properties` agrega:
   ```
   firebase.databaseUrl=https://LA-DIRECCIÓN-COPIADA
   ```
   El proveedor no se configura: lo decide el tipo de APK.

**Deberías ver:** la base vacía con las reglas publicadas. Sin esta línea, la app no muestra «Protección de la app» en Ajustes.

### Paso 2 · Firmar una sola vez y verificar (A01)

```powershell
cd "C:\EmprendedoresPuertoMontt\App para el celular"
```
```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore alerce-entrega.jks -alias alerce -keyalg RSA -keysize 2048 -validity 10000
```
Copia `keystore.properties.ejemplo` como `keystore.properties` y completa las contraseñas. Después compila el APK **público** (release, Play Integrity) y el **privado de pruebas** (debug, proveedor de depuración; nunca se publica):
```powershell
.\gradlew.bat assembleRelease assembleDebug
```
```powershell
cd ..
```
```powershell
npm.cmd run apk:verificar -- "App para el celular\app\build\outputs\apk\release\app-release.apk"
```
**Deberías ver:**
- «Firmado por: CN=…» con **tus** datos;
- «SHA-256 del certificado …», que anotas para el paso 3;
- «SHA-256 del archivo …», que anotas para comparar;
- «Resultado: APTO para publicar».

Si verificas el APK privado de pruebas, debe decir **NO APTO**. Es lo esperado: así no se puede publicar por error.

**No vuelvas a compilar el release:** este mismo archivo va a Hosting (paso 4) y al celular.

**Instalación en el celular:** desinstala la versión anterior, porque tiene otra firma, y luego instala `app-release.apk`.

**Comprobar que el instalado es el mismo archivo:** activa la depuración USB, conecta el celular y ejecuta:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell pm path com.company.ultrapp
```
Usa la ruta que devuelve:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" pull RUTA_QUE_DEVOLVIÓ instalado.apk
```
```powershell
Get-FileHash instalado.apk -Algorithm SHA256
```
Debe coincidir con el SHA-256 del archivo.

### Paso 3 · App Check: registrar y reunir la evidencia en la consola (A11)

1. **Seguridad → App Check → Apps** → app Android `com.company.ultrapp` → **Play Integrity** → pega el **SHA-256 del certificado** del paso 2 → Guardar. Camino sin costo: no se vincula Play Console.
2. **APK privado de pruebas, en el emulador del PC.** El privado y el público tienen firmas distintas, así que no conviven en el mismo celular.
   - Instálalo:
     ```powershell
     & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "App para el celular\app\build\outputs\apk\debug\app-debug.apk"
     ```
   - En la app: **Ajustes → Protección de la app → «Comprobar App Check» → «Ver token de depuración para la consola» → «Copiar token»**. Si no aparece, búscalo en Logcat con el filtro `DebugAppCheckProvider`.
   - Consola → App Check → app Android → ⋮ → **Administrar tokens de depuración → Agregar** → pega el token.
3. **Antes de exigir:**
   - toca «Comprobar App Check» en el emulador;
   - desde el PC, envía la solicitud externa equivalente:
     ```powershell
     curl.exe -X PUT "https://LA-DIRECCIÓN/comprobaciones_app_check/pc-1.json" -d "{\"plataforma\":\"android\",\"version\":\"pc\",\"en\":0}"
     ```
   - Consola → **App Check → APIs → Realtime Database**: la del emulador cuenta como **verificada** y la del `curl` como **no verificada** (origen desconocido). **Esta es la evidencia de «marca».**
4. Toca **Aplicar (Enforce)** y espera hasta 15 minutos.
5. **Evidencia de «rechaza» (A11):**

   | Solicitud | Resultado esperado |
   |---|---|
   | Autorizada: emulador con el APK privado → «Comprobar App Check» | «Firebase aceptó la solicitud de esta app.» |
   | Externa equivalente: `curl` con `pc-2.json` | `Permission denied` |

   Guarda una captura de cada una y de las métricas de la consola.
6. **APK público:** sin Play Console, Play Integrity no reconoce un APK instalado a mano. Su «Comprobar App Check» aparece como no verificado en las métricas y, con «Enforce», es rechazado. **No afecta a la plataforma:** solo usa esta ruta de comprobación. Además, el APK público **no** muestra el botón del token.

### Paso 4 · Hosting (W01, W02)

El plan Spark permite alojar la vitrina y bloquea los APK. En GitHub → Releases → Draft a new release, crea la etiqueta `apk-1.6-alerce` sobre el commit de esta versión, adjunta el archivo firmado `app-release.apk` del paso 2 y pulsa **Publish release**. La ruta de descarga de Firebase redirige a ese archivo. La etiqueta final de la pauta sigue siendo `entrega-8`.

1. Consola → **Hosting → Comenzar**.
2. En el PC, con el **mismo** APK del paso 2. El primer comando vuelve a verificar el APK: si no es apto, se detiene.
   ```powershell
   npm.cmd run hosting:preparar -- --apk "App para el celular\app\build\outputs\apk\release\app-release.apk"
   ```
   ```powershell
   npx.cmd firebase-tools login
   ```
   ```powershell
   npx.cmd firebase-tools deploy --only hosting --project TU-ID
   ```
3. Comprueba la descarga:
   ```powershell
   curl.exe -fL -o descargado.apk https://github.com/Diego-733/EmprendedoresPuertoMontt/releases/download/apk-1.6-alerce/app-release.apk
   ```
   ```powershell
   Get-FileHash descargado.apk -Algorithm SHA256
   ```
   Debe ser igual al SHA-256 del paso 2 y al de `https://TU-ID.web.app/apk-sha256.txt`.
4. Abre `https://TU-ID.web.app/?tienda=t_minimarket_rosa` y `https://TU-ID.web.app/?feria=ID` en el PC y en el celular.

### Paso 5 · Migraciones (W07, W08, C09)

1. Studio → SQL Editor → `supabase/09_administracion.sql` → **Run**.
2. Luego `supabase/10_emprendedoras_y_tipos.sql` → **Run**. Ambas se pueden ejecutar dos veces y no modifican datos existentes. Todas las tiendas quedan activas.
3. Comprueba:
   ```sql
   select nombre from public.migraciones_aplicadas where nombre in ('09_administracion','10_emprendedoras_y_tipos');
   ```
   Deben aparecer las dos filas.

### Paso 6 · Recuperación con buzón real (W06)

1. Desde la URL de **Hosting**: «Inicio de Sesión / Registro» → **Registro** con un correo tuyo de prueba → confírmalo si te lo piden → cierra sesión.
2. «¿Olvidaste tu contraseña? Recupérala aquí» → el correo → **«✉️ Enviar enlace de recuperación»**.
3. Abre el correo y el enlace. **Deberías ver** la web abierta en el dominio de Hosting con **«Nueva contraseña»**. Guarda una clave nueva, cierra sesión e **inicia sesión con la clave nueva**.

**Si no llega:** la instancia no tiene SMTP. Avísale al profesor.

**Si el enlace abre GitHub Pages:** la instancia no tiene la URL de Hosting en las direcciones permitidas de Auth. Completa ahí el cambio de clave y anota cuál dominio se abrió.

### Paso 7 · Push (A07)

Sección 4.

### Paso 8 · Personas ajenas (W10, C11)

- **W10:** crea una cuenta de administración para esa persona (W07). Pídele que, solo con `manual-administrador.md`, **publique y cancele** una feria de prueba. Después desactiva su cuenta.
- **C11:** otra persona clona el repositorio y sigue el README.

### Paso 9 · Commit, push y etiqueta (C01)

Después del commit con la lista de la sección 7 y de que el workflow de Pages termine en verde:
```powershell
git tag -a entrega-8 -m "Entrega 8: producto terminado"
```
```powershell
git push origin entrega-8
```

## 4. Recorrido corto (varios criterios a la vez)

Ten el emisor encendido desde el inicio:
```powershell
npm.cmd run notificar:vigilar
```

1. **C04 (antes de empezar, en el PC):** `npm.cmd run test:reglas` y `npm.cmd run test:sql`.
2. **A01, A02 (celular, APK de entrega):** abrir → «¿Cómo quieres usar la app?».
3. **A06, A10 (compradora con Google, ya con sesión):** directorio → tienda de Doña Marta (debe decir «Siguiendo») → una feria. Deja la app **en segundo plano**.
4. **W01, W02, W05, W09 (PC y celular, ventana privada, URL de Hosting):**
   - directorio → tienda → feria;
   - pie de página → aviso de privacidad;
   - no se ven ventas, contactos ni productos ocultos.
5. **W03 (admin, PC):** nueva feria → publicar → invitaciones → invitar a Doña Marta.
6. **A03, A04, A05 (celular, cerrar sesión → «Soy emprendedora» → `donamarta`):**
   - tienda → guardar;
   - producto con foto → «Lo ven todos»;
   - venta.

   Comprueba cada uno en la web.
7. **A07:** en **Ferias** (celular de Doña Marta) o en la web como Doña Marta, toca **«Participo»**.
   - La terminal muestra **«aceptados por FCM 1»**.
   - En el celular de la compradora llega el push.

   Si usas un solo celular, Doña Marta responde en la web y el celular queda con la compradora.
8. **A09:** modo avión → franja → crear producto → volver la red → llega a la web.
9. **A08:** letra del sistema al máximo → acceso, inicio de sesión y Tienda.
10. **W04:** web como Doña Marta → Mi tienda → cambio visible.
11. **W07, W08, C09 (admin):**
    - Administradores: crear, editar, desactivar y recargar.
    - Cuentas emprendedoras: editar y desactivar Doña Marta → en ventana privada su tienda ya no aparece → reactivarla.
    - Tipos de producto: crear un tipo y desactivar uno en uso.
    - **Bitácora:** muestra todo lo anterior.
12. **W06:** recuperación con el buzón.
13. **A11:** el emulador con el APK privado de pruebas es aceptado; el `curl` equivalente es rechazado; muestra las capturas de las métricas.

## 5. Decisiones tuyas antes de la etiqueta

1. **App Check en el APK público (A11): DECIDIDO, camino sin costo.**
   - El APK público queda con Play Integrity sin vincular a Google Play Console.
   - La evidencia de A11 se obtiene con el APK privado de pruebas (token de depuración, en el emulador) frente a la solicitud externa equivalente (`curl`): **aceptada frente a rechazada** con «Enforce», y **verificada frente a no verificada** en las métricas.
   - Según la documentación oficial, el APK público instalado a mano no puede ser reconocido por Play Integrity sin Play Console. Queda marcado como no verificado, lo que no afecta a la plataforma.
   - A11 queda **pendiente** hasta reunir esas capturas en la consola.
2. **Etiquetas históricas (RNF-MAN-04).** En GitHub solo está `entrega-4` (a7149e8). Candidatos con su evidencia:

   | Etiqueta | Commit | Evidencia |
   |---|---|---|
   | `entrega-5` | `e1ebbe0` (11-nov) | Merge de `entrega5/catalogo-ventas`: migración 05, alta guiada, ventas seguras |
   | `entrega-6` | `f519e60` (18-nov, día de la revisión) | Merge de la entrega 6: vitrina, comprador, difusión, mapas |
   | `entrega-7` | `adf2d49` (22-nov) o `91f84e4` (22-nov) | `adf2d49` es el último commit de `main` antes de la revisión del 23-nov (incluye el emisor). `91f84e4` es el merge de la entrega 7. |

   Etiquetar ahora deja la marca, pero no cambia que se crearon tarde: anótalo en el informe si lo haces.
3. **Revisión de PR y `main` protegida (RNF-MAN-03).** En GitHub hay un solo PR (#1, 4-nov, fusionado por su autor y sin revisiones). Proteger `main` desde ahora (Settings → Branches → regla con revisión obligatoria) es decisión del grupo. No corrige el historial: queda registrado como parcial.
4. **Google (RF-ACC-03, A07).** Lo comprobado en local es **simulado** (unitarias y emulador con Google sustituido). El ingreso **real** depende de que Google esté habilitado en Supabase Auth de la instancia: compruébalo en la demo con la cuenta de la compradora.

## 6. Qué falta antes de poner `entrega-8`

Hacer los pasos 1 a 6 y el 9 de la sección 3 (en ese orden: la base y `firebase.databaseUrl` antes de firmar) y decidir los puntos 2 a 4 de la sección 5. El punto 1 ya está decidido.

**Riesgos:**
- **W06:** SMTP y direcciones permitidas de la instancia.
- **A11:** evidencia real en la consola (token del APK privado, métricas y «Enforce»).
- **W10 y C11:** dependen de personas ajenas al grupo.

## 7. Archivos para el commit

Ver la lista del mensaje de entrega.

Nunca incluyas:
- `supabase.properties`, `firebase.properties`, `keystore.properties`, `*.jks`;
- `js/config.js`, `servicios/notificador/.env`;
- `dist/`, `.firebase/`, `node_modules/`, `test-results/`;
- los APK de `build/`, `firebase-debug.log`, `database-debug.log`.
