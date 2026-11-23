"use strict";
// Servicio de envío de avisos (servicios/notificador) con fetch sustituto: no toca
// Supabase ni Firebase reales. Comprueba el contrato con avisos_por_enviar/resultado_envio.
const test = require("node:test");
const assert = require("node:assert");
const crypto = require("crypto");
const { crearNotificador, clasificarRespuestaFcm, mensajeFcm, firmarJwt } = require("../../servicios/notificador/notificador");

const { privateKey, publicKey } = crypto.generateKeyPairSync("rsa", { modulusLength: 2048 });
const CUENTA = { project_id: "proyecto-prueba", client_email: "envio@proyecto-prueba.iam.gserviceaccount.com",
  private_key: privateKey.export({ type: "pkcs8", format: "pem" }) };
const CONFIG = { supabaseUrl: "https://falso.supabase.co", anonKey: "clave-publica", email: "notificador@alerce.app",
  clave: "secreta", urlWeb: "https://ejemplo.github.io/vitrina/", cuentaServicio: CUENTA };

function respuesta(status, cuerpo) {
  return { ok: status >= 200 && status < 300, status, text: async () => JSON.stringify(cuerpo) };
}

function fetchFalso(lote, fcm) {
  const llamadas = [];
  const f = async (url, op) => {
    const cuerpo = op && op.body && op.headers && /json/.test(op.headers["Content-Type"] || "") ? JSON.parse(op.body) : op && op.body;
    llamadas.push({ url, cuerpo, headers: op.headers });
    if (url.includes("/auth/v1/token")) return respuesta(200, { access_token: "jwt-notificador", expires_in: 3600 });
    if (url.includes("oauth2.googleapis.com")) return respuesta(200, { access_token: "acceso-google", expires_in: 3600 });
    if (url.endsWith("/rpc/avisos_por_enviar")) return respuesta(200, lote);
    // Igual que el servidor: falla temporal → PENDIENTE; algún aceptado → ENVIADO; si no, SIN_DISPOSITIVO.
    if (url.endsWith("/rpc/resultado_envio")) return respuesta(200, { actualizado: true,
      estado: cuerpo.p_temporales.length ? "PENDIENTE" : (cuerpo.p_aceptados.length ? "ENVIADO" : ((cuerpo.p_rechazados || []).length ? "FALLIDO" : "SIN_DISPOSITIVO")) });
    if (url.includes("fcm.googleapis.com")) return fcm(cuerpo.message.token);
    throw new Error("URL inesperada " + url);
  };
  return { f, llamadas };
}

const AVISO = { id: "a1", usuario_id: "u1", tipo: "PARTICIPACION_SEGUIDA", titulo: "Tejidos estará en Feria de Alerce",
  cuerpo: "Feria del 22-11-2026.", feria_id: "f1", enlace: "?feria=f1", intentos: 1 };

test("aviso aceptado por FCM queda ENVIADO (no significa recibido) y usa la cuenta propia, no service_role", async () => {
  const { f, llamadas } = fetchFalso([Object.assign({}, AVISO, { tokens: [{ token: "t-android", plataforma: "ANDROID" }] })],
    () => respuesta(200, { name: "projects/p/messages/1" }));
  const r = await crearNotificador(CONFIG, { fetch: f, log: () => {} }).procesarLote();
  assert.deepStrictEqual(r, { tomados: 1, enviados: 1, sin_dispositivo: 0, fallidos: 0, por_reintentar: 0 });
  const resultado = llamadas.find(l => l.url.endsWith("/rpc/resultado_envio")).cuerpo;
  assert.deepStrictEqual([resultado.p_aceptados, resultado.p_temporales, resultado.p_invalidos], [["t-android"], [], []]);
  const rpc = llamadas.find(l => l.url.endsWith("/rpc/avisos_por_enviar"));
  assert.strictEqual(rpc.headers.Authorization, "Bearer jwt-notificador");
  const envio = llamadas.find(l => l.url.includes("fcm.googleapis.com"));
  assert.match(envio.url, /projects\/proyecto-prueba\/messages:send$/);
  assert.strictEqual(envio.cuerpo.message.notification.title, "Tejidos estará en Feria de Alerce");
  assert.strictEqual(envio.cuerpo.message.data.enlace, "https://ejemplo.github.io/vitrina/?feria=f1");
  assert.strictEqual(envio.cuerpo.message.android.notification.tag, "a1", "el tag evita mostrar dos veces el mismo aviso");
});

