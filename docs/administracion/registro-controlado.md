# Registro controlado de emprendedoras (RN-01) — migración 11

**Estado:** preparada y probada **solo en local** (`npm run test:sql`, PostgreSQL 17 desechable). **No está aplicada en Supabase real.**

Archivos:
- [`supabase/11_registro_controlado.sql`](../../supabase/11_registro_controlado.sql);
- reversión: [`supabase/11_revertir.sql`](../../supabase/11_revertir.sql);
- pruebas: [`tests/sql/pruebas_registro_controlado.js`](../../tests/sql/pruebas_registro_controlado.js).

## 1. Problema

- **Vía abierta de 04.** `al_crear_usuario` daba el rol EMPRENDEDORA si el cliente enviaba `tipo_cuenta=EMPRENDEDORA` en los metadatos. Además, `crear_mi_tienda()` convertía en emprendedora a cualquier cuenta compradora.
- **Correo autoconfirmado.** En la instancia del curso el correo se confirma solo (`docs/entrega5`): tener una cuenta con un correo **no prueba** ser su dueña. Autorizar «un correo» no basta. Quien supiera qué correo autorizó la administración podría registrarlo antes que su dueña.

## 2. Flujo implementado (sin SMTP ni servicios externos)

1. **La administración autoriza** (Android: «Cuentas» › ➕; web: «Cuentas emprendedoras» › «Autorizar»).
   - Llama a `autorizar_emprendedora(correo, nombre, asociación)`.
   - El servidor genera un **código de activación** de 8 caracteres (alfabeto sin confusiones; azar de `gen_random_uuid()`), de un solo uso y válido por 14 días.
   - El código se **devuelve una sola vez**. En la base queda solo su huella, `sha256(correo:código)`, que nunca va a la bitácora.
2. **La administración lo entrega** en persona o por su canal de confianza («Compartir» o «Copiar» arman un mensaje **sin contraseñas**).
3. **La emprendedora crea su cuenta** con ese correo y **su propia contraseña** (o entra con Google). Queda como compradora: los metadatos ya no dan permisos.
4. **Escribe el código:**
   - Android: al registrarse (campo «Código de activación») o después, en «Mi perfil» › «Activar mi tienda con un código».
   - Web: en el formulario de registro o en «Mi perfil».
5. **El servidor verifica** (`activar_con_codigo`):
   - que el correo **de la sesión** (`auth.users`, nunca uno enviado por el cliente) tenga una autorización vigente;
   - que la huella coincida;
   - que el código no haya vencido, no se haya usado y no lleve 5 intentos fallidos (cada intento queda registrado).
6. **El servidor asigna el rol** EMPRENDEDORA, crea **su** tienda y la suma a la asociación indicada. La autorización queda «usada», con fecha y usuaria.

**La administración nunca conoce ni guarda la contraseña.**

### Qué ya no da permisos

| Vía | Resultado |
|---|---|
| Metadatos del registro (`tipo_cuenta`, `rol`) | Se ignoran. |
| `crear_mi_tienda()` desde una compradora | `NO_AUTORIZADA` (solo crea tienda a una emprendedora autorizada que se quedó sin ella). |
| Insertar en `cuentas_autorizadas`, `perfiles` o `tiendas` | RLS lo impide. Ya existía; ahora está probado de nuevo. |
| Usar el código de otra persona | El código queda ligado al correo de la sesión. |
| Una autorización sin código (clientes antiguos o filas previas) | Queda pendiente con `SIN_CODIGO`. La administración genera uno. |

### Administración

