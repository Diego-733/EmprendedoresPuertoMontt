# Asistente Alerce y lectura en voz alta — etapas 1 y 2

> **26-nov-2026 — evolución integral (etapa 3 en lo esencial):** ventas, cantidades, edición, ferias y consultas por voz o texto con confirmación; modo sencillo; centro de ayuda; fotos; asistente web; navegación que ya no vuelve a Inicio al girar. Ver [evolucion-integral.md](evolucion-integral.md).
>
> **26-nov-2026 — revisión crítica hecha.** Se encontraron y corrigieron 22 problemas, uno de ellos un cierre de la app en Android que las pruebas del computador no detectaban. Ver [revision-etapas-1-2.md](revision-etapas-1-2.md). Propuesta (no implementada) de la etapa 3: [propuesta-etapa-3.md](propuesta-etapa-3.md).

Estado: **local, sin commit**, a la espera de revisión. No se publicó el APK, no se desplegó la web, no se tocó Supabase real ni se activó ningún servicio externo.

## 1. Auditoría inicial (antes de estos cambios)

| Tema | Lo que había | Lo que faltaba |
|---|---|---|
| Ayuda | «Guíame paso a paso» solo para crear productos (Android `GuiaProducto` + web `guiaProducto.js`), con reglas de palabras | Ayuda para el resto de las tareas y un lugar único para preguntar |
| Voz de entrada | Dictado del sistema (`RecognizerIntent`, es-CL) en nombre del producto, buscador y guía; web con Web Speech | — (se reutiliza) |
| Voz de salida | Nada | Leer guías y respuestas en voz alta |
| Lenguaje natural | Reglas cortas para tres intenciones del alta | Números en palabras, «lucas», precio total o unitario, cantidades, ambigüedad, correcciones |
| Accesibilidad Android | Botones de 48 dp en general, textos en `sp` | Botón «Quitar» del carrito de 36 dp; aviso de Inicio marcado como título; barra inferior cortada con letra al 200 % («Pro…», «Feri…») |
| Accesibilidad web | Guía con botones de 48 px, foco y `aria-live` | Lectura en voz alta |
| IA | Diseño desacoplado en `docs/entrega8/ia-modulo-desacoplado.md`, inactiva (RN-13) | — (no se activa) |

Líneas base antes de tocar nada: Android 78/78 · web 66/66 · e2e 92/92 · hosting 5/5 · SQL 570/570 · lint 0 errores (78 advertencias).

## 2. Qué se hizo

### Etapa 1 — Lectura en voz alta y accesibilidad

**Android**
- `voz/LectorVoz.kt`: usa la voz del propio celular (`TextToSpeech`). Elige es-CL, luego es-419, es-US, es-MX, es-ES y por último «es». Sin voz en español, el botón no aparece y todo sigue por escrito.
  - Lee solo al tocar «🔊 Escuchar». La lectura automática es opcional y viene **apagada**; nunca habla sola con TalkBack activo.
  - Se calla al salir de la app (`onStop`), al cambiar de pantalla y **antes de abrir el micrófono**, para que el dictado no escuche a la app.
  - Ofrece tres velocidades (Lenta, Normal y Rápida) y lee «$12.000» como «12.000 pesos», sin deletrear comillas ni íconos.
- `voz/BotonEscuchar.kt`: botón de 48 dp que cambia entre «🔊 Escuchar» y «⏹ Detener».
- Guía paso a paso: suma «Escuchar este paso» con frases naturales (`GuiaProducto.LOCUCIONES`). En la revisión también lee lo que se va a guardar.
- Ajustes › Accesibilidad: tarjeta «Lectura en voz alta» con el interruptor, la velocidad, «Probar la voz» y el aviso para TalkBack.
- Manifiesto: consulta `TTS_SERVICE`, que exige Android 11 o superior. No se pide ningún permiso nuevo.
- Correcciones de accesibilidad:
  - El botón «Quitar» del carrito pasó de 36 a 48 dp y ahora dice qué producto quita.
  - El aviso de Inicio dejó de marcarse como título.
  - Las etiquetas de la barra inferior crecen con la letra y, si no caben, se achican juntas en vez de cortarse.

**Web**
- `js/lecturaVoz.js` (`speechSynthesis`) agrega «🔊 Escuchar este paso / ⏹ Detener» a la guía.
  - El botón no aparece si el navegador no tiene lectura en voz alta o no tiene voz en español.
  - Se calla al cambiar de paso, al cerrar la guía y antes del dictado (`busquedaVoz.js`).

### Etapa 2 — Asistente Alerce (Android)

