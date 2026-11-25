# Experiencia de Alerce · rediseño después de la Entrega 8

Mejora de la interfaz y la navegación de la web y de la app Android, sobre la base funcional de la Entrega 8.
**No cambia** migraciones, credenciales, la activación de Google, la automatización de avisos ni los despliegues.
Todo se probó en local con datos ficticios. Nada está publicado ni tiene commit.

## Cómo recorrerlo en local

**Web** (datos ficticios, Supabase sustituto; nunca usa `js/config.js`):

```powershell
npm run demo
```

| Para ver | Abre |
|---|---|
| Quien llega por WhatsApp a una tienda | <http://localhost:4180/?demo=visitante&tienda=t_tejidos_austral> |
| Portada pública | <http://localhost:4180/?demo=visitante> |
| Tienda sin WhatsApp y con nombre largo | <http://localhost:4180/?demo=visitante&tienda=t_reposteria_surena> |
| Emprendedora en la web | <http://localhost:4180/?demo=emprendedora> |
| Administración | <http://localhost:4180/?demo=admin> |

Los datos están en `tests/e2e/datos_demo.js` (4 tiendas de Alerce, 9 productos, 2 ferias, 2 asociaciones, cuentas y bitácora). Las fotos son ilustraciones generadas por el servidor de demostración.

**Android** sin tocar la instancia real (no lee `supabase.properties` ni `firebase.properties`; en el login aparece «Tiendas de prueba (Modo Desarrollador)»):

```powershell
cd "App para el celular"; .\gradlew.bat assembleDebug -PsinServidor=true
```

Sin `-PsinServidor=true` la compilación es igual que antes.

## 1. Funciones antes del rediseño y dónde quedaron

Se listaron todas las funciones visibles, también las que no están en la rúbrica. «Se comprobó» indica cómo se verificó en esta tarea que se puede encontrar y usar.

### Web · visitante y compradora

| Función | Antes | Ahora | Se comprobó |
|---|---|---|---|
| Buscar tiendas | Dos buscadores (cabecera y directorio) | Uno solo, en la portada «¿Qué buscas?»; busca también por producto y sin tildes | e2e `experiencia` y `entrega6` |
| Ver productos con precio desde el inicio | No existía: había que abrir cada tienda | Grilla «Productos» con foto, precio, estado y «Vende: tienda» | e2e `experiencia` |
| Atajos por tipo (Panadería, Artesanía…) | Pills sin uso | Atajos generados de los rubros reales | demo |
| Próxima feria | Sección más abajo | Tarjeta junto al buscador (en el celular, después de los productos) y sección «Ferias» | demo, e2e `entrega6/7` |
| Directorio con 3 filtros | Siempre abiertos | «Filtrar por tipo, sector o asociación» (abierto en escritorio, plegado en el celular) | e2e `entrega6` |
| Asociaciones | Repetidas: pestaña del directorio y sección aparte | Una: pestaña «Asociaciones» del directorio; el menú lleva ahí | e2e `entrega6/7` |
| Ficha de tienda (`?tienda=`, QR) | Ventana con WhatsApp al final | Página completa en el celular: quién es, qué vende, productos y barra fija «Tu mensaje le llega a [tienda] · WhatsApp [número]» | e2e `experiencia`, `whatsapp` |
| Detalle de producto (`?producto=`) | Ficha sin vendedor | «Lo vende» con logo, sector y número de WhatsApp; volver deja la lista en el mismo punto | e2e `experiencia` |
| Botón «atrás» del celular | Salía de la página | Cierra producto, ficha o pedido; la dirección refleja lo abierto | e2e `experiencia` |
| Pedido por WhatsApp (carrito) | «Carrito de Compras» | «Mi pedido»; cada tienda recibe solo lo suyo (sin cambios de lógica) | e2e `whatsapp` |
| Formulario de contacto | Siempre junto a WhatsApp | En la ficha; en cada producto solo si la tienda no tiene WhatsApp | e2e `whatsapp` |
| Seguir tienda, avisos, calificar | — | Sin cambios de lógica (ficha y cabecera) | e2e `entrega7` |
| Favoritos (corazón) | En la franja de color | En la tarjeta, 44 px | demo |
| Agendar hora (barbería) | Botón en la tarjeta | En la ficha: «Pedir una hora» | demo |
| Muro / foro de la tienda | «Muro y Foro», solo en el navegador | Pestaña «Preguntas», sigue avisando «Solo en este navegador» (no es del servidor) | demo |
| Pago QR del carrito | Muestra un QR de ejemplo | Sin cambios. **Es simulado**: no es un medio de pago real | — |
| Tamaño de letra A / A+ / A++ y contraste | Solo cambiaba parte del texto | Cambia todo el texto nuevo (rem) | e2e a 200 % |
| Aviso de privacidad | Pie | Pie | e2e `entrega8` |
| Descargar la app Android | Botón rojo, primero en la portada | Franja «¿Vendes en Alerce?» y pie (los dos enlaces del Release) | `npm run test:hosting` (5/5) |
| Entrar, registrarse, recuperar contraseña, Mi perfil | «Inicio de Sesión / Registro» | «Entrar»; mismos formularios | e2e `web`, `entrega8` |

