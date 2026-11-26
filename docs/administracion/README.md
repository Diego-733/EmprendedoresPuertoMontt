# Administración integral, cuentas, roles y seguridad — informe (26-nov-2026)

**Estado:** todo es local y **sin commit**. No hubo push, merge, release, despliegue ni publicación. **No se tocó Supabase real**: la migración 11 se probó solo en PostgreSQL local desechable. No se usó `service_role`. No se eliminaron cuentas ni funciones.

**Documentos:**
- [auditoria.md](auditoria.md): funciones reales y simuladas;
- [registro-controlado.md](registro-controlado.md): flujo, compatibilidad, decisiones y dependencias;
- [matriz-roles.md](matriz-roles.md): roles y permisos;
- [despliegue.md](despliegue.md): procedimiento, clientes antiguos, reversión y riesgos.

## 1. Problemas funcionales y de seguridad identificados

1. **Registro abierto de emprendedoras (RN-01).** Cualquiera obtenía el rol de vendedora: bastaba enviar los metadatos del registro o llamar a `crear_mi_tienda()` desde una compradora.
2. **El correo no prueba titularidad.** La instancia tiene el registro **autoconfirmado** (`docs/entrega5`), así que autorizar solo un correo no bastaba.
3. **Datos de ejemplo en la app conectada.** En el primer ingreso sin señal en un celular nuevo, la emprendedora veía ventas y tienda inventadas (`MockData`).
4. **Operaciones que solo cambiaban el celular:** autorizar en el modo de ejemplo del panel Android y la antigua «Nueva feria».
5. **Android admin incompleto:** sin ferias reales; asociaciones, tipos y administradores solo de lectura.
6. **Cuenta desactivada sin explicación** en las apps, y el asistente seguía ofreciendo operar (el servidor sí bloqueaba).
7. **Contraseña:** no se podía mostrar al escribirla ni cambiar con la sesión iniciada.
8. **Etiqueta «Emprendedoras»** cortada en la barra inferior con letra al 200 %.

## 2. Funciones simuladas o incompletas detectadas

Ver [auditoria.md](auditoria.md). Lo que queda **solo en demo**:
- las tiendas de prueba y «Entrar Administración» (APK de depuración sin servidor);
- «Asociaciones (ejemplo local)» y las credenciales generadas de la web (solo sin servidor).

Lo que queda **solo local por diseño**:
- el carrito del comprador;
- los favoritos de la web;
- las inscripciones antiguas de ferias en la web (rotuladas).

**No existe** un pago con QR, ni simulado ni real.

## 3. Funciones corregidas

Todos los puntos del §1, con detalle en la auditoría.

## 4. Nuevo flujo de autorización de emprendedoras

| Paso | Qué pasa |
|---|---|
| 1 | La administración autoriza el correo y se genera un **código de activación** de un solo uso (14 días, 5 intentos). Se muestra una vez; solo se guarda su huella. |
| 2 | La administración entrega el código. |
| 3 | La emprendedora crea **su** cuenta con **su** contraseña (o entra con Google). |
| 4 | Escribe el código: al registrarse o en «Mi perfil». |
| 5 | El servidor verifica el correo de la **sesión**, la huella, la vigencia y el uso. |
| 6 | El servidor asigna el rol y crea su tienda (y la asociación). |

La administración nunca conoce la contraseña. Ver [registro-controlado.md](registro-controlado.md).

## 5. Matriz de roles y permisos

Ver [matriz-roles.md](matriz-roles.md): visitante, compradora, autorización pendiente, emprendedora, desactivada y administración, con la comprobación del servidor de cada operación.

## 6. Administración en Android y en la web

### Android (todo con las funciones reales de la web)

- **Cuentas:**
  - autorizar con código (mostrar, compartir y copiar);
  - filtros: Todas, Activas, Pendientes, Desactivadas, Sin tienda y Sin autorización;
  - ficha con: editar nombre y tienda, asociaciones, ver tienda, activar o desactivar con confirmación, código nuevo, revocar y regularizar.
