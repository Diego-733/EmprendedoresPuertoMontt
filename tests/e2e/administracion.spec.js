"use strict";
// Administración integral (migración 11, registro controlado) en la web, con los datos FICTICIOS de la demo
// local (tests/e2e/datos_demo.js) y el Supabase sustituto. No prueba RLS ni la instancia real: eso lo
// cubren las pruebas SQL (tests/sql/pruebas_registro_controlado.js).
const { test, expect } = require("@playwright/test");
const { crearServidorDemo } = require("./demo_local");

let servidor, BASE;
test.beforeAll(async () => {
  servidor = crearServidorDemo();
  await new Promise((ok) => servidor.listen(0, "127.0.0.1", ok));
  BASE = "http://localhost:" + servidor.address().port;
});
test.afterAll(() => new Promise((ok) => servidor.close(ok)));

async function preparar(page) {
  page.on("pageerror", (e) => console.log("ERROR DE PÁGINA:", e.message));
  await page.route(/^https:\/\/(unpkg\.com|tile\.openstreetmap\.org|fonts\.)/, (r) => r.fulfill({ status: 404, body: "" }));
}
const llamadas = (page, rpc) => page.evaluate((n) => window.__FALSO__.llamadas.filter((l) => l.rpc === n), rpc);

test("autorizar una emprendedora genera un código de activación (nunca una contraseña) y se muestra una vez", async ({ page }) => {
  await preparar(page);
  await page.goto(BASE + "/?demo=admin");
  await page.locator("#tab-btn-admin-emp").click();
  await expect(page.locator("#admin-lista-emp-contenedor")).toContainText("Marta Oyarzún");
  await page.locator("#admin-sec-emp button:has-text('Autorizar')").first().click();
  await page.locator("#admin-form-emp-nombre").fill("Luisa Pérez");
  await page.locator("#admin-form-emp-email").fill("Luisa@Correo.cl");
  await page.locator("#admin-btn-guardar-emp").click();
  const caja = page.locator("#admin-box-credenciales");
  await expect(caja).toBeVisible();
  await expect(caja).toContainText("ABCD-2345");
  await expect(caja).toContainText("Se muestra solo esta vez");
  await expect(caja).not.toContainText("Contraseña");
  const rpc = await llamadas(page, "autorizar_emprendedora");
  expect(rpc).toHaveLength(1);
  expect(rpc[0].args).toMatchObject({ p_email: "luisa@correo.cl", p_nombre: "Luisa Pérez" });
  expect(JSON.stringify(rpc[0].args)).not.toMatch(/password|clave/i);
  await expect(page.locator("#admin-lista-emp-contenedor")).toContainText("Luisa Pérez");
});

test("lista de cuentas: pendientes con «Código nuevo» y «Revocar»; antiguas sin autorización con «Regularizar»", async ({ page }) => {
  await preparar(page);
  await page.goto(BASE + "/?demo=admin");
  await page.locator("#tab-btn-admin-emp").click();
  const lista = page.locator("#admin-lista-emp-contenedor");
  await expect(lista).toContainText("Código vigente");
  await expect(lista.locator("button:has-text('Código nuevo')")).toHaveCount(1);
  await expect(lista.locator("button:has-text('Revocar')")).toHaveCount(1);
  await expect(lista).toContainText("Sin autorización registrada");
  page.once("dialog", (d) => d.accept());
  await lista.locator("button:has-text('Regularizar')").first().click();
  await expect.poll(async () => (await llamadas(page, "regularizar_emprendedora")).length).toBe(1);
  // Filtro «Pendientes»
  await page.locator("#admin-filtro-estado-emp").selectOption("PENDIENTES");
  await expect(lista).toContainText("Rosa Vargas");
  await expect(lista).not.toContainText("Marta Oyarzún");
  // Botones de 44 px de alto como mínimo (tras volver a dibujar la lista filtrada)
  await expect(lista.locator("button:has-text('Revocar')")).toBeVisible();
  await page.waitForTimeout(300);
  const alto = await lista.locator("button:has-text('Revocar')").evaluate((b) => b.getBoundingClientRect().height);
  expect(alto).toBeGreaterThanOrEqual(44);
});

