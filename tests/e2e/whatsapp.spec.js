"use strict";
// WhatsApp de cada tienda en la web: ficha, producto, carrito y ayuda. Supabase sustituto y
// window.open interceptado: se comprueba el destinatario y el texto, sin enviar mensajes reales.
const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");
const { RAIZ } = require("./servidor");
const sinSri = (archivo) => /index.html$/.test(archivo) ? fs.readFileSync(archivo, "utf8").replace(/(supabase-js@[^"]+") integrity="[^"]+"/, "$1") : fs.readFileSync(archivo);

const ORIGEN = "http://alerce.test";
const FALSO = fs.readFileSync(path.join(__dirname, "supabase_falso.js"), "utf8");
const TIPOS = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".otf": "font/otf", ".png": "image/png" };

const BASE = { owner_id: null, rubro: "Artesanía", sector: "Alerce", bio: "Tienda de prueba", color_portada: 4284232027, color_logo: 4281236827,
  necesidades: {}, historial_activo: true, valores_producto: {}, mostrar_precios: true };
const tienda = (id, nombre, whatsapp, modo) => Object.assign({}, BASE, { id, nombre, whatsapp, contacto_modo: modo || "AMBOS" });
const producto = (id, tiendaId, nombre, precio) => ({ id, tienda_id: tiendaId, nombre, descripcion: "", categoria: "TEXTIL", precio, publicado: true, disponible: true,
  modalidad: "REGULAR", maneja_stock: true, al_agotarse: "MOSTRAR_AGOTADO", vendida_en: null, foto_url: null, variantes: [{ id: id + "_u", nombre: "Única", stock: 5 }] });

function datos(numeroTejidos) {
  return { tablas: {
    tiendas: [tienda("t_tej", "Tejidos Austral", numeroTejidos === undefined ? "+56 9 1111 2222" : numeroTejidos),
              tienda("t_pan", "Panadería Rosa", ""),
              tienda("t_bar", "Barbería Norte", "1234", "WHATSAPP")],
    productos: [producto("chal", "t_tej", "Chal de lana", 15000), producto("pan", "t_pan", "Pan amasado", 2500), producto("corte", "t_bar", "Corte", 7000)],
    perfiles: [], tipos_producto: [], ferias: [], ventas: [], contactos: []
  }, respuestasRpc: {} };
}

async function preparar(page, estado, carritoPrevio) {
  page.on("pageerror", (e) => console.log("ERROR DE PÁGINA:", e.message));
  await page.route(ORIGEN + "/**", (route) => {
    const ruta = decodeURIComponent(new URL(route.request().url()).pathname);
    if (ruta.endsWith("/js/config.js")) return route.fulfill({ contentType: "text/javascript", body: 'window.__CONFIG__ = {"supabase":{"url":"https://falso.supabase.co","anonKey":"clave-de-prueba"}};' });
    const archivo = path.join(RAIZ, ruta === "/" ? "index.html" : ruta);
    if (!archivo.startsWith(RAIZ) || !fs.existsSync(archivo)) return route.fulfill({ status: 404, body: "" });
    return route.fulfill({ contentType: TIPOS[path.extname(archivo)] || "application/octet-stream", body: sinSri(archivo) });
  });
  await page.route("https://cdn.jsdelivr.net/npm/@supabase/**", (r) => r.fulfill({ contentType: "text/javascript", body: FALSO }));
  await page.route(/^https:\/\/(unpkg\.com|tile\.openstreetmap\.org|fonts\.|picsum|images\.unsplash)/, (r) => r.fulfill({ status: 404, body: "" }));
  await page.route("https://falso.supabase.co/auth/v1/settings", (r) => r.fulfill({ contentType: "application/json", body: JSON.stringify({ external: { google: false } }) }));
  await page.addInitScript(({ estado, carritoPrevio }) => {
    window.__FALSO__ = estado;
    window.__ABIERTOS__ = [];
    window.open = function (url) { window.__ABIERTOS__.push(String(url)); return null; };   // nada sale del navegador
    if (carritoPrevio) localStorage.setItem("trama_carrito_compras", JSON.stringify(carritoPrevio));
  }, { estado, carritoPrevio: carritoPrevio || null });
}

const abiertos = (page) => page.evaluate(() => window.__ABIERTOS__.map(u => { const x = new URL(u); return { numero: x.pathname.slice(1), texto: x.searchParams.get("text") }; }));
async function abrirCarrito(page) { await page.locator("#btn-flotante-carrito, #btn-abrir-carrito").first().click(); await expect(page.locator("#modal-carrito")).toBeVisible(); }

test("ficha y producto: con número abre WhatsApp de ESA tienda; sin número o inválido, avisa y ofrece «Enviar un mensaje» si lo acepta", async ({ page }) => {
  await preparar(page, datos());
  await page.goto(ORIGEN + "/?tienda=t_tej");
  await expect(page.locator("#btn-modal-tienda-whatsapp")).toHaveAttribute("href", /^https:\/\/wa\.me\/56911112222\?text=/);
  await expect(page.locator('[data-producto-vitrina="chal"] .btn-whatsapp-prod')).toHaveAttribute("href", /^https:\/\/wa\.me\/56911112222\?/);
  await expect(page.locator("#modal-tienda-sin-whatsapp")).toBeHidden();

  await page.goto(ORIGEN + "/?tienda=t_pan");
  await expect(page.locator("#btn-modal-tienda-whatsapp")).toBeHidden();
  await expect(page.locator("#modal-tienda-sin-whatsapp")).toHaveText("Esta tienda aún no tiene un WhatsApp de contacto");
  await expect(page.locator("#btn-modal-tienda-formulario")).toBeVisible();
  await expect(page.locator('[data-producto-vitrina="pan"]')).toContainText("Esta tienda aún no tiene un WhatsApp de contacto");
  await expect(page.locator('[data-producto-vitrina="pan"] .btn-whatsapp-prod')).toHaveCount(0);

  await page.goto(ORIGEN + "/?tienda=t_bar");   // número inválido y solo WhatsApp: aviso y sin formulario
  await expect(page.locator("#modal-tienda-sin-whatsapp")).toBeVisible();
  await expect(page.locator("#btn-modal-tienda-formulario")).toBeHidden();
  const hrefs = await page.evaluate(() => Array.from(document.querySelectorAll('a[href*="wa.me"]')).map(a => a.href));
  expect(hrefs.filter(h => /61230000|56961230000/.test(h))).toEqual([]);
});

