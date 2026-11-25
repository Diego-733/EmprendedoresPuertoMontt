# Entrega 8 · Matriz de requisitos y casos de prueba

Estado final de cada requisito base de [`requerimientos-base.md`](https://github.com/rinostrozacl/ProyectoVinculacionConElMedio2026/blob/main/docs/requerimientos-base.md). Revisión del 24-nov-2026.

**Estado:**
- **Cumplido:** construido y con prueba.
- **Parcial:** construido en parte; se dice qué falta.
- **Pendiente:** no construido; se justifica.

**Verificación:**
- **Real:** usado en la instancia real con cuentas de prueba del grupo.
- **Local:** PostgreSQL local real, pruebas unitarias, recorridos web con Supabase sustituto o emulador.
- **Demo:** se demuestra en la revisión del 25-nov con el guion de [README.md](README.md#4-guion-de-la-demo).

**Backend.** Los requisitos piden Firestore y Firebase Emulator Suite. El proyecto usa Supabase (PostgreSQL con RLS), decisión de las entregas 3 y 4. La equivalencia se justifica en la fila de cada requisito.

Las pruebas automáticas se ejecutan con:
- `npm run test:sql` (casos SQL en PostgreSQL local);
- `npm run test:reglas` (reglas de Realtime Database en el emulador de Firebase);
- `npm run test:hosting` (ensayo en el emulador de Hosting);
- `npm run test:web` (pruebas unitarias web);
- `npm run test:e2e` (recorridos web);
- `gradlew testDebugUnitTest` (Android).

La sección 2 detalla cada caso de prueba de los Esenciales.

## 1. Matriz

### Reglas de negocio

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RN-01 | Regla | Parcial | CP-RN-01 | ADMIN solo desde `cuentas_autorizadas` (04). El administrador autoriza correos de emprendedora (web y app). **Desvío:** desde la entrega 4 también existe el alta abierta como emprendedora con tienda privada, por decisión del grupo para el piloto. Para cumplir la regla al pie de la letra hay que desactivar ese alta (cambio menor en `04_registro_vendedores.sql`). | Local |
| RN-02 | Regla | Cumplido | CP-RN-02 | `publicar_feria`/`cancelar_feria` solo ADMIN; la emprendedora no crea ferias (SQL E7). | Local + Real |
| RN-03 | Regla | Cumplido | CP-FER-02 | `invitar_a_feria` y `responder_invitacion`; sin inscripción libre (SQL E7). | Local + Real |
| RN-04 | Regla | Cumplido | CP-ASO-01 | Asociaciones solo editables por ADMIN (RLS, `pruebas_rls.sql`). | Local |
| RN-05 | Regla | Cumplido | CP-ASO-02 | `tienda_asociaciones` (07): cero, una o varias. | Local |
| RN-06 | Regla | Cumplido | CP-ACC-08 | `seguir` y `calificar` exigen sesión (SQL E7). | Local |
| RN-07 | Regla | Cumplido | CP-ACC-03 | Modo comprador de la app: solo «Continuar con Google». Requiere Google activo en Supabase. | Local (emulador) · Demo |
| RN-08 | Regla | Cumplido | CP-VEN-02 | `registrar_venta` marca `vendida_en`; no vuelve (SQL E5, contrato). | Local |
| RN-09 | Regla | Cumplido | CP-PRO-09 | `estado_publico_producto` oculta no disponibles; la dueña los ve (contrato). | Local |
| RN-10 | Regla | Cumplido | CP-SEG-01 | `ventas` solo para la dueña, ni ADMIN (RLS). | Local |
| RN-11 | Regla | Cumplido | CP-CMP-07 | Único aviso a seguidores: confirmación en feria (07). | Local + Real |
| RN-12 | Regla | Cumplido | — | Sectores y ferias de Puerto Montt; sin otras comunas en los datos. | Local |
| RN-13 | Regla | Cumplido | — | No hay IA; ver [ia-modulo-desacoplado.md](ia-modulo-desacoplado.md). | Local |

### Acceso (ACC)

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RF-ACC-01 | Esencial | Cumplido | CP-ACC-01 | Inicio de sesión con correo y contraseña en app y web. | Real |
| RF-ACC-02 | Esencial | Cumplido | CP-ACC-02 | BiometricPrompt; activar y desactivar en Ajustes (`ReglaHuellaTest`). | Local · Demo |
| RF-ACC-03 | Esencial | Cumplido | CP-ACC-03 | «Continuar con Google» en la app. **Simulado:** `SeguirConGoogleTest` y `BotonSeguirTest` en el emulador, con Google sustituido. **Real:** solo funciona si Google está habilitado en Supabase Auth de la instancia. Se comprueba en la demo con la cuenta de Google del grupo. | Local (simulado) · Demo (real) |
| RF-ACC-04 | Esencial | Cumplido | CP-ACC-04 | Web: registro e inicio con correo; Google si está activo (`auth/v1/settings`). | Local (e2e) · Real (correo) |
| RF-ACC-05 | Esencial | Cumplido | CP-ACC-05 | Primera pantalla con «Quiero comprar» y «Soy emprendedora». | Real |
| RF-ACC-06 | Esencial | Cumplido | CP-ACC-06 | PERMITIR / INVITAR / OBLIGAR leído de `configuracion` (E6). | Local · Demo |
| RF-ACC-07 | Esencial | Cumplido | CP-ACC-07 | Vitrina, tienda y feria sin sesión (RLS anónima y e2e). | Real |
| RF-ACC-08 | Esencial | Cumplido | CP-ACC-08 | Acción pendiente que se completa al volver del ingreso (E7, web y app). | Local + Real |
| RF-ACC-09 | Esencial | Cumplido | CP-ACC-09 | «Cerrar sesión» en app y web (e2e). | Real |
| RF-ACC-10 | Importante | Cumplido | — | Recuperación en web y app (`resetPasswordForEmail` → `?recuperar=1` → nueva clave). El envío del correo depende del SMTP de la instancia. | Local (e2e) · Demo |

### Tienda (TIE)

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RF-TIE-01 | Esencial | Cumplido | CP-TIE-01 | Ficha: nombre, logo, descripción, tipo, sector y contacto (app y web; `proteger_tienda`). | Real |
| RF-TIE-02 | Importante | Cumplido | — | Web de la emprendedora: «Mi tienda», «Nuevo producto» y «Caja POS» (E6). | Real |

### Catálogo y productos (PRO)

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RF-PRO-01 | Esencial | Cumplido | CP-PRO-01 | Alta guiada: foto (cámara o galería), datos, opciones, disponibilidad y «Lo ven todos» (`AltaProductoScreen`). | Real |
| RF-PRO-02 | Esencial | Cumplido | CP-PRO-02 | Fotos, nombre, descripción, categoría, precio y tipo. | Real |
| RF-PRO-03 | Importante | Cumplido | — | Campos por tipo (`tipos_producto.campos`, `docs/entrega5/tipos-producto.md`). | Local |
| RF-PRO-04 | Esencial | Cumplido | CP-PRO-04 | Editar y eliminar con confirmación. | Real |
| RF-PRO-05 | Esencial | Cumplido | CP-PRO-05 | Opciones con precio, foto y stock propios. | Local |
| RF-PRO-06 | Esencial | Cumplido | CP-PRO-06 | Regular, a pedido o pieza única. | Local |
| RF-PRO-07 | Esencial | Cumplido | CP-PRO-07 | «¿Cuántos tienes?» o sin stock. | Local |
| RF-PRO-08 | Esencial | Cumplido | CP-PRO-08 | Agotado, ocultar o a pedido (contrato compartido). | Local |
| RF-PRO-09 | Esencial | Cumplido | CP-PRO-09 | Disponible o no disponible («Se acabó» con Deshacer). | Local |
| RF-PRO-10 | Importante | Cumplido | — | Cambio masivo por selección, categoría o todo (`cambiarDisponible`). | Local |
| RF-PRO-11 | Importante | Cumplido | — | Valores por defecto (`tiendas.valores_producto`). | Local |
| RF-PRO-12 | Esencial | Cumplido | CP-PRO-12 | El catálogo público aplica `estado_publico_producto` (SQL, web y Android con los mismos casos). | Local + Real |

### Ventas (VEN)

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RF-VEN-01 | Esencial | Cumplido | CP-VEN-01 | `registrar_venta` atómica e idempotente; descuenta stock (SQL y concurrencia). | Local + Real |
| RF-VEN-02 | Esencial | Cumplido | CP-VEN-02 | Pieza única vendida sale del catálogo. | Local · Demo |
| RF-VEN-03 | Importante | Cumplido | — | Precio, fecha y lugar con historial activo. | Local |
| RF-VEN-04 | Importante | Cumplido | — | Historial con filtros (`HistorialVentasScreen`). | Local |
| RF-VEN-05 | Importante | Cumplido | — | Sugiere la feria del día (`Entrega7Test`). | Local |

### Compartir (COM)

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RF-COM-01 | Esencial | Cumplido | CP-COM-01 | Menú nativo en tienda, producto, asociación y feria. | Local (emulador) · Demo |
| RF-COM-02 | Esencial | Cumplido | CP-COM-02 | QR de tienda, asociación y feria: guardar o compartir. | Local · Demo |
| RF-COM-03 | Esencial | Cumplido | CP-COM-03 | `?tienda=`, `?producto=`, `?asociacion=` y `?feria=` sin sesión (e2e). | Real |

### Ferias (FER)

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RF-FER-01 | Esencial | Cumplido | CP-FER-01 | Crear, editar, publicar y cancelar con todos los campos y mapa (web; SQL E7; e2e). | Real |
| RF-FER-02 | Esencial | Cumplido | CP-FER-02 | Invitar tiendas y asociaciones (`invitar_a_feria`). | Real |
| RF-FER-03 | Importante | Cumplido | — | Agregar directamente (`p_directo`). | Local |
| RF-FER-04 | Esencial | Cumplido | CP-FER-04 | Checklist con los 4 estados (`checklist_feria`). | Local + Real |
| RF-FER-05 | Importante | Cumplido | — | Búsqueda y filtros por estado, tipo y asociación (e2e). | Local |
| RF-FER-06 | Importante | Cumplido | — | Recordatorios y avisos a confirmadas (`enviar_aviso_feria`). | Local |
| RF-FER-07 | Importante | Cumplido | — | Asistencia real (`registrar_asistencia`). | Local |
| RF-FER-08 | Esencial | Cumplido | CP-FER-08 | Push de invitación y «Participo / No participo» con un toque. | Real (22-nov: push recibido) |
| RF-FER-09 | Esencial | Cumplido | CP-FER-09 | Calendario en «Mis ferias» (`Entrega7Test`). | Local · Demo |
| RF-FER-10 | Esencial | Cumplido | CP-FER-10 | Ficha pública con confirmadas y muestra de productos públicos (web y app). | Local + Real |

### Comprador (CMP)

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RF-CMP-01 | Esencial | Cumplido | CP-CMP-01 | Directorio con búsqueda y 3 filtros (web y app). | Real |
| RF-CMP-02 | Esencial | Cumplido | CP-CMP-02 | Tienda, producto y asociación. | Real |
| RF-CMP-03 | Esencial | Cumplido | CP-CMP-03 | Al elegir una opción cambian foto y precio (e2e). | Local + Real |
| RF-CMP-04 | Esencial | Cumplido | CP-CMP-04 | Calendario y ficha de feria. | Real |
| RF-CMP-05 | Esencial | Cumplido | CP-CMP-05 | WhatsApp, formulario o ambos según la tienda. | Local + Real |
| RF-CMP-06 | Esencial | Cumplido | CP-CMP-06 | Seguir y dejar de seguir (sin duplicar). | Real (24-nov) |
| RF-CMP-07 | Esencial | Cumplido | CP-CMP-07 | Push al confirmar la tienda seguida, con el emisor local. | Real (24-nov, emisor local) |
| RF-CMP-08 | Importante | Parcial | — | Notificación del navegador con la página abierta (sondeo cada 60 s). Con la página cerrada falta configurar Firebase web (`FIREBASE_WEB_CONFIG`). | Local |
| RF-CMP-09 | Importante | Cumplido | — | Calificar tienda y producto si están activas (`calificar`). | Local · Demo |

### Configuración de la tienda (CFG)

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RF-CFG-01 | Esencial | Cumplido | CP-CFG-01 | «Mostrar precios» → «Consultar precio». | Real |
| RF-CFG-02 | Esencial | Cumplido | CP-CFG-02 | WhatsApp, formulario o ambos. | Real |
| RF-CFG-03 | Esencial | Cumplido | CP-CFG-03 | «Mensajes recibidos» en la app. | Local + Real |
| RF-CFG-04 | Importante | Cumplido | — | Avisos de contacto con horario; fuera de horario se guarda sin avisar (SQL E7). | Local |
| RF-CFG-05 | Importante | Cumplido | — | Interruptor de calificaciones. | Local |
| RF-CFG-06 | Importante | Cumplido | — | Interruptor de historial de ventas. | Local |

### Asociaciones (ASO)

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RF-ASO-01 | Esencial | Cumplido | CP-ASO-01 | Crear, editar y desactivar con ficha (web, bloque «Asociaciones» del panel). | Local (e2e) · Demo |
| RF-ASO-02 | Esencial | Cumplido | CP-ASO-02 | Asignar y quitar integrantes (`tienda_asociaciones`). | Local + Real |
| RF-ASO-03 | Esencial | Cumplido | CP-ASO-03 | Vista pública con integrantes y acceso a tiendas. | Real |

### Administración (ADM)

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RF-ADM-01 | Esencial | Cumplido | CP-ADM-01 | Migración 10: el administrador autoriza correos, edita nombre y tienda, desactiva y reactiva desde el servidor (`listar_emprendedoras`, `editar_emprendedora`, `cambiar_estado_emprendedora`). Desactivar oculta la tienda y sus productos y bloquea cambios, ventas, respuestas y mensajes (CUENTA_DESACTIVADA), sin borrar nada. Queda en la bitácora. 38 casos SQL, más 3 de actualización desde el esquema anterior y reversión, y recorrido web. | Local · Demo (requiere 09 y 10) |
| RF-ADM-02 | Esencial | Cumplido | CP-ADM-02 | Navegación anónima en el panel web (`configuracion`). | Local + Real |
| RF-ADM-03 | Esencial | Cumplido | CP-ADM-03 | Pestaña web «🏷️ Tipos de producto» (migración 10): crear, editar, desactivar y eliminar solo si nadie lo usa. Un tipo en uso se desactiva y sus productos lo conservan. Solo ADMIN; queda en la bitácora. Los tipos de emprendimiento y las categorías son listas fijas del levantamiento (`CATEGORIAS`, `rubro`). | Local · Demo (requiere 10) |
| RF-ADM-04 | Importante | Cumplido | — | Migración 09: crear, editar, desactivar y reactivar otras cuentas de administración, sin la propia ni la última (56 casos SQL + e2e). | Local · Demo (requiere aplicar 09) |
| RF-ADM-05 | Condicionado | Pendiente | — | Sin financiamiento de IA (RN-13). Diseño en [ia-modulo-desacoplado.md](ia-modulo-desacoplado.md). | — |

### IA (Condicionado)

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RF-IA-01 a 03 | Condicionado | Pendiente | — | No se exige. Punto de conexión y reglas (no sugiere precios; la emprendedora acepta, edita o descarta) en [ia-modulo-desacoplado.md](ia-modulo-desacoplado.md). | — |

### Mapas (MAP)

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RF-MAP-01 | Esencial | Cumplido | CP-MAP-01 | Marcar en el mapa o buscar la dirección al crear o editar una feria. | Real |
| RF-MAP-02 | Esencial | Cumplido | CP-MAP-02 | Mapa y «Cómo llegar» en la app y la web. | Real |
| RF-MAP-03 | Esencial | Cumplido | CP-MAP-03 | Mapa de próximas ferias; con GPS se centra y ordena por distancia. | Local (emulador) · Demo |
| RF-MAP-04 | Importante | Cumplido | — | Punto de venta o retiro opcional. | Local |
| RF-MAP-05 | Importante | Cumplido | — | Sugiere la feria en curso por ubicación (`Entrega7Test`). | Local |

### No funcionales

| ID | Prioridad | Estado | Caso | Evidencia | Verificación |
|---|---|---|---|---|---|
| RNF-USA-01 | Esencial | Cumplido | CP-USA-01 | Lenguaje simple, íconos con texto y confirmaciones (revisión de pantallas). | Demo |
| RNF-USA-02 | Esencial | Cumplido | CP-USA-02 | Botones de 48 dp mínimo (componentes `BotonPrimario`/`BotonSecundario`). | Local |
| RNF-USA-03 | Esencial | Cumplido | CP-USA-03 | Textos en `sp`. **Prueba física pendiente** con letra grande (A08). | Demo |
| RNF-USA-04 | Importante | Cumplido | — | Borrador del producto nuevo guardado al salir (prueba en emulador, E5). | Local |
| RNF-USA-05 | Importante | Pendiente | — | No hubo una sesión de prueba con emprendedoras de la contraparte en esta etapa. Las pruebas se hicieron con cuentas del grupo. Plan: sesión guiada con el manual antes del traspaso. | — |
| RNF-CPT-01 | Esencial | Cumplido | CP-CPT-01 | `minSdk 24` (Android 7.0), definido por los celulares del levantamiento. | Demo |
| RNF-CPT-02 | Esencial | Cumplido | CP-CPT-02 | Web adaptable de 320 a 1280 px (e2e). | Local + Real |
| RNF-REN-01 | Esencial | Cumplido | CP-REN-01 | Fotos comprimidas a 1280 px y JPEG 80 % (app y web). | Local |
| RNF-REN-02 | Importante | Cumplido | — | Franja «Sin conexión» (entrega 8), cola de cambios por subir y borradores. | Local · Demo |
| RNF-SEG-01 | Esencial | Cumplido | CP-SEG-01 | RLS por perfil (`pruebas_rls.sql` y E5–E8). | Local |
| RNF-SEG-02 | Esencial | Cumplido | CP-SEG-02 | BiometricPrompt; solo un token de sesión cifrado por el sistema, sin datos biométricos. | Local |
| RNF-SEG-03 | Esencial | Cumplido | CP-SEG-03 | Sin credenciales en git (`.gitignore`, revisión del índice; ver informe de seguridad). | Local |
| RNF-SEG-04 | Esencial | Cumplido | CP-SEG-04 | **Dos baterías.** (1) Firebase Emulator Suite con las reglas versionadas de Realtime Database (`firebase/database.rules.json`, `npm run test:reglas`): 5 pruebas de permisos y validación. (2) Las reglas reales del backend Supabase (RLS) en PostgreSQL local desechable (`npm run test:sql`), por perfil. App Check exigido no se emula: se demuestra en la consola. | Local |
| RNF-SEG-05 | Esencial | Cumplido | CP-SEG-05 | [informe-seguridad.md](informe-seguridad.md): Android Lint, `npm audit`, revisión de dependencias y manifiesto. | Local |
| RNF-SEG-06 | Importante | Parcial | — | App Check en la app. APK público: Play Integrity, sin proveedor ni tokens de depuración (verificado con `npm run apk:verificar`). APK privado de pruebas: proveedor de depuración. **Decisión del grupo: camino sin costo, sin Google Play Console.** El APK público instalado a mano queda no verificado (marcado). La evidencia de rechazo se toma en la consola con el APK privado frente al `curl` equivalente: pendiente. La web y Supabase no verifican App Check. | Local · Pendiente (consola) |
| RNF-SEG-07 | Importante | Cumplido | — | Bitácora del servidor: quién, qué y cuándo (09; disparadores en ferias, asociaciones, cuentas y configuración). | Local · Demo (requiere 09) |
| RNF-SEG-08 | Esencial | Cumplido | CP-SEG-08 | [informe-seguridad.md](informe-seguridad.md). | Local |
| RNF-PRI-01 | Esencial | Cumplido | CP-PRI-01 | Aviso de privacidad (Ley 19.628) en la web y enlace desde la app. | Local (e2e) · Demo |
| RNF-PRI-02 | Esencial | Cumplido | CP-PRI-02 | Consentimiento explícito; baja en Mi perfil → Avisos (SQL E7). | Local + Real |
| RNF-PRI-03 | Esencial | Cumplido | CP-PRI-03 | Solo lo que la emprendedora publica (RLS anónima, `proteger_contacto`, 07 retira permisos de más). | Local + Real |
| RNF-PRI-04 | Esencial | Cumplido | CP-PRI-04 | Permiso solo al abrir el mapa; sin GPS funciona; no se guarda la ubicación. | Local (emulador) |
| RNF-DAT-01 | Esencial | Cumplido (equivalente) | CP-DAT-01 | Modelo documentado en `supabase/README.md` y `AGENTS.md` (tablas, claves, relaciones, RLS, índices), en PostgreSQL en vez de Firestore. | Local |
| RNF-DAT-02 | Esencial | Cumplido | CP-DAT-02 | Repositorios (Android) y `js/servicio.js` (web). | Local |
| RNF-DAT-03 | Importante | Cumplido (equivalente) | — | Copia local en el celular (`TramaStore`) y cola `Sincronizador`, en vez de la persistencia de Firestore. | Local |
| RNF-DAT-04 | Importante | Cumplido | — | `02_datos_prueba.sql`, `06_datos_demo.sql` y la base local desechable de las pruebas. | Local |
| RNF-OPE-01 | Importante | Cumplido | — | [manual-administrador.md](manual-administrador.md). Falta la prueba con una persona ajena (W10). | Demo |
| RNF-MAN-01 | Esencial | Cumplido | CP-MAN-01 | Kotlin y Android Studio, arquitectura en capas (`AGENTS.md`), README de ejecución. | Local |
| RNF-MAN-02 | Importante | Cumplido | — | [ia-modulo-desacoplado.md](ia-modulo-desacoplado.md). | — |
| RNF-MAN-03 | Esencial | Parcial | CP-MAN-03 | Ramas por entrega (`entrega5/catalogo-ventas`, `entrega6/vitrina-mapas`, `entrega7/ferias-seguidores`) integradas a `main` con merges. **Falta:** en GitHub hay un solo PR (#1, 4-nov, fusionado por su autor y sin revisiones). Las entregas 5 a 7 se integraron sin PR revisado y `main` no está protegida (decisión pendiente del grupo). | Real (historial) |
| RNF-MAN-04 | Esencial | Parcial | CP-MAN-04 | En GitHub solo existe `entrega-4` (a7149e8). Candidatos sin etiqueta: E5 `e1ebbe0`, E6 `f519e60`, E7 `91f84e4` o `adf2d49` (ver README de la entrega, sección 6). La `entrega-8` la crea el grupo sobre el commit de la demo. El historial muestra commits de 6 integrantes. | Real (historial) |
| RNF-MAN-05 | Importante | Pendiente | — | No se usaron issues enlazados a PR. Los requisitos se siguieron en `docs/` (matrices por entrega). | — |
| RNF-IDI-01 | Esencial | Cumplido | CP-IDI-01 | Español de Chile. En la entrega 8 se corrigieron dos textos con voseo y los rótulos internos del panel. | Local · Demo |

**Resumen de Esenciales:** 71 en total.
- Cumplidos: 69, de los cuales RNF-DAT-01 es equivalente (Supabase en vez de Firestore).
- Parciales: 2. Son de proceso del repositorio, no de la plataforma, y la decisión es del grupo:
  - **RNF-MAN-03:** PR revisados y `main` protegida.
  - **RNF-MAN-04:** etiquetas de las entregas 5 a 7.
- RN-01 aparte: es una regla de negocio, con el desvío del alta abierta.

## 2. Casos de prueba de los Esenciales

**Tipos de caso:**
- **Auto:** prueba automática.
- **Demo:** paso manual en la revisión.
- **Real:** ya hecho en la instancia.

**Resultado:**
- ✅ pasó;
- ⏳ por demostrar;
- ◐ parcial.

| Caso | Requisito | Dado / cuando / entonces | Tipo y dónde | Resultado |
|---|---|---|---|---|
| CP-RN-01 | RN-01 | Dado un cliente que envía `tipo_cuenta=ADMIN` en el alta, entonces queda VISITANTE. Solo un correo autorizado obtiene ADMIN. | Auto · `pruebas_rls.sql`, E5 | ✅ (◐ por el alta abierta de emprendedora) |
| CP-RN-02 | RN-02 | Dada una emprendedora, cuando llama `publicar_feria`, entonces recibe SOLO_ADMIN. | Auto · `pruebas_entrega7.js` | ✅ |
| CP-ACC-01 | RF-ACC-01 | Dada `donamarta@alerce.app`, cuando inicia sesión en la app y en la web, entonces ve su tienda. | Real · Demo A03/W04 | ✅ |
| CP-ACC-02 | RF-ACC-02 | Dada una sesión con contraseña, cuando activa «Ingresar con huella», entonces el próximo inicio pide la huella; al desactivarla, ya no. | Auto `ReglaHuellaTest` · Demo | ✅ · ⏳ |
| CP-ACC-03 | RF-ACC-03 | Dado el modo comprador, cuando toca «Continuar con Google», entonces vuelve con sesión. | Auto `SeguirConGoogleTest` · Demo (Google activo) | ✅ · ⏳ |
| CP-ACC-04 | RF-ACC-04 | Dado un visitante en la web, cuando se registra con correo, entonces entra como comprador. | Auto e2e `web.spec.js` (alta) | ✅ |
| CP-ACC-05 | RF-ACC-05 | Al abrir la app, se ven «Quiero comprar» y «Soy emprendedora» y cada uno abre su flujo. | Demo A02 | ✅ (Real E6) · ⏳ |
| CP-ACC-06 | RF-ACC-06 | Con INVITAR guardado en la web, al abrir la app sin sesión aparecen «Continuar con Google» y «Omitir». | Auto `Entrega6Test` · Demo | ✅ |
| CP-ACC-07 | RF-ACC-07 | Sin sesión, se abren el directorio, una tienda y una feria. | Auto e2e · Real W05 | ✅ |
| CP-ACC-08 | RF-ACC-08 | Sin sesión, «Seguir» pide ingresar y al volver queda «Siguiendo». | Auto e2e E7 + SQL | ✅ |
| CP-ACC-09 | RF-ACC-09 | Con sesión, «Cerrar sesión» limpia los datos privados. | Auto e2e `web.spec.js` | ✅ |
| CP-TIE-01 | RF-TIE-01 | La emprendedora cambia la descripción, cierra la app y vuelve: el cambio sigue. | Real · Demo A03 | ✅ · ⏳ |
| CP-PRO-01 | RF-PRO-01 | Alta guiada con foto de cámara hasta «Lo ven todos»: aparece en la vitrina. | Real · Demo A04 | ✅ · ⏳ |
| CP-PRO-02 | RF-PRO-02 | La ficha muestra foto, nombre, descripción, categoría, precio y tipo. | Auto `Entrega5Test` | ✅ |
| CP-PRO-04 | RF-PRO-04 | Editar el precio y eliminar con confirmación. | Auto SQL E5 · Demo | ✅ |
| CP-PRO-05 | RF-PRO-05 | Producto con dos opciones de distinto precio y stock. | Auto SQL E5, contrato | ✅ |
| CP-PRO-06 | RF-PRO-06 | Pieza única, a pedido y regular se muestran distinto. | Auto contrato (`casos_estado.json`) | ✅ |
| CP-PRO-07 | RF-PRO-07 | Sin stock no pide cantidad. | Auto `FormularioProductoTest` | ✅ |
| CP-PRO-08 | RF-PRO-08 | Al llegar a 0: agotado, oculto o a pedido según la regla. | Auto contrato (SQL, web, Android) | ✅ |
| CP-PRO-09 | RF-PRO-09 | «Se acabó» lo quita de la vitrina; la dueña lo sigue viendo. | Auto contrato | ✅ |
| CP-PRO-12 | RF-PRO-12 | La vitrina aplica modalidad, stock, agotado y visibilidad. | Auto contrato + e2e | ✅ |
| CP-VEN-01 | RF-VEN-01 | Venta de 2 unidades: el stock baja 2; un reintento no descuenta otra vez. | Auto SQL E5 + `concurrencia.js` · Demo A05 | ✅ |
| CP-VEN-02 | RF-VEN-02 | Venta de pieza única: desaparece de la vitrina pública. | Auto SQL E5 · Demo A05 | ✅ · ⏳ |
| CP-COM-01 | RF-COM-01 | «Compartir» abre el menú de Android con el enlace web. | Emulador E6 · Demo | ✅ |
| CP-COM-02 | RF-COM-02 | «Código QR» → guardar y compartir imagen. | Emulador E6 | ✅ |
| CP-COM-03 | RF-COM-03 | El enlace o QR abre la ficha sin sesión. | Auto e2e (`?tienda=`, `?feria=`…) | ✅ |
| CP-FER-01 | RF-FER-01 | Crear feria con mapa, publicar y cancelar con motivo. | Auto e2e E7 + SQL · Real W03 | ✅ |
| CP-FER-02 | RF-FER-02 | Invitar una tienda y una asociación sin duplicar. | Auto SQL E7 · Real | ✅ |
| CP-FER-04 | RF-FER-04 | El checklist muestra Invitada, Confirmada, Rechazada y Agregada. | Auto SQL + e2e E7 | ✅ |
| CP-FER-08 | RF-FER-08 | La emprendedora recibe el push y toca «Participo»: queda Confirmada. | Real 22-nov (push) · Auto SQL E8 | ✅ |
| CP-FER-09 | RF-FER-09 | «Mis ferias» muestra el calendario del mes con la invitada. | Auto `Entrega7Test` · Demo | ✅ |
| CP-FER-10 | RF-FER-10 | La ficha pública lista confirmadas con productos públicos. | Auto SQL + e2e | ✅ |
| CP-CMP-01 | RF-CMP-01 | Directorio con búsqueda y filtros por tipo, sector y asociación. | Auto e2e E6 · Real | ✅ |
| CP-CMP-02 | RF-CMP-02 | Abrir tienda, producto y asociación. | Auto e2e · Real | ✅ |
| CP-CMP-03 | RF-CMP-03 | Elegir una opción cambia foto y precio. | Auto e2e `web.spec.js` | ✅ |
| CP-CMP-04 | RF-CMP-04 | Calendario y ficha de feria. | Auto e2e E7 | ✅ |
| CP-CMP-05 | RF-CMP-05 | Botones según WhatsApp/formulario/ambos. | Auto e2e E6 | ✅ |
| CP-CMP-06 | RF-CMP-06 | Seguir y dejar de seguir una tienda. | Real 24-nov · Auto SQL | ✅ |
| CP-CMP-07 | RF-CMP-07 | La tienda seguida confirma una feria: llega el push. | Real 24-nov (emisor local) · Demo A07 | ✅ · ⏳ |
| CP-CFG-01 | RF-CFG-01 | Precios ocultos → «Consultar precio». | Auto e2e E6 | ✅ |
| CP-CFG-02 | RF-CFG-02 | Cambiar la forma de contacto cambia los botones. | Auto e2e E6 | ✅ |
| CP-CFG-03 | RF-CFG-03 | Un mensaje del formulario aparece en «Mensajes recibidos». | Auto SQL E6 · Real | ✅ |
| CP-ASO-01 | RF-ASO-01 | Crear, editar y desactivar una asociación. | Auto e2e E6 · SQL RLS | ✅ |
| CP-ASO-02 | RF-ASO-02 | Asignar y quitar integrante. | Auto SQL E7 + e2e | ✅ |
| CP-ASO-03 | RF-ASO-03 | Vista pública con integrantes → tienda. | Auto e2e E6 | ✅ |
| CP-ADM-01 | RF-ADM-01 | Autorizar, editar y desactivar una emprendedora. Desactivada: su tienda no se ve y no puede guardar productos ni ventas. Reactivada: vuelve todo. | Auto SQL `pruebas_emprendedoras_tipos.js` + e2e E8 · Demo | ✅ |
| CP-ADM-02 | RF-ADM-02 | Guardar INVITAR y recargar: sigue marcado. | Auto e2e E6 · Real | ✅ |
| CP-ADM-03 | RF-ADM-03 | Crear un tipo en la web: aparece en el alta. Desactivar uno en uso: el producto lo conserva. Eliminar solo si nadie lo usa. | Auto SQL + e2e E8 · Demo | ✅ |
| CP-MAP-01 | RF-MAP-01 | Buscar una dirección o tocar el mapa fija la ubicación. | Auto e2e E6 | ✅ |
| CP-MAP-02 | RF-MAP-02 | Ficha con mapa y «Cómo llegar». | Auto e2e E6 · Real | ✅ |
| CP-MAP-03 | RF-MAP-03 | Con permiso: centrado y ordenado por distancia. Sin permiso: por fecha. | Emulador E6 · Demo | ✅ |
| CP-USA-01 | RNF-USA-01 | Recorrido de la emprendedora sin palabras técnicas. | Demo | ⏳ |
| CP-USA-02 | RNF-USA-02 | Botones de 48 dp o más. | Revisión de componentes | ✅ |
| CP-USA-03 | RNF-USA-03 | Con la letra del sistema al máximo, acceso y tienda se usan. | Demo A08 | ⏳ |
| CP-CPT-01 | RNF-CPT-01 | Instalar en un celular con Android 7.0 o superior. | Demo A01 | ⏳ |
| CP-CPT-02 | RNF-CPT-02 | Sin desborde horizontal de 320 a 1280 px. | Auto e2e | ✅ |
| CP-REN-01 | RNF-REN-01 | Una foto de 4000 px se sube a 1280 px. | Auto `Entrega5Test` / web | ✅ |
| CP-SEG-01 | RNF-SEG-01 | Una emprendedora no edita otra tienda ni ve ventas ajenas; el admin no ve ventas. | Auto `pruebas_rls.sql` | ✅ |
| CP-SEG-02 | RNF-SEG-02 | La huella usa BiometricPrompt y no guarda datos biométricos. | Revisión de código + `ReglaHuellaTest` | ✅ |
| CP-SEG-03 | RNF-SEG-03 | `git ls-files` no contiene propiedades, claves ni cuentas de servicio. | Auto (revisión del índice, informe) | ✅ |
| CP-SEG-04 | RNF-SEG-04 | Reglas de Realtime Database en el emulador (permitido / rechazado) y RLS de Supabase por perfil (visitante, compradora, dos emprendedoras, dos administradoras, emisor). | Auto `npm run test:reglas` (5/5) + `npm run test:sql` (570/570) | ✅ |
| CP-SEG-05 | RNF-SEG-05 | Lint, `npm audit` y revisión de dependencias sin hallazgos abiertos. | Informe de seguridad | ✅ |
| CP-SEG-08 | RNF-SEG-08 | El informe reúne pruebas, hallazgos, gravedad y cierre. | Informe de seguridad | ✅ |
| CP-PRI-01 | RNF-PRI-01 | El aviso de privacidad se abre desde la web y desde Ajustes de la app. | Auto e2e E8 · Demo W09 | ✅ |
| CP-PRI-02 | RNF-PRI-02 | Sin consentimiento no hay aviso; al retirarlo se omiten los pendientes. | Auto SQL E7 | ✅ |
| CP-PRI-03 | RNF-PRI-03 | El visitante no lee contactos, ventas ni productos ocultos. | Auto `pruebas_rls.sql`, E7 | ✅ |
| CP-PRI-04 | RNF-PRI-04 | El permiso se pide solo en «Mapa ferias» y rechazarlo no rompe nada. | Emulador E6 | ✅ |
| CP-DAT-01 | RNF-DAT-01 | El modelo documentado coincide con el esquema. | Revisión `supabase/README.md` | ✅ |
| CP-DAT-02 | RNF-DAT-02 | Las pantallas no llaman a la base directamente. | Revisión (`Repositorios.kt`, `servicio.js`) | ✅ |
| CP-MAN-01 | RNF-MAN-01 | Clonar y ejecutar siguiendo el README. | Demo C11 | ⏳ |
| CP-MAN-03 | RNF-MAN-03 | Historial con ramas por entrega y merges. | `git log --graph` | ◐ |
| CP-MAN-04 | RNF-MAN-04 | Etiqueta `entrega-8` sobre el commit de la demo. | Demo C01 | ⏳ |
| CP-IDI-01 | RNF-IDI-01 | Sin pantallas en otro idioma ni voseo. | Búsqueda en el código + Demo | ✅ |