- **Ferias:** «Nueva feria» real (borrador en el servidor); editar, publicar, cancelar con motivo, invitar o agregar tiendas y asociaciones; checklist con asistencia (Asistió / No asistió).
- **Gestión:**
  - asociaciones (crear, editar, integrantes);
  - tipos de producto (crear, editar, activar, borrar si nadie lo usa);
  - administradores (crear, desactivar, reactivar);
  - bitácora;
  - «App sin cuenta»;
  - diagnóstico técnico.
- **Inicio:** avisos reales de pendientes y de cuentas sin autorización.
- **Barra inferior:** «Inicio · Ferias · Cuentas · Gestión».

### Web

- «Cuentas emprendedoras» con los estados de 11, código de activación (en vez de credenciales), «Código nuevo», «Revocar», «Regularizar» y los filtros «Pendientes» y «Sin autorización».
- Si el servidor no tiene 11, todo sigue funcionando con las funciones anteriores.

## 7. Gestión de ferias y asociaciones

| Plataforma | Qué cubre |
|---|---|
| Android | Crear, editar, publicar, cancelar con aviso a las participantes, invitar o agregar, checklist y asistencia (las funciones de 07). Asociaciones e integrantes. |
| Solo web | Imagen, punto en el mapa y avisos manuales (recordatorio / aviso a confirmadas). Se dice así en pantalla. |

No se inventan fechas, asistentes ni estados: el servidor valida (`FERIA_PASADA`, `AUN_NO_ES_EL_DIA`, `PLAZO_VENCIDO`…) y la app muestra el motivo.

## 8. Mejoras de autenticación

- **Mostrar u ocultar la contraseña** en Android (ingreso y registro) y en todos los formularios de la web (botón de 44 px con nombre accesible).
- **Cambio de contraseña con sesión** en Android (Ajustes) y en la web (Mi perfil):
  - reautenticación con la contraseña actual;
  - mínimo 8 caracteres y confirmación;
  - Supabase Auth `PUT /auth/v1/user`;
  - nunca se guarda en el dispositivo ni en registros.
- **Código de activación** en el registro de emprendedora y en «Mi perfil».
- **Mensajes claros** por estado de cuenta: autorización pendiente, código vencido o agotado, cuenta desactivada.
- **Recuperación de contraseña:** sigue con el SMTP de la instancia y no se simula. Los enlaces van solo a la página de recuperación configurada (`URL_RECUPERACION`, la misma de antes).
- **Logs:** no registran contraseñas ni tokens (los mensajes nuevos no incluyen datos).

## 9. Migraciones preparadas

| Archivo | Qué hace | Estado |
|---|---|---|
| `supabase/11_registro_controlado.sql` | Columnas nuevas en `cuentas_autorizadas`; `al_crear_usuario` y `crear_mi_tienda` corregidas; las funciones `autorizar_emprendedora`, `regenerar_codigo_emprendedora`, `revocar_autorizacion`, `regularizar_emprendedora`, `activar_con_codigo`, `estado_mi_cuenta` y `listar_cuentas_emprendedoras`. | Idempotente; **no aplicada** |
| `supabase/11_revertir.sql` | Reversión. | Probada en local |

## 10. Compatibilidad con las cuentas existentes

- **No cambia ningún dato existente.** Lo comprueba la prueba de la huella de perfiles, tiendas, productos, ventas, participaciones y seguimientos.
- **Las emprendedoras de la vía abierta siguen igual** y se marcan «sin autorización registrada» para que decidas.
- **Las autorizaciones previas** quedan pendientes de código.
- **Clientes antiguos:** ver [despliegue.md](despliegue.md).

## 11. Riesgos y estrategia de reversión

Ver [despliegue.md](despliegue.md). La reversión de 11 no borra datos y está probada en local.

## 12. Pruebas de seguridad

**SQL:** 61 casos nuevos (`tests/sql/pruebas_registro_controlado.js`).

