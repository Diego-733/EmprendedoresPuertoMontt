"use strict";
// «Guíame paso a paso» en la web: guía opcional sobre el formulario real de «Añadir producto».
// Supabase sustituto, reconocimiento de voz SIMULADO y una imagen de prueba: nada sale del navegador.
const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");
const { RAIZ } = require("./servidor");
const sinSri = (archivo) => /index.html$/.test(archivo) ? fs.readFileSync(archivo, "utf8").replace(/(supabase-js@[^"]+") integrity="[^"]+"/, "$1") : fs.readFileSync(archivo);

const ORIGEN = "http://alerce.test";
const FALSO = fs.readFileSync(path.join(__dirname, "supabase_falso.js"), "utf8");
const TIPOS = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".otf": "font/otf", ".png": "image/png" };
const PNG = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==", "base64");

const TIENDA = { id: "t_prueba", owner_id: "u-marta", nombre: "Tejidos de Prueba", rubro: "Artesanía", sector: "Alerce Sur", bio: "", whatsapp: "+56948217735",
  color_portada: 4284232027, color_logo: 4281236827, necesidades: { tieneStock: true }, historial_activo: true, valores_producto: {}, mostrar_precios: true };

function estadoPrimeraVez() {
  return {
    tablas: { tiendas: [TIENDA], productos: [], tipos_producto: [], ferias: [], ventas: [],
      perfiles: [{ id: "u-marta", nombre: "Marta", rol: "EMPRENDEDORA", tienda_id: "t_prueba", foto_url: null }] },
    sesion: { user: { id: "u-marta", email: "marta@prueba.cl", user_metadata: {} }, access_token: "falso" },
    // El servidor simulado responde al guardar con el producto tal como quedó (oculto: «Solo yo lo veo»).
    respuestasRpc: { guardar_producto: [{ data: { id: "p_guia", tienda_id: "t_prueba", nombre: "Chal de lana gris", descripcion: "", categoria: "TEXTIL", precio: 15000,
      publicado: false, disponible: true, modalidad: "REGULAR", maneja_stock: true, al_agotarse: "MOSTRAR_AGOTADO", vendida_en: null, foto_url: null,
      variantes: [{ id: "v1", nombre: "Única", stock: 1 }] } }] }
  };
}

async function preparar(page, { voz = { texto: "chal de lana" } } = {}) {
  page.on("pageerror", (e) => console.log("ERROR DE PÁGINA:", e.message));
  await page.route(ORIGEN + "/**", (route) => {
    const ruta = decodeURIComponent(new URL(route.request().url()).pathname);
    if (ruta.endsWith("/js/config.js")) return route.fulfill({ contentType: "text/javascript", body: 'window.__CONFIG__ = {"supabase":{"url":"https://falso.supabase.co","anonKey":"clave-de-prueba"}};' });
    const archivo = path.join(RAIZ, ruta === "/" ? "index.html" : ruta);
    if (!archivo.startsWith(RAIZ) || !fs.existsSync(archivo)) return route.fulfill({ status: 404, body: "" });
    return route.fulfill({ contentType: TIPOS[path.extname(archivo)] || "application/octet-stream", body: sinSri(archivo) });
  });
  await page.route("https://cdn.jsdelivr.net/npm/@supabase/**", (r) => r.fulfill({ contentType: "text/javascript", body: FALSO }));
  await page.route("https://falso.supabase.co/auth/v1/settings", (r) => r.fulfill({ contentType: "application/json", body: JSON.stringify({ external: { google: false } }) }));
  await page.route(/^https:\/\/(unpkg\.com|tile\.openstreetmap\.org|fonts\.|picsum|images\.unsplash)/, (r) => r.fulfill({ status: 404, body: "" }));
  await page.addInitScript(({ estado, voz }) => {
    window.__FALSO__ = estado;
    Object.defineProperty(window, "isSecureContext", { value: true, configurable: true });
    if (!voz) { window.SpeechRecognition = undefined; window.webkitSpeechRecognition = undefined; return; }
    window.__VOZ__ = { textos: [].concat(voz.texto), usados: 0 };
    function Simulado() {}
    Simulado.prototype.start = function () {
      var yo = this, v = window.__VOZ__, texto = v.textos[Math.min(v.usados, v.textos.length - 1)];
      v.usados++;
      setTimeout(function () { var r = [{ transcript: texto }]; r.isFinal = true; yo.onresult({ resultIndex: 0, results: [r] }); yo.onend(); }, 40);
    };
    Simulado.prototype.abort = function () { var yo = this; setTimeout(function () { yo.onerror({ error: "aborted" }); yo.onend(); }, 10); };
    window.SpeechRecognition = undefined;
    window.webkitSpeechRecognition = Simulado;
  }, { estado: estadoPrimeraVez(), voz });
  await page.goto(ORIGEN + "/");
  await expect(page.locator("#btn-abrir-catalogo-nav")).toBeVisible();
}

