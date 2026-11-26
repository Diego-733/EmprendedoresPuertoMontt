# Revisión crítica de las etapas 1 y 2 (26-nov-2026)

Revisión independiente del Asistente Alerce y de la lectura en voz alta, antes de la prueba en un teléfono real. No se dio nada por bueno porque las pruebas pasaran: se probó con unas 40 frases nuevas (no usadas al desarrollar), en el emulador y revisando el código.

**Estado:** todo queda local y sin commit. No se tocaron políticas RLS, reglas de negocio, `guardar_producto`, `registrar_venta` ni servicios reales.

## 1. Problemas encontrados y corregidos

| # | Gravedad | Problema (cómo se reproducía) | Corrección | Prueba |
|---|---|---|---|---|
| 1 | **Crítica** | La app se cerraba al primer mensaje en el celular. Android compila las expresiones regulares con ICU, que no acepta `(?U)`. En el computador las pruebas pasaban. Se encontró solo al probar en el emulador. | Se quitó `(?U)`, que no hacía falta. | `los patrones no usan opciones que Android no acepta` + emulador |
| 2 | **Crítica** | «ya no quiero» **abría el formulario**: «ya» contaba como «sí». | Una frase con negación nunca es un «sí». Se agregaron formas de cancelar: «chao», «no importa», «déjalo», «no gracias», «mejor no». | `ya no quiero cancela y no abre el formulario`, `una negacion nunca es un si` |
| 3 | **Crítica (inventaba datos)** | Si estaba preguntando el nombre, cualquier respuesta se anotaba como nombre: «¿cuánto vendí este mes?», «no sé», «unos 10 más o menos». | Antes de anotar un nombre se revisa que lo sea: no es una pregunta, ni un «no sé», ni una negación, ni solo un número. Si no sirve, se vuelve a preguntar diciendo qué se espera. | `una pregunta en medio no se anota como nombre…`, `no se o un numero suelto…` |
| 4 | Alta | «sube un poncho a 35 mil, no, a 40 mil» anotaba $35.000, y «tengo 5, no, 4» anotaba 5. | **Autocorrección general:** la frase se separa en «no + dato», «perdón», «mejor dicho», «o sea»… y manda lo último dicho. Funciona sin comas, porque el dictado no las pone. | `la autocorreccion dentro de la frase gana` |
| 5 | Alta | «agrega un gorro a 5000 y una bufanda a 8000» anotaba $5.000 sin avisar. | Si hay dos precios distintos y ninguna corrección, no se elige uno: pregunta «¿cuánto cuesta «Gorro»?» con los dos precios como opciones. | `dos precios distintos no se elige uno solo` |
| 6 | Alta | «$8,500» y «$15 000» no daban precio. «quince luquitas» daba **$15**. | Miles con coma o con espacio (este último solo junto al signo $). «luquita(s)» vale mil, y también «luca y media». | `formatos de precio del dictado` |
| 7 | Alta | En la tarjeta del producto **no se podían escribir espacios**: «Gorro de lana» quedaba «Gorrodelana». Se borraban los espacios a cada letra. | La tarjeta guarda el texto tal cual. Los espacios de los extremos se quitan recién al llenar el formulario. | `la tarjeta deja escribir espacios…` + emulador |
| 8 | Alta | A mitad de un producto, «mejor muéstrame mis productos» respondía «No entendí el precio». Las preguntas también se trataban como respuestas. | **Cambio de tema:** las consultas y los «¿cómo…?» se responden sin perder el producto. Ir a otra pantalla se acepta solo si se pide claramente («muéstrame», «ver», «¿dónde…?»). Así, «chaleco para la feria» sigue siendo un nombre. | `ir a otra pantalla en medio…`, `una palabra suelta de feria…` |
| 9 | Media | «no, quince mil», «no, cuatro», «no, es una bufanda» y «cambia el precio» no se entendían al revisar. | Un valor solo se entiende según el último dato preguntado o corregido; si no hay contexto, pregunta «¿7 es el precio o cuántos tienes?». «Cambia el precio» sin valor pregunta el precio nuevo. | `correcciones consecutivas…`, `un numero suelto se entiende segun…` |
| 10 | Media | «quiero agregar otro producto, una bufanda a 8000» **cambiaba el precio del gorro**. | Pregunta antes de dejar de lado el producto anotado: «Sí, empezar con «Bufanda»» o «No, seguir con «Gorro»». | `otro producto a mitad pregunta…` |
| 11 | Media | Nombres que no se reconocían: «agrégame un collar» (con tilde), «agrega gorro de lana» (sin artículo), «a 15 lucas un gorro de lana quiero subir» (desordenada), «tengo 4 chalecos…», «producto: miel de ulmo». «quiero poner una foto» creaba un producto llamado «Foto». | Verbos con o sin tilde, artículo opcional, nombre antes del verbo y lista de palabras que nunca son un nombre. | `nombres con verbo con tilde…`, `poner una foto no crea…` |
| 12 | Media | «¿cómo borro un producto?» enseñaba a **agregar** uno. No había ayuda para agrandar la letra ni para la lectura en voz alta. | Temas nuevos: «Borrar un producto» y «Agrandar la letra o escuchar en voz alta» (este lleva a Ajustes). | `temas de ayuda que faltaban` |
| 13 | Media | «borra el producto gorro», «me llegaron 10 gorros más», «súbele el precio a los aros» y «cuanto e vendido» no se entendían. | El asistente explica dónde hacerlo y dice que no lo hace él («Yo no borro productos», «Yo no anoto la venta: la confirmas tú»). | `borrar y sumar se explican sin hacerlos` |
| 14 | Media | Si se giraba el celular con el aviso «Tienes un producto a medio hacer» abierto, el producto anotado se perdía. | El aviso queda guardado fuera de la pantalla y vuelve a aparecer. Verificado en el emulador. | Emulador |
| 15 | Media | Al cerrar sesión, la conversación (con montos de ventas) quedaba en memoria. Además, dos cuentas sin tienda cargada podían compartirla. | La conversación se borra al cerrar sesión. Se identifica por cuenta **y** tienda. Verificado en el emulador: después de salir y volver a entrar empieza de cero. | Emulador |
| 16 | Media | El aviso «Tienes un producto a medio hacer» decía «se borra» también cuando eran cambios sin guardar de un producto existente. Ese producto no se borra. | Texto propio para ese caso: «¿Descarto esos cambios…? «X» sigue en Mis productos como estaba». | `cambios sin guardar de un producto existente…` |
| 17 | Media | Lectura en voz alta (Android y web): el aviso tardío de una lectura cortada con el mismo botón dejaba el botón en «Escuchar» mientras seguía hablando. | Cada lectura lleva su propio número. En Android, además, el estado se cambia en el hilo principal. La web se calla al cambiar de pestaña. | `el aviso tardío de una lectura cortada…` (web) + emulador |
| 18 | Baja | Si al primer intento no había voz en español, «Escuchar» desaparecía hasta reiniciar la app, aunque después se instalara una voz. | Al abrir Ajustes se vuelve a intentar. | Revisión de código |
| 19 | Baja | Después de cada respuesta, la pantalla bajaba a la tarjeta y la pregunta quedaba fuera de la vista. | Se muestra el último mensaje; la tarjeta queda debajo. | Emulador (capturas 14–16) |
| 20 | Baja | TalkBack podía repetir el mensaje anterior: al quitar las opciones viejas, cambiaba la burbuja completa, que era una «región viva». | Solo el texto del mensaje es región viva. | Revisión de código; falta TalkBack real |
| 21 | Baja | Barra inferior: al girar el celular la letra quedaba achicada, y al mínimo la palabra se cortaba sin «…». | Se recalcula al cambiar el ancho; al mínimo se usa «…». Se corrigió también el comentario, que decía lo contrario. | Emulador (360 dp, letra 130 %) |
| 22 | Baja | Con pantalla chica, la etiqueta del campo («Escribe o dicta lo que necesitas») se partía en dos líneas sobre el borde. | Etiqueta corta: «Escribe o dicta». | Emulador |

