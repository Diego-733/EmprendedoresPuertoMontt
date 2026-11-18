"use strict";
// Recorridos de la Entrega 6 en la web (checklist W01–W17) con un Supabase sustituto.
// La seguridad real (RLS de contactos, configuración, ferias) se prueba en tests/sql.
const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");
const { RAIZ } = require("./servidor");

const ORIGEN = "http://alerce.test";
const FALSO = fs.readFileSync(path.join(__dirname, "supabase_falso.js"), "utf8");
const TIPOS = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".otf": "font/otf", ".png": "image/png" };
// Leaflet servido desde npm (si está instalado: `npm i --no-save leaflet@1.9.4` o LEAFLET_DIST=…/leaflet/dist)
// para no depender de internet. Sin él, se omiten solo las comprobaciones del dibujo del mapa.
const LEAFLET = (() => {
  const candidatos = [path.join(__dirname, "..", "..", "node_modules", "leaflet", "dist"), process.env.LEAFLET_DIST || ""];
  return candidatos.find((d) => d && fs.existsSync(path.join(d, "leaflet.js"))) || null;
})();
const PNG = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=", "base64");

function hoy(dias) {
  const d = new Date(); d.setDate(d.getDate() + dias);
  return d.toISOString().slice(0, 10);
}

const BASE_TIENDA = { owner_id: null, bio: "Tienda de prueba", color_portada: 4284232027, color_logo: 4281236827, necesidades: {}, historial_activo: true, valores_producto: {} };
const TIENDAS = [
  Object.assign({}, BASE_TIENDA, { id: "t_tejidos", nombre: "Tejidos Austral", rubro: "Artesanía", sector: "Alerce Sur", whatsapp: "+56911112222", asociacion_id: "as1", contacto_modo: "AMBOS", mostrar_precios: true, punto_lat: -41.39, punto_lng: -72.89, punto_nombre: "Mi taller" }),
  Object.assign({}, BASE_TIENDA, { id: "t_pan", nombre: "Panadería Doña Rosa", rubro: "Alimentos", sector: "Alerce Histórico", whatsapp: "+56933334444", asociacion_id: "as1", contacto_modo: "FORMULARIO", mostrar_precios: false }),
  Object.assign({}, BASE_TIENDA, { id: "t_barber", nombre: "Barbería Alerce", rubro: "Servicios", sector: "Alerce Norte", whatsapp: "+56955556666", asociacion_id: null, contacto_modo: "WHATSAPP", mostrar_precios: true })
];
function producto(id, tienda, extra) {
  return Object.assign({ id, tienda_id: tienda, nombre: "Producto " + id, descripcion: "Descripción", categoria: "TEXTIL", precio: 1000,
    publicado: true, disponible: true, modalidad: "REGULAR", maneja_stock: true, al_agotarse: "MOSTRAR_AGOTADO", vendida_en: null,
    foto_url: "https://falso.supabase.co/p/" + id + ".jpg", variantes: [{ id: id + "_a", nombre: "Única", stock: 3 }] }, extra || {});
}

function datos() {
  return {
    tablas: {
      tiendas: JSON.parse(JSON.stringify(TIENDAS)),
      productos: [
        producto("chal", "t_tejidos", { variantes: [{ id: "c1", nombre: "Gris", stock: 2, precio: 30000, foto_url: "https://falso.supabase.co/p/gris.jpg" }, { id: "c2", nombre: "Blanco", stock: 1, precio: 35000, foto_url: "https://falso.supabase.co/p/blanco.jpg" }] }),
        producto("pan", "t_pan", { precio: 2500 }),
        producto("corte", "t_barber", { precio: 8000 })
      ],
      perfiles: [], tipos_producto: [], ventas: [], contactos: [],
      asociaciones: [{ id: "as1", nombre: "Manos del Sur", sector: "Alerce", localidad: "Puerto Montt", descripcion: "Red de artesanas", activa: true }],
      ferias: [
        { id: "f1", nombre: "Feria de Alerce", descripcion: "Feria comunal", imagen_url: "https://falso.supabase.co/p/feria.jpg", ubicacion: "Plaza de Alerce", fecha_inicio: hoy(3), fecha_fin: hoy(3), horario: "10 a 18", organizador: "Muni", estado: "PROXIMA", latitud: -41.3959, longitud: -72.8955 },
        { id: "f_pasada", nombre: "Feria Pasada", descripcion: "", ubicacion: "X", fecha_inicio: hoy(-10), fecha_fin: hoy(-9), horario: "", organizador: "", estado: "PROXIMA", latitud: null, longitud: null }
      ],
      configuracion: [{ clave: "navegacion_anonima", valor: "PERMITIR" }]
    }
  };
}

