# Cierre Alerce 2.0 — estado real

**Restricciones respetadas:** no hubo commit, push, despliegue ni migraciones reales. La base es HEAD `38a3b51`; todo lo de «administración integral» y de 2.0 está **sin commit**.
**Clasificación: APTO PARA VALIDACIÓN LOCAL.** La migración 11 y «Confirm email» siguen pendientes en Supabase real, y ambas son obligatorias.

## 1. Hecho

### P0 · Seguridad del registro (migración 11, no aplicada)

- **Códigos sin sesgo:** de 8 símbolos, con alfabeto de 31 y muestreo por rechazo. Se guarda solo el hash sha256, vencen a los 14 días y admiten 5 intentos.
- **`CUENTA_ANTERIOR`:** una cuenta creada antes de emitir el código no puede activarse hasta que la administración confirme en persona («Confirmé en persona», en Android y en la web, mediante `permitir_cuenta_existente`). Esto cierra la preocupación de que alguien registre por adelantado el correo de otra persona.
- **Al activar se cierran las otras sesiones:** el servidor borra `auth.sessions` y el cliente llama a `logout scope=others`.
- **`11_revertir.sql`:** revierte la migración sin reabrir el registro libre.
- **Correo sin sesión:** cuando el registro no devuelve sesión, las apps muestran «revisa tu correo».

### P1 · Asistente

| Pieza | Qué hace |
|---|---|
| `Canonizador` | Corrige errores de voz y tipeo, pero solo hacia palabras clave. Quita diminutivos, excepto al crear un producto y en verbos como «necesito». Convierte formas equivalentes: «acabo de vender», «se llevó dos», «cobré un…», «tengo solo dos…», «¿cómo vendo…?». |
| `InterpreteAlerce` | Suma `VER_HISTORIAL`, `CONSULTAR_SEGUIDORES` (responde con honestidad que no hay cifra) y `CORREGIR`. |
| `InterpreteGeneral` y `AsistenteGeneral` | Atienden a visitante, comprador y administración. Buscan en la vitrina, muestran tiendas, asociaciones y ferias reales, y explican cómo comprar, contactar, seguir tiendas, recuperar el acceso y vender. En administración responden sobre cuentas, pendientes con conteo real, ferias, asistencia, asociaciones, tipos, equipo y bitácora. |
| Límites de seguridad | Las acciones sensibles (desactivar, generar o leer códigos, crear administradores) **no se ejecutan**: se explican y se abre la pantalla, que pide confirmación. Nunca se leen códigos ni contraseñas. |
| Entradas al asistente | Visitante: botón en «Ayuda». Administración: «Gestión» › «Asistente del panel». Emprendedora: igual que antes. |
| Memoria | Separada por cuenta **y rol**; se borra al cerrar sesión. |

### Versión

- APK **2.0.0-rc1 (13)**, mismo paquete `com.company.ultrapp` y mismo certificado.

## 2. Pruebas ejecutadas (2026-11-26)

| Suite | Resultado |
|---|---|
| Android unitarias (`testDebugUnitTest`) | **181/181**, incluye `AsistenteV2Test` |
| Android `lintDebug` | Sin errores (77 avisos, ya existentes) |
| Asistente, frases de diseño | **127/127** |
| Asistente, frases de **reserva** (no usadas para ajustar) | **54/60 = 90 %** |
| SQL (PG17) | **642/642** |
| Web unitarias | 76/76 |
| Reglas de Firebase | 5/5 |
| Hosting | 5/5 |
| e2e Playwright | **101 pasan**, 5 omitidas |

Las 6 frases de reserva que fallan se informan sin ajustar el motor:

- «anótame dos tortas vendidas»
- «me quedaron dos mermeladas»
- «se agotaron las tortas»
- «no quiero que se vea el chaleco» (se entiende como mostrar)
- «¿dónde veo las boletas de mis ventas?» (se entiende como Enséñame)
- «no, no vendí nada» (se entiende como venta)

El caso de la venta no tiene efecto sin confirmación: toda acción muestra la tarjeta «Revisa antes de confirmar».

## 3. APK