| Área | Casos |
|---|---|
| Registro | Comprador; intento de emprendedora o admin por metadatos; registro autorizado; correo autorizado sin código. |
| Falsificación y API directa | `crear_mi_tienda`, UPDATE de rol, INSERT en autorizaciones y tiendas, llamadas sin sesión. |
| Funciones administrativas | Desde emprendedora y desde compradora. |
| Códigos | Ajenos, vencidos, agotados, regenerados y revocados. |
| Datos y privacidad | Conservación de cuentas; ventas privadas (admin y entre cuentas). |
| Cuenta desactivada | API directa, reactivarse, otra tienda, código ya usado. |
| Escalamiento | Emprendedora intenta crear admins. |
| Compatibilidad | Función anterior para apps antiguas; reversión y nueva aplicación. |

**Android:** `AdministracionIntegralTest` (6), más `RedisenoAdminTest` actualizado.
- estados del servidor;
- pendientes y sin autorización;
- mensaje del código sin contraseñas;
- validación de feria;
- app conectada sin datos de ejemplo;
- asistente que no opera sin permiso.

**Web e2e:** `administracion.spec.js` (5).
- autorizar → código sin contraseña;
- lista con pendientes, revocar y regularizar (botones de 44 px);
- servidor sin 11;
- mostrar u ocultar contraseña;
- cambio de contraseña con reautenticación y nada guardado en el navegador.

**Lo que no se probó:** ninguna prueba contra Supabase real. **No se afirma que 11 funcione en la instancia real hasta aplicarla y probarla.**

## 13. Resultados reales de todas las suites

| Suite | Resultado |
|---|---|
| SQL (PG17 local) | **631/631** (antes 570) |
| Android unitarias | **175/175** (antes 169) |
| Lint | **0 errores**, 77 avisos |
| Web (`node --test`) | **76/76** |
| e2e Playwright | **101 pasan**, 5 omitidas (hosting) |
| Hosting | correcto |
| Reglas | **5/5** |

## 14. Capturas (`capturas/`)

| Plataforma | Capturas |
|---|---|
| Android | `android-admin-inicio`, `android-admin-cuentas`, `android-admin-autorizar`, `android-admin-ficha-cuenta`, `android-admin-nueva-feria`, `android-admin-gestion-feria`, `android-admin-asociaciones`, `android-admin-administradores`, `android-admin-barra-200` («Cuentas» completa), `android-ingreso-ver-clave`, `android-registro-codigo` |
| Web | `web-admin-cuentas`, `web-admin-codigo`, `web-perfil-cambiar-clave`, `web-ingreso-ver-clave`, más `web-0x-*` (portada, vitrina, admin y emprendedora) |

Son capturas con datos **de ejemplo** (APK sin servidor y demo web local). En el APK sin servidor, las acciones avisan «no se guarda».

## 15. Archivos modificados

**Servidor y pruebas:**
- `supabase/11_registro_controlado.sql` (nuevo), `supabase/11_revertir.sql` (nuevo), `supabase/README.md`;
- `tests/sql/pruebas_registro_controlado.js` (nuevo), `tests/sql/ejecutar.js`;
- `tests/e2e/administracion.spec.js` (nuevo), `tests/e2e/datos_demo.js`, `tests/e2e/supabase_falso.js` (un RPC sin respuesta se comporta como PGRST202).

**Android:**
- `data/`: AdminStore, `remoto/AdminRepositorio`, ClienteSupabase, Repositorios (`CuentaRepositorio`), VitrinaRepositorio, VitrinaStore, SesionManager, TramaStore;
- `ui/admin/`: AdminInicioScreen, AdminFeriasScreen, FeriasGestion (nuevo), EmprendedorasScreen, GestionAdminScreen;
- `ui/`: ajustes/AjustesScreen, auth/LoginScreen, asistente/EjecutorAsistente, emprendedora/DashboardScreen, componentes/SistemaAlerce, navegacion/Navegacion;
- `app/build.gradle.kts`;
- pruebas: AdministracionIntegralTest (nuevo), RedisenoAdminTest.

