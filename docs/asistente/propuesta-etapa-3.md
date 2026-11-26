# Propuesta técnica — Etapa 3 del Asistente Alerce (NO implementada)

> **26-nov-2026:** implementada en lo esencial (ventas, cantidades, quién lo ve, edición, ferias, consultas, modo sencillo y asistente web). Detalle, decisiones tomadas y lo que falta en [evolucion-integral.md](evolucion-integral.md).
> - **Resuelto:** la navegación al girar (§7.1); las ferias se confirman desde el asistente con la misma operación (§7.2); asistente web proporcionado (§7.3).
> - **Sigue abierto:** el intérprete por puntaje se hizo dentro del motor actual (opción A, §6), pero falta el corpus de frases reales.

Diseño original (se conserva como referencia).

## 1. Principio

> **El asistente prepara; la persona confirma; la operación de siempre ejecuta.**

- La única forma de escribir datos sigue siendo una función que ya existe en `TramaStore` o `FeriasStore`. Esas funciones usan los repositorios, la cola sin señal y las RPC con RLS de siempre.
- No hay rutas nuevas al servidor, ni SQL, ni `service_role`, ni cambios a RLS o a las reglas de negocio.
- El asistente nunca dice «listo» o «guardado» por su cuenta. Repite el **estado real** que devuelve la operación:
  - `EstadoSync.PENDIENTE` → «Quedó en tu celular; se sube cuando haya señal».
  - `ResultadoRespuesta.CONFIRMADA_POR_SERVIDOR`, `PENDIENTE_DE_CONEXION` o `RECHAZADA`, según corresponda.

## 2. Piezas comunes nuevas

| Pieza | Qué hace |
|---|---|
| `ResolutorProductos` (lógica pura, con pruebas) | De «los chalecos azules» a productos **de esta tienda**. Compara nombres normalizados, singular y plural, palabras en común y opciones (tallas y colores). El resultado es `Unico(p)`, `Varios(≤4)` o `Ninguno`. Nunca elige por parecido si la puntuación es baja: muestra opciones con nombre, precio y cuántos quedan. |
| `AccionPendiente` (tipada, de un solo uso) | `PrepararVenta`, `CambiarCantidad`, `MarcarSeAcabo`, `AbrirEdicion`, `ResponderInvitacion`, `AbrirAjusteTienda`. Cada una lleva un `id` único y se **consume una sola vez**, para que un doble toque o un «sí» repetido no ejecuten dos veces. |
| `TarjetaConfirmacion` | Muestra «Antes → Después», con «Confirmar» y «Cancelar» de 48 dp y el foco de TalkBack en el resumen. Un «sí» por voz cuenta solo mientras la tarjeta está a la vista y es lo último que preguntó el asistente. La lectura automática nunca confirma. |

## 3. Cada capacidad, con los requisitos y la operación que reutiliza

### 3.1 Registro de ventas (RF-VEN-01 a 05)

- **Flujo:** «Vendí dos chalecos a quince mil»
  1. `ResolutorProductos` encuentra el producto, o pregunta cuál.
  2. Se llena el **carrito de siempre** (`TramaStore.carritoVenta`, con la misma lógica de `agregarAlCarrito`).
  3. Se abre «Venta».
  4. La persona revisa, toca «Continuar al pago», elige el medio de pago y toca «Confirmar pago». Eso llama a `registrarVenta()`, que es lo de siempre.
- **El asistente no llama a `registrarVenta`.**
- **Atomicidad e idempotencia:** ya las da `registrar_venta`.
  - Cada venta lleva un UUID creado una sola vez al confirmar (`v_<uuid>`), y el reintento de la cola usa ese mismo id.
  - Como el asistente no confirma, no hay una segunda vía que pueda duplicar.
- **Casos especiales:**
  - **Precio dicho distinto del producto:** se avisa («Dijiste $15.000; el producto cuesta $18.000») y se ofrecen las dos opciones. El precio se aplica como lo hace hoy el carrito, con historial (RF-VEN-03). Nunca se cambia el precio del producto.
  - **Carrito con cosas:** se pregunta «¿agregarlo a lo que ya tienes o empezar de nuevo?».
  - **Pieza única ya vendida o sin unidades:** se dice tal cual. No se ofrece vender.
- **Sin señal:** igual que hoy, la venta queda PENDIENTE y se descuenta en el celular. El asistente lo dice así.

### 3.2 Inventario (RF-PRO-07, RF-PRO-09)

| Frase | Operación existente |
|---|---|
| «Se acabó el chaleco» | `marcarAgotado(id)`, con el «Deshacer» de siempre |
| «Me quedan tres» | `cambiarCuantosQuedan(id, varianteId, 3)`. Conserva `stockBase`, así que el servidor aplica solo la diferencia y no pisa una venta hecha en otro dispositivo. |
| «Me llegaron diez» | Lo mismo con «actual + 10», mostrando la suma antes de confirmar |

- Siempre con `TarjetaConfirmacion`: «Chaleco gris: 2 → 3».
- Si tiene opciones (tallas o colores), primero se pregunta cuál.
- Cantidades fuera de 0–9999 se rechazan, igual que la pantalla.

### 3.3 Edición de productos (RF-PRO-04)

- «Cambia el precio del chaleco a 18 mil» abre `abrirEdicion(id)` con el cambio ya puesto en el paso «Datos».
- La persona guarda con «Guardar en Mis productos», que es `guardar_producto`, con sus validaciones.
- **Borrar nunca por voz:** solo se explica, porque es irreversible y exige confirmar en la pantalla.

