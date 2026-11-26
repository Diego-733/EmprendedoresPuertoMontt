# Alerce · Nueva generación (rediseño v2, 26-nov-2026)

**Estado:** todo es local y **sin commit**. No hubo push, merge, release, publicación ni despliegue. No se tocaron Supabase, RLS, la autenticación, los roles, `registrar_venta`, `guardar_producto` ni `stockBase`.

**Capturas:** las de `antes/` y `despues/` se tomaron con el mismo emulador (Pixel 7, 1080×2400, letra al 100 % salvo las `*-200.png`) y los mismos datos de prueba. La web, a 390 px y 1366 px con la demo local.

## 1. Verificación inicial: por qué no se notaba el cambio

| Comprobación | Resultado |
|---|---|
| ¿El APK 1.8.0 trae los cambios? | **Sí.** Las frases nuevas («Elegir varios», «Ocultar diagnóstico», «Emprendimientos (», «Vendido por», «Este producto aún no tiene foto») están dentro de los `classes*.dex` del APK. |
| ¿El emulador usa la compilación correcta? | Sí: el depurable se reinstaló en cada paso con `adb install -r`. |
| ¿El servidor web sirve los archivos actuales? | Sí: los recursos llevan versión en la URL (`?v=…`) y las capturas muestran los cambios. |
| ¿Caché o pantallas duplicadas? | No. Lo que había era otra cosa (abajo). |
| **Panel administrativo Android** | **Nunca se había rediseñado y mostraba datos de ejemplo.** «Inicio» y «Emprendedoras» leían `MockData` (cuentas `@trama.app` y ferias inventadas), aunque la app estuviera conectada. «Cambiar estado» solo cambiaba el celular, y «Nueva feria» creaba un borrador local que nunca llegaba al servidor. |
| Inicio de la emprendedora | La cifra «Seguidores» también salía de datos de ejemplo. |

**Conclusión:** la falta de cambios se debía a que esas pantallas no se habían rediseñado (el encargo anterior trabajó sobre todo la vitrina), no a un problema de distribución.

## 2. Dirección creativa

**Principios** tomados de plataformas de comercio y gestión (sin copiar interfaces):

- **Etsy:** el producto y la persona que lo hace al centro, con un tono cálido.
- **Mercado Libre:** precio y acción principal claros, y búsqueda protagonista.
- **Shopify y Square:** un inicio que dice «lo que requiere atención», áreas de gestión agrupadas y una pantalla de venta rápida tipo caja.

**Identidad «Bosque y ámbar»:**

| Pieza | Valor |
|---|---|
| Marca | Bosque profundo `#0E3B2E` → brote `#2F7A5A`, en degradado para cabeceras, portada y pie. |
| Acento comercial | Ámbar `#F4B740`, siempre con texto oscuro (10:1), en sellos, contadores e insignias. |
| Fondo | Cálido `#F5F3EE` (antes, gris verdoso). |
| Tarjetas | Sin borde, con sombra suave y esquinas de 20 dp. |
| Jerarquía editorial | Sobretítulo en mayúsculas con espaciado, más título ExtraBold. |
| Logo | El oficial, sin cambios. |

## 3. Sistema de diseño

### Android: `ui/componentes/SistemaAlerce.kt` (nuevo)

| Componente | Uso |
|---|---|
| `CabeceraMarca` | Panel de bosque con degradado: sobretítulo, título, subtítulo, acciones y un espacio para cifras o un buscador. |
| `AccionCabecera`, `CifraCabecera`, `FilaCifras` | Acciones y cifras dentro de la cabecera. `FilaCifras` apila las cifras con letra ≥ 130 %. |
| `MosaicoAccion` | Tarjeta de acción con ícono en un cuadro de color e insignia. |
| `ParAdaptable` | Pone dos mosaicos lado a lado, o uno debajo del otro con letra grande. |
| `FilaGestion`, `GrupoFilas` | Lista agrupada con ícono de tono, detalle, insignia y flecha. |
| `EncabezadoSeccion` | Sobretítulo más título, y una acción opcional («Ver todas ›»). |
| `AvisoAccion` | Aviso con franja de color y una acción con nombre. |
| `BloqueFecha` | Día y mes destacados, para las ferias. |
| `PildoraEstado`, `Insignia`, `IconoTono` | Estados y marcas pequeñas. `Tono` reúne MUSGO, AMBAR, CORTEZA, AZUL, OK y NEUTRO, todos con contraste ≥ 7:1. |
| `EstadoCargando` | Indicador de carga siempre acompañado de texto. |

**Cambios globales en `Componentes.kt`** (llegan a todas las pantallas):

- **`Tarjeta`:** sin borde, con sombra y esquinas de 20 dp.
- **Botones:**
  - el primario es un rectángulo redondeado de 52 dp con texto de 16 sp;
  - el secundario es tonal, con fondo musgo claro.
- **`ChipFiltro`:** el seleccionado va en bosque.
- **`CampoBusqueda`:** blanco, con sombra y sin contorno.
- **`TarjetaMetrica`:** sin borde.
- **`TituloSeccion`:** ExtraBold, con `heading()` y una acción con «›».
- **`Vacio`:** ícono en un círculo de 72 dp.
- **`BarraSuperior`:** 22 sp ExtraBold.
- **Barra inferior:** la pestaña activa va en bosque con el ícono en blanco.
- **Tipografía:** más peso en los títulos (`Type.kt`).
- **Colores:** fondo cálido y tokens nuevos (`Color.kt`).

### Web: `css/generacion.css` (nuevo)

Se carga al final y no cambia ninguna función.

- **Cabecera:** franja superior oscura y cabecera blanca con sombra. La navegación por secciones pasa a pestañas tipo píldora.
- **Portada:** panel de marca con degradado, el buscador adentro y el sello ámbar «Hecho a mano en Alerce · Puerto Montt». El sello es el único cambio de HTML, un `<p>` nuevo.
- **Próxima feria:** tarjeta blanca tipo «entrada» con franja ámbar.
- **Productos y emprendimientos:** tarjetas sin borde, con sombra y que se elevan al pasar el mouse (desactivado con `prefers-reduced-motion`).
- **Títulos de sección:** con subrayado ámbar.
- **Panel de la emprendedora:** cabecera de marca y acciones en tarjetas.
- **Panel de administración:**
  - **escritorio (≥ 1024 px):** navegación lateral fija («Áreas de gestión») y el área de trabajo a la derecha;
  - **celular:** cabecera compacta y pestañas en una sola fila deslizable.
- **Pie:** bosque profundo con una franja ámbar.

## 4. Pantalla por pantalla: qué cambió de verdad

| Pantalla | Antes | Después |
|---|---|---|
| **Bienvenida** (01) | Logo, nombre y dos botones centrados sobre fondo gris. | Portada de marca: el logo en un disco blanco sobre bosque con degradado, la promesa en un sello ámbar y dos tarjetas de elección con ícono y explicación. Se conservan los mismos textos, el mismo orden y el mismo comportamiento. |
| **Vitrina** (02) | Título suelto y buscador aparte; abría en «Emprendimientos». | Cabecera de marca con el buscador adentro y la acción «Actualizar». **Abre en «Productos»**, para invitar a descubrir. Se mantienen los filtros, los chips y el conteo del encargo anterior. |
| **Ficha de producto** (03) | — | Recibe el sistema nuevo (tarjetas, botones). Su estructura ya se había rediseñado en el encargo anterior (barra de contacto fija, «Vendido por»), así que se conserva. |
| **Ficha de tienda** (04) | Barra con el nombre, logo pequeño y texto. | Cabecera de marca con rubro y sector, el nombre grande, el logo con anillo blanco y la descripción. Luego el contacto y los productos en grilla. |
| **Inicio de la emprendedora** (06) | Saludo suelto, tres botones de ancho completo y métricas al final (con «Seguidores» de ejemplo). | Cabecera de marca con tres **cifras reales** (vendido este mes, a la vista, unidades) y «Registrar una venta» en blanco dentro de la cabecera. Siguen los avisos con acción, los accesos rápidos en mosaico (añadir, mis productos, ferias, compartir) con insignias, el asistente y la ayuda, la próxima feria con su bloque de fecha y las últimas ventas. |
| **Modo sencillo** (05) | Siete botones con contorno. | Los mismos siete accesos, en el mismo orden y con el mismo texto, como tarjetas grandes de 80 dp con ícono de color. El primero, «Hablar con mi asistente», va destacado en bosque. |
| **Mis productos** (07) | — | Recibe el sistema (tarjetas, chips, botones). **La estructura se conserva:** en la feria lo importante es ver la mayor cantidad de productos sin desplazarse, y el encargo anterior ya la compactó (+218 px). Una cabecera de marca aquí quitaría lugar a la lista. |
| **Creación de productos** (08) | — | Recibe el sistema (botones de 52 dp, campos y tarjetas). Se conservan el flujo por pasos, el borrador y las validaciones de `guardar_producto`, que ya estaban probadas. |
| **Venta** (09) | «Carrito de venta» con una lista de enlaces de texto para elegir productos; el total y el pago, dentro de la lista. | **Punto de venta:** cabecera con el total grande, grilla de productos para tocar (foto, precio, «Quedan N», «Se acabó», «A pedido») y el carrito en dos líneas por producto (qué es / cuánto). **«Continuar al pago» queda fijo abajo.** La lógica es la misma: `DialogoElegirOpcion`, `validarLinea`, `validarVenta` y `PantallaPagoVenta`. |
| **Ajustes** (10) | Título suelto. | Cabecera de marca con sobretítulo según el rol. El contenido se conserva: es una lista de preferencias y ya estaba ordenada por secciones. |
| **Asistente** (11) | — | Recibe el sistema (barra, botones, chips). Se conserva la barra compacta: en una conversación, una cabecera grande quitaría espacio a los mensajes. |
| **Inicio administrativo** (12) | Cuatro métricas de **ejemplo**, ferias de ejemplo, «Nueva feria» (borrador local) y «Directorio». | Cabecera con la fecha y tres cifras **reales**, «Requiere atención» con avisos accionables (feria en curso, correos por registrarse, tiendas desactivadas, cuentas sin tienda, o «Todo al día»), el calendario de ferias con bloque de fecha, seis **áreas de gestión** en mosaico y la actividad reciente (bitácora). |
| **Gestión administrativa** (13–15) | «Emprendedoras» con cuentas de **ejemplo** y «Ajustes» como pestaña. | Pestaña **«Gestión»** nueva, con todas las áreas agrupadas (Personas, Vitrina y ferias, Control, Herramientas). «Emprendedoras» lee las **cuentas reales** (`listar_emprendedoras`), con buscador en la cabecera, chips con conteo por estado y una ficha con «Ver su tienda pública» y «Desactivar» o «Activar» (con confirmación, usando la función de la web). «Ferias» va agrupada por Hoy, Próximas, Anteriores y Canceladas. Hay pantallas de consulta de Asociaciones, Tipos de producto, Administradores y Bitácora. |
| **Portada web** | Tarjeta blanca de texto y tarjeta verde de la feria. | Panel de marca con el buscador adentro, sello ámbar, feria tipo entrada, navegación en píldoras y tarjetas con sombra. |
| **Vitrina web** (móvil y escritorio) | — | Tarjetas sin borde, filtros en una tarjeta, chips ámbar de filtros activos y títulos con acento. |
| **Panel administrativo web** | Pestañas en fila dentro de una caja con borde. | Cabecera de marca. En escritorio, barra lateral fija «Áreas de gestión» y área de trabajo en una tarjeta. En el celular, pestañas deslizables. |

## 5. Panel administrativo Android: datos y permisos

**Lecturas:** son las **mismas funciones que usa la web** y que el servidor protege con `es_admin()`:

- `listar_emprendedoras`;
- `listar_admins`;
- `ver_bitacora`;
- `uso_tipos_producto`;
- la tabla `tipos_producto`.

Ferias y asociaciones salen de los datos públicos de siempre (`VitrinaStore`). Están en `data/AdminStore.kt` y `data/remoto/AdminRepositorio.kt`.

**Escrituras:** son dos, y las dos ya existían en la plataforma:

- **autorizar un correo:** escribe en `cuentas_autorizadas`, igual que antes;
- **activar o desactivar una tienda:** usa `cambiar_estado_emprendedora`, igual que la web, con confirmación. Ya no es un cambio solo local.

**Lo que se edita solo en la web:** crear o editar ferias, asociaciones, tipos y administradores. Se dice así en pantalla, con «Abrir el panel web».

**Datos de ejemplo:** en el APK de pruebas sin servidor se muestran **rotulados** («Datos de ejemplo: … nada de lo que ves aquí es real»). Para revisarlos hay un acceso «Entrar Administración» que solo existe en depuración y sin servidor.

**No se exponen ventas privadas:** el panel no lee ventas.

**Lo que se quitó del inicio administrativo:** el botón «Nueva feria», que solo creaba un borrador en el celular. Crear ferias sigue en la web (Entrega 7). El código de ese detalle de ejemplo (`FeriaDetalleScreen`) no se borró.

## 6. Accesibilidad

- **Letra al 200 %** (capturas `*-200.png`):
  - las cifras de las cabeceras y los mosaicos se apilan (`letraGrande()` ≥ 130 %);
  - la grilla de productos y la de venta pasan a una columna con letra ≥ 150 %.
- **Tamaños táctiles:** acciones de cabecera, filas, mosaicos y botones ≥ 48 dp; pestañas web ≥ 44 px.
- **Lectores de pantalla:**
  - las filas y los mosaicos se leen como un solo elemento, con rol de botón y etiqueta;
  - los títulos están marcados como encabezados;
  - los estados siempre llevan texto.
- **Contraste:**
  - blanco sobre bosque: 12,9:1;
  - texto secundario sobre bosque: 10:1;
  - tinta sobre ámbar: 10:1.
  - El sello de la bienvenida pasó de texto ámbar sobre verde (contraste insuficiente) a tinta sobre ámbar.
- **TalkBack:** no está validado en un teléfono físico.
- **Limitación conocida:** con letra al 200 %, la etiqueta «Emprendedoras» de la barra inferior de administración se acorta con «…». Es la regla de la barra existente: todas se achican juntas hasta la mitad y recién después se acortan.

## 7. Pruebas

| Suite | Resultado |
|---|---|
| Android unitarias | 169/169 (6 nuevas en `RedisenoAdminTest`: resumen, filtro, ferias, integrantes, pestañas y rutas) |
| Lint | 0 errores |
| Web (`node --test`) | 76/76 |
| e2e Playwright | 96 pasan, 5 omitidas. Una prueba detectó que la cabecera admin en el celular empujaba «Nueva feria» fuera de la vista; se corrigió con la cabecera compacta y las pestañas deslizables. |
| Hosting | correcto |
| SQL | 570/570 |
| Reglas | 5/5 |
| Emulador | Recorridos de visitante, comprador, emprendedora (normal y modo sencillo), venta (agregar al carrito) y administración, a 100 % y 200 %. |

## 8. APK para tu revisión

| | Anterior (conservado) | Nuevo |
|---|---|---|
| Archivo | `docs/rediseno/apk/alerce-1.8.0-comercial-10.apk` | `docs/rediseno/apk/alerce-1.9.0-generacion-11.apk` |
| Versión | 1.8.0-comercial (10) | **1.9.0-generacion (11)** |
| SHA-256 del archivo | `4A8CA3B4BB7AA3E203A2FFAB45FA0E0158113E2FC30AB4E8318417D24E05D6D5` | `E420E6C1721ADBC24F09D7A44AFD51B47ACF0A7B02BC7E672E383EC5086EBCA9` |
| Paquete y certificado | `com.company.ultrapp` · `29:15:BB:…:CB:37` | iguales |
| `verificar-apk.js` | APTO | APTO (16,3 MB) |

- **Contenido comprobado:** el APK nuevo contiene «Requiere atención», `listar_emprendedoras`, «Toca lo que vendiste» y «Hecho a mano, cerca tuyo».
- **Acceso de prueba:** «Entrar Administración» está en el código, pero **solo aparece en depuración y sin servidor** (`BuildConfig.DEBUG && !configurado`). En este APK release, conectado, no se muestra.
- Git ignora los `.apk`.
