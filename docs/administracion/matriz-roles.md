# Matriz definitiva de roles y permisos (26-nov-2026)

**Principio:** ocultar un botón **nunca** reemplaza una comprobación. Cada operación indica qué la autoriza en el servidor (RLS o la función con `es_admin()`, `es_duena_de()` o `auth.uid()`). Las rutas directas, las peticiones HTTP modificadas y los clientes antiguos llegan a las mismas reglas.

**Perfiles:**

| Abrev. | Quién | Rol en el servidor |
|---|---|---|
| **Vis.** | visitante sin cuenta | rol anon |
| **Comp.** | comprador con cuenta | `VISITANTE` |
| **Pend.** | cuenta con autorización pendiente | `VISITANTE` con fila sin usar en `cuentas_autorizadas` |
| **Emp.** | emprendedora autorizada y activa | `EMPRENDEDORA` |
| **Des.** | emprendedora desactivada | `tiendas.activa = false` |
| **Adm.** | administración | `ADMIN` |

## Pantallas y navegación

| Pantalla | Vis. | Comp. | Pend. | Emp. | Des. | Adm. |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| Bienvenida, vitrina, fichas, mapa de ferias y ayuda | ✔ | ✔ | ✔ | ✔ (por enlace) | ✔ (por enlace) | ✔ (por enlace) |
| Mi perfil: seguir, calificar y avisos | — | ✔ | ✔ | — | — | — |
| «Activar mi tienda con un código» | — | ✔ | ✔ (destacado) | — | — | — |
| Inicio, Productos, Venta, Ferias, Tienda y asistente | — | — | — | ✔ | ✔ con aviso; las escrituras fallan | — |
| Panel: Inicio, Ferias, Cuentas, Gestión (Android) y Administración (web) | — | — | — | — | — | ✔ |
| Diagnóstico técnico (App Check) | — | — | — | — | — | ✔ (y depuración) |
| Cambiar contraseña | — | ✔ | ✔ | ✔ | ✔ | ✔ |
| Aviso de privacidad | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |

## Operaciones y comprobación del servidor

| Operación | Quién puede | Comprobación en el servidor |
|---|---|---|
| Ver tiendas, productos publicados y ferias publicadas | Todos | RLS `*_ver`. Las tiendas desactivadas no se ven (10). |
| Registrarse | Todos | Auth. `al_crear_usuario` (11) deja VISITANTE, salvo ADMIN de la lista (09). |
| Seguir y calificar | Comp., Pend. | Funciones de 07 con `auth.uid()`. |
| **Activar emprendedora** | Pend. (con su código) | `activar_con_codigo`: correo de la sesión, huella, vigencia, un solo uso y 5 intentos. |
| Crear tienda | Emp. sin tienda | `crear_mi_tienda`: `NO_AUTORIZADA` para VISITANTE (11). |
| Productos (`guardar_producto`, fotos) | Emp. | `es_duena_de(tienda)`. `CUENTA_DESACTIVADA` (10). Carpeta propia en Storage. |
| Ventas (`registrar_venta`) e historial | Emp. (solo las suyas) | `ventas_duena`. **Admin: 0 filas (RN-10).** |
| Responder invitaciones | Emp. | `responder_invitacion` (07) y `CUENTA_DESACTIVADA`. |
| Autorizar, regenerar código, revocar, regularizar | Adm. | `es_admin()` (11). |
| Editar, activar y desactivar emprendedoras | Adm. | `editar_emprendedora` y `cambiar_estado_emprendedora` con `es_admin()` (10). |
| Asociaciones e integrantes | Adm. | RLS `asociaciones_admin` y `tienda_asociaciones` (07). |
| Ferias: crear, editar, publicar, cancelar, invitar y asistencia | Adm. | RLS de ferias más las funciones de 07 con `es_admin()`. |
| Tipos de producto | Adm. | `guardar_tipo_producto` y `eliminar_tipo_producto` (10). |
| Administradores | Adm. | `crear_admin` y `cambiar_estado_admin` (09): no puede desactivarse a sí misma ni quedar sin administración activa. |
| Bitácora | Adm. (lectura) | `ver_bitacora` (09). La escriben los disparadores, no los clientes. |
| Configuración «App sin cuenta» | Adm. | RLS de `configuracion` (06/09). |

## Prohibido y comprobado con pruebas SQL (`tests/sql/pruebas_registro_controlado.js`)

| Intento | Resultado |
|---|---|
| Una compradora pide ser emprendedora o admin en los metadatos | Queda VISITANTE. |
| Una compradora llama a `crear_mi_tienda` | `NO_AUTORIZADA`. |
| Una compradora cambia su rol con un UPDATE a `perfiles` | 0 filas. |
| Una compradora inserta en `cuentas_autorizadas` o en `tiendas` | RLS lo rechaza. |
| Una emprendedora o compradora autoriza, regenera, crea admins o desactiva tiendas | `SOLO_ADMIN`. |
| Llamar a `activar_con_codigo` sin sesión, o a las ayudas internas | `permission denied`. |
| Usar el código de otra persona | `NO_AUTORIZADA`. |
| Código vencido, 5 intentos o código revocado | `CODIGO_VENCIDO`, `DEMASIADOS_INTENTOS` o `NO_AUTORIZADA`. |
| Desactivada: guardar productos o reactivarse por la API | `CUENTA_DESACTIVADA`. `crear_mi_tienda` no le da otra tienda. |
| Administración consulta ventas | 0 filas. |
| Emprendedora consulta ventas de otra | 0 filas. |

## Qué protege cada mecanismo

| Mecanismo | Protege | No protege |
|---|---|---|
| RLS y funciones de Supabase | Todas las lecturas y escrituras de datos, para todos los clientes (también la API directa). | — |
| Firebase App Check / Play Integrity | Solo Realtime Database de Firebase (comprobación de la app). | **No** protege Supabase ni la web. |
| Ocultar controles en las apps | La comodidad y la claridad. | **No** es seguridad. |
