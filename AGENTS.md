# Guía para asistentes de IA (y personas nuevas en el repo)

Proyecto: **alerceshopin** (Grupo 1 ALERCEIS7) · *Programación en Android 2026-2* · Emprendedores Puerto Montt.
Plataforma para emprendedoras de Alerce: **app Android** (principal) + **vitrina web** (GitHub Pages).
Requisitos oficiales del curso: <https://github.com/rinostrozacl/ProyectoVinculacionConElMedio2026> (`docs/requerimientos-base.md`, `docs/planificacion.md`, `docs/problematicas.md`).

> Lee esta guía completa antes de cambiar código. Las secciones 6 y 7 dicen qué falta y cómo hacerlo.

---

## 1. Reglas que no se rompen

- **Nunca subas credenciales al repo** (RNF-SEG-03). La URL y la *anon key* de Supabase van en `App para el celular/supabase.properties` (está en `.gitignore`). No las copies a ningún archivo versionado, ni a esta guía. La `service_role` es del profesor: no se usa ni se pide.
- **Interfaz en español de Chile** (RNF-IDI-01): nada de voseo ("creá", "tocá"). Palabras simples para la emprendedora: "Mis productos", "Cuántos tengo", "Se acabó", "Solo yo lo veo / Lo ven todos". Evita "stock", "variante", "publicar", "borrador" en pantalla.
- **Botones de 48 dp mínimo** y respetar el tamaño de letra del celular (RNF-USA-02/03).
- **Las pantallas no llaman a la base de datos**: todo pasa por repositorios (RNF-DAT-02).
- **El servidor confirma el tipo de cuenta y crea la tienda** (RN-01). En el alta se puede elegir compradora o emprendedora; solo ADMIN requiere autorización del administrador. Ver sección 4.

## 2. Estructura

```
├── AGENTS.md                      ← esta guía
├── App para el celular/           ← app Android (Kotlin + Jetpack Compose)
│   ├── supabase.properties        ← credenciales LOCALES (no está en git)
│   ├── supabase.properties.ejemplo
│   └── app/src/main/java/com/example/trabajodea10/
│       ├── AplicacionTrama.kt     ← arranque: ClienteSupabase, Sincronizador, TramaStore
│       ├── MainActivity.kt        ← FragmentActivity (necesario para la huella)
│       ├── TramaApp.kt            ← Scaffold + elige pantalla según la ruta
│       ├── data/
│       │   ├── Models.kt          ← modelos (Producto, Tienda, Venta, Feria…)
│       │   ├── TramaStore.kt      ← estado en memoria + guardado local (JSON)
│       │   ├── SesionManager.kt   ← login, registro, huella, vitrina (reemplaza a FirebaseManager)
│       │   ├── FotoProductoStore.kt ← comprime y sube fotos
│       │   ├── FirebaseManager.kt ← VACÍO, se puede borrar
│       │   └── remoto/
│       │       ├── ClienteSupabase.kt ← HTTP (OkHttp) a Auth, PostgREST y Storage
│       │       ├── Repositorios.kt    ← Perfil, Cuenta, Tienda, Producto, Venta
│       │       └── Sincronizador.kt   ← cola de cambios "por subir" (sin señal)
│       └── ui/ (admin, ajustes, auth, componentes, emprendedora, navegacion, publico, theme)
├── LA PAgina (segun)/proyectode10/ ← vitrina web (HTML + JS sin framework)
│   ├── index.html, css/styles.css
│   └── js/main.js                 ← TODO el JS de la web (~3.500 líneas)
├── supabase/                      ← backend
│   ├── 01_esquema.sql             ← tablas, RLS, trigger, bucket (se puede re-ejecutar)
│   ├── 02_datos_prueba.sql        ← datos ficticios (4 tiendas de Alerce)
│   ├── pruebas_rls.sql            ← 26 pruebas de seguridad por perfil
│   ├── stub_local.sql             ← simula auth/storage para probar en Postgres local
│   └── README.md                  ← modelo de datos y puesta en marcha
├── index.html                     ← redirige a la vitrina (conserva ?tienda=ID)
└── .github/workflows/deploy-pages.yml ← publica la web en GitHub Pages
```

## 3. Cómo está hecha la app

