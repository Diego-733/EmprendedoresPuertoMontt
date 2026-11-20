"use strict";
// Reglas puras de la Entrega 7 en la web (js/catalogo.js).
const test = require("node:test");
const assert = require("node:assert");
const Cat = require("../../LA PAgina (segun)/proyectode10/js/catalogo.js");

test("fecha en America/Santiago: límites de día y cambios de horario", () => {
  assert.strictEqual(Cat.fechaSantiago(new Date("2026-11-20T02:59:59Z")), "2026-11-19");
  assert.strictEqual(Cat.fechaSantiago(new Date("2026-11-20T03:00:00Z")), "2026-11-20");
  assert.strictEqual(Cat.fechaSantiago(new Date("2026-09-06T03:59:59Z")), "2026-09-05");
  assert.strictEqual(Cat.fechaSantiago(new Date("2026-09-06T04:00:00Z")), "2026-09-06");
  assert.strictEqual(Cat.fechaSantiago(new Date("2026-04-05T03:30:00Z")), "2026-04-04");
});

test("feria activa: publicada y sin terminar; cancelada, borrador o pasada no", () => {
  const f = (estado, ini, fin) => ({ estado, fechaInicio: ini, fechaFin: fin });
  assert.ok(Cat.feriaActiva(f("PROXIMA", "2026-11-22", "2026-11-23"), "2026-11-23"), "último día de una feria de varios días");
  assert.ok(!Cat.feriaActiva(f("PROXIMA", "2026-11-22", "2026-11-23"), "2026-11-24"));
  assert.ok(!Cat.feriaActiva(f("CANCELADA", "2026-11-30", "2026-11-30"), "2026-11-20"));
  assert.ok(!Cat.feriaActiva(f("BORRADOR", "2026-11-30", "2026-11-30"), "2026-11-20"));
  assert.ok(Cat.feriaActiva(f("EN_CURSO", "2026-11-19", null), "2026-11-19"));
});

test("calendario: solo próximas, agrupadas por mes y en orden", () => {
  const ferias = [
    { id: "b", estado: "PROXIMA", fechaInicio: "2026-12-05", fechaFin: "2026-12-05" },
    { id: "a", estado: "PROXIMA", fechaInicio: "2026-11-22", fechaFin: "2026-11-22" },
    { id: "x", estado: "CANCELADA", fechaInicio: "2026-11-25", fechaFin: "2026-11-25" },
    { id: "p", estado: "PROXIMA", fechaInicio: "2026-11-01", fechaFin: "2026-11-02" }
  ];
  const cal = Cat.calendarioFerias(ferias, "2026-11-19");
  assert.deepStrictEqual(cal.map(g => [g.etiqueta, g.ferias.map(f => f.id)]), [["noviembre de 2026", ["a"]], ["diciembre de 2026", ["b"]]]);
});

test("checklist: búsqueda y filtros por estado, tipo y asociación", () => {
  const filas = [
    { nombre: "Tejidos Austral", rubro: "Artesanía", sector: "Alerce Sur", invitacion: "CONFIRMADA", asociaciones: ["as1", "as2"] },
    { nombre: "Barbería", rubro: "Servicios", sector: "Alerce Norte", invitacion: "RECHAZADA", asociaciones: [] },
    { nombre: "Panadería", rubro: "Alimentos", sector: "Alerce", invitacion: "PENDIENTE", asociaciones: ["as1"] }
  ];
  const nombres = (f) => Cat.filtrarChecklist(filas, f).map(x => x.nombre);
  assert.deepStrictEqual(nombres({ estado: "CONFIRMADA" }), ["Tejidos Austral"]);
  assert.deepStrictEqual(nombres({ tipo: "Servicios" }), ["Barbería"]);
  assert.deepStrictEqual(nombres({ asociacion: "as1" }), ["Tejidos Austral", "Panadería"]);
  assert.deepStrictEqual(nombres({ asociacion: "as2" }), ["Tejidos Austral"], "una tienda en varias asociaciones");
  assert.deepStrictEqual(nombres({ asociacion: "__ninguna" }), ["Barbería"]);
  assert.deepStrictEqual(nombres({ texto: "norte" }), ["Barbería"]);
  assert.strictEqual(Cat.etiquetaInvitacion("AGREGADA"), "Agregada por administración");
  assert.strictEqual(Cat.etiquetaInvitacion("PENDIENTE"), "Invitada");
});

test("muestra de catálogo: excluye ocultos, no disponibles, borradores y piezas vendidas", () => {
  const base = { tiendaId: "t1", publicado: true, disponible: true, modalidad: "REGULAR", manejaStock: true, alAgotarse: "MOSTRAR_AGOTADO", vendidaEn: null, variantes: [{ id: "v", stock: 2 }] };
  const prods = [
    Object.assign({}, base, { id: "ok1" }),
    Object.assign({}, base, { id: "borrador", publicado: false }),
    Object.assign({}, base, { id: "no_disp", disponible: false }),
    Object.assign({}, base, { id: "vendida", modalidad: "PIEZA_UNICA", vendidaEn: "2026-11-01" }),
    Object.assign({}, base, { id: "agotada_oculta", alAgotarse: "OCULTAR", variantes: [{ id: "v", stock: 0 }] }),
    Object.assign({}, base, { id: "otra", tiendaId: "t2" }),
    Object.assign({}, base, { id: "ok2" }), Object.assign({}, base, { id: "ok3" }), Object.assign({}, base, { id: "ok4" })
  ];
  assert.deepStrictEqual(Cat.muestraCatalogo(prods, "t1", 3).map(p => p.id), ["ok1", "ok2", "ok3"]);
});

test("pertenencia a varias asociaciones con respaldo a la asociación única", () => {
  assert.ok(Cat.perteneceA({ asociaciones: ["as1", "as2"], asociacionId: "as1" }, "as2"));
  assert.ok(Cat.perteneceA({ asociaciones: [], asociacionId: "as3" }, "as3"), "servidor sin 07");
  assert.ok(Cat.perteneceA({ asociaciones: [], asociacionId: null }, "__ninguna"));
  assert.ok(!Cat.perteneceA({ asociaciones: ["as1"] }, "__ninguna"));
});

test("acción pendiente al iniciar sesión: válida 15 minutos y solo acciones conocidas", () => {
  const p = Cat.crearAccionPendiente("seguir", "TIENDA", "t1", "Tejidos", 1000);
  assert.ok(Cat.accionPendienteVigente(p, 1000 + 60_000));
  assert.ok(!Cat.accionPendienteVigente(p, 1000 + Cat.VIGENCIA_PENDIENTE_MS + 1), "vencida");
  assert.ok(!Cat.accionPendienteVigente(Object.assign({}, p, { accion: "borrar" }), 2000), "acción desconocida");
  assert.ok(!Cat.accionPendienteVigente(Object.assign({}, p, { tipo: "PRODUCTO" }), 2000));
  assert.ok(!Cat.accionPendienteVigente(null, 2000));
});

test("mensajes de error de la entrega 7 en español", () => {
  assert.match(Cat.mensajeError({ message: "AGREGADA_POR_ADMINISTRACION" }), /administración te agregó/);
  assert.match(Cat.mensajeError({ message: "Could not find the function public.seguir" }), /migración 07/);
  assert.match(Cat.mensajeError({ message: "CALIFICACIONES_DESACTIVADAS" }), /no recibe calificaciones/);
});
