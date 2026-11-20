# Entrega 6 — Vitrina, modo comprador, difusión y mapas

Checklist oficial: [checklist-revision-entrega-6.md](https://github.com/rinostrozacl/ProyectoVinculacionConElMedio2026/blob/main/docs/checklist-revision-entrega-6.md) (35 criterios).

## 1. Antes de la demo (una sola vez)

1. **Supabase → SQL Editor**: ejecutar `supabase/06_vitrina_mapas.sql` (después de 01 a 05). Se puede volver a ejecutar.
2. *(Opcional, datos de demo)*: ejecutar `supabase/06_datos_demo.sql` → descripciones de asociaciones, 3 tiendas asignadas a asociaciones y 2 ferias con ubicación.
3. **Ingreso con Google en la app (A08)**: Supabase → Authentication → URL Configuration → *Redirect URLs* → agregar
   `com.company.ultrapp://login-callback`. El proveedor Google debe estar activado (Authentication → Providers).
4. Publicar la web (push a `main`: GitHub Actions la despliega) e instalar el APK nuevo (`1.4-entrega6`).

## 2. Qué se agregó

| Parte | Archivos |
|---|---|
| Base de datos | `supabase/06_vitrina_mapas.sql` (contacto, precios, punto de retiro, asociación de la tienda, ferias con descripción/imagen/organizador, `configuracion`, `contactos` con RLS) |
| Web | `js/vitrina6.js`, `css/vitrina6.css`, cambios en `index.html`, `js/main.js`, `js/catalogo.js`, `js/servicio.js` |
| App | `ui/comprador/*` (directorio, tienda, producto, asociación, feria, mapa, QR), `ui/auth/AccesoScreen.kt`, `ui/emprendedora/VitrinaConfigScreen.kt`, `data/VitrinaStore.kt`, `data/remoto/VitrinaRepositorio.kt` |
| Pruebas | `tests/sql/pruebas_entrega6.js`, `tests/e2e/entrega6.spec.js`, `app/src/test/.../Entrega6Test.kt` |

Mapas: **Leaflet + OpenStreetMap** en la web y **osmdroid** en la app. No requieren clave de API.

## 3. Dónde se prueba cada criterio

### Web
| ID | Cómo mostrarlo |
|---|---|
| W01 | Vitrina → pestañas **Emprendedoras / Asociaciones** (sin sesión). También sección "Directorio y Asociaciones". |
| W02 | Barra de filtros: **Buscar**, **Tipo de emprendimiento**, **Sector**, **Asociación**. |
| W03 | "Ver vitrina" en una tarjeta. |
| W04 | En el catálogo, tocar una opción (chip): cambian foto y precio. |
| W05 | Pestaña Asociaciones → **Ver integrantes** → **Ver tienda**. |
| W06 | Botones según la tienda: WhatsApp, **Escribir un mensaje** (formulario) o ambos. |
| W07 | Tienda con precios ocultos → **Consultar precio** (no se puede agregar al carrito). |
| W08 | Abrir en el navegador del teléfono: filtros apilados, botones de 44 px. |
| W09 | Emprendedora → **Mi Tienda** → guardar → se ve en la vitrina. |
| W10 | Emprendedora → **Nuevo producto** / gestión de catálogo. |
| W11 | Emprendedora → **Caja POS** → venta → baja el stock en la vitrina. |
| W12 | Admin → Panel Admin → **Navegación anónima de la app** (3 opciones) → Guardar. Recargar: sigue marcada. |
| W13 | Admin → **Ferias → Nueva feria**: nombre, descripción, imagen, lugar, fechas, horario, organizador. |
| W14 | En el mismo formulario: **Buscar dirección** o tocar el mapa. |
| W15 | **Editar** la feria, guardar y volver a abrir. |
| W16 | Sección Próximas Ferias → **Ver ficha y mapa**. |
| W17 | **Cómo llegar** (en el celular abre Google Maps con el destino). |

### App Android
| ID | Cómo mostrarlo |
|---|---|
| A01–A02 | Pestaña **Vitrina**: chips Emprendedoras/Asociaciones, búsqueda y 3 filtros. |
| A03 | Tocar una tienda. |
| A04 | Tocar un producto → elegir opción: cambian foto y precio. |
| A05 | Chip "Integrante de …" o pestaña Asociaciones → integrantes → tienda. |
| A06 | Botones **Escribir por WhatsApp** / **Enviar un mensaje** según la configuración. |
| A07 | Tienda con precios ocultos: "Consultar precio". |
| A08 | Cerrar sesión / abrir sin sesión con cada opción guardada en la web: PERMITIR entra directo; INVITAR muestra **Continuar con Google** + **Omitir**; OBLIGAR solo **Continuar con Google**. |
| A09 | **Compartir** en tienda, producto y asociación (menú nativo). El enlace abre la web sin sesión (`?tienda=`, `?producto=`, `?asociacion=`). |
| A10 | **Código QR** en tienda, asociación y feria → **Compartir imagen** / **Guardar en el celular**. |
| A11 | Ficha de feria: mapa + **Cómo llegar**. |
| A12 | Pestaña **Mapa ferias**. |
| A13 | Aceptar el permiso: mapa centrado en la persona y ferias por distancia (muestra "a X km"). |
| A14 | Rechazar el permiso: el mapa sigue funcionando, ferias por fecha. |
| A15 | El permiso se pide solo al entrar a **Mapa ferias** (la app nunca abre en esa pestaña). |
| A16 | Emprendedora → **Tienda** → **Precios, contacto y punto de retiro**. |
| A17 | Emprendedora → **Tienda** → **Mensajes recibidos**. |
| A18 | En la misma pantalla de A16: tocar el mapa para marcar el punto → la tienda pública lo muestra con mapa y "Cómo llegar". |

## 4. Pruebas

```bash
npm run test:web     # reglas del catálogo (node --test)
npm run test:sql     # PostgreSQL local: incluye 06 (navegación, contactos, punto, ferias)
npm run test:e2e     # navegador: incluye tests/e2e/entrega6.spec.js (W01–W17)
cd "App para el celular" && ./gradlew testDebugUnitTest assembleDebug
```

> Revisión y correcciones posteriores (mapas en las pruebas, CANCELADA, fechas en Santiago, varias asociaciones): [docs/entrega7/README.md](../entrega7/README.md).
