# Alerce Emprende — Emprendedores Puerto Montt (Grupo ALERCEIS7)

Plataforma para emprendedoras de Alerce y Puerto Montt:
- **App Android** (Kotlin + Jetpack Compose) para la emprendedora y el comprador.
- **Vitrina web** (HTML + JS, sin framework) para visitantes, compradores, emprendedoras y el administrador.
- **Backend Supabase** (PostgreSQL con RLS, Auth y Storage).
- **Avisos push** con Firebase Cloud Messaging.

Curso *Programación en Android 2026-2*. Requisitos oficiales: <https://github.com/rinostrozacl/ProyectoVinculacionConElMedio2026>.

- 🌐 **Web:** <https://diego-733.github.io/EmprendedoresPuertoMontt/> (GitHub Pages) y la dirección `…web.app` de Firebase Hosting.
- 📦 **App:** botón «Descargar app Android» en la web. Android 7.0 o superior.
- 📋 **Entrega 8** (estado, matriz, seguridad, manuales): [docs/entrega8/README.md](docs/entrega8/README.md).

---

## 1. Ejecutar desde cero

Todo lo de esta sección corre en tu computador, **sin conectarse a ninguna instancia real**. Las pruebas levantan su propio PostgreSQL y la web abre en modo demostración.

### 1.1 Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| Git | cualquiera reciente | clonar |
| Node.js | 22 o superior | pruebas, web local, servicio de avisos |
| Microsoft Edge | instalado | recorridos web automáticos (Playwright usa el Edge del equipo) |
| Android Studio | con JDK incluido (JBR) y SDK de Android (API 36 y 37) | compilar la app; su Java también lo usan el emulador de Firebase y `apksigner` |

### 1.2 Clonar e instalar

```powershell
git clone https://github.com/Diego-733/EmprendedoresPuertoMontt.git
```
```powershell
cd EmprendedoresPuertoMontt
```
```powershell
npm install
```

`npm install` trae:
- PostgreSQL 17 empaquetado para las pruebas;
- el cliente `pg`;
- Playwright;
- Leaflet.

Nada de eso se publica.

### 1.3 Pruebas (sin servidor real)

```powershell
npm run test:sql
```
Levanta un PostgreSQL local desechable, aplica las migraciones de `supabase/` y prueba las reglas de seguridad (RLS) de cada perfil: visitante, compradora, emprendedoras, administradoras y emisor. Resultado esperado: «570 de 570 casos correctos».

```powershell
npm run test:reglas
```
Reglas de Firebase Realtime Database (`firebase/database.rules.json`) en el **emulador oficial de Firebase**, con un proyecto de demostración sin cuenta. Necesita Java: si no está en el PATH, usa el de Android Studio. Resultado esperado: «pass 5».

```powershell
npm run test:web
```
Reglas del catálogo, servicio de avisos, preparación de Hosting (en una carpeta temporal) y verificador de APK.

```powershell
npm run test:e2e
```
Recorridos de la web en Edge con un Supabase sustituto.

```powershell
npm run test:hosting
```
Prepara la web en una carpeta temporal con configuración ficticia, la sirve con el **emulador de Firebase Hosting** y la recorre en Edge.

En Windows con PowerShell, si `npm` está bloqueado por la política de scripts, usa `npm.cmd` (por ejemplo, `npm.cmd run test:sql`). El escenario de actualización de `test:sql` necesita el historial completo de git: clona sin `--depth`.

### 1.4 Web local

```powershell
npm run servir
```
Abre <http://localhost:4173>. Sin `js/config.js` funciona en **modo demostración**, con datos de ejemplo y sin servidor.

Para conectarla a un Supabase propio:
1. Copia `LA PAgina (segun)/proyectode10/js/config.ejemplo.js` como `js/config.js`.
2. Completa la URL y la **clave pública anon**. Nunca la `service_role`.
3. El archivo está en `.gitignore`.

### 1.5 App Android

1. Abre la carpeta `App para el celular` en Android Studio y deja que sincronice Gradle.
2. **Sin configuración**, la app compila y se puede usar con «Tiendas de prueba (Modo Desarrollador)» en el inicio de sesión. Esto funciona solo en el APK debug y sin servidor.
3. **Con servidor:**
   - copia `supabase.properties.ejemplo` como `supabase.properties` (URL y clave anon);
   - para el push, copia `firebase.properties.ejemplo` como `firebase.properties`;
   - ambos están en `.gitignore`.
4. Compila y prueba desde la terminal:
   ```powershell
   cd "App para el celular"
   ```
   ```powershell
   .\gradlew.bat assembleDebug testDebugUnitTest lintDebug
   ```
   El APK queda en `app\build\outputs\apk\debug\app-debug.apk`. Si la terminal no encuentra Java, define `JAVA_HOME` con la carpeta `jbr` de Android Studio.