### Web · emprendedora

| Función | Antes | Ahora | Se comprobó |
|---|---|---|---|
| Panel al entrar | Métricas de ejemplo y 6 botones iguales | «Registrar una venta», «Mis productos», «Añadir producto»; arriba las invitaciones por responder | e2e `experiencia`, `entrega7` |
| Caja POS, catálogo, publicar | Repetidos en cabecera y panel | Solo en el panel (mismos identificadores) | e2e `web` |
| Alerta de productos | «agotado(s)», «stock» | «Se acabó: …» / «Quedan pocos: …» con su color de estado | demo |
| Mis ferias, Mi tienda, QR, afiches, resumen del mes | Botones sueltos | «Más herramientas y resumen del mes» | demo |

### Web · administración

| Función | Antes | Ahora | Se comprobó |
|---|---|---|---|
| Entrar al panel | Al final de la página; tras el clic se veía la sección Ayuda | Panel arriba, bajo la cabecera, abierto en «Ferias» | e2e `experiencia` (sin desplazar) |
| Ferias, asociaciones y app sin cuenta | Una pestaña larga con las tres | Tres pestañas: «Ferias», «Asociaciones», «App sin cuenta» | e2e `entrega6`, `experiencia` |
| Cuentas emprendedoras, Tipos de producto, Administradores, Bitácora | Pestañas con códigos (W04–W09…) | Sin códigos; lista y botón para crear a la vista tras el clic, a 390 y 1280 px | e2e `entrega8`, `experiencia` |
| Crear asociación | Campos sin rótulo antes de la lista | «➕ Crear asociación» abre campos rotulados; la lista queda a la vista | e2e `experiencia` |
| «Salir» | En cabecera y panel | Solo en la cabecera | demo |
| Panel en el celular | Pestañas en una fila con desplazamiento lateral | Pestañas en filas; sin menú público ni pedido encima | e2e `experiencia` |
| Asociaciones y catálogos de ejemplo local | Ocultos con servidor | Igual (solo sin servidor) | e2e `entrega8` |

### Android · emprendedora

| Función | Antes | Ahora | Se comprobó |
|---|---|---|---|
| Inicio | 4 métricas arriba («Stock total»), invitaciones al final, campana sin función, botón flotante | Invitaciones por responder primero; «Registrar una venta» y «Mis productos» grandes con texto; resumen del mes al final («Unidades disponibles») | emulador, capturas |
| Cuántos quedan | Solo dentro del producto (paso 4 de 5) | «Cuántos tengo» − n + en cada tarjeta (48 dp) | emulador |
| «Se acabó» con Deshacer | Sí | Igual, con borde de estado | emulador (20 → «Se acabó» → «Deshacer» → 20) |
| ¿Llegó al servidor? | Solo un contador general «cambios por subir» | Bajo cada producto: «Guardado», «Subiendo…», «Pendiente: sin señal…» o «Solo en este celular»; también en la edición | prueba unitaria `EstadoGuardadoTest`, emulador (modo sin servidor) |
| Nombre del producto | Cortado con «…» | Completo | emulador |
| Destacar (estrella) | 34 dp | 48 dp | código |
| Aviso «Producto sin terminar» | Se partía letra por letra con letra grande | Texto arriba, botones debajo | emulador a 200 % |
| Cabecera de «Mis productos» | Fija: con letra grande casi no quedaba lista | Se desplaza con la lista | emulador a 200 % |
| Botón flotante en Inicio y Mis productos | Repetía «Registrar venta» / «Nuevo producto» solo con ícono | Quitado (las acciones tienen texto en pantalla) | emulador |
| Venta, historial, ferias, tienda, QR, vista previa, ajustes, huella | — | Sin cambios de lógica; toman la paleta nueva | compila; 71 pruebas unitarias |
| Admin y comprador en Android | — | Sin cambios de pantalla; toman la paleta nueva | compila |

