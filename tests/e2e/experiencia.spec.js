"use strict";
// Rediseño de la experiencia (después de la Entrega 8), con los datos FICTICIOS de la demo local
// (tests/e2e/datos_demo.js) y el Supabase sustituto: no prueba RLS ni toca la instancia real.
// - Aceptación móvil: enlace directo a una tienda → producto disponible → precio → contacto correcto → volver a la misma ficha.
// - Administración: lo que se ve INMEDIATAMENTE después de tocar cada pestaña (sin que la prueba desplace la página).
// - Comodidad: sin desplazamiento lateral a 320, 390, 768 y 1280 px, también con la letra al 200 %.
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
// Clic como una persona: sin que Playwright desplace la página hasta el elemento.
const tocarSinDesplazar = (page, selector) => page.locator(selector).evaluate((el) => el.click());
const desbordeLateral = (page) => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);

test.describe("Comprador en el celular (390 px)", () => {
  test.use({ viewport: { width: 390, height: 844 }, hasTouch: true });

  test("aceptación: enlace a una tienda → producto → precio → WhatsApp de esa tienda → vuelve a la misma ficha", async ({ page }) => {
    await preparar(page);
    await page.goto(BASE + "/?demo=visitante&tienda=t_tejidos_austral");
    const ficha = page.locator("#modal-tienda-vitrina");
    await expect(ficha).toHaveClass(/abierto/);
    // Se reconoce de quién es y qué vende, sin bajar.
    await expect(page.locator("#modal-tienda-titulo")).toHaveText("Tejidos Austral");
    await expect(page.locator("#modal-tienda-titulo")).toBeInViewport();
    await expect(page.locator("#modal-tienda-rubro-sector")).toHaveText("Artesanía en Alerce Sur");
    // El contacto queda a la vista (barra fija) y dice a quién le llega antes de abrir WhatsApp.
    await expect(page.locator("#ficha-destinatario")).toHaveText("Tu mensaje le llega a Tejidos Austral · WhatsApp +56 9 3333 4444");
    await expect(page.locator("#btn-modal-tienda-whatsapp")).toBeInViewport();
    await expect(page.locator("#btn-modal-tienda-whatsapp")).toHaveAttribute("href", /^https:\/\/wa\.me\/56933334444\?text=Hola%20Tejidos%20Austral/);
    // Un producto disponible con su precio y su estado en texto.
    const gorro = page.locator('#lista-productos-tienda-modal [data-producto-vitrina="gorro_lana"]');
    await expect(gorro.locator(".prod-precio")).toHaveText("$ 8.500");
    await expect(gorro.locator(".badge-necesidad")).toHaveText("Disponible");
    await expect(page.locator('#lista-productos-tienda-modal [data-producto-vitrina="calcetines"] .badge-necesidad')).toHaveText("A pedido");
    // Se baja hasta el producto y se abre su detalle.
    const panel = ficha.locator(".modal-panel-editorial");
    await panel.evaluate((p) => { p.scrollTop = 320; });
    const scrollAntes = await panel.evaluate((p) => p.scrollTop);
    expect(scrollAntes).toBeGreaterThan(0);
    await gorro.locator(".enlace-producto").click();
    const detalle = page.locator("#modal-producto");
    await expect(detalle).toHaveClass(/abierto/);
    await expect(page).toHaveURL(/producto=gorro_lana/);
    await expect(detalle.locator(".prod-precio")).toHaveText("$ 8.500");
    await expect(detalle.locator(".detalle-vende-nombre")).toHaveText("Tejidos Austral");
    await expect(detalle.locator(".detalle-vende-destino")).toContainText("+56 9 3333 4444");
    // El contacto del producto va a ESA tienda, con el producto y el precio en el mensaje.
    const wsp = detalle.locator(".btn-whatsapp-prod");
    await expect(wsp).toHaveAttribute("aria-label", "Pedir «Gorro de lana» por WhatsApp a Tejidos Austral");
    const href = await wsp.getAttribute("href");
    expect(href).toMatch(/^https:\/\/wa\.me\/56933334444\?/);
    expect(decodeURIComponent(href)).toContain("«Gorro de lana» ($ 8.500)");
    // «Atrás» del celular cierra el detalle y deja la ficha en el mismo punto de la lista.
    await page.goBack();
    await expect(detalle).not.toHaveClass(/abierto/);
    await expect(ficha).toHaveClass(/abierto/);
    await expect(page).toHaveURL(/tienda=t_tejidos_austral/);
    expect(await panel.evaluate((p) => p.scrollTop)).toBe(scrollAntes);
    await expect(gorro).toBeInViewport();
    // «Volver» cierra la ficha y la dirección queda en la vitrina (al recargar no reaparece).
    await page.locator("#modal-tienda-vitrina .btn-volver").click();
    await expect(ficha).not.toHaveClass(/abierto/);
    await expect(page).not.toHaveURL(/tienda=/);
  });

  test("portada: buscar un producto muestra precio y tienda; al volver de la ficha se conserva la búsqueda", async ({ page }) => {
    await preparar(page);
    await page.goto(BASE + "/?demo=visitante");
    await expect(page.locator("#campo-busqueda-principal")).toBeInViewport();
    // La descarga de la app ya no es lo primero.
    await expect(page.locator('a[download="AlerceEmprende.apk"]').first()).not.toBeInViewport();
    await page.locator("#campo-busqueda-principal").fill("kuchen");
    const tarjetas = page.locator("#grilla-productos-portada [data-producto-portada]");
    await expect(tarjetas).toHaveCount(1);
    await expect(tarjetas.first()).toContainText("$ 9.000");
    await expect(tarjetas.first()).toContainText("Vende: Repostería y Pastelería Artesanal Sureña");
    await tarjetas.first().click();
    await expect(page.locator("#modal-producto")).toHaveClass(/abierto/);
    await page.goBack();
    await expect(page.locator("#modal-producto")).not.toHaveClass(/abierto/);
    await expect(page.locator("#campo-busqueda-principal")).toHaveValue("kuchen");
    await expect(tarjetas).toHaveCount(1);
    // La tienda que vende lo buscado también aparece en el listado de tiendas.
    await expect(page.locator("#grilla-tiendas-marketplace .tarjeta-tienda")).toHaveCount(1);
  });

  test("tienda sin WhatsApp: el aviso es claro y el mensaje va por el formulario de ESA tienda", async ({ page }) => {
    await preparar(page);
    await page.goto(BASE + "/?demo=visitante&tienda=t_reposteria_surena");
    await expect(page.locator("#modal-tienda-titulo")).toHaveText("Repostería y Pastelería Artesanal Sureña de la Señora Clementina Barrientos");
    await expect(page.locator("#btn-modal-tienda-whatsapp")).toBeHidden();
    await expect(page.locator("#modal-tienda-sin-whatsapp")).toHaveText("Esta tienda aún no tiene un WhatsApp de contacto");
    await expect(page.locator("#btn-modal-tienda-formulario")).toBeInViewport();
  });
});