### 3.4 Participación en ferias (RF-FER-08, RF-FER-09)

- «Sí, participo en la Feria Navideña»:
  1. Se busca entre las invitaciones **pendientes y vigentes** de `FeriasStore.misFerias`.
  2. Si hay varias, se pregunta cuál.
  3. Una tarjeta muestra el nombre, la fecha y el lugar.
  4. «Confirmar» llama a `FeriasStore.responder(feriaId, participo)`, la misma función del botón «Participo».
- La respuesta se dice según el resultado real:
  - **Confirmada:** «Quedó confirmada».
  - **Sin señal:** «Sin señal: quedó en tu celular y se envía sola».
  - **Rechazada:** se lee el mensaje de la regla del servidor, por ejemplo «sin cupos».
- **Decisión pendiente (tuya):** ¿se puede confirmar desde el asistente, o se lleva siempre a «Ferias» para tocar el botón ahí?

### 3.5 Gestión de tienda (RF-CFG-01 a 03)

- «Cambia mi WhatsApp al +56 9…» abre «Tienda» con el campo ya escrito y con el mismo validador.
- Guarda la persona («Guardar cambios», que llama a `guardarTienda`).
- Mostrar u ocultar precios y otros interruptores: solo se lleva a la pantalla, porque afectan lo que ven todos los clientes.

## 4. Productos ambiguos

- «el chaleco» con tres chalecos → tres opciones: «Chaleco gris · $18.000 · quedan 2», etc.
- «Ninguno» → «No encontré «poncho» en tus productos. ¿Quieres agregarlo?», sin crear nada.
- Las opciones muestran siempre el precio y cuántos quedan, para no equivocarse entre dos parecidos.
- La lista se arma con los productos **de esta tienda** (`productos.filter { tiendaId == tienda.id }`).

## 5. Confirmación, atomicidad, idempotencia y sin conexión

| Riesgo | Cómo se evita |
|---|---|
| Ejecutar sin querer | `TarjetaConfirmacion` obligatoria para todo lo que cambia datos. Un «sí» vale solo para la tarjeta visible. |
| Doble ejecución | `AccionPendiente` de un solo uso. En ventas, el id de la venta y la idempotencia de `registrar_venta` (y además el asistente no confirma ventas). |
| Pisar cambios hechos en otro dispositivo | `stockBase` (diferencias), que ya existe. |
| Afirmar algo falso | El texto final se arma con el resultado devuelto, nunca antes. |
| Sin señal | Las mismas colas de hoy (`Sincronizador`, `respuestasPendientes`). Se dice «quedó en tu celular». |
| Otra cuenta | Todo sale del estado de la cuenta con sesión; la conversación se borra al cerrar sesión (ya hecho). |

## 6. Límite del intérprete local al crecer — decisión necesaria

El intérprete actual revisa las intenciones en un orden fijo de expresiones regulares. Con la etapa 3 (unas 15 intenciones más, todas con nombres de productos y números) aparecen varios problemas:

- **Choques:** «sube» puede ser crear o subir el precio; «feria» aparece en nombres de productos.
- **Datos más complejos:** varios productos, opciones (tallas y colores) y referencias como «el mismo».
- **Mantención:** cada frase nueva puede romper otra.

| Opción | Costo | Pros | Contras |
|---|---|---|---|
| A. **Reordenar las reglas por puntaje** (cada intención puntúa y el resto de la frase se interpreta según la intención ganadora) + **corpus de frases reales** como pruebas | $0, sin internet | Sigue siendo predecible y verificable; mejora los choques | Sigue sin entender frases muy libres |
| B. Clasificador pequeño en el celular (unos MB) para la intención + las reglas actuales para los datos | $0 por uso; APK más grande | Más flexible con frases nuevas | Hay que entrenarlo con frases chilenas reales; resultados menos predecibles |
| C. IA en el servidor (Edge Function, que solo devuelve una `AccionPendiente` en JSON con esquema fijo) | Pago por uso | Lo más flexible | Necesita señal; los datos van a un tercero; requiere presupuesto y aviso de privacidad (`docs/entrega8/ia-modulo-desacoplado.md`, hoy INACTIVA) |

**Recomendación:** A antes de la etapa 3. Además, juntar 100–200 frases reales de emprendedoras (con su permiso) como pruebas de regresión. Dejar B o C para la etapa 4.

## 7. Otras decisiones de arquitectura que te corresponden

1. **La app vuelve a Inicio al girar el celular o cambiar el tamaño de letra.** Es un problema anterior a este trabajo y afecta a toda la app. Corregirlo exige guardar el historial de pantallas de forma persistente (`rememberSaveable` con `Ruta` serializable). Es un cambio transversal; recomiendo hacerlo antes de la etapa 3, porque los flujos de confirmación son más largos.
2. ¿Confirmar ferias desde el asistente o llevar siempre a «Ferias»? (punto 3.4).
3. ¿Asistente en la web? Hoy la web solo tiene lectura en voz alta de la guía.

## 8. Pruebas previstas para la etapa 3

- `ResolutorProductos`: casos con nombres parecidos, plurales y opciones.
- Cada `AccionPendiente`: se consume una sola vez y no ejecuta sin confirmar.
- Integración con `TramaStore`: carrito precargado sin llamar a `registrarVenta`; `cambiarCuantosQuedan` conserva `stockBase`.
- Las mismas suites de hoy: SQL 570, e2e, Android.
- Emulador y teléfono físico con TalkBack.