### Dependen de configuración externa o ya estaban pendientes (sin cambios)

- Google en Supabase Auth (RN-07, E6-A08, E7-A12): el botón aparece solo si está activo.
- Notificación del navegador con la página cerrada (RF-CMP-08): falta `FIREBASE_WEB_CONFIG`.
- Envío de correos de recuperación: depende del SMTP de la instancia.
- Migraciones 09 y 10 aplicadas en la instancia real (según `docs/entrega8`); la demo las simula.
- IA (RF-ADM-05, RF-IA-01 a 03): condicionado, sin implementar.
- Release `apk-1.6-alerce`: los enlaces apuntan ahí; reemplazar el APK sigue pendiente del equipo.

## 2. Problemas encontrados y cambio por tarea

| Dónde | Problema | Tarea real | Cambio | Criterio que sigue funcionando |
|---|---|---|---|---|
| Portada móvil | Lo primero era «Descargar app»; tiendas y productos bajo el pliegue | Encontrar un producto | Buscador y productos con precio arriba | RF-CMP-01, W08 (sin desborde) |
| Ficha de tienda | WhatsApp al final; no se veía a quién iba | Contactar a la dueña | Destinatario visible y barra fija | RF-CMP-05, corrección WhatsApp (e2e `whatsapp`) |
| Ficha → producto | Sin «quién vende»; «atrás» sacaba de la página | Revisar y volver | «Lo vende» y historial | RF-COM-03 (`?producto=`, `?tienda=`) |
| Buscadores | Repetidos | Buscar | Uno | RF-CMP-01 |
| Panel admin | Fuera de vista al entrar y tras el clic | Organizar una feria al mes | Panel arriba, pestañas separadas | RF-ADM-01 a 04, RF-FER-01, RF-ASO-01 |
| Inicio Android | Métricas antes que tareas; «stock» | Vender en la feria | Tareas grandes y avisos | RNF-IDI-01, RNF-USA-02/03 |
| Mis productos Android | Cambiar unidades obligaba a recorrer 5 pasos | Marcar cuántos quedan | «Cuántos tengo» en la lista | RF-PRO-07, RF-PRO-09 |
| Guardado Android | No se sabía si el cambio llegó | Confiar en el cambio sin señal | Estado por producto | RNF-REN-02 |
| Estados | «Agotado» en la web y «Se acabó» en la app | Entender la vitrina | «Se acabó» en las dos | RF-PRO-08, contrato compartido |

## 3. Referencias revisadas y decisiones

