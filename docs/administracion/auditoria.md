# Auditoría de funcionalidades: reales y simuladas (26-nov-2026)

Revisión de Android (`App para el celular`) y la web (`LA PAgina (segun)/proyectode10`), contra el código y las migraciones (`supabase/`).

**Categorías:**

| | Significado |
|---|---|
| **S** | conectada al servidor |
| **L** | solo local (este dispositivo) |
| **Sim** | simulada |
| **P** | parcial |
| **1P** | exclusiva de una plataforma |
| **Pend** | pendiente |
| **Demo** | solo en demostración |

## Hallazgos corregidos en este encargo

| # | Antes | Categoría antes | Ahora |
|---|---|---|---|
| 1 | **Registro abierto de emprendedoras.** `tipo_cuenta=EMPRENDEDORA` en los metadatos o `crear_mi_tienda()` convertían a cualquiera en vendedora (04). | S, pero inseguro | **Migración 11** (local): solo con un código de activación entregado por la administración. Ver [registro-controlado.md](registro-controlado.md). |
| 2 | **App conectada que arrancaba con datos de ejemplo** (`MockData`). En un celular nuevo y sin señal, «Vendido este mes» y la tienda mostraban ventas inventadas. | Sim (en producción) | Los datos de ejemplo se cargan **solo** en el APK de pruebas sin servidor o en el modo de prueba del equipo (`TramaStore.usarEjemplos`, `cargarEjemplos()`). Lo controla la prueba `laAppConectadaNoUsaDatosDeEjemplo`. |
| 3 | **Panel admin Android, «Autorizar correo» en el modo de ejemplo:** agregaba la cuenta solo en el celular. | L (aparentaba guardar) | Sin servidor, toda operación responde «Versión de prueba sin servidor: esta operación no se guarda en ninguna parte». |
| 4 | **«Nueva feria» en Android:** creaba un borrador local que nunca llegaba al servidor (se quitó del inicio en v2). | L | **Formulario real:** el mismo upsert de la web, que queda en BORRADOR. Además: publicar, cancelar con motivo, invitar o agregar, checklist y asistencia, con las funciones de 07. |
| 5 | **Android admin:** asociaciones, tipos y administradores eran de solo lectura (v2). | P / 1P (web) | Crear y editar asociaciones e integrantes, tipos de producto (con borrado si nadie los usa), crear, desactivar y reactivar administradores, y «App sin cuenta». Todo usa las mismas funciones y tablas que la web. |
| 6 | **Emprendedoras en Android:** activar y desactivar existían (v2), pero no había edición, asociaciones, pendientes, revocar ni regularizar. | P | Completo (ver la matriz). |
| 7 | **Cifra «Seguidores»** de la emprendedora en Android, desde MockData. | Sim | Se quitó en v2. La función interna `TramaStore.seguidores()` sigue sin usarse; la web ya decía «Sin datos». |
| 8 | **Cuenta desactivada:** el servidor ya bloqueaba (10), pero las apps no lo explicaban y el asistente seguía ofreciendo operar. | P | Aviso en el Inicio y en Ajustes (Android), y en el panel web. El asistente no ejecuta acciones que cambian datos con la cuenta desactivada ni con una cuenta que no sea de emprendedora. |
| 9 | **Contraseña:** no se podía ver al escribirla ni cambiarla con la sesión iniciada. | Pend | Botón Mostrar/Ocultar (Android y web) y cambio con reautenticación (Android y web). |
| 10 | **Etiqueta «Emprendedoras»** cortada en la barra inferior con letra al 200 %. | — | Ahora es «Cuentas» y entra completa (captura `android-admin-barra-200.png`). |

## Inventario por área (estado después del encargo)

| Área | Android | Web | Notas |
|---|---|---|---|
| Vitrina, fichas y búsqueda | S | S | Datos públicos con RLS. |
| Carrito (pedido del comprador) | L (por diseño) | L (por diseño) | Se envía a cada tienda por WhatsApp; no hay pagos en línea. |
| Pago con QR | — | — | **No existe**: no hay un pago QR simulado. Los medios de pago de una venta solo se anotan. |
| Ventas (`registrar_venta`) | S, con cola sin señal | S | Sin cambios; privacidad RN-10 (la administración no las ve). |
| Inventario (`guardar_producto`, stockBase) | S, con cola | S | Sin cambios. |
| Estado «Guardado» / «por subir» | S | S | Refleja la cola real. |
| Seguimientos y calificaciones | S (07) | S (07) | La web conserva además favoritos locales (`trama_tiendas_favoritas`): **L**. |
| Avisos (notificaciones) | S (07–08) | S | El envío automático (08) depende de su activación (fuera de este encargo). |
| Mensajes por formulario (contactos) | S | S | |
| Ferias para la emprendedora (mis ferias, responder) | S | S | La web conserva una sección antigua de inscripciones **solo en el navegador** (así rotulada). |
| Ferias, administración | **S (nuevo)** | S | Imagen, punto en el mapa y avisos manuales: **1P web**. |
| Asociaciones e integrantes | **S (nuevo)** | S | |
| Tipos de producto | **S (nuevo)** | S | Los campos propios del tipo se editan solo en la web (**1P**). |
| Administradores | **S (nuevo)** | S | El servidor impide quedar sin administración. |
| Bitácora | S | S | Solo lectura. |
| Configuración «App sin cuenta» | **S (nuevo)** | S | |
| Diagnóstico técnico (App Check) | S (lectura) | — | Solo ADMIN o depuración. |
| Estadísticas del panel | S | S | Solo conteos reales (cuentas, ferias); ninguna cifra inventada. |
| «Asociaciones (ejemplo local)» y credenciales generadas (web sin servidor) | — | **Demo** | Solo aparece sin servidor configurado. Con servidor está oculta y no se generan contraseñas. |
| Tiendas de prueba y «Entrar Administración» | **Demo** | — | Solo `BuildConfig.DEBUG && !configurado`. Nunca en el APK release conectado. |
| `FeriaDetalleScreen` (ferias de ejemplo del celular) | **Demo / sin acceso** | — | Ninguna pantalla la abre. Se conserva el código sin borrarlo. |

## Lo que queda pendiente o depende de otros

| Pendiente | Situación |
|---|---|
| Migración 11 | Preparada y probada en local, **no aplicada** en Supabase real. |
| Confirmación de correo | La instancia tiene el correo **autoconfirmado**. El código de activación lo compensa, pero la cuenta de quien **compra** no prueba que el correo sea suyo (ver las dependencias). |
| Recuperación de contraseña | Usa el SMTP de la instancia del curso (límite de envíos). No se simula. |
| TalkBack | No está validado en un teléfono físico. |