## 2. ¿Entiende con flexibilidad o depende de frases fijas?

**Honestamente: depende de palabras clave.** Lo que se corrigió son **mecanismos generales**, no cientos de frases:

- filtro de lo que sirve como dato;
- negación;
- autocorrección;
- contexto del último dato;
- cambio de tema;
- dos precios distintos.

Eso lo hace mucho más robusto en el flujo principal (crear un producto) y, sobre todo, **más seguro**: cuando no entiende, pregunta en vez de inventar.

Sus límites reales son:

- **Las intenciones se revisan en un orden fijo.** Al agregar comandos, las palabras chocan («feria», «precio», «subir» aparecen en varias). La etapa 3 duplicaría estos choques.
- **Frases con dos productos o con referencias** («el mismo de ayer», «el azul») no se resuelven: se pregunta.
- **Errores de dictado nuevos** se entienden solo si caen en una regla. Por ejemplo, «cuanto e vendido» se agregó; otros no.

Por eso, para la etapa 3 se propone cambiar el modelo de interpretación antes de sumar comandos (ver la [propuesta](propuesta-etapa-3.md), §6). Es una **decisión que te corresponde**.

## 3. Experiencia de uso (revisada en el emulador)

| Pregunta | Respuesta |
|---|---|
| ¿Para qué sirve? | El saludo lo dice, y en Inicio el botón dice «Dime qué necesitas: te enseño o te ayudo a hacerlo». |
| ¿Cómo empezar a hablar? | Hay un micrófono en el campo, que dice «Escribe o dicta», y opciones para tocar. |
| ¿Cuándo escucha? | Solo con el diálogo del sistema abierto, después de tocar el micrófono. Lo dice el aviso inicial. |
| ¿Qué interpretó? | La tarjeta «Tu producto (aún no se guarda)» muestra cada dato y se puede editar. |
| ¿Cómo corregir? | Diciéndolo («no, quince mil») o escribiendo en la tarjeta. |
| ¿Cómo cancelar? | Botón «Cancelar» o diciendo «cancelar», «chao», «ya no quiero»… |
| ¿Cómo volver a escuchar? | «🔊 Escuchar» en cada respuesta y «Escuchar resumen» en la tarjeta. |
| ¿Cómo seguir al formulario? | «Continuar» o «Continuar: agregar foto y guardar». Al volver aparece «Volver a «Añadir producto»». |
| ¿Cuándo se guardó de verdad? | Solo al tocar «Guardar en Mis productos» en el formulario. El asistente lo repite y nunca dice «guardado». |

