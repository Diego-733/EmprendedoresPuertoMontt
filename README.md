# 🛍️ Trama Alerce / PuertoEmprende — Emprendedores Puerto Montt

Plataforma integral diseñada para potenciar a las emprendedoras de Alerce y Puerto Montt. Cuenta con una **App Móvil nativa en Android (Jetpack Compose)** y una **Vitrina Web interactiva desplegada en GitHub Pages**.

---

## 📲 Accesos Rápidos y Descargas

* 📦 **[Descargar App Android (APK v1.4 · Entrega 6)](https://github.com/Diego-733/EmprendedoresPuertoMontt/raw/main/app-debug.apk)** *(Clic para descargar el archivo `.apk` e instalar en tu celular)*
* 🌐 **[Ver Vitrina Web en Vivo (GitHub Pages)](https://diego-733.github.io/EmprendedoresPuertoMontt/)**
* 📦 **[Descarga Alternativa del APK (vía GitHub Pages)](https://diego-733.github.io/EmprendedoresPuertoMontt/app-debug.apk)**
* 📋 **[Guía de la Entrega 6 (cómo probar cada criterio)](docs/entrega6/README.md)**

---

## ✨ Características Principales

### 1. 🛒 Publicación Ultra-Simple (Estilo Facebook Marketplace)
* Formulario directo, sin pasos complejos ni textos técnicos redundantes.
* Pensado especialmente para emprendedoras que solo utilizan WhatsApp y Facebook.
* Subida de fotos reales desde la galería del teléfono o explorador web.
* Selección táctil de disponibilidad inmediata o por encargo ("A pedido").

### 2. 🎨 Estudio y Generador de Afiches Comerciales (Flyers por Capas)
* Generador de flyers promocionales para compartir en WhatsApp, Facebook e Instagram.
* **Profundidad visual por capas:** Letras y gráficos gigantes detrás, producto en primer plano con sombra, y pastillas de precio / llamada a la acción en la parte superior.
* **5 Estilos de afiches profesionales:**
  1. ⚡ **Estilo 1 (Split Urbano):** Fondo dividido en 2 tonos, letras gigantes verticales rotadas detrás y producto en ángulo flotante.
  2. 🔥 **Estilo 2 (Tipografía Outline):** Fondo en degradé cálido con tipografía hueca de gran escala y banner de precio.
  3. ✨ **Estilo 3 (Splash Pop):** Explosión / sol orgánico de contraste en la capa posterior, producto al frente y botón *"➔ PEDIR AHORA"*.
  4. 🏷️ **Estilo 4 (Minimal Editorial):** Estética sobria, marco perimetral fino y tipografía refinada estilo Apple / Farfetch.
  5. 🌲 **Estilo 5 (Tradición):** Fondo verde bosque profundo, marco dorado y sello artesanal de identidad local.

### 3. 🔤 Tipografía Suiza y Selector de Colores Dinámico
* **Britti Sans:** Tipografía neo-grotesca moderna, limpia y de máxima legibilidad editorial integrada en formato OpenType (`.otf`) nativo.
* **Paleta dinámica de 8 colores:** Original, Carbón (`#18181B`), Carmín (`#DC2626`), Ámbar (`#F59E0B`), Cobalto (`#1D4ED8`), Bosque (`#047857`), Violeta (`#7C3AED`) y Fucsia (`#DB2777`), más selector hexadecimal libre en la web.
* Cálculo armónico de sombras y contrastes para garantizar legibilidad de precios y textos.

### 4. 🛒 Carrito de Compras & Integración con WhatsApp
* Selección de productos con variantes y cálculo automático de total.
* Generación de pedido formateado listo para enviar al WhatsApp de la emprendedora.

### 5. 📱 Códigos QR Oficiales
* Generación de códigos QR vectoriales para acceder directamente a la tienda o vitrina digital en ferias presenciales.
* En la app, el QR de una **tienda, asociación o feria** se puede **guardar en el celular o compartir como imagen**.

### 6. 🔎 Directorio, Asociaciones y Contacto (Entrega 6)
* Directorio de **emprendedoras y asociaciones** con búsqueda y filtros por **tipo de emprendimiento, sector y asociación**, en la web y en la app.
* Ficha pública de cada asociación con sus integrantes y acceso a sus tiendas.
* Cada emprendedora elige cómo la contactan (**WhatsApp, formulario o ambos**) y si muestra sus precios (si no, se ve **«Consultar precio»**).
* Los mensajes del formulario llegan a la **bandeja "Mensajes recibidos"** de la app.
* Compartir tienda, producto y asociación con el **menú nativo de Android**: el enlace abre la vitrina web sin iniciar sesión.

### 7. 🗺️ Ferias y Mapas (Entrega 6)
* Ficha de cada feria con **mapa y botón "Cómo llegar"** (abre la app de mapas del celular), en la web y en la app.
* **Mapa de próximas ferias** en la app: con permiso de ubicación se centra en tu posición y ordena por cercanía; sin permiso, por fecha. El permiso se pide solo al abrir el mapa y la ubicación no se guarda en el servidor.
* **Punto de venta o retiro** opcional de cada emprendedora, marcado en un mapa y visible en su tienda.
* Mapas con OpenStreetMap (Leaflet en la web, osmdroid en la app): no requieren clave de API.

---

## 📥 ¿Cómo instalar la App en tu Celular Android?

1. Descarga el archivo [`app-debug.apk`](https://github.com/Diego-733/EmprendedoresPuertoMontt/raw/main/app-debug.apk) (v1.4 · Entrega 6). Si tenías una versión anterior, se actualiza sobre ella.
2. En tu celular, abre el archivo descargado.
3. Si el sistema te lo solicita, activa la opción **"Permitir la instalación de aplicaciones de fuentes desconocidas"** en tu navegador o explorador de archivos.
4. Presiona **Instalar** y ¡listo! Podrás utilizar la app completa en tu dispositivo.

---

## 📂 Estructura del Proyecto

```text
├── app-debug.apk                  # Última versión compilada del APK para instalación directa
├── App para el celular/           # Código fuente de la App Android nativa
│   ├── app/src/main/java/...      # Pantallas Jetpack Compose, navegación y estado
│   │   └── ui/comprador/          # Modo comprador: directorio, tienda, mapa de ferias, QR (Entrega 6)
│   └── app/src/main/res/font/     # Tipografías nativas (Britti Sans)
├── LA PAgina (segun)/             # Código fuente de la Vitrina Web
│   └── proyectode10/
│       ├── index.html             # Interfaz web responsiva con canvas de afiches
│       ├── css/styles.css         # Estilos sobrios minimalistas estilo Apple
│       ├── js/main.js             # Motor Canvas 2D, carrito, QR y marketplace
│       ├── js/vitrina6.js         # Directorio, asociaciones, ferias con mapa y panel admin (Entrega 6)
│       └── fonts/                 # Tipografías web (@font-face)
├── supabase/                      # Base de datos (06_vitrina_mapas.sql = Entrega 6)
├── docs/entrega6/                 # Guía de la Entrega 6
└── README.md                      # Documentación oficial del proyecto
```

---

## 👥 Roles de Usuario

* **Emprendedoras:** Gestión de productos, creación y exportación de afiches para redes sociales, control de inventario y pedidos.
* **Compradores:** Exploración de vitrina local con filtros por tipo, sector y asociación, mapa de ferias, armado de carrito y contacto por WhatsApp o formulario.
* **Administrador:** Registro de emprendedoras, gestión de asociaciones, ferias con ubicación en el mapa y configuración de la navegación sin sesión de la app (permitir, invitar u obligar el ingreso con Google).
