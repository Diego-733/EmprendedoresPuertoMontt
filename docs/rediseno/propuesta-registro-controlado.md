# Propuesta: registro controlado de emprendedoras (RN-01)

> **Actualización 26-nov-2026:** implementada en local como la migración 11, con código de activación (la instancia
> tiene el correo autoconfirmado, por lo que autorizar solo el correo no bastaba). **No está aplicada en Supabase real.**
> Ver [docs/administracion/registro-controlado.md](../administracion/registro-controlado.md). El texto de abajo es la propuesta original.

> Esto es solo documentación. No se cambió el servidor, ni RLS, ni el trigger, ni el registro actual. No se bloqueó ni eliminó ninguna cuenta.

## 1. Situación actual (desviación de RN-01)

RN-01 pide que **solo la administración** habilite cuentas de emprendedora. Hoy el servidor hace otra cosa, según `supabase/04_registro_vendedores.sql`, titulado «Registro abierto de emprendedoras/vendedores (RN-01 actualizado)».

| Vía | Qué pasa hoy |
|---|---|
| Trigger `al_crear_usuario` | Si el correo está en `cuentas_autorizadas`, usa ese rol. **Si no está**, y el cliente envía `tipo_cuenta = EMPRENDEDORA` en los metadatos, igual queda como EMPRENDEDORA y se le crea una tienda. ADMIN nunca se acepta desde metadatos; eso está bien. |
| RPC `crear_mi_tienda(p_nombre)` | Cualquier cuenta autenticada que no sea ADMIN pasa a EMPRENDEDORA con una tienda nueva. |

## 2. Dónde aparece el registro abierto en la interfaz

| Lugar | Archivo | Llama a |
|---|---|---|
| Web: modal «Registrar Emprendedor/a» / «🚀 Registrar mi Emprendimiento» | `js/main.js`: `registrarEmprendedorModal`, `abrirModalRegistroEmprendedor` | `auth.signUp` con `tipo_cuenta = EMPRENDEDORA` |
| Web: segundo flujo de registro (alrededor de la línea 3954) y registro normal (alrededor de la 6300) | `js/main.js` | `auth.signUp` |
| Android: pestaña Emprendedora en modo registro, «Activar mi cuenta de emprendedora» | `ui/auth/LoginScreen.kt` | `ClienteSupabase` → `/auth/v1/signup` |
| Android: Mi perfil → «Crear mi tienda y empezar a vender» | `ui/ajustes/AjustesScreen.kt` → `SesionManager.crearTiendaParaUsuario` | RPC `crear_mi_tienda` |

**Nota sobre Android:** el título ya dice «Activar mi cuenta de emprendedora». La app ya está pensada para correos autorizados; es el servidor el que acepta además a cualquiera.

## 3. Flujo propuesto

1. La administración autoriza el correo. Eso ya existe: Android «Emprendedoras → Crear cuenta», web «Autorizar cuenta de emprendedora» (W04). Ambas escriben en `cuentas_autorizadas`.
2. La persona entra con «Activar mi cuenta» usando **ese** correo. El trigger le asigna EMPRENDEDORA y la tienda autorizada.
3. Si alguien sin autorización intenta registrarse como emprendedora:
   - queda como **compradora** (VISITANTE);
   - ve: «Tu correo todavía no está autorizado. Pide a la asociación o a la municipalidad que lo habilite.»
4. Opcional, que decide la administración: una tabla `solicitudes_emprendedora` (correo, nombre, rubro, sector y fecha) que la administración aprueba. Aprobar inserta en `cuentas_autorizadas` y llama a una RPC que promueve la cuenta existente.

## 4. Cambios necesarios (para una tarea futura, con autorización expresa)

### Servidor (migración nueva `11_registro_controlado.sql`, con su `11_revertir.sql`)

- `al_crear_usuario`: quitar la rama `tipo_cuenta = EMPRENDEDORA` para correos no autorizados. Quedan como VISITANTE.
- `crear_mi_tienda`: exigir que el correo esté en `cuentas_autorizadas` con rol EMPRENDEDORA, o aprobado en `solicitudes_emprendedora`.
- **Cuentas existentes:** no se tocan. Las emprendedoras que ya se registraron por la vía abierta siguen igual. Una consulta de solo lectura las lista para que la administración decida (`diagnostico_solo_lectura.sql`).
- Pruebas en `pruebas_rls.sql`:
  - un correo no autorizado con `tipo_cuenta = EMPRENDEDORA` queda VISITANTE;
  - `crear_mi_tienda` sin autorización falla;
  - un correo autorizado obtiene su tienda.

### Interfaz

- **Web:** el modal «Registrar mi Emprendimiento» pasa a ser «Activar mi cuenta (correo autorizado)», con el aviso del punto 3. O bien se convierte en «Solicitar ser emprendedora» si se aprueba la tabla de solicitudes.
- **Android, Mi perfil:** «Crear mi tienda» se reemplaza por «¿Vendes? Pide que habiliten tu correo», con el contacto de la administración. O se mantiene, mostrando el error claro del servidor.
- **Android, Ingreso:** mostrar el aviso de «correo no autorizado» cuando el registro termina con rol VISITANTE en la pestaña Emprendedora.
- **Asistente:** actualizar los temas de ayuda sobre cómo empezar a vender.

## 5. Decisiones que te corresponden

1. ¿Registro solo por invitación, o con una solicitud que la administración aprueba?
2. ¿Qué pasa con las emprendedoras ya registradas por la vía abierta? Recomendación: mantenerlas y revisarlas manualmente.
3. ¿Qué contacto se muestra a quien no está autorizado?