| Campo | Valor |
|---|---|
| Archivo | `docs/rediseno/apk/alerce-2.0.0-rc1-13.apk` (16,4 MB; carpeta ignorada por git). Se conservan los APK 10, 11 y 12. |
| SHA-256 del archivo | `1249B50BCDC2A0ECE06E65F55464E058AD1ED060FD0B99D2DE8F907FC957018E` |
| SHA-256 del certificado | `29:15:BB:41:53:1A:6D:E2:A2:4A:84:A9:46:75:11:A4:C3:95:C8:B9:ED:23:58:8C:6B:F8:5E:5E:AF:DC:CB:37` (sin cambios, App Check sigue válido) |
| `verificar-apk.js` | «APTO para publicar» |

**No probado en celular ni emulador en esta etapa:** el asistente de visitante y de administración, y la búsqueda desde el asistente. TalkBack tampoco está probado.

## 4. Limitaciones conocidas

- El asistente de la **web** no tiene todavía las intenciones de visitante y administración (solo Android).
- Un token de acceso ya emitido sigue valiendo hasta 1 hora después de cerrar las otras sesiones.
- El asistente no muestra el número de seguidores, porque la app no tiene esa cifra para la emprendedora.

## 5. Procedimiento de publicación (en este orden)

| # | Quién | Paso |
|---|---|---|
| 1 | Usuario | Revisar el diff y hacer **commit** en una rama (no en `main`). **Nunca** incluir `supabase.properties`, `firebase.properties`, `keystore.properties` ni `js/config.js`. |
| 2 | Profesor o dueño del proyecto Supabase | **Respaldo:** Dashboard › Database › Backups, o `pg_dump` de los esquemas `public` y `auth`. |
| 3 | Profesor o dueño | En el SQL Editor, ejecutar `supabase/11_registro_controlado.sql` completo. Es idempotente y la web funciona con o sin la migración. |
| 4 | Profesor o dueño | Auth › Providers › Email: activar **«Confirm email»** y configurar un **SMTP propio** (el de Supabase tiene un límite muy bajo). Revisar en URL Configuration la *Site URL* y las *Redirect URLs* de la web. **Obligatorio**: sin esto, un tercero puede ocupar el correo de una emprendedora. |
| 5 | Profesor o dueño | Verificar en el SQL Editor: `select proname from pg_proc where proname in ('activar_con_codigo','autorizar_emprendedora','permitir_cuenta_existente');` debe devolver 3 filas. |
| 6 | Usuario | Prueba integrada con cuentas de prueba: el admin autoriza un correo nuevo → se crea la cuenta (llega el correo de confirmación) → se activa con el código → la tienda queda creada. Probar también: código incorrecto 5 veces → bloqueo; cuenta creada antes del código → `CUENTA_ANTERIOR`. |
| 7 | Usuario | Instalar `alerce-2.0.0-rc1-13.apk` **encima** del 1.10.0 (mismo certificado: conserva los datos). Recorrer emprendedora, visitante y admin, incluido el asistente. |
| 8 | Usuario | Aprobar el APK. Hacer merge a `main` y push. **El push a `main` despliega GitHub Pages automáticamente** (`.github/workflows/deploy-pages.yml`). |
| 9 | Usuario | En GitHub, crear el Release (p. ej. `apk-2.0.0-rc1`) y adjuntar el APK con el nombre **`app-release.apk`**. |
| 10 | Usuario | **Enlaces del APK:** la web apunta a `releases/download/apk-1.6-alerce/app-release.apk`, en `index.html` (dos lugares), `dist/hosting/index.html` y el test `tests/e2e/hosting.spec.js:71`. Opción recomendada: cambiarlos los cuatro a `releases/latest/download/app-release.apk`, que no vuelve a quedar viejo. Debe hacerse **antes** del push del paso 8; si no, el enlace sigue entregando el 1.6. |
| 11 | Usuario | Hosting de Firebase, si se usa: `npm run test:hosting` y luego `firebase deploy --only hosting`. |
| 12 | Usuario | Comprobación: descargar desde la web y comparar el SHA-256 con el de la tabla del APK. Forzar la recarga (Ctrl+F5), porque Pages puede servir caché unos minutos. |
| 13 | Usuario | Postcontrol: iniciar sesión real como admin y como emprendedora, abrir la vitrina sin cuenta y revisar la bitácora. |
| — | **Reversión** | SQL: ejecutar `supabase/11_revertir.sql` (vuelve a «solo correos autorizados», no reabre el registro). Web: revertir el commit (Pages se redespliega solo). App: publicar de nuevo el APK 12 (`alerce-1.10.0-administracion-12.apk`). |

Claude no ejecutó ninguno de estos pasos. Solo puede hacerlos si el usuario lo autoriza explícitamente, y nunca con `service_role`.
