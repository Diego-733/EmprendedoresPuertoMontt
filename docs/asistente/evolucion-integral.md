# Evolución integral del Asistente Alerce, accesibilidad y experiencia (26-nov-2026)

Todo está **local y sin commit**. No se tocaron RLS, reglas de negocio, `guardar_producto`, `registrar_venta`, Supabase real, Firebase ni producción. Las funciones de siempre siguen iguales, y quien no quiera usar el asistente no pierde nada.

## 1. Estado inicial (antes de este trabajo)

| Suite | Resultado |
|---|---|
| Android unitarias | 135/135 |
| Android lint | 0 errores, 78 advertencias |
| Web unitarias | 71/71 |
| E2E | 94 pasan (+5 de hosting omitidas, que corren aparte) |
| Hosting | 5/5 |
| SQL | 570/570 |
| Reglas | 5/5 |

**APK de referencia:** 1.6.1-experiencia (8), SHA-256 `3131FB784CF4AD885AF8F2AABB1D63B49C6A214EAA0FC23086EB5BEDC1938F57`.

## 2. Problemas encontrados y corregidos

| Problema | Origen | Corrección |
|---|---|---|
| Al girar el celular o cambiar la letra, toda la app volvía a Inicio | Anterior | La pila de pantallas se guarda con `rememberSaveable` como nombres de pantalla e ids, ligada a la cuenta y al rol. Si cambió la sesión no se restaura, y se quitan las pantallas sin sentido (pago sin productos, producto borrado). Verificado en el emulador: el pago sigue abierto y con el medio elegido después de girar. |
| La foto recién tomada se podía perder si Android recreaba la pantalla mientras la cámara estaba abierta | Anterior | El destino de la foto y el archivo temporal se guardan con `rememberSaveable`. |
| Lo último escrito en «Añadir producto» (menos de 300 ms) se podía perder al girar | Anterior | Se guarda el borrador al cerrar o recrear la pantalla. |
| El medio de pago y el lugar de la venta se perdían al girar | Anterior | `rememberSaveable` en «Confirmar pago». |
| «Transferencia» se partía en «Transfere / ncia» | Anterior | Los medios de pago se acomodan en varias líneas. |
| En los interruptores de Ajustes solo respondía el interruptor pequeño, y TalkBack leía el título aparte | Anterior | Toda la fila es el interruptor (`toggleable`, rol Switch) y TalkBack la lee como un solo control. |
| Las estrellas de calificación no decían si estaban marcadas | Anterior | Se agregó `stateDescription`. |
| La ayuda de ventas decía «Toca los productos… cada toque suma uno» y «Confirmar pago» | Etapa 2 | Se corrigió con la pantalla real: elegir cantidad y «Agregar», luego «Confirmar y generar PDF». |
| Varias regresiones que introduje durante este trabajo y detecté antes de entregar | Esta entrega | Todas corregidas:<br>• web: desborde lateral a 200 % por reutilizar la clase del micrófono del buscador;<br>• web: palabras partidas en los botones de opción;<br>• web: saludo con opciones de otro rol;<br>• Android: mensaje «se está subiendo» en una cuenta sin servidor;<br>• Android: botones de confirmación fuera de la pantalla a 200 %. |

## 3. Qué se implementó

### Arquitectura del asistente (Android)

```
voz (dictado del sistema) o texto ─► NumerosEs (normalización y números chilenos)
  ─► MotorInterpretacion (hoy MotorLocal = InterpreteAlerce: puntaje por señales + clase de pedido)
  ─► ValidadorResultado (rangos y lista cerrada; protege de un motor futuro)
  ─► SesionAsistente (contexto: crear producto, cambio de tema, cancelar, «seguir»)
      └► GestorOperaciones (ventas, cantidades, quién lo ve, edición, ferias, consultas)
            └► ResolutorProductos (productos reales de la tienda; nunca elige si hay dudas)
  ─► Accion tipada + Confirmacion (de un solo uso)
  ─► EjecutorAsistente (UI) ─► TramaStore / FeriasStore (las funciones de siempre)
  ─► Resultado real: guardado solo en el celular, subiendo, sin señal, rechazado
```

- **Clase de pedido:** cada intención es CONSULTA, AYUDA, ACCION, NAVEGACION o CONVERSACION. Una pregunta resta puntaje a las acciones: «¿cuántos gorros vendí?» es una consulta aunque contenga «vendí».
- **Motor intercambiable:** `MotorInterpretacion` permite cambiar el motor sin tocar la ejecución. Cualquier motor solo puede devolver una intención de la lista cerrada con datos tipados, que pasan por `ValidadorResultado`.
- **Confirmación de un solo uso:** cada tarjeta tiene un número. Un doble toque o un «sí» repetido no ejecutan dos veces, y un número viejo o de otra sesión se rechaza.