Además se revisó:

- Las opciones manuales siguen iguales y el asistente es opcional.
- Pantalla chica (360 dp), letra al 130 % y al 200 %.
- Volver atrás y entrar de nuevo: el producto anotado se conserva.
- Giro con el aviso abierto: se conserva (corrección 14).

**Hallazgo anterior a este trabajo (no corregido):** al girar el celular o cambiar el tamaño de letra del sistema, **toda la app vuelve a Inicio**. El historial de pantallas se guarda con `remember` y no sobrevive a que Android recree la pantalla. Afecta a todas las pantallas y no solo al asistente; corregirlo exige un cambio de arquitectura en la navegación. Ver la propuesta, §7.

## 4. Voz y accesibilidad

- **Solo una lectura a la vez.** Tocar otro «Escuchar» corta el anterior (verificado por estado en el emulador).
- **El micrófono calla la lectura** antes de abrirse; también al salir de la pantalla o de la app.
- **Lectura automática** apagada por defecto, y nunca con TalkBack activo.
- **Sin micrófono:**
  - Si no hay reconocedor de voz, el micrófono no aparece y se escribe.
  - Si no se dice nada, aparece «No se escuchó nada…».
  - Alerce no pide permiso de micrófono: lo pide el dictado del sistema. Si se niega, ese diálogo no devuelve texto y queda el teclado.
- **Sin voz en español:** «Escuchar» no aparece y Ajustes explica cómo instalarla.
- **TalkBack:** está instalado en el emulador, pero desde la línea de comandos no se puede hacer el doble toque con la rapidez necesaria, así que **no está verificado**. Falta probar en un teléfono:
  - que no hable encima de TalkBack;
  - que anuncie cada respuesta una sola vez;
  - el orden de lectura de la tarjeta.

## 5. Seguridad y datos

- **Revisado en el código:**
  - El asistente sigue sin escribir datos: solo `Navegar`, `AbrirFormularioProducto` (que llena el **borrador** de siempre) e `IniciarGuia`.
  - Guardar sigue siendo el botón del formulario (`guardar_producto`, RLS).
  - Las ventas solo se hacen en «Venta» (`registrar_venta`).
- **No hay duplicados:** tocar «Continuar» dos veces abre un solo borrador, porque el producto anotado se limpia al primer toque. El borrador no se pisa sin preguntar.
- **Las cuentas no se mezclan:**
  - El borrador ya era por cuenta (`PersistenciaStore` por usuario).
  - La conversación ahora se borra al cerrar sesión y se identifica por cuenta + tienda.

## 6. Pruebas (resultados reales tras las correcciones)

Ver la tabla del [README](README.md#5-pruebas-ejecutadas-resultados-reales).

## 7. Lo que necesita un teléfono físico

1. Dictado real en español de Chile:
   - errores de transcripción reales;
   - cómo escribe los precios el dictado del teléfono;
   - qué pasa al negar el permiso de micrófono del dictado.
2. Voz es-CL (el emulador solo tiene es-US) y el **sonido real**: el emulador corre sin audio, así que se verificó el estado y no lo que se oye.
3. TalkBack completo (punto 4).
4. Gama baja: tiempo de respuesta y memoria.
5. APK release con el servidor real: el guardado real de un producto creado con el asistente y el estado «Pendiente» sin señal.