const guia = (page) => page.locator("#guia-paso");

test("primera vez: pide ayuda, elige foto de la galería, dicta y corrige el nombre, guarda como «Solo yo lo veo»", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await preparar(page);
  await page.locator("#btn-abrir-catalogo-nav").click();                 // Mis productos
  await page.getByRole("button", { name: "🧭 Guíame paso a paso" }).first().click();
  await page.locator("#guia-frase").fill("quiero subir un producto");
  await page.locator("#guia-ir").click();

  // Paso 1 · Foto: indicación breve; el ejemplo está detrás de «Ver un ejemplo» y dice que es un ejemplo.
  await expect(page.locator("#modal-publicar-producto")).toHaveClass(/abierto/);
  await expect(guia(page)).toHaveAttribute("data-paso", "foto");
  await expect(guia(page)).toContainText("Toca «📁 Elegir foto»");
  await guia(page).getByText("Ver un ejemplo").click();
  await expect(guia(page).getByRole("img", { name: /Ejemplo de encuadre/ })).toBeVisible();
  await expect(guia(page).locator("figcaption")).toContainText("no es la foto de tu tienda");
  await guia(page).getByText("Necesito ayuda").click();
  await expect(guia(page)).toContainText("Si no das permiso a la cámara, elige la galería");
  await expect(page.locator("#guia-siguiente")).toHaveText("Seguir sin foto");
  // Sin cámara: elige una imagen de la galería (el mismo botón «📁 Elegir foto»).
  await page.locator("#pub-foto-input").setInputFiles({ name: "chal.png", mimeType: "image/png", buffer: PNG });
  await expect(page.locator("#pub-foto-preview img")).toBeVisible();
  await expect(page.locator("#guia-estado-paso")).toContainText("Así quedó tu foto. Para cambiarla, toca «📁 Elegir foto»");
  await expect(page.locator("#guia-siguiente")).toHaveText("Siguiente");
  await page.locator("#guia-siguiente").click();

  // Paso 2 · Nombre y precio: dicta el nombre, vuelve atrás y lo corrige.
  await expect(guia(page)).toHaveAttribute("data-paso", "datos");
  await page.locator("#guia-siguiente").click();                          // sin nombre no avanza
  await expect(page.locator("#guia-estado-paso")).toHaveText("Falta el «Nombre del producto».");
  await page.getByRole("button", { name: "Dictar el nombre del producto" }).click();
  await expect(page.locator("#pub-nombre")).toHaveValue("chal de lana");
  await expect(page.locator("#pub-nombre-voz-estado")).toContainText("Revísalo y corrígelo");
  await page.locator("#guia-volver").click();
  await expect(guia(page)).toHaveAttribute("data-paso", "foto");
  await expect(page.locator("#pub-foto-preview img")).toBeVisible();      // volver no borra la foto
  await page.locator("#guia-siguiente").click();
  await expect(page.locator("#pub-nombre")).toHaveValue("chal de lana");  // ni el nombre
  await page.locator("#pub-nombre").fill("Chal de lana gris");
  await page.locator("#pub-precio").fill("15000");
  await page.locator("#guia-siguiente").click();

  // Paso 3 · Quién lo ve: «Solo yo lo veo».
  await expect(guia(page)).toHaveAttribute("data-paso", "publicacion");
  if (await page.locator("#pub-publicado").isChecked()) await page.locator("#pub-publicado").uncheck();
  await expect(page.locator("#guia-estado-paso")).toHaveText("Ahora: solo tú lo ves.");
  await page.locator("#guia-siguiente").click();

  // Paso 4 · Revisar: el guardado lo confirma la persona con el botón de siempre.
  await expect(guia(page)).toHaveAttribute("data-paso", "guardar");
  await expect(page.locator("#guia-estado-paso")).toContainText("Nombre: Chal de lana gris");
  await expect(page.locator("#guia-estado-paso")).toContainText("Quién lo ve: Solo yo lo veo");
  // La ayuda no tapa el botón de guardar: queda arriba de él, sin superponerse.
  const tarjeta = await guia(page).boundingBox(), boton = await page.locator("#pub-guardar").boundingBox();
  expect(tarjeta.y + tarjeta.height).toBeLessThanOrEqual(boton.y + 1);
  expect(await page.evaluate(() => window.__FALSO__.llamadas.some((x) => x.rpc === "guardar_producto"))).toBe(false);
  await page.locator("#pub-guardar").click();
  await expect(page.locator("#modal-publicar-producto")).not.toHaveClass(/abierto/);
  await expect(page.locator("#toast-texto")).toContainText("Guardado en el servidor: «Chal de lana gris» (por ahora no se muestra al público)");
  const llamada = await page.evaluate(() => window.__FALSO__.llamadas.filter((x) => x.rpc === "guardar_producto").pop().args.p);
  expect(llamada.nombre).toBe("Chal de lana gris");
  expect(llamada.precio).toBe(15000);
  expect(llamada.publicado).toBe(false);
  expect(llamada.fotoUrl).toMatch(/^https:\/\/falso\.supabase\.co\/storage\/v1\/object\/public\/productos\/u-marta\//);
});

