"use strict";
// Asistente Alerce en la web (js/asistenteWeb.js): mismo contrato de frases que Android,
// respeto del rol y ninguna acción que guarde por su cuenta. Corre en Node, sin navegador.
const test = require("node:test");
const assert = require("node:assert/strict");
const path = require("path");
const fs = require("fs");
const A = require(path.join(__dirname, "../../LA PAgina (segun)/proyectode10/js/asistenteWeb.js"));
const contrato = JSON.parse(fs.readFileSync(path.join(__dirname, "../contrato/asistente_frases.json"), "utf8"));

test("contrato compartido con Android: intención, clase, tema, nombre y precio", () => {
  for (const c of contrato.casos) {
    const r = A.interpretar(c.frase);
    assert.equal(r.intencion, c.intencion, c.frase);
    assert.equal(r.clase, contrato.clases[c.intencion], "clase de " + c.frase);
    if (c.tema) assert.equal(r.tema, c.tema, "tema de " + c.frase);
    if (c.precio !== undefined) assert.equal(r.datos.precio, c.precio, "precio de " + c.frase);
    if (c.nombre !== undefined) assert.equal(r.datos.nombre, c.nombre, "nombre de " + c.frase);
  }
});

test("precios chilenos como en Android", () => {
  assert.equal(A.precio("a quince luquitas"), 15000);
  assert.equal(A.precio("a luca y media"), 1500);
  assert.equal(A.precio("a $15 000"), 15000);
  assert.equal(A.precio("a 2 cuadras"), null);
  assert.equal(A.aDigitos("doce mil quinientos"), "12500");
});

test("lo de la emprendedora no se ofrece sin su sesión", () => {
  const r = A.responder("vendí dos gorros", false);
  assert.equal(r.accion, undefined);
  assert.match(r.texto, /cuenta de emprendedora/);
  const crear = A.responder("quiero agregar un chaleco a quince mil", false);
  assert.equal(crear.accion, undefined);
});

test("con sesión de emprendedora solo navega o llena el formulario; nunca guarda", () => {
  const acciones = new Set();
  ["quiero agregar un chaleco de lana a quince mil", "vendí dos gorros", "muéstrame mis productos", "quiero compartir mi tienda",
   "quiero cambiar mi número de whatsapp", "mis ferias", "busco chalecos", "mi pedido", "¿cómo agrego un producto?"].forEach((f) => {
    const r = A.responder(f, true);
    if (r.accion) acciones.add(r.accion);
    (r.opciones || []).forEach((o) => o.accion && acciones.add(o.accion));
  });
  const permitidas = new Set(["publicar", "venta", "productos", "qr", "tienda", "misFerias", "ferias", "buscar", "carrito", "entrar", "guia"]);
  for (const a of acciones) assert.ok(permitidas.has(a), "acción no permitida: " + a);
  const crear = A.responder("quiero agregar un chaleco de lana a quince mil", true);
  assert.deepEqual([crear.accion, crear.datos.nombre, crear.datos.precio], ["publicar", "Chaleco de lana", 15000]);
  assert.match(crear.texto, /antes de eso no se guarda nada/);
});

test("búsqueda para quien compra y temas propios de la web", () => {
  const r = A.interpretar("busco chalecos de lana");
  assert.equal(r.intencion, "BUSCAR_PRODUCTO");
  assert.equal(r.datos.busqueda, "chalecos de lana");
  assert.equal(A.interpretar("¿Cómo compro un producto?").tema, "COMPRAR");
  const ayuda = A.responder("¿cómo registro una venta?", false);
  assert.match(ayuda.texto, /panel de la emprendedora/);
});