### Lo que el asistente puede hacer ahora

| Capacidad | Cómo | Operación que reutiliza |
|---|---|---|
| Crear productos | Lo de antes (etapas 1 y 2) | Borrador + «Guardar en Mis productos» (`guardar_producto`) |
| **Registrar ventas** | Varias líneas, cantidad, opción (talla), precio dicho, medio, lugar. Correcciones («no, eran cuatro», «fue en efectivo»). Resumen y confirmación; luego llena el **carrito de «Venta»** y preselecciona el medio. | Se cobra y confirma en «Venta» → `registrarVenta` → `registrar_venta` (atómica, idempotente, cantidades) |
| **Cantidades** | «Me quedan cinco», «tengo diez disponibles», «llegaron tres más», «se acabó», «disponible otra vez» | `cambiarCuantosQuedan` (conserva `stockBase`), `marcarAgotado`, `cambiarDisponibilidad`. Si la cantidad cambió entre preparar y confirmar, **no se aplica** (campo `conocida`). |
| **Quién lo ve** | «Oculta los chalecos», «vuelve a mostrar las bufandas» (una o todas) | `togglePublicado`, solo en los que cambian |
| **Editar** | Precio con valor, descripción o foto | Abre el formulario de siempre con el cambio puesto; se guarda en el formulario. Nunca pisa otro borrador. |
| **Borrar** | Solo explica el camino | El de siempre, con su confirmación |
| **Ferias** | Invitaciones, dónde queda, cómo llegar (mapa), «participo», «no podré asistir» | `FeriasStore.responder`, igual que los botones. Se informa confirmada, pendiente sin señal o rechazada. Las confirmadas no se cambian, porque la app no lo permite. |
| **Consultas** | Ventas del mes, ventas de un producto, productos, cuáles se acabaron, datos de la tienda | Solo estado local de la cuenta |
| **Conversación** | «No quiero registrar una venta», «mejor cancela todo», «quiero hacer otra cosa», preguntas intermedias, «seguir», «este producto», «los de lana», «el segundo» | — |

### Otras mejoras

- **Modo sencillo** (Ajustes › Accesibilidad):
  - Inicio con 7 accesos grandes: hablar con mi asistente, registrar una venta, agregar un producto, ver mis productos, ver mis ferias, compartir mi tienda y necesito ayuda.
  - Las invitaciones pendientes se siguen mostrando arriba; la barra de abajo y «Ver el inicio completo» llevan a todo lo demás.
  - Se guarda por cuenta, en las preferencias de la emprendedora.
- **Centro de ayuda por tareas:** usa los mismos temas que «Enséñame», sin contenido duplicado.
  - Cada tema tiene pasos, «Escuchar», un botón a la pantalla real y «Guíame paso a paso» cuando existe.
  - Temas nuevos: cambiar los datos de la tienda, recuperar el acceso, modo sencillo y ventas pendientes.
- **Fotos:**
  - «Consejos para una buena foto», que se pueden leer y escuchar.
  - Aviso local si la foto está muy oscura o muy clara. Solo aconseja, sin bloquear ni usar servicios externos. El desenfoque no se revisa porque no es confiable.
- **Botones de confirmación fijos** sobre el campo de texto, siempre visibles con letra grande.
- **Asistente web** (`js/asistenteWeb.js`), dentro de «Ayuda»; no flota sobre la página.
  - Ayuda por tareas con los nombres reales de la web y navegación a lo que existe.
  - «Añadir producto» llenado con nombre y precio, sin guardar.
  - Búsqueda en la vitrina para quien compra; respeta el rol.
  - Reutiliza el dictado y la lectura en voz de la web, y la letra grande.
  - `main.js` solo recibe un bloque que le pasa las funciones de siempre.
- **Contrato compartido:** `tests/contrato/asistente_frases.json` tiene 23 frases con intención, clase, tema, nombre y precio. Lo prueban Android (`ContratoAsistenteTest`) y la web (`asistenteWeb.test.js`).
- **Versión:** 1.7.0-asistente (código 9). Mismo applicationId y misma firma, así que actualiza la app instalada.

## 4. Decisiones de diseño y accesibilidad

- **No se agregó un tema de alto contraste en Android.**
  - El contraste medido ya cumple WCAG AA y más: claro, 5,6–7,8:1; oscuro, 7,4–9,9:1.
  - Muchas pantallas usan colores fijos, así que un tema paralelo quedaría a medias.
  - Android tiene su propio «texto de alto contraste». La web ya tenía su botón «Contraste».
