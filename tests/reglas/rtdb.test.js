"use strict";
// Entrega 8 (C04) · Reglas de Realtime Database (firebase/database.rules.json) en el EMULADOR de Firebase.
// Solo existe la ruta de comprobación de App Check: escritura de una sola vez con forma fija,
// sin lectura y nada más en la base. Ejecutar con: npm run test:reglas (levanta el emulador).
// Nota: el emulador prueba las REGLAS; App Check exigido solo se comprueba en la consola real.
const test = require("node:test");
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment, assertSucceeds, assertFails } = require("@firebase/rules-unit-testing");

const REGLAS = fs.readFileSync(path.join(__dirname, "..", "..", "firebase", "database.rules.json"), "utf8");
const [host, puerto] = (process.env.FIREBASE_DATABASE_EMULATOR_HOST || "127.0.0.1:9000").split(":");
let entorno;
const valido = () => ({ plataforma: "android", version: "1.6-entrega8", en: Date.now() });

test.before(async () => {
  entorno = await initializeTestEnvironment({ projectId: "demo-alerce", database: { host, port: Number(puerto), rules: REGLAS } });
});
test.after(async () => { if (entorno) await entorno.cleanup(); });
test.beforeEach(async () => { await entorno.clearDatabase(); });

const sinSesion = () => entorno.unauthenticatedContext().database();
const conSesion = () => entorno.authenticatedContext("u-prueba").database();

test("se permite una comprobación válida (sin datos personales), con o sin sesión", async () => {
  await assertSucceeds(sinSesion().ref("comprobaciones_app_check/a1").set(valido()));
  await assertSucceeds(conSesion().ref("comprobaciones_app_check/a2").set(valido()));
});

test("no se puede sobrescribir ni borrar una comprobación existente", async () => {
  await assertSucceeds(sinSesion().ref("comprobaciones_app_check/b1").set(valido()));
  await assertFails(sinSesion().ref("comprobaciones_app_check/b1").set(valido()));
  await assertFails(sinSesion().ref("comprobaciones_app_check/b1").remove());
});

test("la forma se valida: faltan campos, otra plataforma, tipos o campos extra", async () => {
  const db = sinSesion();
  await assertFails(db.ref("comprobaciones_app_check/c1").set({ plataforma: "android", version: "1" }));
  await assertFails(db.ref("comprobaciones_app_check/c2").set(Object.assign(valido(), { plataforma: "ios" })));
  await assertFails(db.ref("comprobaciones_app_check/c3").set(Object.assign(valido(), { en: "ayer" })));
  await assertFails(db.ref("comprobaciones_app_check/c4").set(Object.assign(valido(), { version: "x".repeat(40) })));
  await assertFails(db.ref("comprobaciones_app_check/c5").set(Object.assign(valido(), { correo: "alguien@ejemplo.cl" })));
});

test("nadie lee: ni las comprobaciones ni la raíz", async () => {
  await assertFails(sinSesion().ref("comprobaciones_app_check").get());
  await assertFails(conSesion().ref("comprobaciones_app_check").get());
  await assertFails(sinSesion().ref("/").get());
});

test("no se escribe en ninguna otra ruta de la base", async () => {
  await assertFails(sinSesion().ref("otra/ruta").set({ a: 1 }));
  await assertFails(conSesion().ref("/").set({ a: 1 }));
  await assertFails(sinSesion().ref("comprobaciones_app_check").set({ x: valido() }));
});