test.describe("Administración: el listado y «crear» se ven apenas se toca cada pestaña", () => {
  for (const ancho of [390, 1280]) {
    test(`a ${ancho} px`, async ({ page }) => {
      await page.setViewportSize({ width: ancho, height: ancho < 800 ? 844 : 800 });
      await preparar(page);
      await page.goto(BASE + "/?demo=admin");
      await expect(page.locator("#admin-dashboard")).toBeVisible();
      // Al entrar, el panel está arriba (no al final de la página).
      await expect(page.locator("#modal-admin-titulo")).toBeInViewport();
      await expect(page.locator("#tab-btn-admin-e6")).toHaveAttribute("aria-selected", "true");
      await expect(page.locator("#e6-btn-nueva-feria")).toBeInViewport();
      const casos = [
        ["#tab-btn-admin-adm", "#admin-lista-admins-contenedor", "#admin-sec-adm button:has-text('Crear')", "coord@alerce.app"],
        ["#tab-btn-admin-emp", "#admin-lista-emp-contenedor", "#admin-sec-emp button:has-text('Autorizar')", "Marta Oyarzún"],
        ["#tab-btn-admin-cat", "#admin-sec-cat", "#admin-sec-cat button:has-text('tipo')", "Pan y masas"],
        ["#tab-btn-admin-e6", "#e6-admin-lista-ferias", "#e6-btn-nueva-feria", "Feria Navideña de Alerce"],
        ["#tab-btn-admin-asoc6", "#e6-admin-lista-asoc", "#admin-e6-asoc summary:has-text('Crear asociación')", "Manos del Sur"],
        ["#tab-btn-admin-log", "#admin-tabla-bitacora-body", null, "Publicó feria"]
      ];
      for (const [pestana, lista, crear, texto] of casos) {
        await tocarSinDesplazar(page, pestana);
        await expect(page.locator(pestana)).toHaveAttribute("aria-selected", "true");
        await expect(page.locator(lista)).toContainText(texto);
        // Comprobado sin que la prueba desplace la página: debe verse así tras el clic.
        await expect(page.locator(lista).first()).toBeInViewport();
        if (crear) await expect(page.locator(crear).first()).toBeInViewport();
      }
      expect(await desbordeLateral(page)).toBeLessThanOrEqual(0);
    });
  }
});

test.describe("Sin desplazamiento lateral", () => {
  for (const ancho of [320, 390, 768, 1280]) {
    for (const letra of ["100%", "200%"]) {
      test(`${ancho} px con letra al ${letra}: portada, ficha y panel de la emprendedora`, async ({ page }) => {
        await page.setViewportSize({ width: ancho, height: 800 });
        await preparar(page);
        await page.addInitScript((l) => { document.addEventListener("DOMContentLoaded", () => { document.documentElement.style.fontSize = l; }); }, letra);
        for (const url of ["/?demo=visitante", "/?demo=visitante&tienda=t_reposteria_surena", "/?demo=emprendedora"]) {
          await page.goto(BASE + url);
          await page.waitForTimeout(400);
          expect(await desbordeLateral(page), url).toBeLessThanOrEqual(0);
          // Los nombres largos se leen completos (no se cortan con «…»).
          if (url.includes("tienda=")) {
            const nombre = page.locator("#modal-tienda-titulo");
            expect(await nombre.evaluate((el) => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
          }
        }
      });
    }
  }
});

test.describe("Emprendedora en la web", () => {
  test.use({ viewport: { width: 390, height: 844 } });
  test("al entrar ve primero «Registrar una venta», «Mis productos» y la invitación por responder", async ({ page }) => {
    await preparar(page);
    await page.goto(BASE + "/?demo=emprendedora");
    await expect(page.locator("#btn-abrir-pos-nav")).toBeInViewport();
    await expect(page.locator("#btn-abrir-pos-nav")).toContainText("Registrar una venta");
    await expect(page.locator("#btn-abrir-catalogo-nav")).toContainText("Mis productos");
    await expect(page.locator("#panel-emp-invitaciones")).toContainText("Feria Navideña de Alerce");
    await expect(page.locator("#panel-emp-invitaciones")).toBeInViewport();
  });
});