- Entrada opcional en Inicio: «Asistente Alerce · Dime qué necesitas: te enseño o te ayudo a hacerlo». No reemplaza ningún botón y nada obliga a usarlo.
- **Enséñame** (`TemasAyuda`): explica 11 tareas con los nombres reales de los botones, con un botón para ir a la pantalla.
  - Los temas de producto ofrecen además «Guíame en la pantalla», que reutiliza la guía existente sin duplicarla.
- **Ayúdame a hacerlo**, crear un producto (prioridad):
  - Ejemplo: «Quiero agregar un producto. Es un chaleco de lana hecho a mano, cuesta quince mil pesos y tengo cuatro disponibles» da Nombre «Chaleco de lana», Descripción «Chaleco de lana hecho a mano», $15.000 y 4.
  - Si falta el nombre o el precio, pregunta; la cantidad la pregunta una vez y acepta «después».
  - Ante un número dudoso («veinte»), pregunta «¿Son $20 o $20.000?».
  - Ante un precio total, pregunta el precio de cada uno.
  - Acepta correcciones por voz («no, el precio es doce mil», «tengo cinco», «cambia el nombre a…») o a mano en la tarjeta «Tu producto (aún no se guarda)».
  - «Continuar» llena el **borrador de siempre** y abre el **formulario de siempre** con la guía encendida. Ahí se agrega la foto, se pasa por las mismas validaciones y se guarda con «Guardar en Mis productos».
  - Nunca pisa un producto a medio hacer sin preguntar.
- **Consultas** (solo lectura, solo esta cuenta, estado local): cuánto vendí este mes (dice cuántas ventas faltan por subir) y la próxima feria con las invitaciones por responder.
- **Navegación directa:** mis productos, ferias, compartir (QR), WhatsApp de la tienda, mensajes e historial.
- **Ambigüedad:** «quiero vender gorros» ofrece «Subirlo a mi tienda» o «Anotar una venta que hice». No adivina.
- **Cambios de datos** (venta, se acabó, me quedan, precio, ocultar): en esta etapa **no se ejecutan**. El asistente explica cómo se hace y ofrece ir a la pantalla, donde la persona confirma con los botones de siempre.
- Privacidad:
  - El micrófono se abre solo al tocarlo. El diálogo del sistema sirve de indicador de que está escuchando.
  - Alerce no guarda audio, y la conversación vive solo mientras la app está abierta.
  - Funciona sin dictado (solo texto), sin voz (solo texto) y sin señal (todo es local).

### Tabla de órdenes por voz (etapa 2)

| Lo que dice | Qué hace | ¿Cambia datos? |
|---|---|---|
| «Quiero agregar un chaleco de lana a quince lucas, tengo cuatro» | Prepara el producto y lo muestra para revisar | No, hasta «Guardar en Mis productos» |
| «Vendí dos chalecos» | Explica y ofrece ir a «Venta» | No (la venta se confirma en «Venta») |
| «Se acabó el chaleco» / «Me quedan tres» / «Cambia el precio» / «Oculta la bufanda» | Explica dónde hacerlo y ofrece ir a «Mis productos» | No |
| «¿Cuánto vendí este mes?» | Responde con las ventas locales de la cuenta | No |
| «¿Cuándo es mi próxima feria?» | Responde con la misma feria que muestra Inicio | No |
| «Quiero participar en una feria» / «Mis ferias» | Lleva a «Ferias» | No |
| «Quiero compartir mi tienda» | Abre el QR de la tienda | No |
| «Cambiar mi teléfono» | Lleva a «Tienda» y explica | No |
| «Mis mensajes» / «Mis productos» | Abre la pantalla | No |
| «¿Cómo…?» (incluye «¿cómo borro un producto?» y «¿cómo agrando la letra?») | Enséñame: pasos numerados y botón para ir | No |
| «Borra el gorro» / «Me llegaron diez gorros» | Explica dónde se hace y dice que no lo hace él | No |
| A mitad de un producto: «no, quince mil», «perdón, cuatro», «cambia el precio», «es una bufanda», «otro producto…» | Corrige el dato según el contexto, pregunta el valor nuevo o pregunta antes de dejar de lado el producto anotado | No |
| «Cancelar», «chao», «ya no quiero», «no importa» | Borra lo anotado (nada estaba guardado) | No |

## 3. Decisiones