1. **Compra local · Mercado Libre.** [Documentación pública «Reputación de vendedores»](https://developers.mercadolibre.cl/es_ar/reputacion-de-vendedores) (leída; las páginas de listados y de ayuda pedían iniciar sesión o rechazaron la lectura automática, por eso no se citan). Mercado Libre describe la reputación como la imagen que da confianza al comprar y la muestra en cada publicación.
   **Decisión:** junto al precio va siempre quién vende («Vende: …» en la grilla y «Lo vende» en el detalle, con logo, sector y número). **Por qué en Alerce:** quien llega por WhatsApp compra a una vecina, no a una marca; saber de quién es el producto es lo que da confianza.
2. **Herramienta para vender · WhatsApp Business.** [«Introducing Catalogs for Small Businesses»](https://blog.whatsapp.com/introducing-catalogs-for-small-businesses?lang=es) (blog oficial, 2019). El catálogo muestra imagen y precio de cada artículo y la conversación sigue en el chat del negocio.
   **Decisión:** cada producto muestra foto, precio y estado, y el contacto dice antes de abrirse a qué tienda y número llega («Tu mensaje le llega a Tejidos Austral · WhatsApp +56 9 3333 4444»). **Por qué en Alerce:** las emprendedoras ya venden por WhatsApp; la vitrina debe parecerse a lo que conocen y no abrir un chat equivocado.
3. **Servicios públicos y accesibilidad · GOV.UK Design System.** [Error summary](https://design-system.service.gov.uk/components/error-summary/) y [Error message](https://design-system.service.gov.uk/components/error-message/): rótulo visible en cada campo, mensaje que dice qué pasó y cómo arreglarlo, y no borrar lo escrito.
   **Decisión:** campos con rótulo visible (no solo marcador) en lo nuevo y en «Crear asociación»; los estados se dicen con palabras («Pendiente: sin señal, quedó en este celular y se sube solo cuando vuelva»); la barra de contacto se aparta mientras se escribe. **Por qué en Alerce:** una administradora que entra una vez al mes y personas con poca experiencia digital no deben adivinar qué falta ni perder lo que escribieron.

## 4. Color, tipografía y acción destacada

| Nombre | Color | Uso | Contraste sobre blanco |
|---|---|---|---|
| Musgo | `#1F5C46` | Acción principal: contactar, guardar, vender, pestaña elegida | 7,8:1 |
| Corteza | `#8C3B22` | Precios y el punto del logo | 7,6:1 |
| Tinta | `#1E2421` | Texto | 15:1 |
| Tinta suave | `#4F5B55` | Texto secundario (antes `#71717A`, 4,8:1) | 7:1 |
| Neblina | `#F3F5F2` | Fondo | — |

Estados, siempre con texto: **Disponible / Guardado** `#1E6B3A` sobre `#E3F1E6`; **Pendiente / Quedan pocos** `#7A4E00` sobre `#FBEFD2`; **Se acabó** `#9B2C1E` sobre `#FBE4E0`; **A pedido / Subiendo** `#2E4E7A` sobre `#E4ECF6` (todos sobre 5,5:1). Misma lógica en `css/alerce.css` y `ui/theme/Color.kt`.

**Por qué:** el musgo del bosque sureño y la corteza rojiza del alerce son del lugar, se distinguen del rojo y negro genéricos anteriores y mantienen el logo (ALERCE con su punto). Un solo color de acción hace que «lo importante» se reconozca en ambas plataformas. Tipografía: **Atkinson Hyperlegible** (diseñada para baja visión) en la web; Android usa la del sistema y respeta su tamaño.

**Acción destacada por vista:** portada → buscar; ficha de tienda → «Escribir por WhatsApp» (con destinatario); producto → «Pedir por WhatsApp»; emprendedora (web y app) → «Registrar una venta»; administración → crear en la sección elegida.

## 5. Cambios por rol

- **Compradora:** busca productos desde el inicio y ve precio y tienda; la ficha de tienda es una página clara con contacto fijo y destinatario visible; «atrás» la devuelve al mismo punto; «Mi pedido» en lugar de carrito.
- **Emprendedora:** al entrar ve invitaciones por responder, «Registrar una venta» y «Mis productos»; cambia «Cuántos tengo» o marca «Se acabó» desde la lista y lee si quedó **Guardado** o **Pendiente**.
- **Administración:** el panel aparece arriba; cada sección (ferias, asociaciones, cuentas, tipos, administradores, bitácora, app sin cuenta) muestra su lista y su botón para crear apenas se toca, también en el celular.

## 6. Qué se comprobó y qué no

**Comprobado en local:**
- Web: `npm run test:web` (63/63), `npm run test:hosting` en el emulador (5/5: los dos enlaces al APK y `?tienda=`/`?feria=`) y 80 recorridos e2e en Edge (los 66 anteriores, 6 actualizados, más 14 nuevos en `tests/e2e/experiencia.spec.js`): aceptación móvil completa, búsqueda conservada al volver, tienda sin WhatsApp, administración a 390 y 1280 px **sin que la prueba desplace la página**, y sin desplazamiento lateral a 320, 390, 768 y 1280 px con letra al 100 % y al 200 %.
- Pruebas actualizadas (lo que demuestran no cambia): «Ver vitrina» → «Ver productos y contacto»; «Agotado» → «Se acabó»; «Inicio de Sesión / Registro» → «Entrar»; un solo buscador; «Ver lugar y quiénes van»; los filtros del teléfono se abren con su texto; navegación anónima y asociaciones en sus pestañas; la pestaña «Tipos de producto» sin emoji; una respuesta más de `mis_ferias` porque el panel la pide para el aviso.
- Android: compila; 71 pruebas unitarias sin fallos (incluida la nueva `EstadoGuardadoTest`); `lintDebug` con 0 errores (78 avisos, los revisados en los archivos tocados son de estilo y ya existían). En el emulador Pixel 7 (Modo Desarrollador, sin servidor): inicio, «Cuántos tengo» (20 → 19 y de vuelta a 20 con «+»), «Se acabó» y «Deshacer» (vuelve a 20), letra del sistema al 200 %.

**No comprobado (queda para prueba real o con usuarios):**
- Estado «Pendiente → Guardado» contra el servidor real con y sin señal (aquí solo la regla en prueba unitaria y el modo sin servidor).
- Invitaciones por responder en el inicio Android con datos del servidor (el modo sin servidor no tiene ferias).
- Lectores de pantalla (TalkBack, NVDA) y teclado físico de punta a punta; el foco y el cierre con Escape sí tienen e2e.
- Teclado en pantalla de un celular real sobre la barra de contacto (se probó la regla CSS, no un teléfono).
- `npm run test:sql` y `test:reglas` no se corrieron: esta tarea no toca SQL ni las reglas de Firebase.
- Comprensión real del lenguaje y del orden: ver las tareas de abajo.

## 7. Cinco tareas para probar con personas reales

Redactadas como objetivos; no dicen qué tocar.

1. *(Compradora, celular)* «Una amiga te mandó por WhatsApp el enlace de Tejidos Austral. Quieres regalar un gorro: averigua cuánto cuesta y pregúntale a la dueña si lo tiene en otro color.»
2. *(Compradora, celular)* «Buscas kuchen para el domingo. Encuentra quién vende en Alerce y cuánto cuesta, y vuelve a lo que estabas mirando antes.»
3. *(Emprendedora, app, una mano)* «Estás en la feria y acabas de vender el último pan amasado. Deja tu tienda al día antes de atender a la siguiente persona.»
4. *(Emprendedora, app, sin señal)* «Te llegaron 6 frascos de miel mientras no tienes internet. Anótalos y dinos si tus clientas ya lo pueden ver.»
5. *(Administradora municipal, computador)* «Mañana entra una nueva coordinadora a ayudarte con las ferias. Dale acceso al panel y revisa después quién hizo cambios esta semana.»

Para cada una: anotar dónde dudan, qué tocan primero y si terminan sin ayuda.

## 8. Capturas

`docs/experiencia/capturas/antes/` y `docs/experiencia/capturas/despues/` (regenerar la web con `node tests/e2e/capturas_demo.js <carpeta> todo` y el recorrido con `… recorrido`).

| Pantalla | Antes | Después |
|---|---|---|
| Portada móvil (390 px) | `antes/portada-movil.png` | `despues/portada-movil.png` |
| Portada escritorio | `antes/portada-escritorio.png` | `despues/portada-escritorio.png` |
| Tienda por enlace, móvil | `antes/tienda-enlace-movil.png` | `despues/tienda-enlace-movil.png` |
| Misma tienda, escritorio | `antes/tienda-enlace-escritorio.png` | `despues/tienda-enlace-escritorio.png` |
| Nombre largo a 320 px | `antes/tienda-nombre-largo-320.png` | `despues/tienda-nombre-largo-320.png` |
| Admin · Administradores (escritorio y móvil) | `antes/admin-administradores-*.png` | `despues/admin-administradores-*.png` |
| Emprendedora web, móvil | `antes/emprendedora-web-movil.png` | `despues/emprendedora-web-movil.png` |
| Inicio emprendedora Android | `antes/android-inicio-emprendedora.png` | `despues/android-inicio-emprendedora.png` (y `-letra-200`) |
| Mis productos Android | `antes/android-mis-productos.png` | `despues/android-mis-productos.png`, `-letra-200`, `android-se-acabo-con-deshacer.png` |
| Recorrido de aceptación móvil | — | `despues/recorrido-1…4-*.png` |

## 9. Archivos

- Web: `index.html` (cabecera, portada, ficha de tienda, panel admin, pie), `css/alerce.css` (nuevo), `js/main.js`, `js/vitrina6.js`, `js/catalogo.js` (etiqueta «Se acabó»).
- Android: `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/componentes/Componentes.kt`, `ui/emprendedora/DashboardScreen.kt`, `CatalogoScreen.kt`, `AltaProductoScreen.kt`, `TramaApp.kt`, `data/TramaStore.kt` (`cambiarCuantosQuedan`), `data/remoto/Sincronizador.kt` (`productosPorSubir`, `EstadoGuardado`), `app/build.gradle.kts` (`-PsinServidor`), prueba `EstadoGuardadoTest.kt`.
- Pruebas y demo: `tests/e2e/experiencia.spec.js`, `demo_local.js`, `datos_demo.js`, `capturas_demo.js`; ajustes en `web.spec.js`, `entrega6/7/8.spec.js`; `package.json` (`npm run demo`).
- Manuales: `docs/entrega8/manual-administrador.md` y `manual-emprendedora.md` con los nombres nuevos.