test("sin dispositivos: no llama a FCM y el servidor decide (SIN_DISPOSITIVO o ENVIADO si otro ya lo aceptó)", async () => {
  const { f, llamadas } = fetchFalso([Object.assign({}, AVISO, { tokens: [] })], () => { throw new Error("no debía enviar"); });
  const r = await crearNotificador(CONFIG, { fetch: f, log: () => {} }).procesarLote();
  assert.strictEqual(r.sin_dispositivo, 1);
  assert.strictEqual(llamadas.filter(l => l.url.includes("fcm")).length, 0);
  assert.deepStrictEqual(llamadas.find(l => l.url.endsWith("/rpc/resultado_envio")).cuerpo.p_aceptados, []);
});

test("token no registrado se informa para desactivarlo; otro token del mismo aviso sí se envía", async () => {
  const { f, llamadas } = fetchFalso([Object.assign({}, AVISO, { tokens: [{ token: "viejo", plataforma: "ANDROID" }, { token: "web", plataforma: "WEB" }] })],
    (token) => token === "viejo" ? respuesta(404, { error: { status: "NOT_FOUND", details: [{ errorCode: "UNREGISTERED" }] } }) : respuesta(200, {}));
  await crearNotificador(CONFIG, { fetch: f, log: () => {} }).procesarLote();
  const resultado = llamadas.find(l => l.url.endsWith("/rpc/resultado_envio")).cuerpo;
  assert.deepStrictEqual(resultado.p_aceptados, ["web"]);
  assert.deepStrictEqual(resultado.p_invalidos, ["viejo"]);
  const web = llamadas.find(l => l.url.includes("fcm") && l.cuerpo.message.token === "web").cuerpo.message;
  assert.strictEqual(web.webpush.fcm_options.link, "https://ejemplo.github.io/vitrina/?feria=f1");
});

test("falla temporal de FCM o de red queda FALLIDO para reintento controlado por el servidor", async () => {
  const { f, llamadas } = fetchFalso([Object.assign({}, AVISO, { tokens: [{ token: "t", plataforma: "ANDROID" }] })],
    () => respuesta(503, { error: { status: "UNAVAILABLE" } }));
  const r = await crearNotificador(CONFIG, { fetch: f, log: () => {} }).procesarLote();
  assert.strictEqual(r.por_reintentar, 1);
  assert.deepStrictEqual(llamadas.find(l => l.url.endsWith("/rpc/resultado_envio")).cuerpo.p_temporales, ["t"]);
});

test("dos dispositivos: uno acepta y el otro falla temporalmente → se informa por separado (el servidor reintenta solo el que falló)", async () => {
  const { f, llamadas } = fetchFalso([Object.assign({}, AVISO, { tokens: [{ token: "celular", plataforma: "ANDROID" }, { token: "tablet", plataforma: "ANDROID" }] })],
    (token) => token === "celular" ? respuesta(200, {}) : respuesta(503, { error: { status: "UNAVAILABLE" } }));
  const r = await crearNotificador(CONFIG, { fetch: f, log: () => {} }).procesarLote();
  const resultado = llamadas.find(l => l.url.endsWith("/rpc/resultado_envio")).cuerpo;
  assert.deepStrictEqual([resultado.p_aceptados, resultado.p_temporales], [["celular"], ["tablet"]]);
  assert.strictEqual(r.por_reintentar, 1, "no se da por terminado mientras falte el otro dispositivo");
});