**Web:** `js/cuentas.js` (nuevo), `js/servicio.js`, `js/catalogo.js`, `js/main.js`, `index.html`, `css/generacion.css`.

**Documentos:** `docs/administracion/*` (nuevo), `docs/rediseno/propuesta-registro-controlado.md`.

## 16. APK generado

| | |
|---|---|
| Archivo | `docs/rediseno/apk/alerce-1.10.0-administracion-12.apk` |
| Versión | **1.10.0-administracion (12)** |
| Tamaño | 16,4 MB |
| Paquete y certificado | `com.company.ultrapp`, `29:15:BB:…:CB:37` (los mismos de siempre) |
| SHA-256 | `5E509691928F2C3C5F9D5734DD5F9E3E31D579DE6FB12A92F226C74FECBB1DF4` |
| `verificar-apk.js` | APTO |
| Copias anteriores conservadas | 1.9.0 (11) y 1.8.0 (10), en la misma carpeta |

> **No es una actualización lista para producción con cuentas reales.** Las funciones del registro controlado dependen de la migración 11, que no está aplicada. Contra el servidor actual, el APK funciona como antes en todo lo demás. Autorizar con código desde Android avisa que el servidor no lo tiene, y la lista de cuentas usa la función de 10.

## 17. Instrucciones de prueba local

1. **Web:** `npm run demo` y abrir:
   - `http://localhost:4180/?demo=admin` (Administración › Cuentas emprendedoras › Autorizar: el código de la demo es `ABCD-2345`);
   - `?demo=emprendedora` (Mi perfil › Cambiar mi contraseña);
   - `?demo=visitante` (Entrar: botón «Mostrar»).
2. **Android sin servidor:**
   1. compilar con `gradlew assembleDebug -PsinServidor=true` e instalar;
   2. en «Soy emprendedora» › «Tiendas de prueba (Modo Desarrollador)» › «Entrar Administración»;
   3. las acciones avisan «no se guarda».
3. **Base local:** `npm run test:sql` aplica 11 y su reversión en un PostgreSQL desechable.
4. **Con cuentas reales:** solo después de aplicar 11 según [despliegue.md](despliegue.md).

## 18. Dependencias del profesor o de la infraestructura

| Dependencia | Para qué |
|---|---|
| Aplicar 11 en la instancia (SQL Editor) | Activar el registro controlado. |
| «Confirm email» y SMTP | Recomendado para verificar la titularidad de toda cuenta. |
| SMTP | La recuperación de contraseña depende del de la instancia (con su límite). |

Ver [registro-controlado.md §4](registro-controlado.md).

## 19. Procedimiento propuesto para un despliegue seguro

Ver [despliegue.md](despliegue.md): respaldo, luego 11, luego prueba de humo con cuentas de prueba, luego la web, luego el APK, y por último la revisión de las cuentas de la vía abierta.

## 20. Limitaciones pendientes

- **Fuera de Android:** imagen y mapa de ferias, avisos manuales y campos propios de los tipos siguen solo en la web.
- **Correo autoconfirmado:** la titularidad del correo de las **compradoras** no se verifica (no afecta los permisos de emprendedora gracias al código).
- **TalkBack** no está validado en un teléfono físico.
- **Pruebas con Supabase real:** no se hicieron.
- **Asistente:** respeta el rol y la desactivación, pero su ampliación de cobertura queda para el próximo encargo.
- **Web:** la sección antigua de inscripciones a ferias guardadas solo en el navegador se conserva rotulada (no se quitó sin autorización).

## 21. Estado de Git

HEAD `38a3b51` («rediseño integral de Alerce y panel administrativo», commit tuyo). Todos los cambios de este encargo están **sin commit**. Los APK están en `docs/rediseno/apk/` e ignorados por Git (`*.apk`).