5. **APK firmado de entrega:**
   - copia `keystore.properties.ejemplo` como `keystore.properties` con tu clave (`.jks`, fuera de git);
   - ejecuta `.\gradlew.bat assembleRelease`;
   - verifícalo desde la raíz con `npm run apk:verificar -- "App para el celular\app\build\outputs\apk\release\app-release.apk"`. Debe estar firmado con tu clave, no ser depurable y no contener el proveedor de depuración de App Check.

   El APK de depuración usa el proveedor de depuración de App Check; el release (público), Play Integrity. Ver [docs/entrega8/README.md](docs/entrega8/README.md#paso-2--firmar-una-sola-vez-y-verificar-a01).

### 1.6 Servidor propio (opcional)

Para montar un Supabase nuevo, aplica en el SQL Editor y en orden:
1. `01_esquema.sql`, `02_datos_prueba.sql`, `03_migracion_fotos.sql`, `04_registro_vendedores.sql`, `05_catalogo_ventas.sql`;
2. `06_vitrina_mapas.sql`, `07_ferias_seguidores.sql`;
3. `09_administracion.sql` y luego `10_emprendedoras_y_tipos.sql`, en ese orden. Se revierten al revés: `10_revertir.sql` y luego `09_revertir.sql`.

La `08_envio_automatico.sql` es opcional y no forma parte de la entrega 8. Detalle: `supabase/README.md` y `docs/entrega5/administrador.md`.

### 1.7 Servicio de avisos push (opcional)

1. Copia `servicios/notificador/.env.ejemplo` como `.env` y complétalo:
   - una cuenta registrada en la tabla `notificadores`;
   - la cuenta de servicio de Firebase (fuera de git).
2. Enciéndelo durante la demostración (en Windows) y detenlo con Ctrl+C:
   ```powershell
   npm.cmd run notificar:vigilar
   ```

### 1.8 Publicar la web en Firebase Hosting

En el plan Spark la vitrina se sirve desde Firebase y el APK desde GitHub Releases. Antes del despliegue, publica el APK firmado como `app-release.apk` en la release `apk-1.6-alerce` (paso 4 de `docs/entrega8/README.md`).

```powershell
npm run hosting:preparar -- --apk "App para el celular\app\build\outputs\apk\release\app-release.apk"
```
```powershell
npx firebase-tools deploy --only hosting --project <id-del-proyecto>
```
El primer comando arma `dist/hosting` con la configuración pública. Con `--apk`, rechaza un APK sin firma o de prueba y publica `apk-sha256.txt`. GitHub Pages se publica solo al hacer push a `main` (`.github/workflows/deploy-pages.yml`).

---

## 2. Estructura

```text
├── App para el celular/          App Android (Kotlin, Compose). Capas: ui/ → data/ (TramaStore) → data/remoto/ (repositorios, Supabase)
├── LA PAgina (segun)/proyectode10/  Vitrina web: index.html, js/servicio.js (capa de datos), js/main.js, vitrina6.js, ferias7.js
├── supabase/                     Migraciones SQL (01–10), reversiones y pruebas RLS
├── servicios/notificador/        Emisor de avisos push (Node, sin dependencias)
├── tests/                        sql/ (PostgreSQL local), reglas/ (emulador de Firebase), web/ (unitarias), e2e/ (Edge), contrato/
├── scripts/                      preparar-hosting.js, verificar-apk.js, probar-reglas.js, ensayar-hosting.js
├── firebase/database.rules.json  Reglas de Realtime Database (ruta de comprobación de App Check)
├── docs/                         Informes y guías por entrega (docs/entrega8: entrega final)
├── AGENTS.md                     Arquitectura, reglas del proyecto y trampas conocidas
└── firebase.json                 Configuración de Firebase Hosting
```

## 3. Perfiles

- **Emprendedora:** tienda, productos con foto y opciones, ventas, ferias, mensajes y configuración.
- **Comprador/visitante:** directorio, tiendas, ferias con mapa, seguir y recibir avisos, calificar.
- **Administrador (web):** ferias (crear, publicar, cancelar, invitar, checklist, asistencia), asociaciones, cuentas, otros administradores, bitácora y navegación de la app.

## 4. Seguridad

- Nunca se suben credenciales: `supabase.properties`, `firebase.properties`, `keystore.properties`, `*.jks`, `js/config.js` y `servicios/notificador/.env` están en `.gitignore`.
- La web y la app usan solo la clave pública. El control está en RLS y en las funciones del servidor.
- Informe completo: [docs/entrega8/informe-seguridad.md](docs/entrega8/informe-seguridad.md).
