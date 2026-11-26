# Rediseño comercial — Alerce (26-nov-2026)

**Estado:** todo es local y **sin commit**. No se hizo push, merge, release, publicación ni despliegue. No se tocaron Supabase, RLS ni la configuración de producción. HEAD sigue en `240ee45`.

## Documentos

- [matriz-visibilidad.md](matriz-visibilidad.md): qué ve cada rol y estado de sesión, en Android y en la web.
- [propuesta-registro-controlado.md](propuesta-registro-controlado.md): la desviación de RN-01 y cómo corregirla. **No está implementada.**
- `capturas/antes/` y `capturas/despues/`: capturas comparativas (emulador Pixel 7, 1080×2400; web a 390 px y 1280 px).
- `apk/alerce-1.8.0-comercial-10.apk`: copia del APK release. Git la ignora (`*.apk`).

## Auditoría (problemas encontrados → decisión)

| # | Problema | Decisión |
|---|---|---|
| 1 | **Android, vitrina:** los tres filtros (tipo, sector y asociación) ocupaban unos 500 px antes del primer emprendimiento. | Botón «Filtros» con panel inferior, chips de filtros activos que se pueden quitar, «Limpiar filtros» y «Ver N resultados». **Se conservan los tres filtros** y la opción «Sin asociación». |
| 2 | No había forma de buscar **productos** en toda la vitrina, solo tiendas. | Pestaña «Productos (n)» con grilla, usando los mismos datos públicos de antes (`publicados()`) y las mismas reglas: `estadoPublico`, ocultos fuera, «Consultar precio». Lo agotado va al final. |
| 3 | Al buscar en «Emprendimientos» no se veía qué producto coincidía. | Hasta 4 productos que coinciden, más «Ver los N productos». |
| 4 | **Ficha de tienda:** los productos estaban debajo de calificaciones, QR y punto de retiro. | Orden nuevo: presentación → contacto → seguir → **productos en grilla** → calificaciones, compartir/QR y retiro. No se quitó nada. |
| 5 | **Ficha de producto:** el contacto quedaba al fondo y no había vínculo claro a la tienda. | Barra de contacto fija abajo, «Vendido por {tienda} ›» y disponibilidad en verde. |
| 6 | Productos sin foto: grandes bloques de color con una inicial competían con las fotos reales. | Estado neutro «Sin foto» / «Este producto aún no tiene foto». |
| 7 | **Mis productos:** dos botones de ancho completo y una línea «Cambiar varios a la vez» empujaban los productos hacia abajo. | «Añadir producto» y «Guíame paso a paso» en una fila (se apilan con letra grande); «☑ Elegir varios» pasa a ser un chip en la fila de filtros. |
| 8 | «Protección de la app» (App Check / Play Integrity) aparecía en Ajustes para todas las personas. | Se movió a «Diagnóstico técnico (administración)»: plegado, solo ADMIN o depuración. El texto aclara que hoy solo lo verifica Firebase. **No se cambió la inicialización, los proveedores ni las reglas.** |
| 9 | Textos de ingreso y error poco claros («no configurado»). | «Entra a tu tienda para…», «Entra para seguir tus tiendas favoritas…» y «Esta versión de la app no está conectada al servidor…». |
| 10 | **Web, directorio:** el resumen de filtros no decía cuántos había activos ni cuáles. | «Filtros (n)», chips «Tipo: X ✕» (con `aria-label` «Quitar el filtro …», de 44 px de alto) y «Limpiar filtros». |
| 11 | La **Inicio** de la emprendedora | Ya era accionable (invitaciones, tareas del día, alertas, próxima feria); no se cambió. |

## Dirección y sistema de diseño

- **Ideas guía:**
  - Que la foto y el precio manden.
  - Una sola acción principal por pantalla.
  - Que la información técnica no aparezca en el uso diario.
- **Se reutilizan los componentes existentes:** `ContenedorChips`, `ChipFiltro`, `Tarjeta`, `FotoProducto`, `CampoBusqueda`, `Vacio` y la paleta (Musgo, RojoAlerce, GrisChip, NaranjaContenedor).
- **Componentes nuevos (privados):**
  - `TarjetaProductoGrilla`, que admite ocultar la tienda y un modo `ancha`;
  - `EtiquetaFiltroActivo`;
  - `SelectorFiltro`;
  - `columnasGrilla()`: 2 columnas, o 1 con la letra al 150 % o más.