async function preparar(page, estadoFalso) {
  await page.route(ORIGEN + "/**", (route) => {
    const ruta = decodeURIComponent(new URL(route.request().url()).pathname);
    if (ruta.endsWith("/js/config.js")) {
      return route.fulfill({ contentType: "text/javascript", body: 'window.__CONFIG__ = {"supabase":{"url":"https://falso.supabase.co","anonKey":"clave-de-prueba"}};' });
    }
    const archivo = path.join(RAIZ, ruta === "/" ? "index.html" : ruta);
    if (!archivo.startsWith(RAIZ) || !fs.existsSync(archivo)) return route.fulfill({ status: 404, body: "" });
    return route.fulfill({ contentType: TIPOS[path.extname(archivo)] || "application/octet-stream", body: fs.readFileSync(archivo) });
  });
  await page.route("https://cdn.jsdelivr.net/npm/@supabase/**", (route) => route.fulfill({ contentType: "text/javascript", body: FALSO }));
  await page.route("https://unpkg.com/leaflet@1.9.4/dist/**", (route) => {
    const nombre = new URL(route.request().url()).pathname.split("/dist/").pop();
    if (!LEAFLET || !fs.existsSync(path.join(LEAFLET, nombre))) return route.fulfill({ status: 404, body: "" });
    return route.fulfill({ contentType: TIPOS[path.extname(nombre)] || "image/png", body: fs.readFileSync(path.join(LEAFLET, nombre)) });
  });
  await page.route(/^https:\/\/tile\.openstreetmap\.org\//, (route) => route.fulfill({ contentType: "image/png", body: PNG }));
  await page.route(/^https:\/\/nominatim\.openstreetmap\.org\//, (route) => route.fulfill({ contentType: "application/json", body: JSON.stringify([{ lat: "-41.4717", lon: "-72.9396", display_name: "Plaza de Armas, Puerto Montt, Chile" }]) }));
  await page.route("https://falso.supabase.co/auth/v1/settings", (route) => route.fulfill({ contentType: "application/json", body: JSON.stringify({ external: { google: true } }) }));
  await page.route(/^https:\/\/(fonts\.|picsum|images\.unsplash|falso\.supabase\.co\/(p|storage))/, (route) => route.fulfill({ status: 200, contentType: "image/svg+xml", body: "<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'/>" }));
  await page.addInitScript((estado) => { window.__FALSO__ = estado; }, estadoFalso);
}

test.describe("Entrega 6 · vitrina pública", () => {
  test("W01–W02: directorio de emprendedoras y asociaciones, búsqueda y 3 filtros", async ({ page }) => {
    await preparar(page, datos());
    await page.goto(ORIGEN + "/");
    const tarjetas = page.locator("#grilla-tiendas-marketplace .tarjeta-tienda");
    await expect(tarjetas).toHaveCount(3);
    await page.locator("#e6-filtro-tipo").selectOption("Servicios");
    await expect(tarjetas).toHaveCount(1);
    await expect(tarjetas.first()).toContainText("Barbería");
    await page.locator("#e6-limpiar-filtros").click();
    await page.locator("#e6-filtro-sector").selectOption("Alerce Histórico");
    await expect(tarjetas).toHaveCount(1);
    await expect(tarjetas.first()).toContainText("Panadería");
    await page.locator("#e6-limpiar-filtros").click();
    await page.locator("#e6-filtro-asociacion").selectOption("as1");
    await expect(tarjetas).toHaveCount(2);
    await page.locator("#e6-limpiar-filtros").click();
    await page.locator("#e6-busqueda").fill("tejidos");
    await expect(tarjetas).toHaveCount(1);
    await page.locator("#e6-limpiar-filtros").click();
    await expect(tarjetas).toHaveCount(3);
    await page.locator("#e6-tab-asociaciones").click();
    await expect(page.locator("#e6-lista-asociaciones-directorio .e6-tarjeta-asociacion")).toHaveCount(1);
    await expect(page.locator("#e6-lista-asociaciones-directorio")).toContainText("Manos del Sur");
    await expect(page.locator("#e6-lista-asociaciones-directorio")).toContainText("2 integrantes");
  });

  test("W05: la asociación lista integrantes y lleva a su tienda", async ({ page }) => {
    await preparar(page, datos());
    await page.goto(ORIGEN + "/");
    await page.locator("#e6-tab-asociaciones").click();
    await page.locator("#e6-lista-asociaciones-directorio button", { hasText: "Ver integrantes" }).click();
    await expect(page.locator("#modal-asociacion")).toHaveClass(/abierto/);
    await expect(page.locator("#modal-asociacion .e6-integrante")).toHaveCount(2);
    await page.locator("#modal-asociacion button", { hasText: "Ver tienda" }).first().click();
    await expect(page.locator("#modal-tienda-vitrina")).toHaveClass(/abierto/);
    await expect(page.locator("#lista-productos-tienda-modal .producto-fila-card").first()).toBeVisible();
  });

  test("W06–W07: contacto según configuración y «Consultar precio»", async ({ page }) => {
    const e = datos();
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await expect(page.locator("#grilla-tiendas-marketplace .tarjeta-tienda")).toHaveCount(3);
    // Solo WhatsApp
    await page.evaluate(() => window.TramaWeb.abrirVitrinaTienda("t_barber"));
    await expect(page.locator("#btn-modal-tienda-whatsapp")).toBeVisible();
    await expect(page.locator("#btn-modal-tienda-formulario")).toBeHidden();
    await expect(page.locator("#lista-productos-tienda-modal")).toContainText("$ 8.000");
    await page.keyboard.press("Escape");
    // Solo formulario y precios ocultos
    await page.evaluate(() => window.TramaWeb.abrirVitrinaTienda("t_pan"));
    await expect(page.locator("#btn-modal-tienda-whatsapp")).toBeHidden();
    await expect(page.locator("#btn-modal-tienda-formulario")).toBeVisible();
    await expect(page.locator("#lista-productos-tienda-modal")).toContainText("Consultar precio");
    await expect(page.locator("#lista-productos-tienda-modal")).not.toContainText("2.500");
    await expect(page.locator("#lista-productos-tienda-modal .btn-whatsapp-prod")).toHaveCount(0);
    await page.locator("#btn-modal-tienda-formulario").click();
    await expect(page.locator("#modal-contacto")).toHaveClass(/abierto/);
    await page.locator("#contacto-nombre").fill("Vecina");
    await page.locator("#contacto-medio").fill("+56 9 1111 2222");
    await page.locator("#contacto-mensaje").fill("¿Tiene pan hoy?");
    await page.locator("#btn-enviar-contacto").click();
    await expect(page.locator("#modal-contacto")).not.toHaveClass(/abierto/);
    const enviado = await page.evaluate(() => window.__FALSO__.tablas.contactos[0]);
    expect(enviado).toMatchObject({ tienda_id: "t_pan", nombre: "Vecina", mensaje: "¿Tiene pan hoy?" });
    // Ambos
    await page.keyboard.press("Escape");
    await page.evaluate(() => window.TramaWeb.abrirVitrinaTienda("t_tejidos"));
    await expect(page.locator("#btn-modal-tienda-whatsapp")).toBeVisible();
    await expect(page.locator("#btn-modal-tienda-formulario")).toBeVisible();
  });

  test("W04 + RF-MAP-04: variante cambia foto y precio; punto de retiro en mapa", async ({ page }) => {
    await preparar(page, datos());
    await page.goto(ORIGEN + "/");
    await expect(page.locator("#grilla-tiendas-marketplace .tarjeta-tienda")).toHaveCount(3);
    await page.evaluate(() => window.TramaWeb.abrirVitrinaTienda("t_tejidos"));
    const tarjeta = page.locator('[data-producto-vitrina="chal"]');
    await expect(tarjeta.locator(".prod-precio")).toHaveText("$ 30.000");
    await tarjeta.locator(".chip-variante", { hasText: "Blanco" }).click();
    await expect(page.locator('[data-producto-vitrina="chal"] .prod-precio')).toHaveText("$ 35.000");
    await expect(page.locator('[data-producto-vitrina="chal"] img.prod-foto')).toHaveAttribute("src", /blanco/);
    if (LEAFLET) await expect(page.locator("#mapa-punto-tienda.leaflet-container")).toBeVisible();
    await expect(page.locator("#modal-tienda-extras a.e6-como-llegar")).toHaveAttribute("href", /destination=-41\.39,-72\.89/);
  });

  test("W16–W17: ficha de feria con mapa y «Cómo llegar»; las pasadas no se listan", async ({ page }) => {
    await preparar(page, datos());
    await page.goto(ORIGEN + "/");
    const ferias = page.locator("#grilla-ferias-container .tarjeta-feria-editorial");
    await expect(ferias).toHaveCount(1);
    await ferias.first().locator("button", { hasText: "Ver ficha y mapa" }).click();
    await expect(page.locator("#modal-feria")).toHaveClass(/abierto/);
    if (LEAFLET) {
      await expect(page.locator("#mapa-ficha-feria.leaflet-container")).toBeVisible();
      await expect(page.locator("#mapa-ficha-feria .leaflet-marker-icon")).toHaveCount(1);
    }
    await expect(page.locator("#modal-feria-cuerpo a.e6-como-llegar")).toHaveAttribute("href", "https://www.google.com/maps/dir/?api=1&destination=-41.3959,-72.8955");
  });

  for (const [tipo, id, selector] of [["producto", "chal", "#modal-producto"], ["asociacion", "as1", "#modal-asociacion"], ["feria", "f1", "#modal-feria"], ["tienda", "t_pan", "#modal-tienda-vitrina"]]) {
    test(`RF-COM-03: el enlace ?${tipo}= abre su página sin sesión`, async ({ page }) => {
      await preparar(page, datos());
      await page.goto(ORIGEN + "/?" + tipo + "=" + id);
      await expect(page.locator(selector)).toHaveClass(/abierto/);
    });
  }

  test("W08: directorio y filtros usables en teléfono (375 px) sin desborde", async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 });
    await preparar(page, datos());
    await page.goto(ORIGEN + "/");
    await expect(page.locator("#grilla-tiendas-marketplace .tarjeta-tienda")).toHaveCount(3);
    const m = await page.evaluate(() => ({ doc: document.documentElement.scrollWidth, vista: window.innerWidth }));
    expect(m.doc).toBeLessThanOrEqual(m.vista);
    const alto = await page.locator("#e6-filtro-tipo").evaluate((el) => el.getBoundingClientRect().height);
    expect(alto).toBeGreaterThanOrEqual(44);
    await page.locator("#e6-filtro-tipo").selectOption("Artesanía");
    await expect(page.locator("#grilla-tiendas-marketplace .tarjeta-tienda")).toHaveCount(1);
    await page.evaluate(() => window.TramaWeb.abrirVitrinaTienda("t_tejidos"));
    const panel = await page.locator("#modal-tienda-vitrina .modal-panel-editorial").evaluate((el) => ({ sw: el.scrollWidth, cw: el.clientWidth }));
    expect(panel.sw).toBeLessThanOrEqual(panel.cw + 1);
  });
});

