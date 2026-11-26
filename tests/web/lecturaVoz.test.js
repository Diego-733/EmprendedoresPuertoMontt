"use strict";
// Lectura en voz alta de la web (js/lecturaVoz.js): elección de voz, texto para la voz y
// comportamiento sin soporte. Corre en Node, sin navegador.
const test = require("node:test");
const assert = require("node:assert/strict");
const path = require("path");
const L = require(path.join(__dirname, "../../LA PAgina (segun)/proyectode10/js/lecturaVoz.js"));

const voz = (lang) => ({ lang, name: "Voz " + lang });

test("prefiere Chile, luego Latinoamérica y España; nunca otro idioma", () => {
  assert.equal(L.elegirVoz([voz("en-US"), voz("es-ES"), voz("es-CL")]).lang, "es-CL");
  assert.equal(L.elegirVoz([voz("es-ES"), voz("es-MX")]).lang, "es-MX");
  assert.equal(L.elegirVoz([voz("es_US")]).lang, "es_US");
  assert.equal(L.elegirVoz([voz("es-AR")]).lang, "es-AR");
  assert.equal(L.elegirVoz([voz("en-US"), voz("fr-FR")]), null);
  assert.equal(L.elegirVoz([]), null);
});

test("precios en pesos y sin símbolos que la voz deletrearía", () => {
  assert.equal(L.paraLeer("Precio: $15.000"), "Precio: 15.000 pesos");
  assert.equal(L.paraLeer("Precio: $ 15000"), "Precio: 15000 pesos");
  assert.equal(L.paraLeer("Toca «📁 Elegir foto»"), "Toca Elegir foto");
  assert.equal(L.paraLeer("🧭 Paso 1"), "Paso 1");
});

test("sin speechSynthesis: no está disponible y hablar no hace nada", () => {
  assert.equal(L.disponible({}), false);
  const lector = L.crear({});
  assert.equal(lector.disponible, false);
  assert.equal(lector.hablar("x", "hola"), false);
  assert.doesNotThrow(() => lector.detener());
});

test("con voces cargadas pero ninguna en español no lee; con es-CL lee y avisa inicio y fin", () => {
  const dichos = [];
  let pendiente = null;
  const ventana = (voces) => ({
    SpeechSynthesisUtterance: function (t) { this.text = t; },
    speechSynthesis: { getVoices: () => voces.map(voz), speak: (u) => { dichos.push(u); pendiente = u; }, cancel: () => {} }
  });
  assert.equal(L.crear(ventana(["en-US"])).hablar("a", "hola"), false);
  assert.equal(dichos.length, 0);
  const estados = [];
  const lector = L.crear(ventana(["en-US", "es-CL"]));
  assert.equal(lector.hablar("guia", "Cuesta $2.500", (s) => estados.push(s)), true);
  assert.equal(dichos[0].text, "Cuesta 2.500 pesos");
  assert.equal(dichos[0].lang, "es-CL");
  assert.equal(lector.leyendo(), "guia");
  pendiente.onend();
  assert.equal(lector.leyendo(), null);
  assert.deepEqual(estados, [true, false]);
});

test("el aviso tardío de una lectura cortada no apaga la nueva del mismo botón", () => {
  const dichos = [];
  const ventana = {
    SpeechSynthesisUtterance: function (t) { this.text = t; },
    speechSynthesis: { getVoices: () => [voz("es-CL")], speak: (u) => dichos.push(u), cancel: () => {} }
  };
  const estados = [];
  const lector = L.crear(ventana);
  lector.hablar("guia", "Paso uno", (s) => estados.push(s));
  lector.hablar("guia", "Paso dos", (s) => estados.push(s));   // corta el anterior
  dichos[0].onerror();                                         // el navegador avisa tarde el corte
  assert.equal(lector.leyendo(), "guia", "la segunda lectura sigue sonando");
  dichos[1].onend();
  assert.equal(lector.leyendo(), null);
});