test("carrito con dos tiendas: «Enviar pedido a [tienda]» por tienda, cada una recibe solo sus productos", async ({ page }) => {
  await preparar(page, Object.assign(datos(), { tablas: Object.assign(datos().tablas, { tiendas: [tienda("t_tej", "Tejidos Austral", "+56 9 1111 2222"), tienda("t_pan", "Panadería Rosa", "+56 9 3333 4444")] }) }));
  await page.goto(ORIGEN + "/?tienda=t_tej");
  await page.locator('[data-producto-vitrina="chal"] .btn-anadir-carrito').click();
  await page.evaluate(() => window.TramaWeb.abrirVitrinaTienda("t_pan"));
  await page.locator('[data-producto-vitrina="pan"] .btn-anadir-carrito').click();
  await page.locator('[data-producto-vitrina="pan"] .btn-anadir-carrito').click();
  await page.evaluate(() => window.TramaWeb.cerrarModal("modal-tienda-vitrina"));
  await abrirCarrito(page);
  await expect(page.locator("#btn-finalizar-pedido-general")).toBeHidden();
  await expect(page.locator("#aviso-carrito-varias-tiendas")).toContainText("productos de 2 tiendas");
  await page.locator("#modal-carrito .carrito-envio-tienda button", { hasText: "Enviar pedido por WhatsApp a Tejidos Austral" }).click();
  await page.locator("#modal-carrito .carrito-envio-tienda button", { hasText: "Enviar pedido por WhatsApp a Panadería Rosa" }).click();
  const envios = await abiertos(page);
  expect(envios.map(e => e.numero)).toEqual(["56911112222", "56933334444"]);
  expect(envios[0].texto).toContain("¡Hola Tejidos Austral!");
  expect(envios[0].texto).toContain("1 x Chal de lana");
  expect(envios[0].texto).not.toContain("Pan amasado");
  expect(envios[1].texto).toContain("2 x Pan amasado");
  expect(envios[1].texto).not.toContain("Chal de lana");
  // El pedido se conserva: el carrito sigue con sus productos.
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("trama_carrito_compras")).length)).toBe(2);
});

test("número cambiado después de agregar al carrito: el pedido va al número actual de la tienda", async ({ page }) => {
  const previo = [{ id: "c1", productoId: "chal", varianteId: "chal_u", tiendaId: "t_tej", tiendaNombre: "Tejidos Austral",
    tiendaWhatsapp: "+56 9 1111 2222", nombre: "Chal de lana", varianteNombre: "Única", precio: 15000, cantidad: 1, fotoUrl: null }];
  await preparar(page, datos("+56 9 5555 6666"), previo);   // la dueña cambió su número
  await page.goto(ORIGEN + "/");
  await abrirCarrito(page);
  await expect(page.locator("#btn-finalizar-pedido-general")).toHaveText("Enviar pedido por WhatsApp a Tejidos Austral");
  await page.locator("#btn-finalizar-pedido-general").click();
  const envios = await abiertos(page);
  expect(envios.map(e => e.numero)).toEqual(["56955556666"]);
});

test("tienda sin WhatsApp en el carrito: avisa y «Enviar un mensaje» abre el formulario con el pedido, sin perderlo", async ({ page }) => {
  const previo = [{ id: "c2", productoId: "pan", varianteId: "pan_u", tiendaId: "t_pan", tiendaNombre: "Panadería Rosa",
    tiendaWhatsapp: "+56 9 9999 9999", nombre: "Pan amasado", varianteNombre: "Única", precio: 2500, cantidad: 3, fotoUrl: null }];
  await preparar(page, datos(), previo);
  await page.goto(ORIGEN + "/");
  await abrirCarrito(page);
  const grupo = page.locator('[data-tienda-pedido="t_pan"]');
  await expect(grupo).toContainText("Esta tienda aún no tiene un WhatsApp de contacto");
  await expect(page.locator("#btn-finalizar-pedido-general")).toBeHidden();
  await grupo.locator("button", { hasText: "Enviar mensaje a Panadería Rosa" }).click();
  await expect(page.locator("#modal-contacto")).toBeVisible();
  await expect(page.locator("#contacto-mensaje")).toHaveValue(/3 x Pan amasado[\s\S]*Total: \$ 7\.500/);
  expect(await abiertos(page)).toEqual([]);   // no se abrió WhatsApp con el número viejo ni otro
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("trama_carrito_compras")).length)).toBe(1);
});

test("ayuda: sin el número fijo desconocido y con orientación para contactar a cada tienda", async ({ page }) => {
  await preparar(page, datos());
  await page.goto(ORIGEN + "/");
  const ayuda = page.locator("#ayuda");
  await expect(ayuda).toContainText("¿Cómo pido o pregunto por un producto?");
  await expect(ayuda).not.toContainText("6123");
  expect(await page.evaluate(() => document.documentElement.innerHTML.match(/6123[\s.-]?0000|56961230000/g))).toBeNull();
});