1. **Reglas locales, no IA.** El intérprete (`InterpreteAlerce`, `NumerosEs`) es Kotlin puro: gratis, sin internet, con pruebas, y siempre da la misma respuesta. **No se presenta como IA.**
2. **Acciones cerradas y tipadas.** El asistente solo puede producir `Navegar(destino)`, `AbrirFormularioProducto(datos)` o `IniciarGuia`.
   - No hay SQL, no usa la base de datos y no esquiva repositorios.
   - Los productos se guardan con el mismo `guardarProducto` (RPC `guardar_producto`, RLS) al tocar el botón de siempre.
3. **Las ventas siguen solo en «Venta».** `registrar_venta` (atómica e idempotente) no se tocó ni se llama desde el asistente.
4. **Lectura automática apagada por defecto.** En una feria, que la app hable sola sorprende y expone datos; se activa en Ajustes.
5. **Simple y opcional.** El «modo simple» se evaluó y queda para la etapa 3. Hoy Inicio ya muestra las dos tareas diarias primero y el asistente es una tercera opción.

### Si más adelante se quiere IA generativa (no activada)

Encaja en `docs/entrega8/ia-modulo-desacoplado.md`: una interfaz `AsistenteProducto` con `AsistenteNulo` por defecto, el interruptor `ia_asistencia` en `configuracion` (INACTIVA) y una Edge Function en el servidor (la clave nunca va en la app). La IA solo podría **proponer** una de las mismas acciones tipadas, y la persona revisa como hoy.

| Opción | Costo aproximado | Riesgos |
|---|---|---|
| Reglas locales (actual) | $0 | Frases muy libres pueden no entenderse (responde «No entendí» y ofrece opciones) |
| Modelo pequeño en el celular | $0 por uso; APK más pesado | Celulares de gama baja lentos; precisión variable |
| API de un proveedor por Edge Function | Pago por uso; requiere presupuesto | Datos salen a un tercero (aviso de privacidad), latencia, sin señal no funciona, respuestas inventadas (por eso solo propone y nunca pone precios) |

## 4. Trazabilidad (ID existentes de `docs/entrega8/matriz-requisitos.md` y `ia-modulo-desacoplado.md`)

| Requisito | Cómo lo apoya este trabajo | Prueba |
|---|---|---|
| RF-PRO-01 / RF-PRO-02 | Alta de producto por voz o texto que llena el mismo formulario | `AsistenteAlerceTest`, `AsistenteVozYBorradorTest`, emulador (capturas 03–08) |
| RF-PRO-07 | «Tengo cuatro» va a «¿Cuántos tienes?» | `prepara el borrador…`, captura 06–07 |
| RF-PRO-09 | El asistente explica «Se acabó / Deshacer» sin ejecutarlo | `operacionesQueCambianDatosNoSeEjecutanSolas` |
| RF-VEN-01 | Las ventas siguen solo por `registrar_venta`; el asistente no las ejecuta | Suite SQL 570/570, `Entrega5Test` |
| RF-FER-08 / RF-FER-09 | «Próxima feria» e invitaciones por responder; ir a «Ferias» | `consultasUsanSoloLosDatosDeLaCuenta` |
| RF-COM-02 | «Compartir mi tienda» abre el QR | Emulador |
| RF-CFG-02 / RF-CFG-03 | Ir a «WhatsApp de tu tienda» y a «Mensajes recibidos» | `navegacionDirectaSoloParaVerCosas` |
| RNF-USA-01 / 02 / 03 | Lenguaje simple, botones de 48 dp, letra al 200 % (barra inferior, guía web) | e2e `guia.spec.js`, emulador (capturas 12) |
| RNF-USA-04 | Nunca pisa un borrador sin preguntar | `nunca pisa un producto a medio hacer`, captura 13 |
| RNF-USA-05 | **Sigue pendiente**: falta probar con emprendedoras reales | — |
| RNF-IDI-01 | Español de Chile, sin voseo ni «stock» | `textosSinVoseoNiJerga`, prueba de locuciones |
| RNF-REN-02 | Asistente y lectura funcionan sin señal (todo local) | Diseño; ver límites |
| RNF-DAT-02 / RNF-MAN-02 | La pantalla usa el estado y los repositorios de siempre; intérprete desacoplado | Revisión de código |
| RNF-PRI-01 / RNF-PRI-03 | Micrófono solo al tocar, sin audio guardado, solo datos de la cuenta | Revisión de código, aviso en pantalla |
| RN-13, RF-IA-01..03, RF-ADM-05 | **Sin cambios**: no se activó IA; queda la vía desacoplada | — |

## 5. Pruebas ejecutadas (resultados reales)