- **Navegación propia** (sin Navigation Compose): `ui/navegacion/Navegacion.kt` tiene `sealed class Ruta` y `ControladorNavegacion` (una pila). `TramaApp.kt` muestra la pantalla de la ruta actual.
- **Estado**: `TramaStore` (objeto con listas `mutableStateListOf`) es la fuente para la UI. Todo cambio llama a `persistir()` (JSON en SharedPreferences) y a `Sincronizador` (servidor).
- **Flujo de un cambio** (ej. guardar producto): pantalla → `TramaStore.guardarProducto()` → guarda local → `Sincronizador.productoCambiado(id)` → si hay red, `ProductoRepositorio.guardar()`; si no, queda en la cola y se reintenta al abrir la app o al entrar.
- **Al entrar** (`SesionManager.prepararSesion`): lee `perfiles` → si es emprendedora trae su tienda, productos e historial (sin pisar lo pendiente).
- **Huella**: después de entrar con contraseña se ofrece activarla. La próxima vez, `SesionManager.ingresarConHuella()` renueva el refresh token guardado. La app no guarda datos biométricos.
- **Fotos**: `FotoProductoStore.copiarDesdeUri` comprime (1280 px, JPEG 80 %) y corrige rotación. Al sincronizar se sube a Storage `productos/<uid>/<productoId>_<ts>.jpg` y se guarda la URL pública.
- **"Modo Desarrollador"** en el login (tiendas de prueba sin servidor): solo aparece en builds debug **sin** `supabase.properties`. Una sesión de ese modo guarda todo solo en el celular.
- **HTTP**: OkHttp directo contra la API REST de Supabase (no se usa supabase-kt). Actualizar tiendas usa `PATCH` (la emprendedora no tiene permiso de INSERT en `tiendas`, así que un *upsert* fallaría). Productos y ventas usan *upsert* (`POST … ?on_conflict=id` + `Prefer: resolution=merge-duplicates`).

### Compilar

1. Copiar `App para el celular/supabase.properties.ejemplo` → `supabase.properties` y completar `supabase.url` y `supabase.anonKey` (pedir los datos al grupo; **no** están en el repo).
2. Abrir la carpeta `App para el celular` en Android Studio → Build → Generate App Bundles or APKs → Generate APKs.
3. Salida: `App para el celular/app/build/outputs/apk/debug/app-debug.apk`.

Sin `supabase.properties` la app compila y funciona solo con datos de ejemplo.

## 4. Backend Supabase (resumen; detalle en `supabase/README.md`)

| Tabla | Clave | Lectura | Escritura |
|---|---|---|---|
| `cuentas_autorizadas` | `email` | admin | admin |
| `perfiles` | `id` = `auth.users.id` | la propia persona, admin | admin |
| `tiendas` | `id` (text) | todos (sin sesión también) | dueña (`owner_id`) o admin; crear/borrar solo admin |
| `productos` | `id` (text) | lo que deja ver `estado_publico_producto` (05): todos · el resto: solo la dueña | dueña; cantidades con `guardar_producto()` |
| `ventas` | `id` (text) | **solo la dueña** (ni el admin, RN-10) | solo `registrar_venta()` (atómica e idempotente, 05) |
| `tipos_producto` | `id` | todos | admin |
| `asociaciones` | `id` | todos | admin |
| `ferias` | `id` | publicadas: todos · borradores: admin | admin (`publicar_feria`, `cancelar_feria`) |
| `tienda_asociaciones` (07) | (`tienda_id`, `asociacion_id`) | todos | admin (una tienda puede estar en varias) |
| `participaciones` | (`feria_id`, `tienda_id`) | admin y la dueña (el público usa `participantes_feria()`) | admin (`invitar_a_feria`, `registrar_asistencia`); la dueña solo con `responder_invitacion()` |
| `seguimientos`, `preferencias_avisos`, `dispositivos`, `avisos`, `calificaciones` (07) | — | solo la propia cuenta | solo por funciones del servidor (`seguir`, `guardar_consentimiento`, `registrar_dispositivo`, `calificar`…) |

- **Registro (RN-01)**: un trigger en `auth.users` crea el perfil. El alta puede solicitar cuenta `VISITANTE` o `EMPRENDEDORA`; el servidor asigna el rol y crea la tienda de quien elige emprender. `ADMIN` solo se concede desde `cuentas_autorizadas`; nunca se acepta desde metadatos enviados por el cliente. Las cuentas compradoras existentes también pueden crear su tienda desde Mi perfil.
- Funciones de apoyo: `es_admin()`, `es_duena_de(tienda_id)`.
- **Storage**: bucket público `productos`; cada usuaria escribe solo en su carpeta `uid/…`.
- Cuentas de prueba (se registran una vez desde la app o la web con cualquier contraseña de 6+): `admin@alerce.app`, `donamarta@alerce.app`, `barberia@alerce.app`, `tejidos@alerce.app`, `reposteria@alerce.app`.

### Formato de columnas que importa al conectar la web

