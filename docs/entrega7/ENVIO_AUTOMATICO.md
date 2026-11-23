# Envío automático de avisos (Entrega 7)

Los avisos de la entrega 7 se envían solos, aunque ningún computador del grupo esté encendido.

Los siete tipos llegan por el mismo camino: `INVITACION`, `AGREGADA`, `RECORDATORIO`, `AVISO_FERIA`, `FERIA_CANCELADA`, `PARTICIPACION_SEGUIDA` y `CONTACTO`. Da igual si la acción se hizo desde la web o desde la app.

## 1. Cómo funciona

```
Acción en la web o la app  ──►  Supabase guarda el aviso (07, igual que antes)
(invitar, «Participo», contacto…)        │
                                         ▼  trigger de 08 (solo si quedó PENDIENTE)
                         pg_net pide UNA ejecución a GitHub (workflow_dispatch)
                                         │
                                         ▼
                 GitHub Actions «Enviar avisos» (servidores de GitHub)
                 entra con la cuenta del notificador → vacía la cola →
                 Firebase entrega al Android / navegador → espera reintentos → termina
```

- **Quién recibe qué no cambia.** Lo sigue decidiendo la migración 07, ya aplicada: consentimiento, dejar de seguir, horario de contacto, invitación ya respondida, feria cancelada, celular compartido y duplicados por destino. La 08 solo decide *cuándo* despertar al emisor.
- **Sin actividad no se consulta nada.** No hay cron. Cada ejecución vacía la cola, espera los reintentos que queden y se apaga. Si mientras corre llegan más avisos, se envían en esa misma ejecución.
- **Si Firebase falla un momento**, la misma ejecución reintenta ese destino a los 2, 4, 8 y 16 minutos, sin repetir lo que otro destino ya aceptó. Antes de cada intento se vuelve a validar el aviso. Al quinto intento fallido queda `FALLIDO`.
- **Si el disparo se pierde** (GitHub no inició la ejecución o se cortó), la ejecución da señales de vida. Si pasan 5 minutos sin ellas, se considera perdida. Se vuelve a pedir con el siguiente aviso nuevo o con la próxima revisión de avisos de cualquier web o app abierta, siempre que haya algo listo.
- **Vigía (opcional, requiere `pg_cron`)** para el caso en que la petición se pierde y nadie vuelve a abrir la web ni la app:
  - Al pedir una ejecución, la base programa un trabajo de `pg_cron` que cada minuto revisa **solo la propia base**, sin salir a internet.
  - Si la ejecución pedida no dio señales, vuelve a pedirla con espera creciente: 5, 10, 20, 40 y luego cada 60 minutos.
  - Si un reintento de Firebase quedó para después, lo retoma cuando toca.
  - Cuando la cola queda vacía, **el trabajo se borra a sí mismo**: con la cola vacía no existe ni corre.
  - Viene apagado (`vigia_activa = false`). Se enciende en el paso I, después de confirmar `pg_cron`.
- **La web con la página abierta** muestra sus avisos con la notificación del navegador (W18). Esa revisión cada 60 s ya existía, solo corre con la pestaña visible y no depende del emisor.

## 2. Dónde corre, cómo se autentica y qué permisos usa

| Pieza | Dónde corre | Cómo se autentica | Permiso mínimo |
|---|---|---|---|
| Trigger y disparo (`08_envio_automatico.sql`) | En la base de Supabase del curso | Token de GitHub *fine-grained* guardado en Vault (o en la tabla privada `envio_automatico`) | Solo el repositorio `EmprendedoresPuertoMontt`, permiso **Actions: Read and write**. No puede leer ni cambiar código. |
| Emisor (`servicios/notificador`, workflow `notificador.yml`) | GitHub Actions (gratis en repositorios públicos) | Cuenta común del notificador (tabla `notificadores`) con la clave pública `anon` | Solo `avisos_por_enviar`, `resultado_envio`, `latido_envio` y `cerrar_envio`. Nunca usa la `service_role`. |
| Envío a Firebase | Desde el mismo workflow | JSON de la cuenta de servicio de `alerceisiete`, como secreto de GitHub | Solo mensajería |

**Ninguna clave privada va en la web, el APK ni Git.** El token de GitHub queda en Vault. La contraseña del notificador y el JSON de Firebase quedan en los secretos de GitHub y en tu `.env` ignorado. Ningún cliente puede leer el token ni pedir ejecuciones: lo comprueban las pruebas.