| Suite | Resultado |
|---|---|
| Android unitarias (`testDebugUnitTest`) | **163/163** (135 + 20 `AsistenteOperacionesTest` + 4 `NavegacionPersistenteTest` + 1 `ContratoAsistenteTest` + 3 `RevisionFotoTest`; una prueba anterior se actualizó a la venta preparada) |
| Android lint | 0 errores, 78 advertencias (las mismas de antes) |
| Web unitarias (`npm run test:web`) | **76/76** (+5 `asistenteWeb.test.js`, con el contrato compartido) |
| E2E (`npm run test:e2e`) | **96 pasan** (+2 del asistente web); las 5 omitidas son las de hosting, que corren aparte |
| Hosting (`npm run test:hosting`) | 5/5 |
| SQL (`npm run test:sql`, PostgreSQL local y desechable) | 570/570 |
| Reglas (`npm run test:reglas`, emulador local) | 5/5 |
| APK release (`scripts/verificar-apk.js`) | 1.7.0-asistente (9), firmado (v2), mismo certificado, SHA-256 `AD5FB842…F4BC7`, APTO |

Revisión del 26-nov en el emulador:
- Frases nuevas, autocorrección, pregunta a mitad de un producto, volver atrás y entrar de nuevo.
- Giro con el aviso del borrador abierto, cerrar sesión y volver a entrar (la conversación empieza de cero).
- Una sola lectura a la vez y el micrófono la calla.
- Pantalla de 360 dp con letra al 130 % y letra al 200 %.

Capturas 14–16.

En la primera ronda de pruebas en el emulador se encontraron y corrigieron dos errores:
- «a ochenta lucas» quedaba dentro del nombre.
- Enter agregaba una línea en vez de enviar.

Ambos tienen prueba.

## 6. Límites y lo que necesita un celular físico

- Dictado con el micrófono real, permiso de micrófono negado y voz es-CL instalada: probarlo en un celular. El emulador escribe con teclado y tiene voz es-US.
- TalkBack: el código no lee solo cuando TalkBack está activo, pero falta probarlo con TalkBack de verdad. El emulador lo tiene, pero el doble toque no se puede automatizar de forma confiable.
- Sonido real: el emulador corre sin audio; se verificó el estado de la lectura, no lo que se oye.
- Problema anterior a este trabajo: girar el celular o cambiar la letra del sistema devuelve toda la app a Inicio (ver la propuesta, §7).
- El dictado de Google puede usar internet; sin señal, el asistente funciona escribiendo.
- El APK release se generó pero no se probó instalado: usa el servidor real y su firma no coincide con la versión de depuración del emulador.
- La web no tiene el asistente (etapa 2 solo en Android); la web tiene la lectura en voz alta de la guía.

## 7. Propuestas para las etapas 3 y 4 (no iniciadas)

Diseño detallado de la etapa 3: [propuesta-etapa-3.md](propuesta-etapa-3.md).

- **Etapa 3:**
  - Venta precargada en «Venta» con «Vendí dos chalecos», que la persona confirma.
  - «Se acabó», «me quedan» y precio por voz con resumen y confirmación explícita, siempre a través de `guardar_producto` con `stockBase`.
  - Elegir entre productos de nombre parecido.
  - Modo simple.
  - Asistente en la web.
- **Etapa 4:** IA opcional detrás de la interfaz desacoplada, solo si la administración la habilita y hay presupuesto.

## 8. Archivos

Android (nuevos): `data/asistente/NumerosEs.kt`, `InterpreteAlerce.kt`, `TemasAyuda.kt`, `SesionAsistente.kt`, `voz/LectorVoz.kt`, `voz/BotonEscuchar.kt`, `ui/asistente/PantallaAsistente.kt`, `test/AsistenteAlerceTest.kt`, `test/AsistenteVozYBorradorTest.kt`, `test/AsistenteRevisionTest.kt`.
Android (modificados): `AndroidManifest.xml`, `MainActivity.kt`, `AplicacionTrama.kt`, `TramaApp.kt`, `ui/navegacion/Navegacion.kt`, `data/TramaStore.kt`, `data/GuiaProducto.kt`, `ui/emprendedora/GuiaProductoUi.kt`, `ui/emprendedora/AltaProductoScreen.kt`, `ui/emprendedora/DashboardScreen.kt`, `ui/emprendedora/PosScreen.kt`, `ui/ajustes/AjustesScreen.kt`, `ui/componentes/Componentes.kt`.
Web (nuevo): `js/lecturaVoz.js`, `tests/web/lecturaVoz.test.js`. Web (modificados): `js/guiaProducto.js`, `js/busquedaVoz.js`, `js/main.js`, `index.html`, `tests/e2e/guia.spec.js`.
Capturas: `docs/experiencia/capturas/asistente/`.
