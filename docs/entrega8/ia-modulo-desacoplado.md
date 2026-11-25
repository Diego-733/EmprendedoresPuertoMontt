# Cómo se incorporaría la IA como módulo desacoplado (RNF-MAN-02)

La plataforma funciona completa sin IA (RN-13). La asistencia con IA es un requisito **Condicionado**:
- RF-ADM-05: el administrador la habilita o deshabilita;
- RF-IA-01 a 03: sugerencias al crear un producto; la emprendedora decide; nunca precios.

Este documento explica cómo agregarla **sin rehacer el flujo de creación de productos**.

## 1. Principio

La IA solo **propone** valores para campos que el formulario ya tiene:
- título;
- descripción;
- categoría;
- observaciones sobre la foto.

El flujo actual no cambia: foto → datos → opciones → disponibilidad → «Lo ven todos». Lo único nuevo es una tarjeta de sugerencias **opcional** en el paso de datos, que aparece solo si:
- el administrador habilitó la IA;
- hay conexión;
- el proveedor respondió.

En cualquier otro caso no se muestra nada y el producto se crea igual.

## 2. Piezas

```
App / Web (formulario existente)
   │  1. foto ya comprimida + tipo elegido (sin datos personales)
   ▼
AsistenteProducto (interfaz)  ←─ implementación NULA por defecto (devuelve "sin sugerencias")
   │  2. si está habilitado: llama al servidor
   ▼
Función del servidor «sugerir_producto» (Edge Function o servicio aparte)
   │  3. guarda la clave del proveedor de IA (nunca en la app ni en la web)
   ▼
Proveedor de IA (modelo de visión y lenguaje)
```

- **Interfaz en Android:** `data/asistente/AsistenteProducto.kt` con `suspend fun sugerir(foto: ByteArray, tipo: String?): Sugerencias?`.
  - `AsistenteNulo` siempre devuelve `null`; es el valor por defecto.
  - `AsistenteRemoto` llama al servidor.
  - La pantalla `AltaProductoScreen` solo conoce la interfaz, igual que hoy conoce los repositorios (RNF-DAT-02).
- **Interfaz en la web:** `js/servicio.js` agrega `sugerirProducto(blob, tipo)`. Si la función no existe o falla, devuelve «sin sugerencias».
- **Habilitar o deshabilitar (RF-ADM-05):** una fila más en la tabla `configuracion` (clave `ia_asistencia`, valor `ACTIVA`/`INACTIVA`). Se maneja igual que `navegacion_anonima`: el panel web la cambia, solo ADMIN escribe (RLS ya existente) y queda en la bitácora por el disparador de la 09.
- **Servidor:** recibe la foto y el tipo, consulta al proveedor y devuelve JSON con `titulo`, `descripcion`, `categoria` (de la lista `CATEGORIAS` existente) y `observaciones_foto`. **Nunca devuelve precio** (RF-IA-03): el esquema de respuesta no tiene ese campo y el servidor descarta cualquier precio que llegue.

## 3. Interacción (RF-IA-02)

Cada sugerencia aparece **al lado** del campo, con tres botones: **Usar**, **Editar** o **Descartar**.

Nada se escribe en el producto sin que la emprendedora toque «Usar» o «Editar». Las observaciones de la foto («está oscura», «se ve cortada») son consejos y no bloquean el guardado.

## 4. Por qué no rompe nada

- El formulario, sus validaciones (`FormularioProducto.kt`, `catalogo.js`) y `guardar_producto` en el servidor no cambian.
- Las sugerencias solo llenan campos que la persona ya podía escribir.
- Sin conexión, sin habilitar o con error del proveedor, se comporta exactamente como hoy.
- Las pruebas existentes siguen valiendo. Se agregan pruebas del adaptador:
  - el asistente nulo no muestra nada;
  - una sugerencia con precio se descarta;
  - «Descartar» no cambia el formulario.

## 5. Privacidad y costo

- Solo se envían la foto del producto y su tipo: ni el nombre de la emprendedora, ni su contacto, ni sus ventas.
- El aviso de privacidad se actualiza antes de habilitarla.
- La clave del proveedor vive solo en el servidor (RNF-SEG-03).
- El costo depende del proveedor. Por eso la opción nace **INACTIVA** y la habilita el administrador cuando haya financiamiento.
