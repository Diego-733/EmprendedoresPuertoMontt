"use strict";
// Entrega 8 en la web con un Supabase sustituto: aviso de privacidad (W09), recuperación de
// contraseña (W06, solo la parte de la página), gestión de otras cuentas de administrador (W07)
// y bitácora (W08). Las reglas reales (quién puede crear administradores, qué se anota en la
// bitácora) se prueban contra PostgreSQL en tests/sql/pruebas_administracion.js.
const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");
const { RAIZ } = require("./servidor");
// El SDK de Supabase se reemplaza por uno falso: en la copia servida a la prueba se quita su verificación SRI.
const sinSri = (archivo) => /index.html$/.test(archivo) ? fs.readFileSync(archivo, "utf8").replace(/(supabase-js@[^"]+") integrity="[^"]+"/, "$1") : fs.readFileSync(archivo);

const ORIGEN = "http://alerce.test";
const FALSO = fs.readFileSync(path.join(__dirname, "supabase_falso.js"), "utf8");
const TIPOS = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".otf": "font/otf", ".png": "image/png" };

function datos(conAdmin) {
  const e = {
    tablas: {
      tiendas: [], productos: [], tienda_asociaciones: [], tipos_producto: [], ventas: [], contactos: [],
      seguimientos: [], preferencias_avisos: [], avisos: [], asociaciones: [], ferias: [],
      configuracion: [{ clave: "navegacion_anonima", valor: "PERMITIR" }],
      perfiles: [],
      cuentas_autorizadas: []
    },
    respuestasRpc: {}
  };
  if (conAdmin) {
    e.tablas.perfiles = [
      { id: "u-admin", email: "admin@alerce.app", nombre: "Admin", rol: "ADMIN", tienda_id: null },
      { id: "u-marta", email: "donamarta@alerce.app", nombre: "Doña Marta", rol: "EMPRENDEDORA", tienda_id: "t_marta", creado_en: "2026-11-01" }
    ];
    e.tablas.cuentas_autorizadas = [{ email: "nueva@alerce.app", rol: "EMPRENDEDORA", nombre: "Nueva Emprendedora" }];
    e.sesion = { user: { id: "u-admin", email: "admin@alerce.app", user_metadata: {} }, access_token: "falso" };
  }
  return e;
}

