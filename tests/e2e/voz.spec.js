"use strict";
// Buscar por voz y contacto claro en la vitrina web. Supabase sustituto, reconocimiento de voz
// SIMULADO (no se usa el micrófono) y window.open interceptado: no sale ningún mensaje real.
const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");
const { RAIZ } = require("./servidor");
const sinSri = (archivo) => /index.html$/.test(archivo) ? fs.readFileSync(archivo, "utf8").replace(/(supabase-js@[^"]+") integrity="[^"]+"/, "$1") : fs.readFileSync(archivo);

const ORIGEN = "http://alerce.test";
const FALSO = fs.readFileSync(path.join(__dirname, "supabase_falso.js"), "utf8");
const TIPOS = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".otf": "font/otf", ".png": "image/png" };

const BASE = { owner_id: null, rubro: "Repostería", sector: "Alerce", bio: "Tienda de prueba", color_portada: 4284232027, color_logo: 4281236827,
  necesidades: {}, historial_activo: true, valores_producto: {}, mostrar_precios: true };
const tienda = (id, nombre, whatsapp, modo, extra) => Object.assign({}, BASE, { id, nombre, whatsapp, contacto_modo: modo || "AMBOS" }, extra || {});
const producto = (id, tiendaId, nombre, precio) => ({ id, tienda_id: tiendaId, nombre, descripcion: "", categoria: "GOURMET", precio, publicado: true, disponible: true,
  modalidad: "REGULAR", maneja_stock: true, al_agotarse: "MOSTRAR_AGOTADO", vendida_en: null, foto_url: null, variantes: [{ id: id + "_u", nombre: "Única", stock: 5 }] });

const LARGO = "Repostería y Tortas Artesanales de la Señora Marta del Sector Alerce Norte";
function datos() {
  return { tablas: {
    tiendas: [
      tienda("t_wsp", "Tejidos Marta", "+56 9 4821 7735"),                           // WhatsApp válido
      tienda("t_msg", "Panadería Rosa", "+56 9 4821 0001", "FORMULARIO"),            // solo mensajes internos
      tienda("t_sin", "Barbería Norte", "1234", "WHATSAPP", { rubro: "Barbería" }),  // sin contacto válido
      tienda("t_demo", "Minimarket Ejemplo", "+56 9 8765 4321"),                     // número de los datos de ejemplo
      tienda("t_largo", LARGO, "+56 9 4821 9999")
    ],
    productos: [producto("torta", "t_largo", "Torta de chocolate para cumpleaños", 18000), producto("chal", "t_wsp", "Chal de lana", 15000),
      producto("pan", "t_msg", "Pan amasado", 2500), producto("corte", "t_sin", "Corte de pelo", 7000), producto("leche", "t_demo", "Leche entera", 1200)],
    perfiles: [], tipos_producto: [], ferias: [], ventas: [], contactos: []
  }, respuestasRpc: {} };
}

/** voz: null = navegador sin reconocimiento; si no, { modo, texto } para el reconocimiento simulado. */
async function preparar(page, { voz = { modo: "exito", texto: "torta de cumpleaños" }, seguro = true } = {}) {
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
  await page.addInitScript(({ estado, voz, seguro }) => {
    window.__FALSO__ = estado;
    window.__ABIERTOS__ = [];
    window.open = function (url) { window.__ABIERTOS__.push(String(url)); return null; };
    // El dominio de prueba es http: se simula el contexto seguro que tiene la web publicada (HTTPS).
    Object.defineProperty(window, "isSecureContext", { value: seguro, configurable: true });
    if (!voz) { window.SpeechRecognition = undefined; window.webkitSpeechRecognition = undefined; return; }
    window.__VOZ__ = { modo: voz.modo, texto: voz.texto, iniciados: 0, abortados: 0, lang: null };
    function resultado(texto, final) { var r = [{ transcript: texto }]; r.isFinal = final; return { resultIndex: 0, results: [r] }; }
    function Simulado() {}
    Simulado.prototype.start = function () {
      var yo = this, v = window.__VOZ__;
      v.iniciados++; v.lang = yo.lang;
      setTimeout(function () {
        if (v.modo === "exito") {
          yo.onresult(resultado(v.texto.split(" ")[0], false));
          setTimeout(function () { yo.onresult(resultado(v.texto, true)); yo.onend(); }, 60);
        } else if (v.modo === "permiso") { yo.onerror({ error: "not-allowed" }); yo.onend(); }
        else if (v.modo === "silencio") { yo.onerror({ error: "no-speech" }); yo.onend(); }
        else if (v.modo === "parcial") { yo.onresult(resultado(v.texto, false)); }   // sigue escuchando hasta que se cancele
      }, 40);
    };
    Simulado.prototype.abort = function () { var yo = this; window.__VOZ__.abortados++; setTimeout(function () { yo.onerror({ error: "aborted" }); yo.onend(); }, 10); };
    Simulado.prototype.stop = Simulado.prototype.abort;
    window.SpeechRecognition = undefined;
    window.webkitSpeechRecognition = Simulado;
  }, { estado: datos(), voz, seguro });
  await page.goto(ORIGEN + "/");
  await expect(page.locator("#grilla-productos-portada .portada-prod").first()).toBeVisible();
}