test("clasificación de respuestas de FCM (documentación ErrorCode v1)", () => {
  assert.strictEqual(clasificarRespuestaFcm(200, {}), "ACEPTADO");
  assert.strictEqual(clasificarRespuestaFcm(404, { error: { details: [{ errorCode: "UNREGISTERED" }] } }), "TOKEN_INVALIDO");
  assert.strictEqual(clasificarRespuestaFcm(400, { error: { message: "The registration token is not a valid FCM registration token", status: "INVALID_ARGUMENT" } }), "TOKEN_INVALIDO");
  assert.strictEqual(clasificarRespuestaFcm(429, { error: { status: "RESOURCE_EXHAUSTED", details: [{ errorCode: "QUOTA_EXCEEDED" }] } }), "FALLA_TEMPORAL");
  assert.strictEqual(clasificarRespuestaFcm(500, null), "FALLA_TEMPORAL");
});

test("INVALID_ARGUMENT por el contenido del mensaje NO desactiva un dispositivo válido; por el token sí", () => {
  const porToken = { error: { status: "INVALID_ARGUMENT", message: "Request contains an invalid argument.",
    details: [{ "@type": "type.googleapis.com/google.rpc.BadRequest", fieldViolations: [{ field: "message.token", description: "Invalid registration token" }] },
              { "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", errorCode: "INVALID_ARGUMENT" }] } };
  const porContenido = { error: { status: "INVALID_ARGUMENT", message: "Request contains an invalid argument.",
    details: [{ "@type": "type.googleapis.com/google.rpc.BadRequest", fieldViolations: [{ field: "message.android.notification.color", description: "Invalid color" }] },
              { "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", errorCode: "INVALID_ARGUMENT" }] } };
  const demasiadoGrande = { error: { status: "INVALID_ARGUMENT", message: "Message is too big", details: [{ errorCode: "INVALID_ARGUMENT" }] } };
  assert.strictEqual(clasificarRespuestaFcm(400, porToken), "TOKEN_INVALIDO");
  assert.strictEqual(clasificarRespuestaFcm(400, porContenido), "MENSAJE_RECHAZADO");
  assert.strictEqual(clasificarRespuestaFcm(400, demasiadoGrande), "MENSAJE_RECHAZADO");
  assert.strictEqual(clasificarRespuestaFcm(401, { error: { status: "UNAUTHENTICATED", details: [{ errorCode: "THIRD_PARTY_AUTH_ERROR" }] } }), "FALLA_TEMPORAL");
});

test("mensaje rechazado se informa aparte: el token NO va a la lista de inválidos", async () => {
  const { f, llamadas } = fetchFalso([Object.assign({}, AVISO, { tokens: [{ token: "valido", plataforma: "ANDROID" }] })],
    () => respuesta(400, { error: { status: "INVALID_ARGUMENT", message: "Message is too big", details: [{ errorCode: "INVALID_ARGUMENT" }] } }));
  const r = await crearNotificador(CONFIG, { fetch: f, log: () => {} }).procesarLote();
  const resultado = llamadas.find(l => l.url.endsWith("/rpc/resultado_envio")).cuerpo;
  assert.deepStrictEqual([resultado.p_invalidos, resultado.p_rechazados, resultado.p_temporales], [[], ["valido"], []]);
  assert.strictEqual(r.fallidos, 1);
});

test("el JWT de la cuenta de servicio pide solo el alcance de mensajería y está bien firmado", () => {
  const jwt = firmarJwt(CUENTA, 1_800_000_000);
  const [c, p, s] = jwt.split(".");
  const carga = JSON.parse(Buffer.from(p, "base64url").toString());
  assert.strictEqual(carga.scope, "https://www.googleapis.com/auth/firebase.messaging");
  assert.strictEqual(carga.exp - carga.iat, 3600);
  assert.ok(crypto.createVerify("RSA-SHA256").update(c + "." + p).verify(publicKey, Buffer.from(s, "base64url")));
});

test("el mensaje web no incluye datos de otras personas ni tokens ajenos", () => {
  const m = mensajeFcm(AVISO, { token: "tok", plataforma: "WEB" }, "https://x.test/");
  assert.deepStrictEqual(Object.keys(m.message.data).sort(), ["aviso_id", "enlace", "feria_id", "tipo"]);
  assert.strictEqual(m.message.android, undefined);
});