async function preparar(page, estadoFalso) {
  page.on("pageerror", (err) => console.log("ERROR DE PÁGINA:", err.message));
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
  await page.route(/^https:\/\/unpkg\.com\//, (route) => route.fulfill({ status: 404, body: "" }));
  await page.route(/^https:\/\/tile\.openstreetmap\.org\//, (route) => route.fulfill({ status: 404, body: "" }));
  await page.route("https://falso.supabase.co/auth/v1/settings", (route) => route.fulfill({ contentType: "application/json", body: JSON.stringify({ external: { google: false } }) }));
  await page.route(/^https:\/\/(fonts\.|picsum|images\.unsplash|falso\.supabase\.co\/(p|storage))/, (route) => route.fulfill({ status: 200, contentType: "image/svg+xml", body: "<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'/>" }));
  await page.addInitScript((estado) => { window.__FALSO__ = estado; }, estadoFalso);
}

const llamadas = (page, filtro) => page.evaluate((f) => window.__FALSO__.llamadas.filter(l => l[f.k] !== undefined && (!f.v || l[f.k] === f.v)), filtro);
const llamadasRpc = (page, nombre) => page.evaluate((n) => window.__FALSO__.llamadas.filter(l => l.rpc === n).map(l => l.args), nombre);

test.describe("Entrega 8 · privacidad y recuperación", () => {
  test("W09: el aviso de privacidad se abre desde el pie de página y explica qué es público", async ({ page }) => {
    await preparar(page, datos(false));
    await page.goto(ORIGEN + "/");
    await page.locator("#btn-aviso-privacidad").click();
    const modal = page.locator("#modal-privacidad");
    await expect(modal).toBeVisible();
    await expect(modal).toContainText("Ley N° 19.628");
    await expect(modal).toContainText("solo la información que la emprendedora decide mostrar");
    await expect(modal).toContainText("Ventas");
    await expect(modal).toContainText("no se guarda en el servidor");
  });

  test("W09: el enlace ?privacidad=1 abre el aviso directamente (lo usa la app)", async ({ page }) => {
    await preparar(page, datos(false));
    await page.goto(ORIGEN + "/?privacidad=1");
    await expect(page.locator("#modal-privacidad")).toBeVisible();
  });

  test("W06 (parte de la página): pedir el enlace llama a Supabase con la vuelta a ?recuperar=1", async ({ page }) => {
    await preparar(page, datos(false));
    await page.goto(ORIGEN + "/");
    await page.evaluate(() => window.TramaWeb.abrirModal("modal-recuperar-contrasena"));
    await page.locator("#recuperar-email").fill("compradora.prueba@example.com");
    await page.locator("#btn-enviar-recuperacion").click();
    await expect(page.locator("#recuperar-aviso-exito")).toContainText("recibirás un enlace");
    const r = await llamadas(page, { k: "recuperar" });
    expect(r).toEqual([{ recuperar: "compradora.prueba@example.com", redirectTo: ORIGEN + "/?recuperar=1" }]);
  });
});

test.describe("Entrega 8 · administración con datos del servidor", () => {
  test("Con servidor se ocultan las pestañas de ejemplo y, sin la 10, las cuentas emprendedoras vienen de perfiles", async ({ page }) => {
    const e = datos(true);
    const sin10 = { error: { message: "PGRST202: Could not find the function public.listar_emprendedoras" } };
    e.respuestasRpc.listar_emprendedoras = [sin10, sin10, sin10];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await expect(page.locator("#admin-dashboard")).toBeVisible();
    await expect(page.locator("#tab-btn-admin-asoc")).toBeHidden();
    await expect(page.locator("#tab-btn-admin-cat")).toHaveText("🏷️ Tipos de producto");
    await expect(page.locator("#admin-kpis")).toBeHidden();
    await page.locator("#tab-btn-admin-emp").click();
    const lista = page.locator("#admin-lista-emp-contenedor");
    await expect(lista).toContainText("Doña Marta");
    await expect(lista).toContainText("Nueva Emprendedora");
    await expect(lista).toContainText("Pendiente");
    await expect(lista).not.toContainText("Panadería Doña Rosa");   // ejemplo local de la entrega 4
  });

  test("W07: lista, crea, edita y desactiva otra cuenta de administración en el servidor", async ({ page }) => {
    const e = datos(true);
    const lista1 = [
      { email: "admin@alerce.app", nombre: "Admin", estado: "ACTIVA", creado_en: "2026-11-01T12:00:00Z", es_yo: true },
      { email: "coord@alerce.app", nombre: "Coordinación", estado: "ACTIVA", creado_en: "2026-11-20T12:00:00Z", es_yo: false }
    ];
    const lista2 = lista1.concat([{ email: "soporte@alerce.app", nombre: "Soporte", estado: "PENDIENTE_REGISTRO", creado_en: "2026-11-24T12:00:00Z", es_yo: false }]);
    const lista3 = JSON.parse(JSON.stringify(lista2)); lista3[1].nombre = "Coordinación Alerce";
    const lista4 = JSON.parse(JSON.stringify(lista3)); lista4[1].estado = "DESACTIVADA";
    e.respuestasRpc.listar_admins = [{ data: lista1 }, { data: lista2 }, { data: lista3 }, { data: lista4 }];
    e.respuestasRpc.crear_admin = [{ data: "PENDIENTE_REGISTRO" }];
    e.respuestasRpc.editar_admin = [{ data: null }];
    e.respuestasRpc.cambiar_estado_admin = [{ data: "DESACTIVADA" }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await page.locator("#tab-btn-admin-adm").click();
    const cont = page.locator("#admin-lista-admins-contenedor");
    await expect(cont).toContainText("coord@alerce.app");
    await expect(cont).toContainText("(tu cuenta)");
    // Crear
    const respuestas = ["Soporte", "soporte@alerce.app"];
    page.on("dialog", (d) => { if (d.type() === "prompt") d.accept(respuestas.shift() || "Coordinación Alerce"); else d.accept(); });
    await page.locator("#admin-sec-adm button", { hasText: "Crear" }).click();
    await expect.poll(() => llamadasRpc(page, "crear_admin")).toEqual([{ p_email: "soporte@alerce.app", p_nombre: "Soporte" }]);
    await expect(page.locator("#toast-texto")).toContainText("Debe registrarse en la web con ese correo");
    await expect(cont).toContainText("Debe registrarse con este correo");
    // Editar nombre (la segunda fila es coord@)
    await cont.locator("div", { hasText: "coord@alerce.app" }).locator("button", { hasText: "Editar nombre" }).last().click();
    await expect.poll(() => llamadasRpc(page, "editar_admin")).toEqual([{ p_email: "coord@alerce.app", p_nombre: "Coordinación Alerce" }]);
    await expect(cont).toContainText("Coordinación Alerce");
    // Desactivar
    await cont.locator("div", { hasText: "coord@alerce.app" }).locator("button", { hasText: "Desactivar" }).last().click();
    await expect.poll(() => llamadasRpc(page, "cambiar_estado_admin")).toEqual([{ p_email: "coord@alerce.app", p_activo: false }]);
    await expect(cont.locator("div", { hasText: "coord@alerce.app" }).last()).toContainText("Desactivada");
    // La propia cuenta no tiene botón de desactivar
    await expect(cont.locator("div", { hasText: "admin@alerce.app" }).last().locator("button", { hasText: "Desactivar" })).toHaveCount(0);
  });

  test("W07: un error del servidor se muestra en español (ej.: última administradora)", async ({ page }) => {
    const e = datos(true);
    e.respuestasRpc.listar_admins = [{ data: [{ email: "admin@alerce.app", nombre: "Admin", estado: "ACTIVA", creado_en: "2026-11-01T12:00:00Z", es_yo: false }] }];
    e.respuestasRpc.cambiar_estado_admin = [{ error: { message: "ULTIMA_ADMIN" } }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    page.on("dialog", (d) => d.accept());
    await page.locator("#tab-btn-admin-adm").click();
    await page.locator("#admin-lista-admins-contenedor button", { hasText: "Desactivar" }).click();
    await expect(page.locator("#toast-texto")).toContainText("Debe quedar al menos otra administradora activa");
  });

  test("W08: la bitácora muestra quién, qué y cuándo desde el servidor (sin datos de ejemplo)", async ({ page }) => {
    const e = datos(true);
    e.respuestasRpc.ver_bitacora = [{ data: [
      { en: "2026-11-24T15:30:00Z", quien: "admin@alerce.app", accion: "Publicó feria", objeto_tipo: "feria", objeto_id: "f1", detalle: { nombre: "Feria de Alerce", estado: "PROXIMA" } },
      { en: "2026-11-24T15:10:00Z", quien: "coord@alerce.app", accion: "Editó asociación", objeto_tipo: "asociación", objeto_id: "as1", detalle: { nombre: "Manos del Sur", campos: ["descripcion"] } }
    ] }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await page.locator("#tab-btn-admin-log").click();
    const tabla = page.locator("#admin-tabla-bitacora-body");
    await expect(tabla.locator("tr")).toHaveCount(2);
    await expect(tabla).toContainText("Publicó feria");
    await expect(tabla).toContainText("coord@alerce.app");
    await expect(tabla).toContainText("campos: descripcion");
    await expect(tabla).toContainText("24-11-2026, 12:30");   // hora de Chile (UTC−3)
    await expect(tabla).not.toContainText("Inicio de Plataforma");   // registro de ejemplo de la entrega 4
    expect(await llamadasRpc(page, "ver_bitacora")).toEqual([{ p_limite: 200 }]);
  });

  test("RF-ADM-01: edita y desactiva una emprendedora en el servidor y la lista lo refleja", async ({ page }) => {
    const e = datos(true);
    const fila = (estado, nombre, tienda) => ({ usuario_id: "u-marta", email: "donamarta@alerce.app", nombre, tienda_id: "t_marta", tienda_nombre: tienda, estado, creado_en: "2026-11-01T12:00:00Z" });
    const inicial = { data: [fila("ACTIVA", "Doña Marta", "Minimarket"), { usuario_id: null, email: "nueva@alerce.app", nombre: "Nueva", tienda_id: null, tienda_nombre: null, estado: "PENDIENTE_REGISTRO", creado_en: "2026-11-20T12:00:00Z" }] };
    // Se pide al abrir la pestaña y después de cada cambio.
    e.respuestasRpc.listar_emprendedoras = [inicial,
      { data: [fila("ACTIVA", "Marta Soto", "Minimarket Soto")] },
      { data: [fila("DESACTIVADA", "Marta Soto", "Minimarket Soto")] }];
    e.respuestasRpc.editar_emprendedora = [{ data: null }];
    e.respuestasRpc.cambiar_estado_emprendedora = [{ data: "DESACTIVADA" }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await page.locator("#tab-btn-admin-emp").click();
    const lista = page.locator("#admin-lista-emp-contenedor");
    await expect(lista).toContainText("Pendiente: debe registrarse con este correo");
    await expect(lista.locator(":scope > div", { hasText: "nueva@alerce.app" }).locator("button")).toHaveCount(0);
    const respuestas = ["Marta Soto", "Minimarket Soto"];
    page.on("dialog", (d) => { if (d.type() === "prompt") d.accept(respuestas.shift()); else d.accept(); });
    await lista.locator("button", { hasText: "Editar" }).click();
    await expect.poll(() => llamadasRpc(page, "editar_emprendedora")).toEqual([{ p_usuario: "u-marta", p_nombre: "Marta Soto", p_tienda_nombre: "Minimarket Soto" }]);
    await expect(lista).toContainText("Tienda: Minimarket Soto");
    await lista.locator("button", { hasText: "Desactivar" }).click();
    await expect.poll(() => llamadasRpc(page, "cambiar_estado_emprendedora")).toEqual([{ p_usuario: "u-marta", p_activa: false }]);
    await expect(lista).toContainText("Desactivada");
    await expect(lista.locator("button", { hasText: "Reactivar" })).toHaveCount(1);
  });

  test("RF-ADM-03: tipos de producto del servidor: crear, desactivar y no eliminar uno en uso", async ({ page }) => {
    const e = datos(true);
    e.tablas.tipos_producto = [
      { id: "tejido", categoria: "TEXTIL", nombre: "Tejido a mano", activo: true, orden: 1 },
      { id: "jabon", categoria: "COSMETICA", nombre: "Jabón artesanal", activo: true, orden: 2 }];
    e.respuestasRpc.uso_tipos_producto = [{ data: [{ tipo_id: "tejido", productos: 3 }, { tipo_id: "jabon", productos: 0 }] }, { data: [] }, { data: [] }];
    e.respuestasRpc.guardar_tipo_producto = [{ data: "velas_aromaticas", alGuardar: null }, { data: "tejido" }];
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    await page.locator("#tab-btn-admin-cat").click();
    const lista = page.locator("#admin-lista-tipos-servidor");
    await expect(lista).toContainText("Tejido a mano");
    await expect(lista).toContainText("3 productos");
    await expect(lista).not.toContainText("Abarrotes y Alimentos");   // ejemplo local de la entrega 4
    await expect(lista.locator(":scope > div", { hasText: "Tejido a mano" }).locator("button", { hasText: "Eliminar" })).toHaveCount(0);
    await expect(lista.locator(":scope > div", { hasText: "Jabón artesanal" }).locator("button", { hasText: "Eliminar" })).toHaveCount(1);
    const respuestas = ["Velas aromáticas", "6"];
    page.on("dialog", (d) => { if (d.type() === "prompt") d.accept(respuestas.shift()); else d.accept(); });
    await page.locator("#admin-sec-cat button", { hasText: "+ Nuevo tipo de producto" }).click();
    await expect.poll(() => llamadasRpc(page, "guardar_tipo_producto")).toEqual([
      { p_id: "velas_aromaticas", p_nombre: "Velas aromáticas", p_categoria: "DECORACION", p_orden: 3, p_campos: null, p_activo: true }]);
    await expect(page.locator("#toast-texto")).toContainText("Tipo creado");
    await page.locator("#admin-lista-tipos-servidor > div", { hasText: "Tejido a mano" }).locator("button", { hasText: "Desactivar" }).click();
    await expect.poll(() => llamadasRpc(page, "guardar_tipo_producto").then(x => x.length)).toBe(2);
    expect((await llamadasRpc(page, "guardar_tipo_producto"))[1]).toEqual({ p_id: "tejido", p_nombre: "Tejido a mano", p_categoria: "TEXTIL", p_orden: 1, p_campos: null, p_activo: false });
    await expect(page.locator("#toast-texto")).toContainText("los productos que lo usan lo conservan");
  });

  test("Cada pestaña del panel muestra su contenido justo debajo, a la vista (no al final de la página)", async ({ page }) => {
    const e = datos(true);
    e.tablas.ferias = [{ id: "f1", nombre: "Feria de Alerce", descripcion: "x", ubicacion: "Plaza", fecha_inicio: "2026-12-01", fecha_fin: "2026-12-01", horario: "10 a 18", organizador: "Muni", estado: "PROXIMA", latitud: -41.4, longitud: -72.9 }];
    e.respuestasRpc.listar_admins = [{ data: [{ email: "admin@alerce.app", nombre: "Admin", estado: "ACTIVA", creado_en: "2026-11-01T12:00:00Z", es_yo: true }] }];
    e.respuestasRpc.ver_bitacora = [{ data: [{ en: "2026-11-24T15:30:00Z", quien: "admin@alerce.app", accion: "Publicó feria", objeto_tipo: "feria", objeto_id: "f1", detalle: {} }] }];
    e.respuestasRpc.listar_emprendedoras = [{ data: [] }, { data: [] }];
    await page.setViewportSize({ width: 1280, height: 800 });
    await preparar(page, e);
    await page.goto(ORIGEN + "/");
    // Al entrar: pestaña de ferias, asociaciones y app.
    await expect(page.locator("#admin-sec-e6")).toBeVisible();
    await expect(page.locator("#admin-sec-adm")).toBeHidden();
    // Administradores: su panel aparece a la vista y el bloque de ferias se oculta.
    await page.locator("#tab-btn-admin-adm").click();
    await expect(page.locator("#admin-sec-e6")).toBeHidden();
    await expect(page.locator("#admin-sec-adm")).toBeInViewport();
    await expect(page.locator("#admin-lista-admins-contenedor")).toContainText("admin@alerce.app");
    await expect(page.locator("#admin-sec-adm button", { hasText: "Crear" })).toBeInViewport();
    // Bitácora.
    await page.locator("#tab-btn-admin-log").click();
    await expect(page.locator("#admin-sec-log")).toBeInViewport();
    await expect(page.locator("#admin-tabla-bitacora-body")).toContainText("Publicó feria");
    // Cuentas y tipos.
    await page.locator("#tab-btn-admin-emp").click();
    await expect(page.locator("#admin-sec-emp")).toBeInViewport();
    await page.locator("#tab-btn-admin-cat").click();
    await expect(page.locator("#admin-sec-cat")).toBeInViewport();
    // Volver a ferias.
    await page.locator("#tab-btn-admin-e6").click();
    await expect(page.locator("#admin-sec-e6")).toBeInViewport();
    await expect(page.locator("#admin-sec-cat")).toBeHidden();
  });
});