test("frase ambigua: ofrece dos opciones claras; la voz puede abrir el paso de la foto", async ({ page }) => {
  await preparar(page, { voz: { texto: "cómo agrego una foto" } });
  await page.locator("#btn-abrir-catalogo-nav").click();
  await page.locator("#gestion-guia").click();
  await page.locator("#guia-frase").fill("vender");
  await page.locator("#guia-frase").press("Enter");
  const opciones = page.locator("#guia-respuesta button");
  await expect(opciones).toHaveText(["Subir un producto nuevo", "Registrar una venta"]);   // cuenta con ventas
  await page.locator("#guia-frase").fill("no sé");
  await page.locator("#guia-ir").click();
  await expect(opciones).toHaveText(["Subir un producto nuevo", "Agregar una foto"]);
  await page.getByRole("button", { name: "Decir qué quieres hacer" }).click();             // por voz
  await expect(guia(page)).toHaveAttribute("data-paso", "foto");
  await expect(guia(page).getByRole("img", { name: /Ejemplo de encuadre/ })).toBeVisible(); // la pregunta por la foto abre el ejemplo
});

test("salir de la guía conserva lo escrito; sin micrófono todo se hace escribiendo; quien no la usa no ve cambios", async ({ page }) => {
  await preparar(page, { voz: null });
  await page.locator("#btn-abrir-publicar-nav").click();                 // flujo de siempre
  await expect(guia(page)).toHaveCount(0);
  await expect(page.locator("#pub-nombre-voz")).toBeHidden();            // navegador sin voz: no hay micrófono
  await page.locator("#pub-nombre").fill("Gorro");
  await page.locator("#pub-abrir-guia").click();                         // pide ayuda en el momento de la duda
  await expect(guia(page)).toHaveAttribute("data-paso", "foto");
  await page.locator("#guia-siguiente").click();                         // «Seguir sin foto»
  await page.locator("#pub-precio").fill("8000");
  await page.locator("#guia-salir").click();
  await expect(guia(page)).toHaveCount(0);
  await expect(page.locator("#pub-nombre")).toHaveValue("Gorro");
  await expect(page.locator("#pub-precio")).toHaveValue("8000");
  await expect(page.locator("#modal-publicar-producto")).toHaveClass(/abierto/);
});

test("celular angosto y letra grande: la tarjeta de ayuda cabe y sus botones miden 48 px", async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 });
  await preparar(page);
  await page.addStyleTag({ content: "html { font-size: 200% !important; }" });
  await page.locator("#btn-abrir-publicar-nav").click();
  await page.locator("#pub-abrir-guia").click();
  const medidas = await page.evaluate(() => {
    const t = document.getElementById("guia-paso");
    return { cabe: t.getBoundingClientRect().right <= window.innerWidth + 1,
      botones: Array.from(t.querySelectorAll("button, summary")).map((b) => b.getBoundingClientRect().height) };
  });
  expect(medidas.cabe).toBe(true);
  for (const alto of medidas.botones) expect(alto).toBeGreaterThanOrEqual(48);
});
