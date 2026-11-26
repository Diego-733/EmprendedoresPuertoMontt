# Matriz de visibilidad por rol y estado de sesión

Estado al 26-nov-2026, después del rediseño comercial. Todo está **sin commit**.

**Fuentes de la matriz:**
- `ui/navegacion/Navegacion.kt` (`itemsSegunRol`, `rutaInicial`).
- `AjustesScreen.kt`.
- `LoginScreen.kt`.
- La web: `main.js` y `vitrina6.js`.

El rol lo asigna **siempre el servidor**, con la tabla `perfiles` y el trigger `al_crear_usuario`. La interfaz solo decide qué se muestra. La protección de los datos la dan RLS y las RPC, que este trabajo no tocó.

## Columnas

| Abrev. | Quién es |
|---|---|
| **Vis.** | Visitante sin cuenta: entró con «Quiero comprar» o abrió la web sin sesión. |
| **Comp.** | Comprador con cuenta. Su rol en el servidor es `VISITANTE` y tiene sesión. |
| **Emp.** | Emprendedora (`EMPRENDEDORA`). |
| **Adm.** | Administración (`ADMIN`). |
| **Diag.** | Diagnóstico técnico: solo en compilaciones de depuración o con rol ADMIN. |

## Android

| Elemento | Vis. | Comp. | Emp. | Adm. | Notas |
|---|:-:|:-:|:-:|:-:|---|
| Vitrina: pestañas Productos, Emprendimientos y Asociaciones, buscador y «Filtros» | ✔ | ✔ | — | — | Nuevo: la pestaña global «Productos» reutiliza `ProductoRepositorio.publicados()`, los mismos datos públicos de antes. |
| Ficha de tienda y de producto, contacto por WhatsApp o llamada | ✔ | ✔ | ✔* | ✔* | *Se llega por enlace o QR. Los productos ocultos nunca aparecen (`ReglasCatalogo.estadoPublico`). |
| Precio | ✔ | ✔ | ✔ | ✔ | Si la tienda lo oculta, se ve «Consultar precio» (`ReglasVitrina.textoPrecio`). |
| Mapa de ferias y Ayuda | ✔ | ✔ | — | — | Pestañas «Mapa ferias» y «Ayuda». |
| Seguir tiendas, calificar y avisos de ferias | — | ✔ | — | — | Requiere cuenta. El visitante ve una invitación a entrar. |
| «Crear mi tienda y empezar a vender» (Mi perfil) | — | ✔ | — | — | Registro abierto: es la desviación de RN-01 (ver [propuesta-registro-controlado.md](propuesta-registro-controlado.md)). |
| Inicio, Productos (Mis productos), Venta, Ferias, Tienda | — | — | ✔ | — | Solo los datos de su tienda; RLS lo garantiza. |
| Asistente Alerce, Centro de ayuda y modo sencillo | — | — | ✔ | — | Las operaciones se confirman con una tarjeta y llaman a las funciones de siempre. |
| Inicio de administración, Ferias, Emprendedoras y **Gestión** (v2) | — | — | — | ✔ | Desde v2 lee datos **reales** con las mismas RPC de la web (`listar_emprendedoras`, `listar_admins`, `ver_bitacora`, `uso_tipos_producto`), protegidas con `es_admin()`. Escribe solo en «Autorizar correo» (`cuentas_autorizadas`) y en «Activar/Desactivar tienda» (`cambiar_estado_emprendedora`, con confirmación). Antes mostraba datos de ejemplo. |
| Ajustes: accesibilidad, voz y tamaño de texto | ✔ | ✔ | ✔ | ✔ | |
| Aviso de privacidad | ✔ | ✔ | ✔ | ✔ | Sigue accesible desde Ajustes / Mi perfil, sin cambios. |
| **Diagnóstico técnico** (App Check / Play Integrity) | — | — | — | ✔ | **Cambiado:** antes «Protección de la app» se veía en Ajustes para todos. Ahora está plegado bajo «Diagnóstico técnico (administración)», solo para ADMIN o compilaciones de depuración. |
| Credenciales de prueba en la pantalla de ingreso | Diag. | Diag. | Diag. | Diag. | Ya estaban limitadas a `BuildConfig.DEBUG && !ConfigSupabase.configurado`; no se cambiaron. |

### Qué NO cambió en la protección

- App Check y Play Integrity siguen inicializándose igual, con los mismos proveedores y reglas.
- Solo cambió **dónde se ve** el panel.
- El panel ahora dice textualmente que hoy App Check lo verifica solo Firebase (Realtime Database), y que Supabase y la web no lo verifican. Así no se promete una protección que no existe.

## Web

| Elemento | Vis. | Comp. | Emp. | Adm. | Notas |
|---|:-:|:-:|:-:|:-:|---|
| Vitrina: Tiendas y Asociaciones, buscador principal y «Filtros» | ✔ | ✔ | ✔ | ✔ | Nuevo: el resumen dice «Filtros (n)», hay chips de filtros activos que se pueden quitar y un «Limpiar filtros». |
| Ficha de tienda con contacto fijo («Tu mensaje le llega a…») | ✔ | ✔ | ✔ | ✔ | |
| «Registrar mi Emprendimiento» (modal de registro) | ✔ | — | — | — | Registro abierto; desviación de RN-01 (ver propuesta). |
| Panel de la emprendedora | — | — | ✔ | — | |
| Administración: ferias, cuentas (W04), asociaciones y bitácora | — | — | — | ✔ | «Autorizar cuenta de emprendedora» usa `cuentas_autorizadas`. |
| Asistente web (ayuda y navegación) | ✔ | ✔ | ✔ | ✔ | Por rol: las operaciones de la emprendedora responden «es para las emprendedoras» a los demás. |

## Riesgos que quedan abiertos (fuera del alcance de este rediseño)

1. **Registro abierto de emprendedoras** en la web y en Android. Se documenta y se propone, sin implementar.
2. **App Check no cubre Supabase ni la web.** Se informa con honestidad en el diagnóstico técnico. Cubrirlo exigiría verificar tokens en el servidor, por ejemplo en las Edge Functions; es una decisión aparte.
