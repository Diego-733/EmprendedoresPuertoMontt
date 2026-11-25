"use strict";
// Recorridos de la Entrega 6 en la web (checklist W01–W17) con un Supabase sustituto.
// La seguridad real (RLS de contactos, configuración, ferias) se prueba en tests/sql.
const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");
const { RAIZ } = require("./servidor");
// El SDK de Supabase se reemplaza por uno falso: en la copia servida a la prueba se quita su verificación SRI.
const sinSri = (archivo) => /index.html$/.test(archivo) ? fs.readFileSync(archivo, "utf8").replace(/(supabase-js@[^"]+") integrity="[^"]+"/, "$1") : fs.readFileSync(archivo);

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
    return route.fulfill({ contentType: TIPOS[path.extname(archivo)] || "application/octet-stream", body: sinSri(archivo) });
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
    await expect(page.locator("#mapa-punto-tienda.leaflet-container")).toBeVisible();
    await expect(page.locator("#mapa-punto-tienda .leaflet-marker-icon")).toHaveCount(1);
    await expect(page.locator("#modal-tienda-extras a.e6-como-llegar")).toHaveAttribute("href", /destination=-41\.39,-72\.89/);
  });

  test("W16–W17: ficha de feria con mapa y «Cómo llegar»; las pasadas no se listan", async ({ page }) => {
    await preparar(page, datos());
    await page.goto(ORIGEN + "/");
    const ferias = page.locator("#grilla-ferias-container .tarjeta-feria-editorial");
    await expect(ferias).toHaveCount(1);
    await ferias.first().locator("button", { hasText: "Ver ficha y mapa" }).click();
    await expect(page.locator("#modal-feria")).toHaveClass(/abierto/);
    await expect(page.locator("#mapa-ficha-feria.leaflet-container")).toBeVisible();
    await expect(page.locator("#mapa-ficha-feria .leaflet-marker-icon")).toHaveCount(1);
    await expect(page.locator("#mapa-ficha-feria .leaflet-tile-loaded").first()).toBeVisible();
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
    await expect(page.locator("#mapa-form-feria.leaflet-container")).toBeVisible();
    await page.locator("#mapa-form-feria").click({ position: { x: 60, y: 60 } });
    await expect(page.locator("#e6-form-feria")).not.toContainText("-41.4717, -72.9396");
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

  // ---- Avance de «Nueva feria» guardado en el navegador (por cuenta, sin Supabase)
  const CLAVE_AVANCE = "alerce_borrador_feria_v1:";
  function conDosAdmins(idSesion) {
    const e = conAdmin();
    e.tablas.perfiles.push({ id: "u-admin2", nombre: "Admin Dos", rol: "ADMIN", tienda_id: null });
    if (idSesion === "u-admin2") e.sesion = { user: { id: "u-admin2", email: "admin2@alerce.app", user_metadata: {} }, access_token: "falso" };
    return e;
  }
  async function abrirNuevaFeria(page) {
    await page.locator("#admin-e6 button", { hasText: "Nueva feria" }).click();
    await expect(page.locator("#e6-form-feria")).toBeVisible();
  }
  const avance = (page, cuenta) => page.evaluate((k) => JSON.parse(localStorage.getItem(k) || "null"), CLAVE_AVANCE + cuenta);
  const imagenGuardada = (page, cuenta) => page.evaluate((c) => new Promise((ok) => {
    const r = indexedDB.open("alerce_borradores", 1);
    r.onupgradeneeded = () => r.result.createObjectStore("imagenes_feria");
    r.onsuccess = () => { const g = r.result.transaction("imagenes_feria").objectStore("imagenes_feria").get(c); g.onsuccess = () => { ok(g.result ? (g.result.blob || g.result).size : 0); r.result.close(); }; };
  }), cuenta);
  // Solicitudes a Supabase: las del sustituto (window.__FALSO__.llamadas) y cualquier petición de red a *.supabase.co
  // salvo las imágenes servidas por las rutas de prueba.
  function vigilarRed(page, registro) {
    page.on("request", (r) => { if (/supabase\.co\/(rest|auth|storage)\/v1\/(?!object\/public)/.test(r.url()) && !/auth\/v1\/settings/.test(r.url())) registro.push(r.method() + " " + r.url()); });
  }
  const llamadas = (page) => page.evaluate(() => window.__FALSO__.llamadas.length);
  const escrituras = (page) => page.evaluate(() => window.__FALSO__.llamadas.filter((l) => l.subida || l.borrado || (l.tabla && l.op !== "select")));
  async function esperarCargaEstable(page) {
    await expect(page.locator("#admin-e6 button", { hasText: "Nueva feria" })).toBeVisible();
    let antes = -1;
    await expect.poll(async () => { const n = await llamadas(page); const quieto = n === antes; antes = n; return quieto; }, { intervals: [400, 400, 400, 400] }).toBe(true);
  }
  async function comprobarRecuperada(page, lat) {
    await expect(page.locator("#e6-feria-recuperada")).toHaveText("Recuperamos la feria que estabas preparando.");
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("Feria en preparación");
    await expect(page.locator("#e6-feria-descripcion")).toHaveValue("Una feria a medio escribir");
    await expect(page.locator("#e6-feria-inicio")).toHaveValue(hoy(6));
    await expect(page.locator("#e6-feria-fin")).toHaveValue(hoy(7));
    await expect(page.locator("#e6-feria-estado")).toHaveValue("BORRADOR");
    await expect(page.locator("#e6-form-feria .e6-imagen-previa img")).toHaveAttribute("src", /^blob:/);
    await expect(page.locator("#e6-form-feria")).toContainText("Ubicación marcada: " + lat.lat + ", " + lat.lng);
    await expect(page.locator("#e6-feria-aviso-imagen")).toHaveText("");
  }

  test("Nueva feria: el avance vuelve al salir, al recargar y al cerrar la pestaña, sin solicitudes a Supabase", async ({ page, context }) => {
    test.skip(!LEAFLET, "Requiere Leaflet local para marcar el mapa");
    const red = [];
    vigilarRed(page, red);
    await preparar(page, conAdmin());
    await page.goto(ORIGEN + "/");
    await esperarCargaEstable(page);
    const base = await llamadas(page);
    const redBase = red.length;

    await abrirNuevaFeria(page);
    await expect(page.locator("#e6-feria-recuperada")).toHaveText("");
    await page.locator("#e6-feria-nombre").fill("Feria en preparación");
    await page.locator("#e6-feria-descripcion").fill("Una feria a medio escribir");
    await page.locator("#e6-feria-inicio").fill(hoy(6));
    await page.locator("#e6-feria-fin").fill(hoy(7));
    await page.locator("#e6-feria-estado").selectOption("BORRADOR");
    await page.locator("#e6-feria-imagen").setInputFiles({ name: "feria.png", mimeType: "image/png", buffer: PNG });
    await expect(page.locator("#mapa-form-feria.leaflet-container")).toBeVisible();
    await page.locator("#mapa-form-feria").click({ position: { x: 80, y: 80 } });
    await expect(page.locator("#e6-form-feria")).toContainText("Ubicación marcada");
    await expect.poll(async () => (await avance(page, "u-admin") || {}).imagen).toBe("guardada");
    expect(await imagenGuardada(page, "u-admin")).toBeGreaterThan(0);
    const guardado = await avance(page, "u-admin");
    expect(guardado).toMatchObject({ nombre: "Feria en preparación", inicio: hoy(6), fin: hoy(7), estado: "BORRADOR" });
    expect(typeof guardado.lat).toBe("number");
    const punto = { lat: guardado.lat, lng: guardado.lng };

    // 1) Salir con el botón y volver a abrir.
    await page.locator("#e6-feria-salir").click();
    await expect(page.locator("#e6-form-feria")).toBeHidden();
    await expect(page.locator("#e6-btn-nueva-feria")).toContainText("continuar la que preparabas");
    await abrirNuevaFeria(page);
    await comprobarRecuperada(page, punto);
    await expect(page.locator("#toast-texto")).toContainText("Recuperamos la feria");

    expect(await llamadas(page)).toBe(base);
    expect(red.length).toBe(redBase);

    // 2) Cambiar de sección: el panel se vuelve a dibujar (eso relee la configuración del panel,
    //    no el avance) y el formulario desaparece.
    await page.evaluate(() => window.AlerceVitrina6.pintarAdmin());
    await expect(page.locator("#e6-form-feria")).toBeHidden();
    await esperarCargaEstable(page);
    const trasPanel = await llamadas(page);
    await abrirNuevaFeria(page);
    await comprobarRecuperada(page, punto);
    expect(await llamadas(page)).toBe(trasPanel);
    expect(red.length).toBe(redBase);

    // 3) Recargar la página.
    await page.reload();
    await esperarCargaEstable(page);
    const trasRecarga = await llamadas(page);
    const redRecarga = red.length;
    await abrirNuevaFeria(page);
    await comprobarRecuperada(page, punto);
    await page.waitForTimeout(500);
    expect(await llamadas(page)).toBe(trasRecarga);
    expect(red.length).toBe(redRecarga);
    expect(await escrituras(page)).toEqual([]);

    // 4) Cerrar la pestaña y abrir otra en el mismo navegador.
    const otra = await context.newPage();
    vigilarRed(otra, red);
    await preparar(otra, conAdmin());
    await page.close();
    await otra.goto(ORIGEN + "/");
    await esperarCargaEstable(otra);
    const enOtra = await llamadas(otra);
    const redOtra = red.length;
    await abrirNuevaFeria(otra);
    await comprobarRecuperada(otra, punto);
    await otra.waitForTimeout(500);
    expect(await llamadas(otra)).toBe(enOtra);
    expect(red.length).toBe(redOtra);
    expect(await escrituras(otra)).toEqual([]);
    expect(red).toEqual([]);
  });

  test("Nueva feria: otra cuenta no ve el avance; si guardar falla se conserva; guardar y descartar lo limpian; editar no cambia", async ({ page }) => {
    test.skip(!LEAFLET, "Requiere Leaflet local para marcar el mapa");
    const dialogos = [];
    let respuesta = true;
    page.on("dialog", (d) => { dialogos.push(d.message()); return respuesta ? d.accept() : d.dismiss(); });
    await preparar(page, conDosAdmins("u-admin"));
    await page.goto(ORIGEN + "/");
    await abrirNuevaFeria(page);
    await page.locator("#e6-feria-nombre").fill("Feria en preparación");
    await page.locator("#e6-feria-imagen").setInputFiles({ name: "feria.png", mimeType: "image/png", buffer: PNG });
    await expect(page.locator("#mapa-form-feria.leaflet-container")).toBeVisible();
    await page.locator("#mapa-form-feria").click({ position: { x: 90, y: 70 } });
    await expect.poll(async () => (await avance(page, "u-admin") || {}).imagen).toBe("guardada");

    // Otra cuenta administradora en el mismo navegador: formulario vacío.
    await page.addInitScript((estado) => { window.__FALSO__ = estado; }, conDosAdmins("u-admin2"));
    await page.reload();
    await abrirNuevaFeria(page);
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("");
    await expect(page.locator("#e6-feria-recuperada")).toHaveText("");
    await expect(page.locator("#e6-form-feria .e6-imagen-previa img")).toHaveCount(0);
    await expect(page.locator("#e6-form-feria")).toContainText("Toca el mapa");
    await expect(page.locator("#e6-btn-nueva-feria")).toHaveText("➕ Nueva feria");
    expect(await avance(page, "u-admin2")).toBeNull();
    // Descartar sin cambios no pregunta.
    await page.locator("#e6-feria-descartar").click();
    await expect(page.locator("#e6-form-feria")).toBeHidden();
    expect(dialogos).toEqual([]);

    // De vuelta en la primera cuenta, su avance sigue ahí.
    await page.addInitScript((estado) => { window.__FALSO__ = estado; }, conDosAdmins("u-admin"));
    await page.reload();
    await abrirNuevaFeria(page);
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("Feria en preparación");
    await expect(page.locator("#e6-form-feria .e6-imagen-previa img")).toHaveAttribute("src", /^blob:/);

    // Completa y falla al guardar: se conserva lo ingresado (también al recargar).
    await page.locator("#e6-feria-descripcion").fill("Descripción");
    await page.locator("#e6-feria-lugar").fill("Plaza de Alerce");
    await page.locator("#e6-feria-inicio").fill(hoy(8));
    await page.locator("#e6-feria-horario").fill("10:00 a 18:00");
    await page.locator("#e6-feria-organizador").fill("Municipalidad");
    await page.evaluate(() => { window.__FALSO__.fallar["ferias:upsert"] = { message: "falla simulada del servidor" }; });
    await page.locator("#e6-form-feria button", { hasText: "Guardar feria" }).click();
    await expect(page.locator("#e6-form-feria")).toContainText("No se guardó");
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("Feria en preparación");
    expect(await avance(page, "u-admin")).toMatchObject({ lugar: "Plaza de Alerce", horario: "10:00 a 18:00" });
    await page.reload();
    await abrirNuevaFeria(page);
    await expect(page.locator("#e6-feria-lugar")).toHaveValue("Plaza de Alerce");
    await expect(page.locator("#e6-form-feria .e6-imagen-previa img")).toHaveAttribute("src", /^blob:/);

    // Guardar bien: llega al servidor (con la imagen recuperada) y el avance se borra.
    await page.locator("#e6-form-feria button", { hasText: "Guardar feria" }).click();
    await expect(page.locator("#toast-texto")).toContainText("Feria creada");
    const creada = await page.evaluate(() => window.__FALSO__.tablas.ferias.find((f) => f.nombre === "Feria en preparación"));
    expect(creada).toMatchObject({ ubicacion: "Plaza de Alerce", estado: "PROXIMA" });
    expect(creada.imagen_url).toMatch(/^https:\/\/falso\.supabase\.co\/storage\/.*feria_/);
    expect(typeof creada.latitud).toBe("number");
    expect(await avance(page, "u-admin")).toBeNull();
    await expect.poll(() => imagenGuardada(page, "u-admin")).toBe(0);
    await expect(page.locator("#e6-btn-nueva-feria")).toHaveText("➕ Nueva feria");
    await abrirNuevaFeria(page);
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("");
    await expect(page.locator("#e6-feria-recuperada")).toHaveText("");
    await expect(page.locator("#e6-form-feria .e6-imagen-previa img")).toHaveCount(0);

    // Descartar con cambios pide confirmación: «Cancelar» conserva, «Aceptar» borra.
    await page.locator("#e6-feria-nombre").fill("Feria que no va");
    respuesta = false;
    await page.locator("#e6-feria-descartar").click();
    expect(dialogos.length).toBe(1);
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("Feria que no va");
    respuesta = true;
    await page.locator("#e6-feria-descartar").click();
    expect(dialogos.length).toBe(2);
    await expect(page.locator("#e6-form-feria")).toBeHidden();
    expect(await avance(page, "u-admin")).toBeNull();
    await abrirNuevaFeria(page);
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("");

    // Editar una feria existente: igual que antes (Cancelar, sin avance guardado).
    await page.locator("#e6-admin-lista-ferias .e6-admin-item", { hasText: "Feria de Alerce" }).locator("button", { hasText: "Editar" }).click();
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("Feria de Alerce");
    await expect(page.locator("#e6-feria-descartar")).toHaveCount(0);
    await page.locator("#e6-feria-nombre").fill("Cambio sin guardar");
    await page.locator("#e6-form-feria button", { hasText: "Cancelar" }).click();
    expect(await avance(page, "u-admin")).toBeNull();
    await page.locator("#e6-admin-lista-ferias .e6-admin-item", { hasText: "Feria de Alerce" }).locator("button", { hasText: "Editar" }).click();
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("Feria de Alerce");
    expect(await page.evaluate(() => window.__FALSO__.tablas.ferias.find((f) => f.id === "f1").nombre)).toBe("Feria de Alerce");
  });

  test("Nueva feria: si el navegador no conserva la imagen, se mantienen los demás campos y se pide elegirla de nuevo", async ({ page }) => {
    await preparar(page, conAdmin());
    // Simula un navegador sin IndexedDB (modo privado restrictivo): los textos sí se guardan.
    await page.addInitScript(() => { Object.defineProperty(window, "indexedDB", { value: undefined, configurable: true }); });
    await page.goto(ORIGEN + "/");
    await abrirNuevaFeria(page);
    await page.locator("#e6-feria-nombre").fill("Feria sin imagen guardada");
    await page.locator("#e6-feria-imagen").setInputFiles({ name: "feria.png", mimeType: "image/png", buffer: PNG });
    await expect(page.locator("#e6-feria-aviso-imagen")).toContainText("tendrás que elegirla de nuevo");
    await page.reload();
    await abrirNuevaFeria(page);
    await expect(page.locator("#e6-feria-recuperada")).toHaveText("Recuperamos la feria que estabas preparando.");
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("Feria sin imagen guardada");
    await expect(page.locator("#e6-feria-aviso-imagen")).toContainText("No pudimos conservar la imagen en este navegador: elígela de nuevo.");
    await expect(page.locator("#e6-form-feria .e6-imagen-previa img")).toHaveCount(0);
    await page.locator("#e6-form-feria button", { hasText: "Guardar feria" }).click();
    await expect(page.locator("#e6-form-feria")).toContainText("imagen");
  });

  // ---- Orden de finalización y fallos del guardado local
  // Retiene canvas.toBlob (el último paso de la compresión) para decidir desde la prueba
  // en qué orden terminan las fotos. Cada foto se reconoce por su ancho en píxeles.
  const RETENER_COMPRESION = () => {
    const original = HTMLCanvasElement.prototype.toBlob;
    window.__colaCompresion = [];
    window.__retenerCompresion = false;
    HTMLCanvasElement.prototype.toBlob = function (cb, ...args) {
      if (!window.__retenerCompresion) return original.call(this, cb, ...args);
      const canvas = this;
      window.__colaCompresion.push({ ancho: canvas.width, liberar: () => original.call(canvas, cb, ...args) });
    };
    window.__liberarCompresion = (ancho) => {
      const i = window.__colaCompresion.findIndex((e) => e.ancho === ancho);
      if (i < 0) throw new Error("No hay compresión retenida de ancho " + ancho);
      window.__colaCompresion.splice(i, 1)[0].liberar();
    };
  };
  async function pngDeAncho(page, ancho) {
    const b64 = await page.evaluate((w) => {
      const c = document.createElement("canvas"); c.width = w; c.height = w;
      const g = c.getContext("2d"); g.fillStyle = "#c33"; g.fillRect(0, 0, w, w);
      return c.toDataURL("image/png").split(",")[1];
    }, ancho);
    return { name: "foto_" + ancho + ".png", mimeType: "image/png", buffer: Buffer.from(b64, "base64") };
  }
  const anchoGuardado = (page, cuenta) => page.evaluate((c) => new Promise((ok) => {
    const r = indexedDB.open("alerce_borradores", 1);
    r.onupgradeneeded = () => r.result.createObjectStore("imagenes_feria");
    r.onsuccess = () => {
      const g = r.result.transaction("imagenes_feria").objectStore("imagenes_feria").get(c);
      g.onsuccess = () => { r.result.close(); if (!g.result) return ok(0); createImageBitmap(g.result.blob).then((b) => ok(b.width), () => ok(-1)); };
    };
  }), cuenta);
  const anchoVistaPrevia = (page) => page.evaluate(() => { const i = document.querySelector("#e6-form-feria .e6-imagen-previa img"); return i && i.complete ? i.naturalWidth : 0; });
  const compresionesRetenidas = (page) => page.evaluate(() => window.__colaCompresion.length);

  for (const orden of [[3, 5], [5, 3]]) {
    test(`Nueva feria: elijo la foto A (3 px) y luego B (5 px); terminan en orden ${orden.join(" → ")} y al volver aparece B`, async ({ page }) => {
      await preparar(page, conAdmin());
      await page.addInitScript(RETENER_COMPRESION);
      await page.goto(ORIGEN + "/");
      await abrirNuevaFeria(page);
      await page.locator("#e6-feria-nombre").fill("Feria con dos fotos");
      const fotoA = await pngDeAncho(page, 3), fotoB = await pngDeAncho(page, 5);
      await page.evaluate(() => { window.__retenerCompresion = true; });
      await page.locator("#e6-feria-imagen").setInputFiles(fotoA);
      await expect.poll(() => compresionesRetenidas(page)).toBe(1);
      await page.locator("#e6-feria-imagen").setInputFiles(fotoB);
      await expect.poll(() => compresionesRetenidas(page)).toBe(2);
      for (const ancho of orden) {
        await page.evaluate((w) => window.__liberarCompresion(w), ancho);
        await page.waitForTimeout(400);
      }
      await expect.poll(async () => (await avance(page, "u-admin") || {}).imagen).toBe("guardada");
      expect(await anchoGuardado(page, "u-admin")).toBe(5);
      await page.locator("#e6-feria-salir").click();
      await page.reload();
      await abrirNuevaFeria(page);
      await expect(page.locator("#e6-feria-nombre")).toHaveValue("Feria con dos fotos");
      await expect.poll(() => anchoVistaPrevia(page)).toBe(5);
      await expect(page.locator("#e6-feria-aviso-imagen")).toHaveText("");
    });
  }

  test("Nueva feria: la compresión pendiente de una feria descartada no borra ni reemplaza la foto de la siguiente", async ({ page }) => {
    const dialogos = [];
    page.on("dialog", (d) => { dialogos.push(d.message()); d.accept(); });
    await preparar(page, conAdmin());
    await page.addInitScript(RETENER_COMPRESION);
    await page.goto(ORIGEN + "/");
    const fotoA = await pngDeAncho(page, 3), fotoB = await pngDeAncho(page, 5);

    // a) Feria descartada con su foto aún comprimiéndose: al terminar no deja nada guardado.
    await abrirNuevaFeria(page);
    await page.locator("#e6-feria-nombre").fill("Feria que se descarta");
    await page.evaluate(() => { window.__retenerCompresion = true; });
    await page.locator("#e6-feria-imagen").setInputFiles(fotoA);
    await expect.poll(() => compresionesRetenidas(page)).toBe(1);
    await page.locator("#e6-feria-descartar").click();
    await expect(page.locator("#e6-form-feria")).toBeHidden();
    await page.evaluate(() => window.__liberarCompresion(3));
    await page.waitForTimeout(600);
    expect(await anchoGuardado(page, "u-admin")).toBe(0);
    expect(await avance(page, "u-admin")).toBeNull();

    // b) Feria descartada con foto pendiente → la siguiente feria elige otra foto, que termina
    //    ANTES que la pendiente de la descartada. La siguiente conserva la suya.
    await abrirNuevaFeria(page);
    await page.locator("#e6-feria-nombre").fill("Otra que se descarta");
    await page.locator("#e6-feria-imagen").setInputFiles(fotoA);
    await expect.poll(() => compresionesRetenidas(page)).toBe(1);
    await page.locator("#e6-feria-descartar").click();
    await expect(page.locator("#e6-form-feria")).toBeHidden();
    await abrirNuevaFeria(page);
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("");
    await page.locator("#e6-feria-nombre").fill("La feria siguiente");
    await page.locator("#e6-feria-imagen").setInputFiles(fotoB);
    await expect.poll(() => compresionesRetenidas(page)).toBe(2);
    await page.evaluate(() => window.__liberarCompresion(5));
    await expect.poll(async () => (await avance(page, "u-admin") || {}).imagen).toBe("guardada");
    await page.evaluate(() => window.__liberarCompresion(3));
    await page.waitForTimeout(600);
    expect(await anchoGuardado(page, "u-admin")).toBe(5);
    expect(await avance(page, "u-admin")).toMatchObject({ nombre: "La feria siguiente", imagen: "guardada" });
    expect(dialogos.length).toBe(2);
    await page.reload();
    await abrirNuevaFeria(page);
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("La feria siguiente");
    await expect.poll(() => anchoVistaPrevia(page)).toBe(5);
  });

  const FALLAR_AVANCE = () => {
    const original = Storage.prototype.setItem;
    window.__fallarAvance = false;
    Storage.prototype.setItem = function (k, v) {
      if (window.__fallarAvance && String(k).startsWith("alerce_borrador_feria")) throw new DOMException("Almacenamiento lleno", "QuotaExceededError");
      return original.call(this, k, v);
    };
  };

  test("Nueva feria: si falla el guardado local con una versión anterior, avisa y «Salir» pide confirmar perder los últimos cambios", async ({ page }) => {
    const dialogos = [];
    let aceptar = false;
    page.on("dialog", (d) => { dialogos.push(d.message()); return aceptar ? d.accept() : d.dismiss(); });
    await preparar(page, conAdmin());
    await page.addInitScript(FALLAR_AVANCE);
    await page.goto(ORIGEN + "/");
    await abrirNuevaFeria(page);
    await page.locator("#e6-feria-nombre").fill("Versión 1");
    expect(await avance(page, "u-admin")).toMatchObject({ nombre: "Versión 1" });
    await expect(page.locator("#e6-feria-aviso-avance")).toHaveText("");

    // La última edición no se puede guardar: aviso visible y queda la versión anterior.
    await page.evaluate(() => { window.__fallarAvance = true; });
    await page.locator("#e6-feria-nombre").fill("Versión 2");
    await expect(page.locator("#e6-feria-aviso-avance")).toContainText("No pudimos guardar tu avance en este navegador");
    await expect(page.locator("#e6-feria-aviso-avance")).toContainText("verás la versión guardada antes");
    expect(await avance(page, "u-admin")).toMatchObject({ nombre: "Versión 1" });

    // «Salir» → «Cancelar»: el formulario sigue abierto con lo último escrito.
    await page.locator("#e6-feria-salir").click();
    expect(dialogos).toHaveLength(1);
    expect(dialogos[0]).toContain("No pudimos guardar tus últimos cambios");
    await expect(page.locator("#e6-form-feria")).toBeVisible();
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("Versión 2");

    // «Salir» → «Aceptar»: se cierra y al volver aparece la versión anterior.
    aceptar = true;
    await page.locator("#e6-feria-salir").click();
    expect(dialogos).toHaveLength(2);
    await expect(page.locator("#e6-form-feria")).toBeHidden();
    await abrirNuevaFeria(page);
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("Versión 1");
    await expect(page.locator("#e6-feria-recuperada")).toHaveText("Recuperamos la feria que estabas preparando.");

    // Cuando el navegador vuelve a guardar, el aviso desaparece y «Salir» no pregunta.
    await page.locator("#e6-feria-nombre").fill("Versión 3");
    await expect(page.locator("#e6-feria-aviso-avance")).toContainText("No pudimos guardar");
    await page.evaluate(() => { window.__fallarAvance = false; });
    await page.locator("#e6-feria-nombre").fill("Versión 3b");
    await expect(page.locator("#e6-feria-aviso-avance")).toHaveText("");
    await page.locator("#e6-feria-salir").click();
    expect(dialogos).toHaveLength(2);
    await expect(page.locator("#e6-form-feria")).toBeHidden();
    await page.reload();
    await abrirNuevaFeria(page);
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("Versión 3b");
  });

  test("Nueva feria: si el guardado local falla desde el inicio, avisa que se perderá todo y al salir confirmado vuelve vacía", async ({ page }) => {
    const dialogos = [];
    page.on("dialog", (d) => { dialogos.push(d.message()); d.accept(); });
    await preparar(page, conAdmin());
    await page.addInitScript(FALLAR_AVANCE);
    await page.goto(ORIGEN + "/");
    await page.evaluate(() => { window.__fallarAvance = true; });
    await abrirNuevaFeria(page);
    await page.locator("#e6-feria-nombre").fill("Nunca guardada");
    await expect(page.locator("#e6-feria-aviso-avance")).toContainText("perderás lo que ingresaste");
    expect(await avance(page, "u-admin")).toBeNull();
    await page.locator("#e6-feria-salir").click();
    expect(dialogos).toHaveLength(1);
    await expect(page.locator("#e6-form-feria")).toBeHidden();
    await abrirNuevaFeria(page);
    await expect(page.locator("#e6-feria-nombre")).toHaveValue("");
    await expect(page.locator("#e6-feria-recuperada")).toHaveText("");
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