- **Sin dependencias nuevas:** sin servicios externos, sin ofertas, valoraciones ni escasez inventadas. «Quedan N» solo aparece cuando la tienda maneja cantidades, igual que antes.

## Mediciones antes / después

Medidas en las mismas condiciones: emulador 1080×2400, letra al 100 % y datos de la demo.

| Medida | Antes | Después |
|---|---|---|
| Android, vitrina: posición vertical del primer emprendimiento | y ≈ 1330 px | y ≈ 910 px (≈ 420 px más arriba) |
| Android, Mis productos: primera tarjeta de producto | y ≈ 1362 px | y ≈ 1144 px (≈ 218 px más arriba) |
| Android, ficha de tienda: título «Productos» | y ≈ 1363 px | y ≈ 1124 px, y los productos ahora van en grilla |
| Android, ficha de producto: botón de contacto | al final de la lista (había que bajar) | siempre a la vista (barra fija) |
| Web, filtros activos visibles sin abrir el panel | no | sí (chips con su conteo) |
| Web, carga de la portada (primera visita, demo local) | móvil 3326 ms · escritorio 527 ms | móvil 3197 ms · escritorio 488 ms |

**Sobre la carga web:** la diferencia está dentro de la variación normal entre ejecuciones. Se informa como «sin empeoramiento medible», **no** como una mejora.

**No se midió conversión.** No hay datos de uso real, así que no se afirma ninguna mejora porcentual.

## Accesibilidad

- **Revisado en el emulador con la letra al 200 %** (`*-200.png`):
  - la vitrina: los chips bajan de línea y nada se corta;
  - el panel de filtros;
  - los productos, que pasan a una columna con nombres completos;
  - la ficha de producto: «Vendido por…» baja de línea sin cortarse y el aviso sin foto tiene margen;
  - Mis productos, con los botones apilados.
- **Tamaños táctiles:** IconButtons de 48 dp, «Vendido por» con `heightIn(48.dp)` y rol de botón, y los chips web de 44 px (lo comprueba la prueba e2e W08).
- **Lectores de pantalla:**
  - las tarjetas fusionan su contenido y tienen `onClickLabel` («Ver …»);
  - los chips de filtro, «Quitar el filtro …»;
  - los encabezados están marcados con `heading()`.
- **TalkBack:** no está validado en un teléfono físico. Falta esa prueba.
- **Web a 200 %:** la suite e2e completa comprueba que no haya desplazamiento lateral a 320, 390, 768 y 1280 px.

## Pruebas

| Suite | Resultado |
|---|---|
| Android `testDebugUnitTest` | 163/163 |
| Android `lintDebug` | 0 errores, 78 avisos (los mismos que antes) |
| Web `npm run test:web` | 76/76 |
| e2e Playwright (completa) | 96 pasan, 5 omitidas (hosting, como siempre). W08 ahora además prueba los chips de filtro: conteo, `aria-label`, 44 px y que al quitarlos se restablece la lista. |
| `npm run test:hosting` | correcto |
| SQL (PG17) | 570/570 |
| Reglas Firebase | 5/5 |

## APK

| | Anterior | Nuevo |
|---|---|---|
| Versión | 1.7.0-asistente (9) | **1.8.0-comercial (10)** |
| SHA-256 del archivo | `AD5FB842A14F5A999DDCB6FF47491CDCDAE3C9004878BD71E109827D986F4BC7` | `4A8CA3B4BB7AA3E203A2FFAB45FA0E0158113E2FC30AB4E8318417D24E05D6D5` |
| Paquete y firma | `com.company.ultrapp`, certificado `29:15:BB:…:CB:37` | lo mismo (misma keystore; en `build.gradle.kts` solo cambió la versión) |
| Tamaño | — | 16,2 MB |
| `verificar-apk.js` | APTO | APTO |

## Siguiente tarea recomendada (administración)

1. **Decidir RN-01:** invitación o solicitud aprobada (ver la propuesta). Después, la migración 11 con su reversión y sus pruebas RLS.
2. Revisar manualmente las cuentas de emprendedora creadas por la vía abierta.
3. Probar TalkBack en un teléfono físico: vitrina, panel de filtros, grilla y barra de contacto.
4. **Opcional:** llevar la pestaña global de productos también a la web. Hoy la web busca productos desde el buscador principal.