test.describe("Entrega 6 · administración", () => {
  function conAdmin() {
    const e = datos();
    e.tablas.perfiles = [{ id: "u-admin", nombre: "Admin", rol: "ADMIN", tienda_id: null }];
    e.sesion = { user: { id: "u-admin", email: "admin@alerce.app", user_metadata: {} }, access_token: "falso" };
    return e;
  }

  test("W12: elige la navegación anónima y queda guardada al recargar", async ({ page }) => {
    await preparar(page, conAdmin());
    await page.goto(ORIGEN + "/");
    const radios = page.locator('input[name="e6-nav-anonima"]');
    await expect(radios).toHaveCount(3);
    await expect(page.locator('input[name="e6-nav-anonima"][value="PERMITIR"]')).toBeChecked();
    for (const valor of ["INVITAR", "OBLIGAR", "PERMITIR", "OBLIGAR"]) {
      await page.locator(`input[name="e6-nav-anonima"][value="${valor}"]`).check();
      await page.locator("#admin-e6 button", { hasText: "Guardar opción" }).click();
      await expect.poll(() => page.evaluate(() => window.__FALSO__.tablas.configuracion[0].valor)).toBe(valor);
    }
    // Al volver a abrir el panel, se lee del servidor la opción guardada (el sustituto
    // vive en memoria: recargar la página lo reiniciaría).
    await page.evaluate(() => window.AlerceVitrina6.pintarAdmin());
    await expect(page.locator('input[name="e6-nav-anonima"][value="OBLIGAR"]')).toBeChecked({ timeout: 10000 });
    await expect(page.locator("#admin-e6")).toContainText("Guardado en el servidor: Obligar");
  });

  test("W13–W15: crea una feria con todos sus datos y ubicación (búsqueda y mapa) y la edita", async ({ page }) => {
    await preparar(page, conAdmin());
    await page.goto(ORIGEN + "/");
    await page.locator("#admin-e6 button", { hasText: "Nueva feria" }).click();
    await page.locator("#e6-feria-nombre").fill("Feria Nueva");
    await page.locator("#e6-feria-descripcion").fill("Descripción de la feria");
    await page.locator("#e6-feria-inicio").fill(hoy(5));
    await page.locator("#e6-feria-horario").fill("10:00 a 18:00");
    await page.locator("#e6-feria-organizador").fill("Municipalidad");
    await page.locator("#e6-feria-imagen").setInputFiles({ name: "feria.png", mimeType: "image/png", buffer: PNG });
    // Sin ubicación no se guarda
    await page.locator("#e6-form-feria button", { hasText: "Guardar feria" }).click();
    await expect(page.locator("#e6-form-feria")).toContainText("ubicación en el mapa");
    await page.locator("#e6-feria-busqueda").fill("Plaza de Armas Puerto Montt");
    await page.locator("#e6-form-feria button", { hasText: "Buscar" }).click();
    await page.locator(".e6-resultado").first().click();
    await expect(page.locator("#e6-form-feria")).toContainText("Ubicación marcada: -41.4717, -72.9396");
    await expect(page.locator("#e6-feria-lugar")).not.toHaveValue("");
    // También se puede marcar tocando el mapa
    if (LEAFLET) {
      await page.locator("#mapa-form-feria").click({ position: { x: 60, y: 60 } });
      await expect(page.locator("#e6-form-feria")).not.toContainText("-41.4717, -72.9396");
    }
    await page.locator("#e6-form-feria button", { hasText: "Guardar feria" }).click();
    await expect(page.locator("#toast-texto")).toContainText("Feria creada");
    const creada = await page.evaluate(() => window.__FALSO__.tablas.ferias.find((f) => f.nombre === "Feria Nueva"));
    expect(creada).toMatchObject({ descripcion: "Descripción de la feria", horario: "10:00 a 18:00", organizador: "Municipalidad", estado: "PROXIMA" });
    expect(creada.imagen_url).toMatch(/^https:\/\/falso\.supabase\.co\/storage\/.*feria_/);
    expect(typeof creada.latitud).toBe("number");
    await expect(page.locator("#grilla-ferias-container")).toContainText("Feria Nueva");
    // Editar y volver a abrir
    await page.locator("#e6-admin-lista-ferias .e6-admin-item", { hasText: "Feria Nueva" }).locator("button", { hasText: "Editar" }).click();
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("Feria Nueva");
    await page.locator("#e6-feria-nombre").fill("Feria Nueva Editada");
    await page.locator("#e6-form-feria button", { hasText: "Guardar feria" }).click();
    await expect(page.locator("#toast-texto")).toContainText("Cambios de la feria guardados");
    await page.locator("#e6-admin-lista-ferias .e6-admin-item", { hasText: "Feria Nueva Editada" }).locator("button", { hasText: "Editar" }).click();
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("Feria Nueva Editada");
    const total = await page.evaluate(() => window.__FALSO__.tablas.ferias.filter((f) => f.nombre.startsWith("Feria Nueva")).length);
    expect(total).toBe(1);
  });

  test("asigna una emprendedora a una asociación y aparece como integrante", async ({ page }) => {
    await preparar(page, conAdmin());
    await page.goto(ORIGEN + "/");
    const fila = page.locator("#e6-admin-lista-asoc .e6-admin-item", { hasText: "Manos del Sur" });
    await expect(fila).toContainText("2 integrantes");
    await fila.locator("select").selectOption("t_barber");
    await expect(page.locator("#e6-admin-lista-asoc .e6-admin-item", { hasText: "Manos del Sur" })).toContainText("3 integrantes");
  });
});