test("servidor sin la migración 11: la lista sigue funcionando con la función anterior", async ({ page }) => {
  await preparar(page);
  await page.addInitScript(() => {
    window.__FALSO__ = window.__FALSO__ || {};
    window.__FALSO__.sin11 = true;
  });
  await page.goto(BASE + "/?demo=admin");
  // Se fuerza la respuesta «función no existe» para la lista de 11.
  await page.evaluate(() => {
    const sb = window.TramaWeb.obtenerServicio();
    sb.listarCuentasEmprendedoras = () => Promise.resolve({ ok: false, error: { message: "PGRST202: Could not find the function public.listar_cuentas_emprendedoras" } });
  });
  await page.locator("#tab-btn-admin-emp").click();
  await expect(page.locator("#admin-lista-emp-contenedor")).toContainText("Marta Oyarzún");
  await expect(page.locator("#admin-lista-emp-contenedor button:has-text('Código nuevo')")).toHaveCount(0);
});

test("mostrar u ocultar la contraseña en el ingreso, con nombre accesible", async ({ page }) => {
  await preparar(page);
  await page.goto(BASE + "/?demo=visitante");
  await page.locator("#btn-mi-perfil-header").click();
  const clave = page.locator("#auth-inp-password");
  await expect(clave).toBeVisible();
  await clave.fill("secreta123");
  const boton = page.locator("#auth-inp-password + .cuentas-ver-clave");
  await expect(boton).toHaveAttribute("aria-label", "Mostrar la contraseña");
  await boton.click();
  await expect(clave).toHaveAttribute("type", "text");
  await expect(boton).toHaveAttribute("aria-pressed", "true");
  await boton.click();
  await expect(clave).toHaveAttribute("type", "password");
  const alto = await boton.evaluate((b) => b.getBoundingClientRect().height);
  expect(alto).toBeGreaterThanOrEqual(44);
});

test("cambiar la contraseña con sesión: comprueba la actual antes y valida la nueva", async ({ page }) => {
  await preparar(page);
  await page.goto(BASE + "/?demo=emprendedora");
  await page.locator("#btn-mi-perfil-header").click();
  await expect(page.locator("#modal-mi-perfil")).toBeVisible();
  await page.locator("#cuentas-clave > summary").click();
  await page.locator("#cuentas-clave-actual").fill("actual123");
  await page.locator("#cuentas-clave-nueva").fill("corta");
  await page.locator("#cuentas-clave-repetir").fill("corta");
  await page.locator("#cuentas-btn-clave").click();
  await expect(page.locator("#cuentas-aviso")).toContainText("al menos 8 caracteres");
  await page.locator("#cuentas-clave-actual").fill("actual123");
  await page.locator("#cuentas-clave-nueva").fill("NuevaClave2026");
  await page.locator("#cuentas-clave-repetir").fill("NuevaClave2026");
  await page.locator("#cuentas-btn-clave").click();
  await expect(page.locator("#cuentas-aviso")).toContainText("Tu contraseña se cambió");
  const login = await page.evaluate(() => window.__FALSO__.llamadas.filter((l) => l.login));
  expect(login.length).toBeGreaterThanOrEqual(1);   // reautenticación con la contraseña actual
  // Ninguna contraseña queda guardada en el navegador.
  const guardado = await page.evaluate(() => JSON.stringify(Object.assign({}, localStorage)) + JSON.stringify(Object.assign({}, sessionStorage)));
  expect(guardado).not.toContain("NuevaClave2026");
  expect(guardado).not.toContain("actual123");
  // A una emprendedora no se le ofrece «Activar mi tienda».
  await expect(page.locator("#cuentas-activar")).toBeHidden();
});