**El vigía** corre dentro de la base, con `pg_cron`, como el dueño de las funciones (el mismo rol con que aplicas la 08 en Studio). No necesita claves nuevas: reutiliza el mismo token de Vault.

**Otras opciones que se descartaron:**
- **Una función en el servidor** (Edge Function) requiere desplegar en el Supabase del profesor.
- **Un cron fijo** (en GitHub o en la base) consulta aunque la cola esté vacía.

## 3. Activación (la haces tú)

Hazlo en este orden. Hasta el paso G no cambia nada para los usuarios.

### A. Commit y push
Sube estos archivos a `main`:
- `supabase/08_envio_automatico.sql` y `supabase/08_revertir.sql`
- `.github/workflows/notificador.yml`
- `servicios/notificador/enviar.js`, `servicios/notificador/notificador.js` y `servicios/notificador/comprobar_secretos.sh`
- `tests/…` (pruebas)
- este documento

### B. Secretos de GitHub
En GitHub, entra al repositorio y abre **Settings → Secrets and variables → Actions → New repository secret**. Crea estos cinco (sin comillas):

| Nombre | Valor |
|---|---|
| `SUPABASE_URL` | El mismo de `servicios/notificador/.env` |
| `SUPABASE_ANON_KEY` | El mismo de `.env` (clave pública `anon`) |
| `NOTIFICADOR_EMAIL` | El de `.env` |
| `NOTIFICADOR_CLAVE` | El de `.env` |
| `FIREBASE_CUENTA_SERVICIO` | El **contenido completo** del JSON de la cuenta de servicio |

Para no ver ni mostrar los valores, cópialos al portapapeles desde PowerShell, en la carpeta del repositorio. Pégalos en GitHub uno por uno.

```powershell
((Select-String -Path servicios\notificador\.env -Pattern '^NOTIFICADOR_CLAVE=(.*)$').Matches[0].Groups[1].Value) | Set-Clipboard
```

```powershell
Get-Content ((Select-String -Path servicios\notificador\.env -Pattern '^FIREBASE_CUENTA_SERVICIO_ARCHIVO=(.*)$').Matches[0].Groups[1].Value.Trim('"')) -Raw | Set-Clipboard
```

Para los otros tres secretos, cambia `NOTIFICADOR_CLAVE` en el primer comando por `SUPABASE_URL`, `SUPABASE_ANON_KEY` o `NOTIFICADOR_EMAIL`.

### C. Probar el workflow a mano (una vez)
1. Abre **Actions → Enviar avisos → Run workflow → Run workflow**.
2. Si aún no aplicaste la 08, el registro termina con una línea como `modo un_lote (08 sin aplicar) · pasadas 1 · tomados N …`.
3. Si falta un secreto, la ejecución **falla** (queda en rojo) en el paso «Comprobar secretos». El mensaje nombra los que faltan, sin mostrar valores: por ejemplo, `Faltan secretos de GitHub Actions: NOTIFICADOR_CLAVE`. Un JSON de Firebase mal pegado también falla con un mensaje claro.
4. Esta ejecución es real: envía lo que haya pendiente en la cola.

#### C2. Si falla al iniciar sesión: diagnóstico sin tocar la cola
**Actions → Enviar avisos → Run workflow**, elige `modo: diagnostico-sesion` y pulsa **Run workflow**. Hace **un** intento de inicio de sesión con la cuenta del notificador y termina: no lee la cola, no llama a ninguna RPC ni a Firebase. El paso «Probar solo el inicio de sesión» muestra:
- el HTTP, el formato y un código reconocido;
- una línea «Rastro» que dice si hubo redirección, el identificador de solicitud (`cf-ray`, `x-request-id`, `x-kong-request-id`…), la cabecera `Server` y si hay cabeceras de Kong;
- la versión de Node.

Nunca muestra el cuerpo, cookies, la clave, el token ni otras cabeceras.

El inicio de sesión (el mismo en el diagnóstico y en el envío normal) **no sigue redirecciones**: si el servidor responde 301, 302, 303, 307 o 308, el emisor se detiene en esa primera petición. No visita el destino, no le reenvía la clave ni la contraseña y no muestra su URL; solo dice si apuntaba al mismo servidor o a otro.