| Columna | Formato en la base | En la web hoy |
|---|---|---|
| `tiendas.color_portada`, `color_logo`, `productos.color_foto` | número ARGB, ej. `4281153352` (= `0xFF2D3748`) | texto `"#2D3748"` |
| `productos.variantes` | `[{ "id": "v1", "nombre": "Frasco", "stock": 12 }]` | `[{ nombre, precio }]` + `stockTotal` aparte |
| `productos.precio` | un solo precio por producto | precio por variante |
| `productos.categoria` | `TEXTIL`, `CUERO`, `JOYERIA`, `COSMETICA`, `GOURMET`, `DECORACION` | texto libre |
| `productos.publicado` | boolean; si es `false` el público no lo ve (RLS) | no existe |
| `tiendas.necesidades` | JSON `{tieneStock, tieneAgendamiento, tieneVentaPorWhatsApp, tieneProductosAPedido, participaEnFerias, tieneForoComunidad}` | igual |
| `ventas.items` | `[{productoId, varianteId, nombre, detalle, precio, cantidad, fotoUrl}]`; `fecha_local` = `"AAAA-MM-DD HH:mm"` | distinto (ver POS) |

Conversión de color en JS:

```js
const aHex = n => "#" + (Number(n) & 0xFFFFFF).toString(16).padStart(6, "0").toUpperCase();
const aArgb = hex => 0xFF000000 + parseInt(hex.replace("#", ""), 16);
```

## 5. Qué está hecho (al 8-nov-2026)

- App conectada a Supabase: inicio y registro con rol asignado por el servidor, perfil con foto, recuperación de contraseña y acceso biométrico opcional que usa BiometricPrompt con credencial del dispositivo como alternativa.
- Cualquier persona puede registrarse como emprendedora y recibe su propia tienda desde el servidor. Las cuentas compradoras existentes también pueden crear su tienda desde Mi perfil. ADMIN se conserva restringido a `cuentas_autorizadas`.
- Esquema base `supabase/01_esquema.sql` y migración `supabase/03_migracion_fotos.sql`: fotos de perfil, logo de tienda, bucket `perfiles` con escritura limitada a la carpeta del usuario y función `actualizar_mi_perfil` (solo nombre/foto).
- Las fotos web y móvil se comprimen antes de subir; perfiles y fichas de negocio muestran sus imágenes cuando están disponibles.
- Web conectada a Supabase por `js/config.js`, generado en despliegue con `SUPABASE_URL` y `SUPABASE_ANON_KEY` de GitHub Actions. La clave pública queda expuesta en el cliente por diseño; RLS es el control de acceso. No incluir secretos en el repositorio.
- Mejoras de uso: “Mis productos” solo muestra los de la propia tienda; botón “Se acabó” con Deshacer; dictado por voz en nombre y descripción; guardado automático de productos nuevos; “¿Cuántos tienes?”; confirmación antes de borrar; QR y enlace por tienda (`?tienda=ID`).

## 5b. Entrega 5 (rama `entrega5/catalogo-ventas`)

- Contrato único del catálogo en SQL (`05_catalogo_ventas.sql`), web (`js/catalogo.js`) y Android (`data/ReglasCatalogo.kt`), probado con `tests/contrato/casos_estado.json`. Ver `docs/entrega5/contrato-catalogo.md`.
- La web lee y escribe solo por `js/servicio.js`; nunca guarda el catálogo en el navegador ni muestra tiendas de ejemplo fuera del modo demostración.
- Android: alta guiada (`AltaProductoScreen.kt`), vista previa, historial con filtros, configuración de ventas, sincronización por cuenta (`Sincronizador.kt`).
- Pruebas: `npm run test:sql`, `npm run test:web`, `npm run test:e2e` y `./gradlew testDebugUnitTest`. Ver `docs/desarrollo-y-pruebas.md` e informe en `docs/entrega5/README.md`.

## 5c. Entrega 6 y 7

- Entrega 6: `docs/entrega6/README.md` (vitrina, comprador, difusión, mapas).
- Entrega 7 (rama `entrega7/ferias-seguidores`): `supabase/07_ferias_seguidores.sql`, `js/ferias7.js`, `data/Ferias7.kt`, `data/FeriasStore.kt`, `avisos/AvisosCelular.kt`, `servicios/notificador/`. Contrato, matrices de los 70 criterios, bloqueos y procedimiento de aplicación: `docs/entrega7/README.md`.
- Ferias: el servidor es la única fuente. La lista local `TramaStore.ferias`/`MockData` y `FERIAS_COMUNALES` de la web ya no se muestran a la emprendedora ni al público.
- Avisos: los crea solo el servidor; los envía `servicios/notificador` (cuenta en la tabla `notificadores`, nunca `service_role`).