| Operación | Función | Regla |
|---|---|---|
| Generar código nuevo | `regenerar_codigo_emprendedora` | El anterior deja de servir. |
| Revocar | `revocar_autorizacion` | Solo si no se usó. Una cuenta activa se **desactiva** (10), no se revoca. |
| Regularizar | `regularizar_emprendedora` | Deja constancia de que se conserva a una emprendedora de la vía abierta. No cambia nada más. |
| Lista con estados reales | `listar_cuentas_emprendedoras` | ACTIVA · DESACTIVADA · SIN_TIENDA · PENDIENTE_REGISTRO (aún sin cuenta) · PENDIENTE_ACTIVACION (con cuenta, sin código), más «autorizada», «código vigente» e intentos. |
| Estado de la propia cuenta | `estado_mi_cuenta` | Para que las apps expliquen qué falta, sin datos de otras personas. |

**Administradores:** el procedimiento de 09 no cambia. `crear_admin` y `cambiar_estado_admin` impiden desactivarse a sí misma y quedar sin otra administración activa.

## 3. Cuentas existentes: compatibilidad y regularización

- **11 no cambia ningún rol, perfil, tienda, producto, foto, venta, participación, asociación, seguimiento, calificación ni configuración.** Lo comprueba la prueba «11 conserva perfiles, roles, tiendas…».
- **Emprendedoras de la vía abierta:** siguen igual. En la lista aparecen como **«Sin autorización registrada»**, y el inicio de administración lo avisa.
- **Autorizaciones pendientes previas a 11** (sin código): quedan PENDIENTE y piden «Código nuevo».
- **Compradoras que pidieron ser emprendedoras** (metadatos): siguen compradoras. Si la administración las autoriza, activan con código desde «Mi perfil».

### Decisiones que requieren tu aprobación

1. **Qué hacer con las emprendedoras de la vía abierta.**
   - **Recomendación:** revisarlas una por una. Regularizar las legítimas; desactivar (no borrar) las que no correspondan.
   - Una consulta de solo lectura en el SQL Editor las identifica:

     ```sql
     select * from listar_cuentas_emprendedoras() where autorizada = false;
     ```
2. **Activar «Confirm email» en Supabase Auth** (decisión del profesor). No es indispensable para RN-01 gracias al código, pero prueba que cada cuenta es dueña de su correo, también para compradoras.
3. **Vigencia del código y número de intentos** (14 días y 5 intentos): se pueden ajustar.

## 4. Dependencias del profesor o de la infraestructura

| Dependencia | Para qué | ¿Bloquea? |
|---|---|---|
| Aplicar 11 en el SQL Editor (rol con permisos de esquema, **no** `service_role` desde las apps) | Activar el registro controlado en producción | Sí, para usarlo con cuentas reales |
| «Confirm email» en Auth (y su SMTP) | Verificar la titularidad del correo de toda cuenta | No (recomendado) |
| SMTP de la instancia | Recuperar la contraseña por correo | Ya funciona con el límite de la instancia; no se simula |
| Google OAuth | Correo verificado por Google | Ya configurado (Entrega 6) |

## 5. Cuentas desactivadas

| Vía | Qué pasa |
|---|---|
| Servidor (10) | La tienda no se ve, y productos, ventas, participaciones, contactos y la tienda rechazan escrituras con `CUENTA_DESACTIVADA`, también por la API directa o desde un cliente antiguo. |
| Reactivarse | La propia emprendedora no puede (`proteger_activa_tienda`). |
| Reinstalar la app | No sirve: el estado vive en el servidor. |
| Usar el código otra vez | Responde `YA_ACTIVA`, sin efecto. |
| Sesión abierta y operaciones sin señal | Lo que está en cola falla al subir con `CUENTA_DESACTIVADA`. Es un rechazo de regla (`esRechazoDeRegla`): la venta o el producto **sale de la cola, pero queda en el celular marcado como conflicto** con el motivo (`marcarConflictoVenta` / `marcarConflictoProducto`). No se reintenta sin fin, no se pierde en silencio y la emprendedora ve qué pasó. No se sube nada mientras siga desactivada. |
| Interfaz | Aviso en el Inicio y en Ajustes (Android) y en el panel web. El asistente no ejecuta acciones que cambian datos. |
| Datos | Al reactivar, todo vuelve tal como estaba. |