> **Ojo con `cf-ray` y las cabeceras de Kong.** Que aparezcan solo dice por dónde **pasó** la respuesta, no **quién produjo** el 403. Cloudflare agrega `cf-ray` a todo lo que atraviesa, también a un 403 que generó el servidor de detrás. Las cabeceras de Kong pueden venir de Kong o de un servicio detrás de él. Para saber quién lo produjo hace falta el código propio de Cloudflare (`error 10xx` o `desafío`), un código JSON reconocido, o que quien administra el dominio busque ese `cf-ray` en Cloudflare → Security → Events.

| Lo que muestra | Qué significa |
|---|---|
| `formato: HTML` · `Cloudflare: error 10xx` o `desafío` | Cloudflare detuvo la petición antes del servidor de Supabase (1020 = regla de firewall, 1009 = país, 1006–1008 = IP, 1010 = firma del cliente). Quien administra el dominio debe permitir la ruta `/auth/v1/` y `/rest/v1/` para el emisor. |
| `formato: HTML` · `Cloudflare: sin código propio` | Pasó por Cloudflare, pero la página puede venir de Cloudflare o del servidor detrás: el `cf-ray` no basta para saberlo. Quien administra el dominio puede buscarlo en Cloudflare → Security → Events. |
| `formato: HTML` · `cabeceras de Kong: sí` | Pasó por la puerta de enlace de Supabase, pero eso no dice quién produjo el 403 (Kong, algo detrás o algo delante). |
| `formato: HTML`, sin nada de lo anterior | Respondió un componente que no es Supabase Auth. No dice cuál ni si se revisó la contraseña; usa el identificador de solicitud para rastrearla. |
| `redirección: sí, no se siguió (hacia el mismo servidor / hacia otro servidor)` | `SUPABASE_URL` apunta a algo que redirige. Revisa el esquema (`https`), el host y que no tenga ruta extra. El emisor no siguió la redirección. |
| `formato: JSON · código reconocido: invalid_credentials` (u otro) | **Supabase Auth** recibió la petición y la rechazó con ese código. |
| `formato: JSON · código reconocido: kong_…` | La puerta de enlace de Supabase rechazó la clave pública (`SUPABASE_ANON_KEY`). |
| `formato: JSON · código reconocido: ninguno` | JSON sin un código conocido: no se puede saber el origen. |
| `HTTP: sin respuesta` | La conexión no se completó (DNS, TLS o red). |
| `inició sesión correctamente` | Desde GitHub también se puede entrar. |

Los disparos automáticos de Supabase no envían `modo`, así que siempre usan `enviar`.

### D. Comprobación mínima del servidor ✅ (hecha el 22-nov)
Ya confirmaste en Studio que están instalados `pg_net` 0.14.0 y `supabase_vault` 0.3.1, y que una petición de la base a GitHub respondió HTTP 200. No hace falta repetirlo.

### E. Aplicar la 08
Pega el contenido de `supabase/08_envio_automatico.sql` en el SQL Editor y pulsa **Run**.
- No toca datos ni repite la 07.
- Queda **apagada** (`activo = false`).
- Se puede volver a ejecutar sin problema.

### F. Token de GitHub (permisos mínimos)
1. En GitHub abre tu foto → **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**.
2. Configúralo así:
   - **Token name:** `alerce-disparo-avisos`
   - **Expiration:** 30 días (o hasta el fin del semestre)
   - **Repository access:** *Only select repositories* → `EmprendedoresPuertoMontt`
   - **Permissions → Repository permissions → Actions:** *Read and write*. No agregues nada más; *Metadata: Read* se agrega solo.
3. Pulsa **Generate token** y cópialo. No lo pegues en ningún chat.
4. En Studio, guárdalo en Vault con el SQL Editor. El nombre `alerce_disparo_github` debe ser exactamente ese:
   ```sql
   select vault.create_secret('PEGA_AQUÍ_EL_TOKEN', 'alerce_disparo_github', 'Dispara el workflow Enviar avisos');
   ```
   Para cambiarlo más adelante (token vencido), usa `select vault.update_secret((select id from vault.secrets where name = 'alerce_disparo_github'), 'TOKEN_NUEVO');`.
5. Borra el SQL Editor después de ejecutarlo, para que el token no quede en el historial de la pestaña.