// ---------- Modo automático (08): procesarHastaVaciar ----------
function supabaseGuionado(guion) {
  // guion: { avisos_por_enviar: [respuestas...], cerrar_envio: [...], latido_envio: [...] }; cada respuesta {status, cuerpo}
  const llamadas = [];
  const f = async (url, op) => {
    llamadas.push(url.replace(/^.*\/(rpc\/)?/, ""));
    if (url.includes("/auth/v1/token")) return respuesta(200, { access_token: "jwt", expires_in: 3600 });
    if (url.includes("oauth2.googleapis.com")) return respuesta(200, { access_token: "g", expires_in: 3600 });
    if (url.includes("fcm.googleapis.com")) return respuesta(200, {});
    if (url.endsWith("/rpc/resultado_envio")) return respuesta(200, { estado: "ENVIADO" });
    const nombre = url.split("/rpc/")[1];
    const r = (guion[nombre] || []).shift() || { status: 200, cuerpo: nombre === "avisos_por_enviar" ? [] : {} };
    return respuesta(r.status, r.cuerpo);
  };
  return { f, llamadas };
}
const UNO = [Object.assign({}, AVISO, { tokens: [{ token: "t", plataforma: "ANDROID" }] })];

test("modo automático sin 08 aplicada (latido_envio no existe): procesa un solo lote y termina", async () => {
  const { f, llamadas } = supabaseGuionado({ latido_envio: [{ status: 404, cuerpo: { code: "PGRST202" } }], avisos_por_enviar: [{ status: 200, cuerpo: UNO }] });
  const t = await crearNotificador(CONFIG, { fetch: f, log: () => {}, dormir: async () => { throw new Error("no debía esperar"); } }).procesarHastaVaciar();
  assert.deepStrictEqual([t.modo, t.pasadas, t.enviados], ["un_lote (08 sin aplicar)", 1, 1]);
  assert.strictEqual(llamadas.filter(l => l === "cerrar_envio").length, 0);
});

test("modo automático: vacía la cola, espera el reintento con latidos y despierta antes si ya hay algo listo", async () => {
  let reloj = 0; const esperas = [];
  const { f, llamadas } = supabaseGuionado({
    avisos_por_enviar: [{ status: 200, cuerpo: UNO }, { status: 200, cuerpo: [] }, { status: 200, cuerpo: UNO }, { status: 200, cuerpo: [] }],
    cerrar_envio: [{ status: 200, cuerpo: { siguiente: "2026-11-22T12:02:00Z", espera_seg: 120 } }, { status: 200, cuerpo: { siguiente: null } }],
    latido_envio: [{ status: 200, cuerpo: { listos: false } }, { status: 200, cuerpo: { listos: true } }]   // arranque; latido tras el 1.er tramo
  });
  const t = await crearNotificador(CONFIG, { fetch: f, log: () => {}, ahora: () => reloj, dormir: async ms => { esperas.push(ms); reloj += ms; } })
    .procesarHastaVaciar({ presupuestoMs: 10 * 60000 });
  assert.deepStrictEqual([t.enviados, t.esperas, esperas], [2, 1, [30000]], "un solo tramo de 30 s: el latido dijo que ya había algo listo");
  assert.strictEqual(llamadas.filter(l => l === "cerrar_envio").length, 2);
});

test("modo automático: sin tiempo para el reintento, se apaga avisando al servidor (p_rendirse)", async () => {
  const cuerpos = [];
  const { f } = supabaseGuionado({ cerrar_envio: [{ status: 200, cuerpo: { siguiente: "x", espera_seg: 900 } }, { status: 200, cuerpo: { siguiente: null, quedan: true } }] });
  const espia = async (url, op) => { if (url.endsWith("/rpc/cerrar_envio")) cuerpos.push(JSON.parse(op.body)); return f(url, op); };
  const t = await crearNotificador(CONFIG, { fetch: espia, log: () => {}, ahora: () => 0, dormir: async () => {} }).procesarHastaVaciar({ presupuestoMs: 60000 });
  assert.strictEqual(t.sin_tiempo, true);
  assert.deepStrictEqual(cuerpos, [{ p_rendirse: false }, { p_rendirse: true }]);
});