- **La venta usa el precio del catálogo,** como «Venta». Si se dijo otro precio, se avisa y se explica cómo cambiarlo; no se inventan descuentos.
- **El asistente no confirma ventas.** Prepara el carrito y la persona cobra en «Venta». Así se conservan intactas las garantías de `registrar_venta`.
- **No se elige por parecido.** Si un producto no contiene todas las palabras dichas, se muestran opciones con foto, precio y cuántos quedan.
- **Sin escucha permanente.** El micrófono se abre solo al tocarlo, y la lectura se calla al abrirlo, al salir de la pantalla o de la app.

## 5. Trazabilidad (ID reales de `docs/entrega8/matriz-requisitos.md`)

| Requisito | Aporte | Prueba |
|---|---|---|
| RF-PRO-01, 02, 05 | Crear y editar con el formulario de siempre; opciones (tallas) | `AsistenteOperacionesTest`, emulador |
| RF-PRO-04 | Editar: abre el formulario con el cambio; borrar solo por el camino de siempre | `editar precio descripcion y foto…` |
| RF-PRO-07, 09, 10 | Cantidades, «Se acabó», disponible otra vez, varios a la vez | `fijar y sumar…`, `se acabaron las bufandas…`, emulador (captura 19) |
| RF-VEN-01, 02, 03, 05 | Venta preparada → «Venta» → `registrar_venta`; precio del catálogo; feria del día preseleccionada | `vender pregunta cual…`, `varias lineas…`, emulador (17–18), SQL 570 |
| RF-FER-08, 09 | Responder invitaciones con la misma operación; dónde queda y cómo llegar | `participar en la feria…` |
| RF-COM-02 | Compartir la tienda (QR) | Contrato, emulador |
| RF-CFG-02, 03 | WhatsApp y datos de la tienda; mensajes | Contrato, `consultas…` |
| RF-ACC-06 | Sin cambios en el ingreso | e2e |
| RNF-USA-01 a 04 | Lenguaje simple, 48 dp, letra al 100/130/200 %, borradores protegidos, estado al girar | Emulador (20–24), `NavegacionPersistenteTest`, e2e «Sin desplazamiento lateral» |
| RNF-USA-05 | **Pendiente:** probar con emprendedoras reales | — |
| RNF-IDI-01 | Español de Chile sin voseo | `textosSinVoseoNiJerga` |
| RNF-REN-02 | Todo funciona sin señal (local) y se dice si quedó pendiente | `EjecutorAsistente.estadoGuardado` |
| RNF-DAT-02, RNF-MAN-02 | Sin acceso directo a datos; motor intercambiable | Revisión de código, `un motor externo no puede colar datos…` |
| RNF-PRI-01, 03, RNF-SEG-03 | Micrófono solo al tocar, sin audio guardado, conversación borrada al cerrar sesión, sin claves | Emulador (cerrar sesión), revisión del diff |
| RN-13, RF-IA-01 a 03, RF-ADM-05 | **Sin cambios:** no se activó IA (sección 8) | — |

## 6. Ejemplos de conversación (probados)

**Una venta con opciones y corrección del medio de pago:**
1. «Vendí dos chalecos de lana a quince mil cada uno en efectivo»
2. El asistente muestra opciones («Chaleco de Lana Chilota · $38.000 · quedan 6», «Chaleco de lana · $12.000 · quedan 4»).
3. Toca «Chaleco de lana».
4. Tarjeta: 2 × $12.000 = $24.000, Pago: Efectivo, con el aviso «Dijiste $15.000…».
5. «Ir a cobrar» → «Venta» con el carrito → «Efectivo» ya elegido.

**Sumar unidades a un producto con tallas:**
1. «Me llegaron tres chalecos chilotas más»
2. El asistente pregunta la talla.
3. Tarjeta: «2 + 3 = 5».
4. «Confirmar» → «Quedó guardado solo en este celular (la app no está conectada al servidor).»

**Conversación:**
- «¿Cuántos gorros vendí?» → «Este mes vendiste 4 «Gorro azul», 1 «Gorro rojo»».
- «No quiero registrar una venta» → «Está bien, no hago nada».
- A mitad de una venta, «¿Cómo registro una venta?» responde los pasos y agrega «Tu venta sigue anotada… di «seguir»».
- «El precio del chaleco era quince lucas, no, dieciocho» → «$18.000 → abrir y revisar».

## 7. Pruebas finales