### G. Activar y comprobar el disparo
```sql
update public.envio_automatico set activo = true;
select public.solicitar_envio('prueba de activación');   -- debe decir DISPARADO
```
Espera unos 10 segundos y ejecuta:
```sql
select public.estado_envio_automatico();
```
Debe mostrar `"respuesta_github": 204`, `"pg_net": true` y `"tiene_token": true`. En **Actions** debe aparecer una ejecución nueva de «Enviar avisos». Al terminar, `en_curso` vuelve a `false` y `ultimo_resultado` dice `COLA VACÍA`.

| Si ves… | Significa |
|---|---|
| `respuesta_github: 401` | Token incorrecto o vencido |
| `respuesta_github: 403` o `404` | Al token le falta *Actions: Read and write* o no incluye el repositorio, o el workflow no está en `main` |
| `SIN_PG_NET` | Falta el paso D |
| `SIN_TOKEN` | Falta el paso F |

### H. Pausar o revertir
- **Pausar:** `update public.envio_automatico set activo = false;`. Puedes volver a enviar a mano con *Run workflow* o `npm run notificar`.
- **Revertir del todo:** ejecuta `supabase/08_revertir.sql`. Quita también el trabajo del vigía, deja todo como con la 07 y no borra avisos. Borra además el secreto en Studio → Vault.

### I. Vigía (opcional; solo después de confirmar `pg_cron`)
Todavía **no está confirmado** que tu instancia tenga `pg_cron` ni que su programador corra en esta base. Sin el vigía, lo demás funciona igual: solo falta el caso «la petición se pierde y nadie vuelve a abrir la web ni la app».

1. **¿Está disponible?**
   ```sql
   select name, installed_version from pg_available_extensions where name = 'pg_cron';
   ```
   - Si no aparece, no se puede usar el vigía. Déjalo apagado.
   - Si aparece sin versión instalada, actívalo en **Database → Extensions → pg_cron**.
2. **¿Corre de verdad?** Programa un trabajo inofensivo, espera 2 minutos y míralo:
   ```sql
   select cron.schedule('alerce_prueba_cron', '* * * * *', 'select 1');
   ```
   ```sql
   select status, return_message from cron.job_run_details
    where jobid = (select jobid from cron.job where jobname = 'alerce_prueba_cron') order by start_time desc limit 1;
   ```
   ```sql
   select cron.unschedule('alerce_prueba_cron');
   ```
   El segundo paso debe mostrar `succeeded`. Si no hay filas, el programador no corre en esta base y el vigía no funcionaría.
3. **Encender:** `update public.envio_automatico set vigia_activa = true;`
   - Desde ahí, cada disparo programa `alerce_vigia_envio` y la cola vacía lo borra.
   - En `select public.estado_envio_automatico();` verás `pg_cron`, `vigia_activa`, `vigia_programado` y `disparos_sin_respuesta`.
   - Para apagarlo: `update public.envio_automatico set vigia_activa = false;`. El trabajo se borra en su siguiente minuto.

## 4. Prueba final (sin comandos ni computador encendido)

Necesitas una feria **nueva** y publicada, con fechas de hoy en adelante. Créala desde la web: Panel Admin → Ferias → Nueva feria → 📣 Publicar.

Usa una feria nueva en cada ensayo. Una compradora recibe el anuncio de participación una sola vez por feria, así que repetir «Participo» en la misma feria no lo vuelve a enviar.

Con la página publicada de GitHub Pages basta cualquier navegador. Se espera que la notificación llegue entre 30 y 90 segundos después de la acción: lo que tarda GitHub en arrancar la ejecución.

### Recorrido 1 · Invitación → «Participo» → «Confirmada» (A01, A02, W07)
1. **Celular:** ALERCE con la cuenta emprendedora y «Activar avisos» ya tocado (lo hiciste el 22-nov). Déjala en segundo plano con el botón de inicio.
2. **Navegador (admin):** Panel Admin → Ferias → tu feria → **📋 Invitaciones y checklist** → marca tu tienda → **✉️ Invitar seleccionadas**. Deja el checklist abierto.
3. **Celular:** llega «Te invitaron a <feria>». Tócala, ve a **Mis ferias** y toca **Participo**.
4. **Navegador:** el checklist se actualiza solo y tu tienda pasa a **Confirmada**.

### Recorrido 2 · La compradora que sigue recibe el anuncio (A13, W18)
Necesitas dos cuentas: la **compradora** y la **emprendedora** de la tienda.