test("modo automático: una falla momentánea de Supabase se reintenta en la misma ejecución", async () => {
  const esperas = [];
  const { f } = supabaseGuionado({ avisos_por_enviar: [{ status: 503, cuerpo: { message: "no disponible" } }, { status: 200, cuerpo: UNO }] });
  const t = await crearNotificador(CONFIG, { fetch: f, log: () => {}, ahora: () => 0, dormir: async ms => { esperas.push(ms); } }).procesarHastaVaciar();
  assert.deepStrictEqual([t.enviados, esperas], [1, [30000]]);
});

// ---------- Workflow «Enviar avisos»: secretos faltantes ----------
const { spawnSync } = require("child_process");
const path = require("path");
const RAIZ = path.resolve(__dirname, "..", "..");
const CENTINELA = "VALOR-SECRETO-CENTINELA-9876";
function soloPath(extra) { return Object.assign({ PATH: process.env.PATH, SystemRoot: process.env.SystemRoot || "" }, extra); }

test("workflow: si faltan secretos, falla (exit 1) nombrando solo los que faltan y sin mostrar valores", () => {
  const r = spawnSync("bash", [path.join(RAIZ, "servicios", "notificador", "comprobar_secretos.sh")], { encoding: "utf8",
    env: soloPath({ SUPABASE_URL: CENTINELA, NOTIFICADOR_CLAVE: CENTINELA, FIREBASE_CUENTA_SERVICIO: "   " }) });
  const salida = r.stdout + r.stderr;
  assert.strictEqual(r.status, 1);
  assert.match(salida, /::error title=Faltan secretos::Faltan secretos de GitHub Actions: SUPABASE_ANON_KEY, NOTIFICADOR_EMAIL, FIREBASE_CUENTA_SERVICIO\./);
  assert.ok(!salida.includes(CENTINELA), "no muestra valores");
  assert.ok(!/SUPABASE_URL|NOTIFICADOR_CLAVE/.test(salida), "no nombra los que sí están");
});

test("workflow: con los cinco secretos, la comprobación pasa", () => {
  const r = spawnSync("bash", [path.join(RAIZ, "servicios", "notificador", "comprobar_secretos.sh")], { encoding: "utf8",
    env: soloPath({ SUPABASE_URL: "a", SUPABASE_ANON_KEY: "b", NOTIFICADOR_EMAIL: "c", NOTIFICADOR_CLAVE: "d", FIREBASE_CUENTA_SERVICIO: "e" }) });
  assert.strictEqual(r.status, 0);
});

test("emisor en Actions: JSON de Firebase inválido o clave no anon fallan con error visible y sin valores", () => {
  const base = { GITHUB_ACTIONS: "true", SUPABASE_URL: "https://x.test", SUPABASE_ANON_KEY: "sb_publishable_x", NOTIFICADOR_EMAIL: "a@b.test", NOTIFICADOR_CLAVE: CENTINELA };
  const correr = (extra) => spawnSync(process.execPath, [path.join(RAIZ, "servicios", "notificador", "enviar.js"), "--env", path.join(RAIZ, "no-existe.env"), "--hasta-vaciar", "1"],
    { encoding: "utf8", env: soloPath(Object.assign({}, base, extra)) });
  const json = correr({ FIREBASE_CUENTA_SERVICIO: '{"private_key":"' + CENTINELA });
  assert.strictEqual(json.status, 2);
  assert.match(json.stdout, /::error title=Configuración del notificador::La cuenta de servicio de Firebase no es JSON válido/);
  assert.ok(!(json.stdout + json.stderr).includes(CENTINELA));
  const secreta = correr({ SUPABASE_ANON_KEY: "sb_secret_" + CENTINELA, FIREBASE_CUENTA_SERVICIO: JSON.stringify({ client_email: "e", private_key: "k", project_id: "p" }) });
  assert.strictEqual(secreta.status, 2);
  assert.match(secreta.stdout, /no es la clave pública anon/);
  assert.ok(!(secreta.stdout + secreta.stderr).includes(CENTINELA));
});