## 5d. Entrega 8 (producto terminado)

- Estado de los 35 criterios, matriz, informe de seguridad, manuales e IA: `docs/entrega8/`.
- `supabase/09_administracion.sql`: otras cuentas de administración (`listar_admins`, `crear_admin`, `editar_admin`, `cambiar_estado_admin`) y bitácora por disparadores (`ver_bitacora`). Independiente de la 08.
- App: `data/remoto/EstadoConexion.kt` (franja «Sin conexión»), `seguridad/ProteccionApp.kt` (App Check sobre Realtime Database), firma con `keystore.properties` (fuera de git), botones comunes con altura mínima de 48 dp que crece con la letra.
- Web: aviso de privacidad (`#modal-privacidad`, `?privacidad=1`); con servidor, el panel admin no muestra datos de ejemplo; supabase-js con versión fija y SRI.
- Firebase Hosting: `firebase.json` + `npm run hosting:preparar` (`dist/hosting`, fuera de git); ensayo con emulador: `npm run test:hosting`.
- `supabase/10_emprendedoras_y_tipos.sql` (después de 09): `listar_emprendedoras`, `editar_emprendedora`, `cambiar_estado_emprendedora` (tiendas.activa: oculta y bloquea), `guardar_tipo_producto`, `eliminar_tipo_producto` (no si está en uso), `uso_tipos_producto`.
- App Check: `src/debug/.../FabricaAppCheck.kt` (depuración) y `src/release/...` (Play Integrity); reglas en `firebase/database.rules.json` probadas con `npm run test:reglas`; `npm run apk:verificar` antes de publicar.

## 6. Qué falta / configuración externa

1. En Supabase, seguir `docs/entrega5/administrador.md`: diagnóstico de solo lectura, respaldo, 03/04 si faltan y `05_catalogo_ventas.sql`. Para una base nueva (de prueba): 01, 02, 03, 04 y 05.
2. En Supabase Auth, desactivar la confirmación de correo si se requiere entrar inmediatamente después del registro; si permanece activa, el flujo informa que se debe confirmar el correo.
3. Configurar los secretos `SUPABASE_URL` y `SUPABASE_ANON_KEY` en GitHub Actions y `supabase.properties` local para conectar web y Android a la instancia. Sin esa configuración solo se pueden compilar y usar pantallas sin servicios remotos.
4. Aplicar `supabase/07_ferias_seguidores.sql` (con respaldo y ensayo; ver `docs/entrega7/README.md`).
5. Activar Google en Supabase Auth (RN-07, E6-A08, E7-A12) y crear el proyecto Firebase y la cuenta del notificador para el push (E7-A13, E7-W18 con la página cerrada).
6. Borrar `data/FirebaseManager.kt` (vacío), cuando se realice limpieza del código antiguo.

## 7. Conexión web a Supabase

La web inicializa Supabase solo cuando `js/config.js` define URL y anon key. El workflow escribe ese archivo desde los secretos `SUPABASE_URL` y `SUPABASE_ANON_KEY`; el archivo generado está ignorado por Git. La clave anon se sirve al navegador, por lo que todas las operaciones deben quedar protegidas por RLS. El alta de vendedor solicita `tipo_cuenta=EMPRENDEDORA`; el trigger del servidor asigna el rol y crea su tienda. `ADMIN` sigue reservado a `cuentas_autorizadas`. Las imágenes se suben a los buckets `productos` y `perfiles`, bajo la carpeta del ID autenticado.
## 8. Probar las reglas de seguridad

`npm install && npm run test:sql` levanta un PostgreSQL local desechable (nunca la instancia del curso) y ejecuta RLS, ventas, concurrencia, actualización y reversión. Vuelve a ejecutarla al modificar el esquema.

## 9. Trampas conocidas

- Las carpetas tienen espacios y paréntesis (`App para el celular`, `LA PAgina (segun)`): usa comillas en la terminal.
- `HttpURLConnection` no soporta `PATCH`; por eso la app usa OkHttp.
- Las pruebas unitarias se compilan junto con el APK: si renombras clases, actualiza `app/src/test/…`.
- Nunca escribas cantidades directamente en `productos` desde un cliente nuevo: usa `guardar_producto()` con `stockBase`; el trigger `proteger_producto` ignora cantidades enviadas por otra vía.
- Las ventas solo se registran con `registrar_venta()`; reintenta siempre con el mismo `id`.
- `index.html` de la raíz redirige a la web; la URL pública es `https://diego-733.github.io/EmprendedoresPuertoMontt/`.
