"use strict";
// WhatsApp de cada tienda y pedido del carrito (js/catalogo.js). Datos ficticios; no se envía nada.
const test = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const path = require("path");
const Cat = require("../../LA PAgina (segun)/proyectode10/js/catalogo.js");

const RAIZ = path.resolve(__dirname, "..", "..");
const fmt = (n) => "$" + n;
const tienda = (id, nombre, whatsapp, contactoModo) => ({ id, nombre, whatsapp, contactoModo: contactoModo || "AMBOS" });
const item = (tiendaId, nombre, precio, cantidad, extra) => Object.assign({ tiendaId, nombre, precio, cantidad, varianteNombre: "Única" }, extra || {});

test("número chileno: acepta +56 con espacios, sin +56 y fijos; rechaza vacío, incompleto o extranjero", () => {
  assert.strictEqual(Cat.numeroWhatsapp("+56 9 1234 5678"), "56912345678");
  assert.strictEqual(Cat.numeroWhatsapp("56912345678"), "56912345678");
  assert.strictEqual(Cat.numeroWhatsapp("9 1234 5678"), "56912345678");
  assert.strictEqual(Cat.numeroWhatsapp("+56 65 212 3456"), "56652123456");
  assert.strictEqual(Cat.formatearWhatsapp("912345678"), "+56 9 1234 5678");
  for (const malo of [null, undefined, "", "  ", "12345678", "+56 9 1234 567", "+54 9 11 1234 5678", "+56 1 2345 6789", "sin número"]) {
    assert.strictEqual(Cat.numeroWhatsapp(malo), null, "debía rechazar " + malo);
  }
});

test("tienda con número: enlace a ESE número; sin número o inválido: sin enlace y con aviso", () => {
  assert.strictEqual(Cat.enlaceWhatsapp("+56 9 8765 1111", "Hola"), "https://wa.me/56987651111?text=Hola");
  assert.strictEqual(Cat.enlaceWhatsapp("", "Hola"), null, "sin número de respaldo");
  assert.deepStrictEqual(Cat.viasContacto(tienda("a", "A", "+56 9 8765 1111")), { whatsapp: true, faltaWhatsapp: false, formulario: true });
  assert.deepStrictEqual(Cat.viasContacto(tienda("b", "B", "")), { whatsapp: false, faltaWhatsapp: true, formulario: true });
  assert.deepStrictEqual(Cat.viasContacto(tienda("c", "C", "1234", "WHATSAPP")), { whatsapp: false, faltaWhatsapp: true, formulario: false });
  assert.deepStrictEqual(Cat.viasContacto(tienda("d", "D", "+56 9 8765 1111", "FORMULARIO")), { whatsapp: false, faltaWhatsapp: false, formulario: true },
    "respeta la preferencia: solo formulario aunque tenga número");
  assert.strictEqual(Cat.SIN_WHATSAPP, "Esta tienda aún no tiene un WhatsApp de contacto");
});

test("carrito con dos tiendas: cada destinataria recibe solo sus productos, a su número", () => {
  const tiendas = [tienda("t_tej", "Tejidos Austral", "+56 9 1111 2222"), tienda("t_pan", "Panadería Rosa", "+56 9 3333 4444")];
  const carrito = [item("t_tej", "Chal", 15000, 1), item("t_pan", "Pan amasado", 2500, 2), item("t_tej", "Gorro", 8000, 2, { varianteNombre: "Rojo" })];
  const grupos = Cat.agruparPedido(carrito, tiendas);
  assert.deepStrictEqual(grupos.map(g => [g.tiendaId, g.numero, g.items.map(i => i.nombre), g.total]),
    [["t_tej", "56911112222", ["Chal", "Gorro"], 31000], ["t_pan", "56933334444", ["Pan amasado"], 5000]]);
  const textoTej = Cat.textoPedido(grupos[0], { formatear: fmt, medioPago: "Efectivo" });
  assert.match(textoTej, /^¡Hola Tejidos Austral!/);
  assert.match(textoTej, /1 x Chal — \$15000/);
  assert.match(textoTej, /2 x Gorro \(Rojo\) — \$16000/);
  assert.match(textoTej, /Total: \$31000/);
  assert.ok(!/Pan amasado|Panadería/.test(textoTej), "el pedido de Tejidos no incluye productos de otra tienda");
  assert.ok(!/Chal|Gorro/.test(Cat.textoPedido(grupos[1], { formatear: fmt })));
});

test("número cambiado después de agregar al carrito: se usa el contacto ACTUAL de la tienda (por su ID)", () => {
  const carrito = [item("t_tej", "Chal", 15000, 1, { tiendaNombre: "Tejidos", tiendaWhatsapp: "+56 9 1111 2222" })];   // guardado al agregar
  const ahora = [tienda("t_tej", "Tejidos Austral", "+56 9 5555 6666")];
  assert.strictEqual(Cat.agruparPedido(carrito, ahora)[0].numero, "56955556666");
  const sinNumeroAhora = [tienda("t_tej", "Tejidos Austral", "")];
  const g = Cat.agruparPedido(carrito, sinNumeroAhora)[0];
  assert.deepStrictEqual([g.numero, g.formulario, g.disponible], [null, true, true], "si la dueña lo borró, no se usa el número viejo");
  const tiendaRetirada = Cat.agruparPedido(carrito, [])[0];
  assert.deepStrictEqual([tiendaRetirada.numero, tiendaRetirada.disponible, tiendaRetirada.nombre], [null, false, "Tejidos"]);
});

test("el número fijo de dueño desconocido ya no aparece en la web ni en la app", () => {
  const archivos = ["LA PAgina (segun)/proyectode10/index.html", "LA PAgina (segun)/proyectode10/js/main.js",
    "LA PAgina (segun)/proyectode10/js/vitrina6.js", "LA PAgina (segun)/proyectode10/js/catalogo.js",
    "App para el celular/app/src/main/java/com/example/trabajodea10/ui/publico/AyudaScreen.kt",
    "App para el celular/app/src/main/java/com/example/trabajodea10/ui/emprendedora/TiendaScreen.kt",
    "App para el celular/app/src/main/java/com/example/trabajodea10/data/Models.kt"];
  for (const rel of archivos) {
    const texto = fs.readFileSync(path.join(RAIZ, rel), "utf8");
    assert.ok(!/6123[\s.-]?0000|56961230000/.test(texto), rel + " todavía contiene el número desconocido");
    assert.ok(!/ifBlank\s*\{\s*"\+56/.test(texto), rel + " no debe reemplazar un número vacío por uno inventado");
    assert.ok(!/whatsapp: String = "\+56/.test(texto), rel + " no debe traer un número inventado por defecto");
  }
});
