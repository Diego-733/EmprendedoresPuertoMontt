"use strict";
// Capturas de la demo local (datos ficticios) para comparar antes y después del rediseño.
// Uso: node tests/e2e/capturas_demo.js <carpeta-salida>
// Usa el Edge instalado (como las pruebas e2e) y el servidor de demo_local.js en un puerto libre.
const path = require("path");
const fs = require("fs");
const { chromium } = require("@playwright/test");
const { crearServidorDemo } = require("./demo_local");

const salida = path.resolve(process.argv[2] || "test-results/capturas");
fs.mkdirSync(salida, { recursive: true });

const ESCENAS = [
  { nombre: "portada-movil", ancho: 390, alto: 844, url: "/?demo=visitante" },
  { nombre: "portada-escritorio", ancho: 1280, alto: 860, url: "/?demo=visitante" },
  { nombre: "tienda-enlace-movil", ancho: 390, alto: 844, url: "/?demo=visitante&tienda=t_tejidos_austral" },
  { nombre: "tienda-enlace-escritorio", ancho: 1280, alto: 860, url: "/?demo=visitante&tienda=t_tejidos_austral" },
  { nombre: "tienda-nombre-largo-320", ancho: 320, alto: 700, url: "/?demo=visitante&tienda=t_reposteria_surena" },
  { nombre: "admin-administradores-escritorio", ancho: 1280, alto: 860, url: "/?demo=admin", tab: "#tab-btn-admin-adm" },
  { nombre: "admin-administradores-movil", ancho: 390, alto: 844, url: "/?demo=admin", tab: "#tab-btn-admin-adm" },
  { nombre: "emprendedora-web-movil", ancho: 390, alto: 844, url: "/?demo=emprendedora" }
];

(async () => {
  const servidor = crearServidorDemo();
  await new Promise((ok) => servidor.listen(0, "127.0.0.1", ok));
  const base = "http://localhost:" + servidor.address().port;
  const navegador = await chromium.launch({ channel: process.env.PW_CANAL || "msedge" });
  try {
    for (const e of ESCENAS.filter((x) => !process.argv[3] || process.argv[3] === "todo" || x.nombre.includes(process.argv[3]))) {
      const ctx = await navegador.newContext({ viewport: { width: e.ancho, height: e.alto }, locale: "es-CL", deviceScaleFactor: 1 });
      const page = await ctx.newPage();
      await page.route(/^https:\/\/(unpkg\.com|tile\.openstreetmap\.org|fonts\.)/, (r) => r.fulfill({ status: 404, body: "" }));
      await page.goto(base + e.url);
      await page.waitForTimeout(900);
      if (e.tab) { await page.locator(e.tab).click(); await page.waitForTimeout(500); }
      await page.screenshot({ path: path.join(salida, e.nombre + ".png") });
      await ctx.close();
      console.log("✓", e.nombre);
    }
    // Recorrido de aceptación en el celular (solo con «recorrido» como tercer argumento).
    if (process.argv[3] === "recorrido") {
      const ctx = await navegador.newContext({ viewport: { width: 390, height: 844 }, locale: "es-CL", hasTouch: true });
      const page = await ctx.newPage();
      await page.route(/^https:\/\/(unpkg\.com|tile\.openstreetmap\.org|fonts\.)/, (r) => r.fulfill({ status: 404, body: "" }));
      await page.addInitScript(() => { window.open = () => null; });
      const foto = async (n) => { await page.waitForTimeout(450); await page.screenshot({ path: path.join(salida, n + ".png") }); console.log("✓", n); };
      await page.goto(base + "/?demo=visitante&tienda=t_tejidos_austral");
      await foto("recorrido-1-enlace-abre-la-tienda");
      const panel = page.locator("#modal-tienda-vitrina .modal-panel-editorial");
      await panel.evaluate((p) => { p.scrollTop = 320; });
      await foto("recorrido-2-producto-disponible-y-precio");
      await page.locator('[data-producto-vitrina="gorro_lana"] .enlace-producto').click();
      await foto("recorrido-3-detalle-quien-vende-y-contacto");
      await page.goBack();
      await foto("recorrido-4-vuelve-a-la-misma-ficha");
      await ctx.close();
    }
  } finally {
    await navegador.close();
    servidor.close();
  }
})().catch((err) => { console.error(err); process.exit(1); });