const campo = (page) => page.locator("#campo-busqueda-principal");
const mic = (page) => page.locator("#btn-buscar-voz");
const estadoVoz = (page) => page.locator("#estado-buscar-voz");
const productosVisibles = (page) => page.locator("#grilla-productos-portada .portada-prod-nombre").allTextContents();

test.describe("Buscar por voz", () => {
  test("dictado exitoso: muestra el texto, lo pone en el buscador y filtra igual que escribir; se puede corregir", async ({ page }) => {
    await preparar(page);
    await expect(mic(page)).toBeVisible();
    await expect(mic(page)).toHaveAccessibleName("Buscar por voz");
    await mic(page).click();
    await expect(estadoVoz(page)).toContainText("Buscando «torta de cumpleaños»");
    await expect(campo(page)).toHaveValue("torta de cumpleaños");
    expect(await page.evaluate(() => window.__VOZ__.lang)).toBe("es-CL");
    expect(await productosVisibles(page)).toEqual(["Torta de chocolate para cumpleaños"]);
    // Lo mismo escrito a mano da el mismo resultado (misma búsqueda).
    await campo(page).fill("");
    await campo(page).pressSequentially("torta de cumpleaños");
    expect(await productosVisibles(page)).toEqual(["Torta de chocolate para cumpleaños"]);
    // Corregir el texto reconocido: se edita en el mismo buscador.
    await mic(page).click();
    await expect(campo(page)).toHaveValue("torta de cumpleaños");
    await expect(campo(page)).toBeFocused();
    await campo(page).fill("chal");
    expect(await productosVisibles(page)).toEqual(["Chal de lana"]);
    await expect(mic(page)).toHaveAttribute("aria-pressed", "false");
  });

  test("cancelar el dictado (tocar otra vez o Escape) conserva la búsqueda anterior", async ({ page }) => {
    await preparar(page, { voz: { modo: "parcial", texto: "pan con queso" } });
    await campo(page).fill("chal");
    await mic(page).click();
    await expect(mic(page)).toHaveAttribute("aria-pressed", "true");
    await expect(mic(page)).toHaveAccessibleName("Detener el dictado");
    await expect(estadoVoz(page)).toContainText("Escuchando: «pan con queso»");
    await expect(campo(page)).toHaveValue("chal");   // lo parcial no pisa el buscador
    await mic(page).click();
    await expect(estadoVoz(page)).toHaveText("Dictado cancelado. Tu búsqueda sigue igual.");
    await expect(campo(page)).toHaveValue("chal");
    expect(await productosVisibles(page)).toEqual(["Chal de lana"]);
    // Con Escape (el foco en el buscador: Escape no debe borrar la búsqueda).
    await mic(page).click();
    await expect(estadoVoz(page)).toContainText("Escuchando: «pan con queso»");
    await campo(page).focus();
    await page.keyboard.press("Escape");
    await expect(estadoVoz(page)).toHaveText("Dictado cancelado. Tu búsqueda sigue igual.");
    await expect(campo(page)).toHaveValue("chal");
    expect(await page.evaluate(() => window.__VOZ__.abortados)).toBe(2);
  });

  test("permiso de micrófono negado o sin voz: avisa en una frase y se puede seguir escribiendo", async ({ page }) => {
    await preparar(page, { voz: { modo: "permiso", texto: "" } });
    await campo(page).fill("pan");
    await mic(page).click();
    await expect(estadoVoz(page)).toHaveText("El navegador no dio permiso para usar el micrófono. Puedes escribir tu búsqueda.");
    await expect(campo(page)).toHaveValue("pan");
    await campo(page).fill("pan amasado");
    expect(await productosVisibles(page)).toEqual(["Pan amasado"]);
    await page.evaluate(() => { window.__VOZ__.modo = "silencio"; });
    await mic(page).click();
    await expect(estadoVoz(page)).toContainText("No escuchamos nada");
    await expect(campo(page)).toHaveValue("pan amasado");
  });

  test("navegador sin reconocimiento de voz o página sin HTTPS: no aparece el micrófono y el buscador funciona", async ({ page, browser }) => {
    await preparar(page, { voz: null });
    await expect(mic(page)).toBeHidden();
    await campo(page).fill("torta de cumpleaños");
    expect(await productosVisibles(page)).toEqual(["Torta de chocolate para cumpleaños"]);
    const otra = await browser.newPage();
    await preparar(otra, { seguro: false });
    await expect(mic(otra)).toBeHidden();
    await otra.close();
  });

  test("teclado, celular angosto y letra grande: el micrófono se alcanza con Tab, mide 48 px y no tapa el texto", async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await preparar(page);
    await page.addStyleTag({ content: "html { font-size: 200% !important; }" });
    await campo(page).focus();
    await page.keyboard.press("Tab");
    await expect(mic(page)).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(campo(page)).toHaveValue("torta de cumpleaños");
    const m = await mic(page).boundingBox(), c = await campo(page).boundingBox();
    expect(m.width).toBeGreaterThanOrEqual(48);
    expect(m.height).toBeGreaterThanOrEqual(48);
    expect(m.x + m.width).toBeLessThanOrEqual(c.x + c.width + 1);   // dentro del campo
    const relleno = await campo(page).evaluate((el) => parseFloat(getComputedStyle(el).paddingRight));
    expect(relleno).toBeGreaterThanOrEqual(m.width);                 // el texto no queda debajo del botón
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
});

test.describe("A qué tienda le escribo", () => {
  test("ficha de tienda: WhatsApp válido, solo mensajes, sin contacto y número de ejemplo", async ({ page }) => {
    await preparar(page);
    const abrir = (id) => page.evaluate((t) => window.TramaWeb.abrirVitrinaTienda(t), id);
    const wsp = page.locator("#btn-modal-tienda-whatsapp"), form = page.locator("#btn-modal-tienda-formulario"), aviso = page.locator("#modal-tienda-sin-whatsapp");

    await abrir("t_wsp");
    await expect(wsp).toHaveText("Enviar WhatsApp a Tejidos Marta");
    await expect(wsp).toHaveAttribute("href", /^https:\/\/wa\.me\/56948217735\?/);
    await expect(form).toHaveText("Enviar mensaje a Tejidos Marta");
    await expect(aviso).toBeHidden();

    await abrir("t_msg");   // solo mensajes internos: no se ofrece WhatsApp aunque tenga número
    await expect(wsp).toBeHidden();
    await expect(form).toHaveText("Enviar mensaje a Panadería Rosa");
    await expect(aviso).toBeHidden();

    await abrir("t_sin");   // sin canal: una frase, sin botón ni destino de reemplazo
    await expect(wsp).toBeHidden();
    await expect(form).toBeHidden();
    await expect(aviso).toHaveText("Por ahora esta tienda no recibe mensajes desde la vitrina");

    await abrir("t_demo");  // número de los datos de ejemplo: no abre WhatsApp; ofrece el mensaje interno
    await expect(wsp).toBeHidden();
    await expect(form).toHaveText("Enviar mensaje a Minimarket Ejemplo");
    await expect(aviso).toHaveText("Esta tienda aún no tiene un WhatsApp de contacto");
    const enlaces = await page.evaluate(() => Array.from(document.querySelectorAll('a[href*="wa.me"]')).map(a => a.href));
    expect(enlaces.filter(h => /56987654321/.test(h))).toEqual([]);
  });

  test("contacto desde un producto: el mensaje editable lleva el producto y va a esa tienda", async ({ page }) => {
    await preparar(page);
    await page.evaluate(() => window.TramaWeb.abrirVitrinaTienda("t_msg"));
    const tarjeta = page.locator('#modal-tienda-vitrina [data-producto-vitrina="pan"]');
    await tarjeta.getByRole("button", { name: "Enviar mensaje a Panadería Rosa sobre «Pan amasado»" }).click();
    await expect(page.locator("#modal-contacto")).toBeVisible();
    await expect(page.locator("#modal-contacto-sub")).toContainText("Panadería Rosa");
    await expect(page.locator("#contacto-mensaje")).toHaveValue(/Pan amasado/);
    await page.locator("#contacto-mensaje").fill("Hola, ¿tienen pan amasado para mañana?");   // editable antes de enviar
    await expect(page.locator("#contacto-mensaje")).toHaveValue("Hola, ¿tienen pan amasado para mañana?");
    expect(await page.evaluate(() => window.__ABIERTOS__)).toEqual([]);
  });

  test("nombre largo, celular y letra grande: el rótulo se lee completo y no se sale de la pantalla", async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await preparar(page);
    await page.addStyleTag({ content: "html { font-size: 200% !important; }" });
    await page.evaluate(() => window.TramaWeb.abrirVitrinaTienda("t_largo"));
    const wsp = page.locator("#btn-modal-tienda-whatsapp");
    await expect(wsp).toHaveText("Enviar WhatsApp a " + LARGO);
    const medida = await wsp.evaluate((el) => ({ desborda: el.scrollWidth > el.clientWidth + 1, derecha: el.getBoundingClientRect().right, ancho: window.innerWidth }));
    expect(medida.desborda).toBe(false);
    expect(medida.derecha).toBeLessThanOrEqual(medida.ancho);
  });
});