Ver [README](README.md#5-pruebas-ejecutadas-resultados-reales). Además se probó en el emulador y en un navegador real; ver las capturas 14 a 24 y web 04 a 07.

## 8. ¿Conviene incorporar IA generativa?

| Opción | Calidad en español de Chile | Privacidad | Costo y cuotas | Sin señal | Gama baja | Mantención |
|---|---|---|---|---|---|---|
| **Reglas locales (actual)** | Buena en frases habituales; limitada en frases muy libres | Excelente, nada sale del celular | $0 | Sí | Sí | Media: hay que agregar señales con pruebas |
| Clasificador ligero en el celular | Mejor para reconocer la intención; los datos se siguen sacando con reglas | Excelente | $0 por uso; necesita frases reales para entrenarse | Sí | Sí (pocos MB) | Hay que volver a entrenarlo |
| Modelo de lenguaje local | Buena, pero los modelos pequeños se equivocan con números y nombres chilenos | Excelente | $0 por uso; APK de cientos de MB | Sí | **No** (memoria y batería) | Alta |
| API externa con plan gratuito | La mejor comprensión | Los datos van a un tercero; hay que actualizar el aviso de privacidad | Las cuotas gratuitas cambian y se acaban; requiere presupuesto para crecer | **No** | Sí | Media; depende del proveedor |
| Híbrido (reglas primero, API solo si no entiende y hay señal y permiso) | Muy buena | Configurable: solo frases, nunca datos de ventas | Bajo | Parcial | Sí | Media |

**Recomendación:**
- **Ahora no.** El motor local ya cubre bien los flujos reales y no tiene costo ni riesgo de privacidad.
- **Primero:** juntar 100–200 frases reales de emprendedoras, con su permiso, y sumarlas al contrato como pruebas.
- **Si después las frases que no se entienden siguen siendo frecuentes**, la vía más proporcionada es el **híbrido**, en una Edge Function (sin claves en la app), con estas condiciones:
  - el interruptor `ia_asistencia` lo habilita la administración (RF-ADM-05);
  - solo devuelve una intención y datos tipados, que pasan por `ValidadorResultado` y por la misma confirmación;
  - nunca envía ventas ni datos personales;
  - nunca propone precios (RF-IA-03).
- **No verifiqué precios ni cuotas actuales de proveedores;** hay que revisarlos al decidir.

## 9. Lo que falta o necesita un teléfono físico

- **TalkBack completo:** foco y anuncios de la tarjeta de confirmación y de los botones fijos. El emulador lo tiene, pero el doble toque no se puede automatizar.
- **Dictado y voz reales:** dictado en español de Chile, negar el permiso de micrófono, voz es-CL y sonido. El emulador no tiene audio ni voz es-CL.
- **Con el servidor real y el APK release:**
  - una venta preparada por el asistente y confirmada en «Venta» (`registrar_venta`);
  - un cambio de cantidad hecho desde dos celulares a la vez (`stockBase`);
  - responder una feria real.

  Las pruebas SQL (570) cubren la concurrencia y la idempotencia en el servidor, pero el recorrido completo no se probó con el servidor.
- **Aviso de luz de la foto:** solo tiene pruebas unitarias; no se probó con una foto oscura real.
- **Ferias en el emulador:** no hay invitaciones en el modo sin servidor, así que el flujo de ferias solo tiene pruebas unitarias.
- **El intérprete sigue siendo por reglas:** las frases muy libres o con referencias complejas se responden con «no entendí» y opciones.

## 10. Archivos

- **Android, nuevos:**
  - `data/asistente/`: `MotorInterpretacion.kt`, `ResolutorProductos.kt`, `LectorVentas.kt`, `GestorOperaciones.kt`
  - `data/RevisionFoto.kt`
  - `ui/asistente/EjecutorAsistente.kt`, `ui/asistente/CentroAyudaScreen.kt`
  - pruebas: `AsistenteOperacionesTest`, `NavegacionPersistenteTest`, `ContratoAsistenteTest`, `RevisionFotoTest`
- **Android, modificados:**
  - `InterpreteAlerce.kt`, `SesionAsistente.kt`, `TemasAyuda.kt`, `NumerosEs.kt`, `PantallaAsistente.kt`
  - `Navegacion.kt`, `TramaApp.kt`, `DashboardScreen.kt`, `AjustesScreen.kt`, `AltaProductoScreen.kt`, `PantallaPagoVenta.kt`
  - `Componentes.kt` (FilaSwitch), `Comprador7.kt`, `Models.kt` (`modoSencillo`), `build.gradle.kts` (versión)
  - `AsistenteRevisionTest.kt` (una prueba se actualizó a la venta preparada)
- **Web:**
  - nuevos: `js/asistenteWeb.js`, `tests/web/asistenteWeb.test.js`, `tests/contrato/asistente_frases.json`
  - modificados: `js/main.js` (un bloque), `index.html`, `css/styles.css`, `tests/e2e/guia.spec.js` (+2)
