"use strict";
// Recorridos de la Entrega 7 en la web con un Supabase sustituto.
// Aquí se prueba lo que ve y hace la interfaz. Los permisos reales (quién puede
// invitar, responder, seguir, leer avisos) se prueban en tests/sql contra PostgreSQL.
// Las notificaciones de este archivo son las que la página muestra mientras está
// abierta; el push con la página cerrada necesita Firebase (no se simula).
const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");
const { RAIZ } = require("./servidor");
// El SDK de Supabase se reemplaza por uno falso: en la copia servida a la prueba se quita su verificación SRI.
const sinSri = (archivo) => /index.html$/.test(archivo) ? fs.readFileSync(archivo, "utf8").replace(/(supabase-js@[^"]+") integrity="[^"]+"/, "$1") : fs.readFileSync(archivo);

const ORIGEN = "http://alerce.test";
const FALSO = fs.readFileSync(path.join(__dirname, "supabase_falso.js"), "utf8");
const TIPOS = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".otf": "font/otf", ".png": "image/png" };
const LEAFLET = path.join(__dirname, "..", "..", "node_modules", "leaflet", "dist");
if (!fs.existsSync(path.join(LEAFLET, "leaflet.js"))) throw new Error("Falta Leaflet para probar los mapas: ejecuta `npm install`.");
const PNG = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=", "base64");

// Fechas en America/Santiago, igual que la web.
function hoy(dias) {
  const d = new Date(Date.now() + dias * 86400000);
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Santiago", year: "numeric", month: "2-digit", day: "2-digit" }).format(d);
}

const BASE_TIENDA = { owner_id: null, bio: "Tienda de prueba", color_portada: 4284232027, color_logo: 4281236827, necesidades: {}, historial_activo: true, valores_producto: {}, mostrar_precios: true, contacto_modo: "AMBOS" };
function datos() {
  const producto = (id, tienda, extra) => Object.assign({ id, tienda_id: tienda, nombre: "Producto " + id, descripcion: "", categoria: "TEXTIL", precio: 1000,
    publicado: true, disponible: true, modalidad: "REGULAR", maneja_stock: true, al_agotarse: "MOSTRAR_AGOTADO", vendida_en: null,
    foto_url: "https://falso.supabase.co/p/" + id + ".jpg", variantes: [{ id: id + "_a", nombre: "Única", stock: 3 }] }, extra || {});
  return {
    tablas: {
      tiendas: [
        Object.assign({}, BASE_TIENDA, { id: "t_tejidos", nombre: "Tejidos Austral", rubro: "Artesanía", sector: "Alerce Sur", whatsapp: "+56911112222", asociacion_id: "as1" }),
        Object.assign({}, BASE_TIENDA, { id: "t_pan", nombre: "Panadería Doña Rosa", rubro: "Alimentos", sector: "Alerce Histórico", asociacion_id: "as1" }),
        Object.assign({}, BASE_TIENDA, { id: "t_barber", nombre: "Barbería Alerce", rubro: "Servicios", sector: "Alerce Norte", asociacion_id: null })
      ],
      productos: [
        producto("chal", "t_tejidos"), producto("gorro", "t_tejidos"),
        producto("oculto", "t_tejidos", { disponible: false, nombre: "Producto no disponible" }),
        producto("pan", "t_pan"), producto("corte", "t_barber")
      ],
      // Una tienda en dos asociaciones (07): Barbería solo está en as2 por la tabla de pertenencias.
      tienda_asociaciones: [
        { tienda_id: "t_tejidos", asociacion_id: "as1" }, { tienda_id: "t_pan", asociacion_id: "as1" },
        { tienda_id: "t_tejidos", asociacion_id: "as2" }, { tienda_id: "t_barber", asociacion_id: "as2" }
      ],
      perfiles: [], tipos_producto: [], ventas: [], contactos: [], seguimientos: [], preferencias_avisos: [], avisos: [],
      asociaciones: [
        { id: "as1", nombre: "Manos del Sur", sector: "Alerce", localidad: "Puerto Montt", descripcion: "Red de artesanas", activa: true },
        { id: "as2", nombre: "Red del Lago", sector: "Alerce Sur", localidad: "Puerto Montt", descripcion: "Gastronomía", activa: true }
      ],
      ferias: [
        { id: "f1", nombre: "Feria de Alerce", descripcion: "Feria comunal", ubicacion: "Plaza de Alerce", fecha_inicio: hoy(3), fecha_fin: hoy(3), horario: "10 a 18", organizador: "Muni", estado: "PROXIMA", latitud: -41.3959, longitud: -72.8955 },
        { id: "f_hoy", nombre: "Feria de Hoy", descripcion: "Hoy", ubicacion: "Costanera", fecha_inicio: hoy(0), fecha_fin: hoy(1), horario: "11 a 19", organizador: "Muni", estado: "PROXIMA", latitud: -41.47, longitud: -72.94 },
        { id: "f_cancelada", nombre: "Feria Cancelada", descripcion: "", ubicacion: "X", fecha_inicio: hoy(5), fecha_fin: hoy(5), horario: "", organizador: "", estado: "CANCELADA", latitud: null, longitud: null }
      ],
      configuracion: [{ clave: "navegacion_anonima", valor: "PERMITIR" }]
    },
    respuestasRpc: {}
  };
}