**Con dos celulares:** el celular de la compradora sigue los pasos 1 a 3 de abajo. La emprendedora toca «Participo» en el suyo después de la invitación.

**Con un solo celular** (el celular queda para la compradora y la emprendedora confirma desde la web):
1. **Celular → cuenta compradora:** en ALERCE cierra sesión de la emprendedora y entra con la compradora.
   - Desde ese momento, ese celular deja de recibir los avisos de la emprendedora. Es la regla del celular compartido.
2. **Celular:** abre la tienda (o su asociación) → **Seguir** → **Sí, avisarme** → permite las notificaciones. Deja ALERCE en segundo plano.
3. **Navegador A, ventana normal → compradora:** entra a la web con la misma cuenta compradora → 🔔 **Mis avisos** → **Sí, quiero recibir avisos** → **Permitir notificaciones del navegador** → acepta.
   - Deja esa ventana **visible**, no minimizada.
4. **Navegador B, otra ventana privada u otro navegador → admin:** Panel Admin → Ferias → feria nueva → **📋 Invitaciones y checklist** → invita a la tienda.
   - Si lo que sigue la compradora es la asociación, invita a la **asociación**.
5. **Navegador B → cierra sesión y entra como emprendedora:** abre **Mis ferias** → **Participo**.
6. **Resultado esperado:**
   - El **celular** recibe «<Tienda o asociación> estará en <feria>».
   - El **navegador A** muestra la misma notificación en 60 segundos como máximo.
   - Al tocar cualquiera de las dos se abre la ficha de esa feria.
7. Al terminar, en el celular vuelve a entrar con la cuenta emprendedora para que reciba sus avisos otra vez.

## 5. Qué está probado y qué falta

| Criterio | Probado en local (PostgreSQL + emisor real + Firebase simulado) | Pendiente de activar | Solo lo verás con la notificación real |
|---|---|---|---|
| A01 / W03 invitar una tienda → dueña | ✅ llega «Te invitaron a…» a su Android, con feria y enlace | Pasos B–G | Que se vea en tu teléfono |
| W04 invitar una asociación → dueñas de las integrantes | ✅ | B–G | — |
| W05 agregar directo → «Participas en…» | ✅ | B–G | — |
| W09 recordatorio → solo pendientes | ✅ | B–G | — |
| W10 mensaje → confirmadas y agregadas | ✅ | B–G | — |
| W02 cancelar → invitadas, confirmadas y agregadas | ✅ | B–G | — |
| A13 sigue una tienda → push con el nombre de la feria | ✅ | B–G | Recorrido 2 |
| A13 sigue una asociación (integrante invitada por ella confirma) | ✅ | B–G | Recorrido 2 |
| W18 aviso en el navegador | ✅ (página abierta y push web si está configurado) | — (la página abierta ya funciona) | Recorrido 2, paso 6 |
| Seguir, editar la feria o rechazar no anuncian | ✅ | — | — |
| Sigue la tienda y su asociación: un push por confirmación | ✅ | — | — |
| A14 dejar de seguir o retirar el consentimiento con aviso pendiente | ✅ no sale | — | — |
| Celular compartido (cambio de cuenta) | ✅ | — | — |
| Web primero no impide el Android; el reintento no duplica | ✅ | — | — |
| A15 contacto: dentro de horario notifica; fuera de horario o desactivado se guarda sin notificar, ni después | ✅ | B–G | — |
| Fallo temporal recuperado sin otro aviso; revalidación antes del reintento | ✅ | — | — |
| Disparo perdido → se recupera con la siguiente actividad | ✅ | — | — |
| Disparo perdido y nadie abre la web ni la app → el vigía lo repite (5, 10… min) y se borra con la cola vacía | ✅ (pg_cron simulado) | **Paso I** (confirmar `pg_cron`) | — |
| Faltan secretos → el workflow falla nombrándolos, sin valores | ✅ | — | Primera ejecución en Actions |
| Sin «nuevo seguidor» ni avisos por calificar | ✅ | — | — |
| `pg_net` existe y la base sale a internet | ✅ confirmado por ti en Studio | — | — |
| GitHub acepta el token (204) y arranca la ejecución | — | **Paso G** | — |

Comandos de las pruebas locales:
- `npm run test:sql`: 473 casos, 92 del envío automático (`E8 · …`), incluido el vigía.
- `npm run test:web`: 42 casos, incluidos el modo automático del emisor y el paso de secretos.
