"use strict";
// Entrega 8 · Ensayo en el EMULADOR de Firebase Hosting (npm run test:hosting).
// Solo corre si ALERCE_HOSTING_URL está definida (la define scripts/ensayar-hosting.js).
// La web viene del emulador con su config.js ficticio; supabase-js se reemplaza por el
// sustituto de las pruebas, así que nada llega a una instancia real.
const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const URL_HOSTING = process.env.ALERCE_HOSTING_URL;
const FALSO = fs.readFileSync(path.join(__dirname, "supabase_falso.js"), "utf8");
test.skip(!URL_HOSTING, "Solo con el emulador de Hosting (npm run test:hosting)");

function hoy(dias) {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Santiago", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(Date.now() + dias * 86400000));
}
function datos(extra) {
  const tienda = { id: "t_tejidos", nombre: "Tejidos Austral", rubro: "Artesanía", sector: "Alerce Sur", whatsapp: "+56911112222", owner_id: null, bio: "Prueba",
    color_portada: 4284232027, color_logo: 4281236827, necesidades: {}, historial_activo: true, valores_producto: {}, mostrar_precios: true, contacto_modo: "AMBOS", activa: true };
  return Object.assign({
    tablas: {
      tiendas: [tienda],
      productos: [{ id: "chal", tienda_id: "t_tejidos", nombre: "Chal de lana", descripcion: "", categoria: "TEXTIL", precio: 15000, publicado: true, disponible: true,
        modalidad: "REGULAR", maneja_stock: true, al_agotarse: "MOSTRAR_AGOTADO", vendida_en: null, foto_url: null, variantes: [{ id: "a", nombre: "Única", stock: 3 }] }],
      ferias: [{ id: "f1", nombre: "Feria de Alerce", descripcion: "Feria comunal", ubicacion: "Plaza de Alerce", fecha_inicio: hoy(3), fecha_fin: hoy(3), horario: "10 a 18",
        organizador: "Muni", estado: "PROXIMA", latitud: -41.3959, longitud: -72.8955 }],
      perfiles: [], tipos_producto: [], ventas: [], contactos: [], seguimientos: [], preferencias_avisos: [], avisos: [], asociaciones: [], tienda_asociaciones: [],
      configuracion: [{ clave: "navegacion_anonima", valor: "PERMITIR" }]
    },
    respuestasRpc: { participantes_feria: [{ data: [] }, { data: [] }] }
  }, extra || {});
}

async function preparar(page, estado) {
  // El emulador sirve index.html con SRI para el SDK real; el sustituto no coincide, así que
  // solo en la copia que recibe la prueba se quita el atributo integrity del SDK.
  await page.route(URL_HOSTING + "/", async (route) => {
    const resp = await route.fetch();
    const html = (await resp.text()).replace(/(supabase-js@[^"]+") integrity="[^"]+"/, "$1");
    await route.fulfill({ response: resp, body: html });
  });
  await page.route(/\/\?/, async (route) => {
    if (!route.request().url().startsWith(URL_HOSTING)) return route.continue();
    const resp = await route.fetch();
    const tipo = resp.headers()["content-type"] || "";
    if (!/html/.test(tipo)) return route.fulfill({ response: resp });
    await route.fulfill({ response: resp, body: (await resp.text()).replace(/(supabase-js@[^"]+") integrity="[^"]+"/, "$1") });
  });
  await page.route("https://cdn.jsdelivr.net/npm/@supabase/**", (r) => r.fulfill({ contentType: "text/javascript", body: FALSO }));
  await page.route(/^https:\/\/(unpkg\.com|tile\.openstreetmap\.org|fonts\.|picsum|images\.unsplash)/, (r) => r.fulfill({ status: 404, body: "" }));
  await page.route("https://falso.supabase.co/auth/v1/settings", (r) => r.fulfill({ contentType: "application/json", body: JSON.stringify({ external: { google: false } }) }));
  await page.addInitScript((e) => { window.__FALSO__ = e; }, estado);
}

test("W01: el emulador sirve la web con cabeceras de seguridad y la configuración pública ficticia", async ({ request }) => {
  const r = await request.get(URL_HOSTING + "/");
  expect(r.status()).toBe(200);
  expect(r.headers()["x-content-type-options"]).toBe("nosniff");
  expect(r.headers()["x-frame-options"]).toBe("SAMEORIGIN");
  const cfg = await (await request.get(URL_HOSTING + "/js/config.js")).text();
  expect(cfg).toContain("https://falso.supabase.co");
  expect(cfg).not.toMatch(/service_role|sb_secret_/);
});

test("W01: la descarga del APK responde 200 con el tipo de Android", async ({ request }) => {
  const r = await request.get(URL_HOSTING + "/app-debug.apk");
  expect(r.status()).toBe(200);
  expect(r.headers()["content-type"]).toContain("application/vnd.android.package-archive");
  expect((await r.body()).subarray(0, 2).toString()).toBe("PK");
});

test("W02/RF-COM-03: enlaces directos ?tienda= y ?feria= abren su ficha desde Hosting", async ({ page }) => {
  await preparar(page, datos());
  await page.goto(URL_HOSTING + "/?tienda=t_tejidos");
  await expect(page.locator("body")).toContainText("Tejidos Austral");
  await expect(page.locator("body")).toContainText("Chal de lana");
  await page.goto(URL_HOSTING + "/?feria=f1");
  await expect(page.locator("#modal-feria")).toHaveClass(/abierto/);
  await expect(page.locator("#modal-feria")).toContainText("Feria de Alerce");
});

test("W02: en un teléfono (390 px) la vitrina no se desborda", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await preparar(page, datos());
  await page.goto(URL_HOSTING + "/");
  const desborde = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(desborde).toBeLessThanOrEqual(1);
});

test("W06: el enlace de recuperación vuelve al dominio de Hosting con ?recuperar=1 y se guarda la clave nueva", async ({ page }) => {
  // 1) Pedir el enlace desde Hosting: la vuelta debe ser este mismo dominio.
  await preparar(page, datos());
  await page.goto(URL_HOSTING + "/");
  await page.evaluate(() => window.TramaWeb.abrirModal("modal-recuperar-contrasena"));
  await page.locator("#recuperar-email").fill("compradora.prueba@example.com");
  await page.locator("#btn-enviar-recuperacion").click();
  await expect(page.locator("#recuperar-aviso-exito")).toContainText("recibirás un enlace");
  const pedido = await page.evaluate(() => window.__FALSO__.llamadas.filter(l => l.recuperar));
  expect(pedido).toEqual([{ recuperar: "compradora.prueba@example.com", redirectTo: URL_HOSTING + "/?recuperar=1" }]);
  // 2) Volver con la sesión de recuperación (lo que hace el enlace del correo) y guardar la clave.
  const page2 = await page.context().newPage();
  await preparar(page2, datos({ sesion: { user: { id: "u-c", email: "compradora.prueba@example.com", user_metadata: {} }, access_token: "falso" } }));
  await page2.goto(URL_HOSTING + "/?recuperar=1");
  await expect(page2.locator("#modal-cambiar-clave")).toBeVisible();
  await page2.locator("#nueva-clave-input").fill("ClaveNueva2026");
  await page2.locator("#nueva-clave-confirmar").fill("ClaveNueva2026");
  await page2.locator("#btn-guardar-nueva-clave").click();
  await expect(page2.locator("#cambiar-clave-aviso-exito")).toBeVisible();
});