async function preparar(page, estadoFalso) {
  page.on("pageerror", (e) => console.log("ERROR DE PÁGINA:", e.message));
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
    if (!fs.existsSync(path.join(LEAFLET, nombre))) return route.fulfill({ status: 404, body: "" });
    return route.fulfill({ contentType: TIPOS[path.extname(nombre)] || "image/png", body: fs.readFileSync(path.join(LEAFLET, nombre)) });
  });
  await page.route(/^https:\/\/tile\.openstreetmap\.org\//, (route) => route.fulfill({ contentType: "image/png", body: PNG }));
  await page.route("https://falso.supabase.co/auth/v1/settings", (route) => route.fulfill({ contentType: "application/json", body: JSON.stringify({ external: { google: false } }) }));
  await page.route(/^https:\/\/(fonts\.|picsum|images\.unsplash|falso\.supabase\.co\/(p|storage))/, (route) => route.fulfill({ status: 200, contentType: "image/svg+xml", body: "<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'/>" }));
  await page.addInitScript((estado) => { window.__FALSO__ = estado; }, estadoFalso);
}

const llamadasRpc = (page, nombre) => page.evaluate((n) => window.__FALSO__.llamadas.filter(l => l.rpc === n).map(l => l.args), nombre);

test.describe("Entrega 7 · ficha pública y calendario", () => {
  test("W14, W12, W13: calendario de próximas → ficha con confirmadas y muestra solo de productos públicos", async ({ page }) => {
    const e = datos();
    e.respuestasRpc.participantes_feria = [{ data: [{ tienda_id: "t_tejidos", nombre: "Tejidos Austral", rubro: "Artesanía", sector: "Alerce Sur", logo_url: null, asociacion_id: null }] }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    const cal = page.locator("#e7-calendario");
    await expect(cal).toContainText("Calendario de próximas ferias");
    await expect(cal.locator(".e7-calendario-item")).toHaveCount(2);
    await expect(cal).not.toContainText("Feria Cancelada");
    await cal.locator(".e7-calendario-item", { hasText: "Feria de Alerce" }).click();
    await expect(page.locator("#modal-feria")).toHaveClass(/abierto/);
    const part = page.locator("#modal-feria .e7-participante");
    await expect(part).toHaveCount(1);
    await expect(part).toContainText("Tejidos Austral");
    await expect(part.locator(".e7-muestra-item")).toHaveCount(2);
    await expect(part).not.toContainText("Producto no disponible");
    expect(await llamadasRpc(page, "participantes_feria")).toEqual([{ p_feria: "f1" }]);
    await expect(page.locator("#mapa-ficha-feria.leaflet-container")).toBeVisible();
  });

  test("W02 (vista pública): una feria cancelada no aparece como activa y su ficha lo dice", async ({ page }) => {
    const e = datos();
    e.respuestasRpc.participantes_feria = [{ data: [] }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/?feria=f_cancelada");
    await expect(page.locator("#modal-feria")).toHaveClass(/abierto/);
    await expect(page.locator("#modal-feria .e7-cancelada")).toContainText("cancelada");
    await expect(page.locator("#grilla-ferias-container")).not.toContainText("Feria Cancelada");
  });

  test("E6 con 07: una tienda en dos asociaciones aparece en el filtro de ambas", async ({ page }) => {
    await preparar(page, datos());
    await page.goto(ORIGEN + "/");
    const tarjetas = page.locator("#grilla-tiendas-marketplace .tarjeta-tienda");
    await expect(tarjetas).toHaveCount(3);
    await page.locator("#e6-filtro-asociacion").selectOption("as2");
    await expect(tarjetas).toHaveCount(2);
    await expect(page.locator("#grilla-tiendas-marketplace")).toContainText("Barbería");
    await expect(page.locator("#grilla-tiendas-marketplace")).toContainText("Tejidos");
  });
});

test.describe("Entrega 7 · seguir", () => {
  test("W16: sin sesión pide iniciar sesión y, al entrar, queda siguiendo sin volver a pulsar (una sola vez)", async ({ page }) => {
    const e = datos();
    e.tablas.perfiles = [{ id: "u-comp", nombre: "Compradora", rol: "VISITANTE", tienda_id: null }];
    e.usuarioLogin = { id: "u-comp", email: "compradora@correo.cl", user_metadata: {} };
    e.respuestasRpc.seguir = [{ data: { tipo: "TIENDA", id: "t_tejidos", siguiendo: true } }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await expect(page.locator("#grilla-tiendas-marketplace .tarjeta-tienda")).toHaveCount(3);
    await page.evaluate(() => window.TramaWeb.abrirVitrinaTienda("t_tejidos"));
    const boton = page.locator('#modal-tienda-extras [data-seguir="TIENDA:t_tejidos"]');
    await expect(boton).toHaveText("＋ Seguir");
    await boton.click();
    await expect(page.locator("#modal-auth-web")).toHaveClass(/abierto/);
    await expect(page.locator("#auth-aviso-exito")).toContainText("seguir a Tejidos Austral");
    expect(await page.evaluate(() => JSON.parse(localStorage.getItem("alerce_accion_pendiente")).id)).toBe("t_tejidos");
    expect(await llamadasRpc(page, "seguir")).toEqual([]);
    await page.locator("#auth-inp-email").fill("compradora@correo.cl");
    await page.locator("#auth-inp-password").fill("secreta123");
    await page.locator("#btn-auth-submit").click();
    await expect.poll(() => llamadasRpc(page, "seguir")).toEqual([{ p_tipo: "TIENDA", p_id: "t_tejidos" }]);
    await expect(page.locator("#toast-texto")).toContainText("Ahora sigues a Tejidos Austral");
    expect(await page.evaluate(() => localStorage.getItem("alerce_accion_pendiente"))).toBeNull();
    await expect(page.locator("#e7-consentimiento")).toContainText("¿Quieres recibir avisos?");
    await page.waitForTimeout(500);
    expect((await llamadasRpc(page, "seguir")).length).toBe(1);
  });

  test("W16 (cancelar): si cierra el inicio de sesión sin entrar, la acción pendiente se descarta", async ({ page }) => {
    await preparar(page, datos());
    await page.goto(ORIGEN + "/");
    await expect(page.locator("#grilla-tiendas-marketplace .tarjeta-tienda")).toHaveCount(3);
    await page.evaluate(() => window.TramaWeb.abrirVitrinaTienda("t_tejidos"));
    await page.locator('#modal-tienda-extras [data-seguir="TIENDA:t_tejidos"]').click();
    await expect(page.locator("#modal-auth-web")).toHaveClass(/abierto/);
    await page.evaluate(() => window.TramaWeb.cerrarModal("modal-auth-web"));
    await expect.poll(() => page.evaluate(() => localStorage.getItem("alerce_accion_pendiente"))).toBeNull();
    await expect(page.locator("#toast-texto")).toContainText("no se siguió");
  });

  test("W15, W17, consentimiento: con sesión sigue una asociación, acepta avisos, deja de seguir y se da de baja", async ({ page }) => {
    const e = datos();
    e.tablas.perfiles = [{ id: "u-comp", nombre: "Compradora", rol: "VISITANTE", tienda_id: null }];
    e.sesion = { user: { id: "u-comp", email: "compradora@correo.cl", user_metadata: {} }, access_token: "falso" };
    e.respuestasRpc.seguir = [{ data: { siguiendo: true } }];
    e.respuestasRpc.dejar_de_seguir = [{ data: { siguiendo: false } }];
    e.respuestasRpc.guardar_consentimiento = [{ data: { avisos_ferias: true } }, { data: { avisos_ferias: false } }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await expect(page.locator("#e7-boton-avisos")).toBeVisible();
    await page.locator("#e6-tab-asociaciones").click();
    await page.locator("#e6-lista-asociaciones-directorio button", { hasText: "Ver integrantes" }).first().click();
    const boton = page.locator('#modal-asociacion [data-seguir^="ASOCIACION:"]');
    await boton.click();
    await expect(boton).toHaveText("✓ Siguiendo");
    await expect(boton).toHaveAttribute("aria-pressed", "true");
    await page.locator("#e7-consentimiento button", { hasText: "Sí, avisarme" }).click();
    await expect.poll(() => llamadasRpc(page, "guardar_consentimiento")).toEqual([{ p_avisos: true }]);
    await boton.click();
    await expect(boton).toHaveText("＋ Seguir");
    expect((await llamadasRpc(page, "dejar_de_seguir")).length).toBe(1);
    await page.keyboard.press("Escape");
    await page.locator("#e7-boton-avisos").click();
    await expect(page.locator("#modal-avisos")).toHaveClass(/abierto/);
    await page.locator("#modal-avisos button", { hasText: "Darme de baja de los avisos" }).click();
    await expect.poll(() => llamadasRpc(page, "guardar_consentimiento")).toEqual([{ p_avisos: true }, { p_avisos: false }]);
    await expect(page.locator("#modal-avisos")).toContainText("Desactivados");
  });

  test("W18 (página abierta): un aviso de participación se muestra como notificación del navegador y se confirma su recepción", async ({ page }) => {
    const e = datos();
    e.tablas.perfiles = [{ id: "u-comp", nombre: "Compradora", rol: "VISITANTE", tienda_id: null }];
    e.tablas.preferencias_avisos = [{ avisos_ferias: true }];
    e.tablas.avisos = [{ id: "av1", tipo: "PARTICIPACION_SEGUIDA", titulo: "Tejidos Austral estará en Feria de Alerce", cuerpo: "Feria del " + hoy(3) + ".",
      feria_id: "f1", enlace: "?feria=f1", estado: "PENDIENTE", creado_en: new Date().toISOString(), recibido_en: null, leido_en: null }];
    e.sesion = { user: { id: "u-comp", email: "compradora@correo.cl", user_metadata: {} }, access_token: "falso" };
    e.respuestasRpc.confirmar_aviso = [{ data: { registrado: true } }];
    // El servidor decide qué corresponde mostrar (misma regla que el push).
    e.respuestasRpc.avisos_para_mostrar = [{ data: e.tablas.avisos.map(a => ({ id: a.id, tipo: a.tipo, titulo: a.titulo, cuerpo: a.cuerpo, feria_id: a.feria_id, enlace: a.enlace, creado_en: a.creado_en })) }];
    await page.addInitScript(() => {
      window.__NOTIFICACIONES__ = [];
      function N(titulo, op) { window.__NOTIFICACIONES__.push({ titulo, cuerpo: op && op.body, tag: op && op.tag }); }
      N.permission = "granted";
      N.requestPermission = () => Promise.resolve("granted");
      window.Notification = N;
    });
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await expect.poll(() => page.evaluate(() => window.__NOTIFICACIONES__)).toEqual([{ titulo: "Tejidos Austral estará en Feria de Alerce", cuerpo: "Feria del " + hoy(3) + ".", tag: "av1" }]);
    // Un solo evento por destino: este navegador confirma «MOSTRADO» con su propio identificador.
    const consulta = (await llamadasRpc(page, "avisos_para_mostrar"))[0];
    expect(consulta.p_limite).toBe(10);
    expect(consulta.p_destino).toMatch(/^web:[0-9a-f-]{36}$/);
    await expect.poll(() => llamadasRpc(page, "confirmar_aviso")).toEqual([{ p_id: "av1", p_destino: consulta.p_destino, p_evento: "MOSTRADO" }]);
    await expect(page.locator("#e7-insignia-avisos")).toHaveText("1");
  });

  test("W18: si el navegador no logra mostrar la notificación, NO se informa como recibida", async ({ page }) => {
    const e = datos();
    e.tablas.perfiles = [{ id: "u-comp", nombre: "Compradora", rol: "VISITANTE", tienda_id: null }];
    e.sesion = { user: { id: "u-comp", email: "compradora@correo.cl", user_metadata: {} }, access_token: "falso" };
    e.respuestasRpc.avisos_para_mostrar = [{ data: [{ id: "av2", tipo: "PARTICIPACION_SEGUIDA", titulo: "Aviso", cuerpo: "", feria_id: "f1", enlace: "?feria=f1", creado_en: new Date().toISOString() }] }];
    await page.addInitScript(() => {
      function N() { throw new TypeError("Illegal constructor. Use ServiceWorkerRegistration.showNotification() instead."); }
      N.permission = "granted";
      N.requestPermission = () => Promise.resolve("granted");
      window.Notification = N;
    });
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await expect.poll(() => llamadasRpc(page, "avisos_para_mostrar").then(x => x.length)).toBe(1);
    await page.waitForTimeout(400);
    expect(await llamadasRpc(page, "confirmar_aviso")).toEqual([]);
  });

  test("Consultas: sin permiso de notificaciones la página no consulta avisos periódicamente", async ({ page }) => {
    const e = datos();
    e.tablas.perfiles = [{ id: "u-comp", nombre: "Compradora", rol: "VISITANTE", tienda_id: null }];
    e.sesion = { user: { id: "u-comp", email: "compradora@correo.cl", user_metadata: {} }, access_token: "falso" };
    await page.addInitScript(() => { function N() {} N.permission = "default"; N.requestPermission = () => Promise.resolve("default"); window.Notification = N; });
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await expect(page.locator("#e7-boton-avisos")).toBeVisible();
    await page.waitForTimeout(800);
    expect(await llamadasRpc(page, "avisos_para_mostrar")).toEqual([]);
  });
});

test.describe("Entrega 7 · administración de ferias", () => {
  function conAdmin() {
    const e = datos();
    e.tablas.perfiles = [{ id: "u-admin", nombre: "Admin", rol: "ADMIN", tienda_id: null }];
    e.sesion = { user: { id: "u-admin", email: "admin@alerce.app", user_metadata: {} }, access_token: "falso" };
    e.tablas.ferias.push({ id: "f_borrador", nombre: "Feria en borrador", descripcion: "x", ubicacion: "Plaza", fecha_inicio: hoy(8), fecha_fin: hoy(8), horario: "10 a 18", organizador: "Muni", estado: "BORRADOR", latitud: -41.4, longitud: -72.9 });
    const fila = (tienda_id, nombre, rubro, invitacion, asociaciones, extra) => Object.assign({ tienda_id, nombre, rubro, sector: "Alerce", asociaciones, invitacion,
      asistencia: "PENDIENTE", origen: "DIRECTA", via_asociacion: null, invitada_en: new Date().toISOString(), respondida_en: null }, extra || {});
    e.checklist = [
      fila("t_tejidos", "Tejidos Austral", "Artesanía", "CONFIRMADA", ["as1", "as2"]),
      fila("t_pan", "Panadería Doña Rosa", "Alimentos", "PENDIENTE", ["as1"], { origen: "ASOCIACION", via_asociacion: "as1" }),
      fila("t_barber", "Barbería Alerce", "Servicios", "RECHAZADA", ["as2"]),
      fila("t_otra", "Repostería", "Alimentos", "AGREGADA", [])
    ];
    return e;
  }

  test("W01, W02: publica una feria en borrador y cancela una activa (con motivo)", async ({ page }) => {
    const e = conAdmin();
    e.respuestasRpc.publicar_feria = [{ data: { feria_id: "f_borrador", estado: "PROXIMA" } }];
    e.respuestasRpc.cancelar_feria = [{ data: { feria_id: "f1", estado: "CANCELADA", avisadas: 2 } }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    const item = page.locator("#e6-admin-lista-ferias .e6-admin-item", { hasText: "Feria en borrador" });
    await expect(item).toContainText("Borrador (no publicada)");
    await item.locator("button", { hasText: "Publicar" }).click();
    await expect.poll(() => llamadasRpc(page, "publicar_feria")).toEqual([{ p_feria: "f_borrador" }]);
    await expect(page.locator("#toast-texto")).toContainText("Feria publicada");
    page.once("dialog", (d) => d.accept("Lluvia intensa"));
    await page.locator("#e6-admin-lista-ferias .e6-admin-item", { hasText: "Feria de Alerce" }).locator("button", { hasText: "Cancelar feria" }).click();
    await expect.poll(() => llamadasRpc(page, "cancelar_feria")).toEqual([{ p_feria: "f1", p_motivo: "Lluvia intensa" }]);
    await expect(page.locator("#toast-texto")).toContainText("Avisos guardados para 2");
    await page.locator("#e6-admin-lista-ferias button", { hasText: "Editar" }).first().click();
    await expect(page.locator("#e6-feria-estado option[value=CANCELADA]")).toHaveCount(1);
  });

  test("W03–W08: invita y agrega tiendas y asociaciones; checklist con 4 estados, búsqueda y 3 filtros; refleja la respuesta", async ({ page }) => {
    const e = conAdmin();
    const sinCambio = JSON.parse(JSON.stringify(e.checklist));
    const conRespuesta = JSON.parse(JSON.stringify(e.checklist));
    conRespuesta[1].invitacion = "CONFIRMADA";   // la emprendedora respondió «Participo» en la app
    e.respuestasRpc.checklist_feria = [{ data: sinCambio }, { data: sinCambio }, { data: sinCambio }, { data: conRespuesta }, { data: conRespuesta }, { data: conRespuesta }];
    e.respuestasRpc.invitar_a_feria = [{ data: { nuevas: 1, cambiadas: 0, sin_cambio: 1 } }, { data: { nuevas: 0, cambiadas: 1, sin_cambio: 0 } }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await page.locator("#e6-admin-lista-ferias .e6-admin-item", { hasText: "Feria de Alerce" }).locator("button", { hasText: "Invitaciones y checklist" }).click();
    const modal = page.locator("#modal-checklist-feria");
    await expect(modal).toHaveClass(/abierto/);
    const filas = modal.locator(".e7-fila");
    await expect(filas).toHaveCount(4);
    for (const etiqueta of ["Invitada", "Confirmada", "Rechazada", "Agregada por administración"]) await expect(modal.locator(".e7-chip", { hasText: etiqueta }).first()).toBeVisible();
    // Invitar una emprendedora y una asociación (W03, W04)
    await modal.locator("#e7-sel-tiendas label", { hasText: "Barbería" }).locator("input").check();
    await modal.locator("#e7-sel-asoc label", { hasText: "Red del Lago" }).locator("input").check();
    await modal.locator("button", { hasText: "Invitar seleccionadas" }).click();
    await expect.poll(() => llamadasRpc(page, "invitar_a_feria")).toEqual([{ p_feria: "f1", p_tiendas: ["t_barber"], p_asociaciones: ["as2"], p_directo: false }]);
    await expect(modal.locator("#e7-aviso-invitar")).toContainText("sin duplicar");
    // Agregar directamente (W05)
    await modal.locator("#e7-sel-tiendas label", { hasText: "Panadería" }).locator("input").check();
    await modal.locator("button", { hasText: "Agregar directamente" }).click();
    await expect.poll(async () => (await llamadasRpc(page, "invitar_a_feria"))[1]).toEqual({ p_feria: "f1", p_tiendas: ["t_pan"], p_asociaciones: [], p_directo: true });
    // Filtros (W08)
    await modal.locator('select[aria-label="Filtrar por estado"]').selectOption("RECHAZADA");
    await expect(filas).toHaveCount(1);
    await expect(filas.first()).toContainText("Barbería");
    await modal.locator('select[aria-label="Filtrar por estado"]').selectOption("");
    await modal.locator("#e7-filtro-tipo").selectOption("Alimentos");
    await expect(filas).toHaveCount(2);
    await modal.locator("#e7-filtro-tipo").selectOption("");
    await modal.locator("#e7-filtro-asoc").selectOption("as2");
    await expect(filas).toHaveCount(2);
    await modal.locator("#e7-filtro-asoc").selectOption("__ninguna");
    await expect(filas).toHaveCount(1);
    await modal.locator("#e7-filtro-asoc").selectOption("");
    await modal.locator('input[aria-label="Buscar en el checklist"]').fill("panader");
    await expect(filas).toHaveCount(1);
    await modal.locator('input[aria-label="Buscar en el checklist"]').fill("");
    // La respuesta desde la app se refleja al actualizar (W07, simulado: el cambio real se prueba en SQL)
    await modal.locator("button", { hasText: "Actualizar" }).click();
    await expect(modal.locator('.e7-fila[data-tienda="t_pan"] .e7-chip')).toHaveText("Confirmada");
  });

  test("W09–W11: recordatorio solo a pendientes, aviso a confirmadas (sin duplicar por doble clic) y asistencia el día de la feria", async ({ page }) => {
    const e = conAdmin();
    const filasHoy = JSON.parse(JSON.stringify(e.checklist));
    e.respuestasRpc.checklist_feria = Array.from({ length: 8 }, () => ({ data: filasHoy }));
    e.respuestasRpc.enviar_aviso_feria = [{ data: { destinatarias: 1, repetida: false } }, { data: { destinatarias: 2, repetida: false } }];
    e.respuestasRpc.registrar_asistencia = [{ data: { asistencia: "ASISTIO" } }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await page.locator("#e6-admin-lista-ferias .e6-admin-item", { hasText: "Feria de Hoy" }).locator("button", { hasText: "Invitaciones y checklist" }).click();
    const modal = page.locator("#modal-checklist-feria");
    await expect(modal.locator("#e7-btn-recordatorio")).toContainText("(1)");
    await expect(modal.locator("#e7-btn-aviso")).toContainText("(2)");
    await modal.locator("#e7-btn-recordatorio").click();
    await expect(modal.locator("#e7-aviso-envio")).toContainText("Recordatorio guardado para 1 emprendedora");
    await modal.locator("#e7-btn-aviso").click();
    await expect(modal.locator("#e7-aviso-envio")).toContainText("Escribe el mensaje");
    await modal.locator('textarea[aria-label="Mensaje del aviso a confirmadas"]').fill("Lleguen a las 9:00");
    await modal.locator("#e7-btn-aviso").click();
    await expect(modal.locator("#e7-aviso-envio")).toContainText("Aviso guardado para 2");
    const envios = await llamadasRpc(page, "enviar_aviso_feria");
    expect(envios.map(x => [x.p_tipo, x.p_mensaje])).toEqual([["RECORDATORIO", ""], ["AVISO_FERIA", "Lleguen a las 9:00"]]);
    expect(envios[0].p_solicitud).toMatch(/^[0-9a-f-]{36}$/);
    expect(envios[0].p_solicitud).not.toBe(envios[1].p_solicitud);
    // Asistencia: solo para confirmadas o agregadas
    await expect(modal.locator('.e7-fila[data-tienda="t_barber"] select')).toBeDisabled();
    await modal.locator('.e7-fila[data-tienda="t_tejidos"] select').selectOption("ASISTIO");
    await expect.poll(() => llamadasRpc(page, "registrar_asistencia")).toEqual([{ p_feria: "f_hoy", p_tienda: "t_tejidos", p_asistencia: "ASISTIO" }]);
    await expect(page.locator("#toast-texto")).toContainText("Asistió");
  });

  test("W11 (antes del día): en una feria futura la asistencia no se puede marcar", async ({ page }) => {
    const e = conAdmin();
    e.respuestasRpc.checklist_feria = Array.from({ length: 4 }, () => ({ data: e.checklist }));
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await page.locator("#e6-admin-lista-ferias .e6-admin-item", { hasText: "Feria de Alerce" }).locator("button", { hasText: "Invitaciones y checklist" }).click();
    await expect(page.locator('#modal-checklist-feria .e7-fila[data-tienda="t_tejidos"] select')).toBeDisabled();
  });
});

test.describe("Entrega 7 · emprendedora en la web", () => {
  test("Mis ferias: invitaciones del servidor con «Participo» y «No participo» (sin inscripción libre)", async ({ page }) => {
    const e = datos();
    e.tablas.perfiles = [{ id: "u-emp", nombre: "Rosa", rol: "EMPRENDEDORA", tienda_id: "t_pan" }];
    e.sesion = { user: { id: "u-emp", email: "rosa@correo.cl", user_metadata: {} }, access_token: "falso" };
    const inv = { feria_id: "f1", nombre: "Feria de Alerce", ubicacion: "Plaza de Alerce", fecha_inicio: hoy(3), fecha_fin: hoy(3), horario: "10 a 18", estado: "PROXIMA", invitacion: "PENDIENTE", asistencia: "PENDIENTE", origen: "DIRECTA" };
    // La primera respuesta la pide el panel de inicio (aviso «por responder»); las otras, la ventana Mis ferias.
    e.respuestasRpc.mis_ferias = [{ data: [inv] }, { data: [inv] }, { data: [Object.assign({}, inv, { invitacion: "CONFIRMADA" })] }];
    e.respuestasRpc.responder_invitacion = [{ data: { feria_id: "f1", invitacion: "CONFIRMADA", cambio: true } }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await expect(page.locator("#btn-abrir-catalogo-nav")).toBeVisible();
    await expect(page.locator("#panel-emp-invitaciones")).toContainText("Feria de Alerce");
    await page.evaluate(() => window.TramaWeb.abrirModalMisFerias());
    const cont = page.locator("#lista-ferias-gestion-cuerpo");
    await expect(cont).toContainText("Feria de Alerce");
    await expect(cont.locator(".e7-chip")).toHaveText("Invitada");
    await cont.locator("button", { hasText: /^Participo$/ }).click();
    await expect.poll(() => llamadasRpc(page, "responder_invitacion")).toEqual([{ p_feria: "f1", p_participo: true }]);
    await expect(cont.locator(".e7-chip")).toHaveText("Confirmada");
    await expect(cont).not.toContainText("Inscribir");
  });
});
