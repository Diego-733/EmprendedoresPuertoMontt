"use strict";
/**
 * Servicio de envío de avisos (Entrega 7). Corre en un servidor o en GitHub Actions,
 * nunca en la app ni en la web pública.
 *
 *  1. Entra a Supabase con una cuenta propia que el administrador registró en la
 *     tabla `notificadores` (no usa la service_role).
 *  2. Pide un lote con `avisos_por_enviar()`: el servidor ya descartó lo que no
 *     corresponde (sin consentimiento, dejó de seguir, fuera de horario).
 *  3. Envía cada aviso por Firebase Cloud Messaging (API HTTP v1) a los
 *     dispositivos Android y navegadores de esa cuenta.
 *  4. Informa con `resultado_envio()` qué dispositivos aceptó FCM, cuáles fallaron
 *     temporalmente (el servidor los reintenta) y cuáles son inválidos (se desactivan).
 *     ENVIADO = FCM lo aceptó en al menos un dispositivo: NO prueba que se haya visto.
 *
 * Sin dependencias: usa fetch y crypto de Node 18+.
 */
const crypto = require("crypto");

const ALCANCE_FCM = "https://www.googleapis.com/auth/firebase.messaging";
const URL_TOKEN_GOOGLE = "https://oauth2.googleapis.com/token";

function base64url(entrada) {
  return Buffer.from(entrada).toString("base64").replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
}

/** JWT firmado con la cuenta de servicio de Firebase (RS256) para pedir el token de acceso. */
function firmarJwt(cuenta, ahoraSeg) {
  const cabecera = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const cuerpo = base64url(JSON.stringify({
    iss: cuenta.client_email, scope: ALCANCE_FCM, aud: URL_TOKEN_GOOGLE, iat: ahoraSeg, exp: ahoraSeg + 3600
  }));
  const firma = crypto.createSign("RSA-SHA256").update(cabecera + "." + cuerpo).sign(cuenta.private_key);
  return cabecera + "." + cuerpo + "." + base64url(firma);
}

/**
 * Clasifica la respuesta de FCM (API HTTP v1, ErrorCode) para UN token:
 *  - ACEPTADO: FCM aceptó el mensaje (no prueba que se haya mostrado).
 *  - TOKEN_INVALIDO: UNREGISTERED (404), SENDER_ID_MISMATCH (403) o un INVALID_ARGUMENT
 *    que señala el campo token: el dispositivo se desactiva.
 *  - MENSAJE_RECHAZADO: INVALID_ARGUMENT por contenido, tamaño o formato del mensaje.
 *    El dispositivo sigue activo y no se reintenta (fallaría igual).
 *  - FALLA_TEMPORAL: QUOTA_EXCEEDED (429), UNAVAILABLE/INTERNAL (5xx), autenticación
 *    del servidor (401) u otra: se reintenta con espera.
 */
function clasificarRespuestaFcm(status, cuerpo) {
  if (status >= 200 && status < 300) return "ACEPTADO";
  const error = (cuerpo || {}).error || {};
  const detalles = Array.isArray(error.details) ? error.details : [];
  const codigo = detalles.map(d => d && d.errorCode).find(Boolean) || "";
  const estado = error.status || "";
  if (status === 404 || codigo === "UNREGISTERED") return "TOKEN_INVALIDO";
  if (status === 403 && codigo === "SENDER_ID_MISMATCH") return "TOKEN_INVALIDO";
  if (status === 400 || codigo === "INVALID_ARGUMENT" || estado === "INVALID_ARGUMENT") {
    const campos = detalles.flatMap(d => (d && Array.isArray(d.fieldViolations)) ? d.fieldViolations.map(v => String(v.field || "")) : []);
    const senalaToken = campos.some(c => /(^|.)token$/i.test(c)) || /registration token/i.test(String(error.message || ""));
    return senalaToken ? "TOKEN_INVALIDO" : "MENSAJE_RECHAZADO";
  }
  return "FALLA_TEMPORAL";
}

/** Mensaje FCM v1 (notificación visible + datos para abrir la pantalla correcta). */
function mensajeFcm(aviso, destino, urlWeb) {
  const enlace = aviso.enlace ? (urlWeb.replace(/\/?$/, "/") + aviso.enlace.replace(/^\//, "")) : urlWeb;
  const datos = { aviso_id: String(aviso.id), tipo: String(aviso.tipo), feria_id: String(aviso.feria_id || ""), enlace };
  const base = { token: destino.token, notification: { title: aviso.titulo, body: aviso.cuerpo || "" }, data: datos };
  if (destino.plataforma === "ANDROID") {
    base.android = { priority: "HIGH", collapse_key: String(aviso.id), notification: { tag: String(aviso.id), channel_id: "avisos" } };
  } else {
    base.webpush = { fcm_options: { link: enlace }, notification: { tag: String(aviso.id), icon: urlWeb.replace(/\/?$/, "/") + "favicon.ico" } };
  }
  return { message: base };
}

// ---------- Diagnóstico de rechazos (sin cuerpos, credenciales, tokens ni cabeceras) ----------
// Códigos que devuelve Supabase Auth (GoTrue): «error_code» en versiones nuevas y «error»
// (estilo OAuth) en las antiguas. Solo se registra un código si está en esta lista.
const CODIGOS_AUTH = new Set([
  "invalid_credentials", "email_not_confirmed", "phone_not_confirmed", "user_not_found", "user_banned",
  "over_request_rate_limit", "over_email_send_rate_limit", "validation_failed", "bad_json", "bad_jwt",
  "email_provider_disabled", "provider_disabled", "signup_disabled", "captcha_failed", "unexpected_failure",
  "request_timeout", "no_authorization", "not_admin", "session_not_found", "user_sso_managed",
  "invalid_grant", "invalid_request", "unauthorized_client", "unsupported_grant_type", "access_denied"
]);
// Mensajes de la puerta de enlace (Kong) de Supabase sobre la clave pública: se traducen a un código fijo.
const MENSAJES_KONG = {
  "invalid api key": "kong_apikey_invalida",
  "no api key found in request": "kong_sin_apikey",
  "invalid authentication credentials": "kong_credenciales_invalidas",
  "api rate limit exceeded": "kong_limite_de_peticiones"
};

/**
 * Describe un rechazo sin copiar el cuerpo. Devuelve { origen, formato, codigo, detalle }:
 *   origen "supabase": respondió Supabase (Auth, Kong o PostgREST) con un código reconocido.
 *   origen "servidor": llegó HTML, es decir, una página del servidor o de un intermediario
 *                      (firewall, proxy, filtro por país o IP) antes de llegar a Supabase.
 *   origen "desconocido": JSON sin código reconocido, texto plano o respuesta vacía.
 */
function describirRechazo(tipoContenido, texto) {
  const t = String(texto || "");
  if (!t.trim()) return { origen: "desconocido", formato: "vacío", codigo: null, detalle: "respuesta vacía" };
  if (/html/i.test(tipoContenido || "") || /^\s*(<!doctype\s+html|<html|<head|<body|<title)/i.test(t)) {
    return { origen: "servidor", formato: "HTML", codigo: null,
      detalle: "respuesta HTML: la rechazó el servidor que recibe la petición, no Supabase" };
  }
  let c;
  try { c = JSON.parse(t); } catch (e) {
    return { origen: "desconocido", formato: "texto", codigo: null, detalle: "respuesta que no es JSON ni HTML" };
  }
  if (!c || typeof c !== "object") return { origen: "desconocido", formato: "JSON", codigo: null, detalle: "JSON sin código de error reconocido" };
  for (const campo of ["error_code", "error", "code"]) {
    if (typeof c[campo] === "string" && CODIGOS_AUTH.has(c[campo])) {
      return { origen: "supabase", formato: "JSON", codigo: c[campo], detalle: "Supabase Auth rechazó la petición (código " + c[campo] + ")" };
    }
  }
  const kong = typeof c.message === "string" && MENSAJES_KONG[c.message.trim().toLowerCase()];
  if (kong) return { origen: "supabase", formato: "JSON", codigo: kong, detalle: "la puerta de enlace de Supabase rechazó la petición (código " + kong + ")" };
  if (typeof c.code === "string" && /^(PGRST\d{3}|[0-9A-Z]{5})$/.test(c.code)) {
    // PostgREST: código SQLSTATE o PGRST; el mensaje solo si es uno de nuestros códigos (p. ej. SIN_SESION).
    const propio = typeof c.message === "string" && /^[A-Z][A-Z_]{2,39}$/.test(c.message) ? " " + c.message : "";
    return { origen: "supabase", formato: "JSON", codigo: c.code + propio, detalle: "PostgREST " + c.code + propio };
  }
  return { origen: "desconocido", formato: "JSON", codigo: null, detalle: "JSON sin código de error reconocido" };
}

function tipoDeContenido(r) {
  try { return (r.headers && typeof r.headers.get === "function" && r.headers.get("content-type")) || ""; } catch (e) { return ""; }
}

function crearNotificador(config, deps = {}) {
  const pedir = deps.fetch || globalThis.fetch;
  const ahora = deps.ahora || (() => Date.now());
  const registrar = deps.log || ((...a) => console.log(...a));
  let sesion = null;           // { access_token, expira }
  let tokenGoogle = null;      // { valor, expira }

  async function json(r) { const t = await r.text(); try { return t ? JSON.parse(t) : null; } catch (e) { return { texto: t }; } }

  async function entrar() {
    if (sesion && sesion.expira > ahora() + 60000) return sesion.access_token;
    let r;
    try {
      r = await pedir(config.supabaseUrl + "/auth/v1/token?grant_type=password", {
        method: "POST", headers: { apikey: config.anonKey, "Content-Type": "application/json" },
        body: JSON.stringify({ email: config.email, password: config.clave })
      });
    } catch (e) {
      // Sin respuesta HTTP (DNS, TLS, conexión): solo el código del sistema, si es reconocible.
      const cod = [e && e.cause && e.cause.code, e && e.code].find(x => typeof x === "string" && /^[A-Z][A-Z0-9_]{1,40}$/.test(x));
      const err = new Error("No se pudo conectar con Supabase para iniciar sesión (" + (cod || "sin respuesta HTTP") + ")");
      err.diagnostico = { origen: "red", formato: null, codigo: cod || null, status: null };
      throw err;
    }
    const texto = await r.text();
    let c = null;
    try { c = texto ? JSON.parse(texto) : null; } catch (e) { c = null; }
    if (!r.ok || !c || !c.access_token) {
      const d = describirRechazo(tipoDeContenido(r), r.ok ? "" : texto);
      if (r.ok) Object.assign(d, { origen: "desconocido", detalle: "la respuesta no trae una sesión" });
      const err = new Error("No se pudo entrar con la cuenta del notificador (HTTP " + r.status + "): " + d.detalle);
      err.diagnostico = Object.assign({ status: r.status }, d);
      throw err;
    }
    sesion = { access_token: c.access_token, expira: ahora() + (Number(c.expires_in) || 3600) * 1000 };
    return sesion.access_token;
  }

  async function rpc(nombre, cuerpo) {
    const token = await entrar();
    const r = await pedir(config.supabaseUrl + "/rest/v1/rpc/" + nombre, {
      method: "POST",
      headers: { apikey: config.anonKey, Authorization: "Bearer " + token, "Content-Type": "application/json" },
      body: JSON.stringify(cuerpo || {})
    });
    const texto = await r.text();
    if (!r.ok) throw new Error(nombre + " respondió HTTP " + r.status + ": " + describirRechazo(tipoDeContenido(r), texto).detalle);
    try { return texto ? JSON.parse(texto) : null; } catch (e) { return { texto }; }
  }

  async function accesoGoogle() {
    if (tokenGoogle && tokenGoogle.expira > ahora() + 60000) return tokenGoogle.valor;
    const jwt = firmarJwt(config.cuentaServicio, Math.floor(ahora() / 1000));
    const r = await pedir(URL_TOKEN_GOOGLE, {
      method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: "grant_type=" + encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer") + "&assertion=" + encodeURIComponent(jwt)
    });
    const c = await json(r);
    if (!r.ok || !c || !c.access_token) throw new Error("Google rechazó la cuenta de servicio (HTTP " + r.status + ")");
    tokenGoogle = { valor: c.access_token, expira: ahora() + (Number(c.expires_in) || 3600) * 1000 };
    return tokenGoogle.valor;
  }

  async function enviarAUnDestino(aviso, destino) {
    const acceso = await accesoGoogle();
    const r = await pedir("https://fcm.googleapis.com/v1/projects/" + encodeURIComponent(config.cuentaServicio.project_id) + "/messages:send", {
      method: "POST", headers: { Authorization: "Bearer " + acceso, "Content-Type": "application/json" },
      body: JSON.stringify(mensajeFcm(aviso, destino, config.urlWeb))
    });
    return clasificarRespuestaFcm(r.status, await json(r));
  }

  /**
   * Procesa un lote. Informa el resultado POR DISPOSITIVO: si un celular aceptó y otro
   * falló temporalmente, el servidor reintenta solo el que falló (sin duplicar). Devuelve
   * un resumen sin datos personales. "aceptados" = FCM los aceptó; no prueba que se vieran.
   */
  async function procesarLote(limite = 50) {
    const lote = await rpc("avisos_por_enviar", { p_limite: limite }) || [];
    const resumen = { tomados: lote.length, enviados: 0, sin_dispositivo: 0, fallidos: 0, por_reintentar: 0 };
    for (const aviso of lote) {
      const destinos = Array.isArray(aviso.tokens) ? aviso.tokens : [];
      const aceptados = [], temporales = [], invalidos = [], rechazados = [];
      for (const d of destinos) {
        let res;
        try { res = await enviarAUnDestino(aviso, d); } catch (e) { res = "FALLA_TEMPORAL"; registrar("Aviso", aviso.id, "falla de red:", e.message); }
        if (res === "ACEPTADO") aceptados.push(d.token);
        else if (res === "TOKEN_INVALIDO") invalidos.push(d.token);
        else if (res === "MENSAJE_RECHAZADO") rechazados.push(d.token);
        else temporales.push(d.token);
      }
      const r = await rpc("resultado_envio", {
        p_id: aviso.id, p_aceptados: aceptados, p_temporales: temporales, p_invalidos: invalidos, p_rechazados: rechazados,
        p_motivo: destinos.length ? "FCM: " + aceptados.length + " aceptados, " + temporales.length + " con falla temporal, " +
          invalidos.length + " tokens inválidos, " + rechazados.length + " mensajes rechazados" : "Sin dispositivos nuevos por enviar"
      }) || {};
      if (r.estado === "ENVIADO") resumen.enviados++;
      else if (r.estado === "PENDIENTE") resumen.por_reintentar++;
      else if (r.estado === "FALLIDO") resumen.fallidos++;
      else if (r.estado === "SIN_DISPOSITIVO") resumen.sin_dispositivo++;
    }
    return resumen;
  }

  /** Llama una RPC que puede no existir (08 sin aplicar): devuelve undefined en vez de fallar. */
  async function rpcOpcional(nombre, cuerpo) {
    try { return await rpc(nombre, cuerpo); }
    catch (e) { if (/respondió HTTP 404/.test(e.message)) return undefined; throw e; }
  }

  /**
   * Modo automático (08): lo usa el workflow que dispara Supabase al guardar un aviso.
   * Vacía la cola; si quedan reintentos programados, espera hasta el próximo (avisando
   * que sigue vivo cada ~30 s y despertando antes si llega algo nuevo) y termina cuando
   * no queda nada o se acaba el tiempo. Sin 08 aplicada, procesa un solo lote.
   */
  async function procesarHastaVaciar(opciones = {}) {
    const dormir = deps.dormir || (ms => new Promise(res => setTimeout(res, ms)));
    const presupuestoMs = opciones.presupuestoMs || 40 * 60000;
    const latidoMs = opciones.latidoMs || 30000;
    const fin = ahora() + presupuestoMs;
    const total = { tomados: 0, enviados: 0, sin_dispositivo: 0, fallidos: 0, por_reintentar: 0, pasadas: 0, esperas: 0, modo: "automatico" };
    const sumar = r => { for (const k of ["tomados", "enviados", "sin_dispositivo", "fallidos", "por_reintentar"]) total[k] += r[k]; total.pasadas++; };

    if (await rpcOpcional("latido_envio") === undefined) {
      sumar(await procesarLote(50));
      total.modo = "un_lote (08 sin aplicar)";
      return total;
    }
    let erroresSeguidos = 0;
    for (;;) {
      let r, c;
      try {
        r = await procesarLote(50);
        sumar(r);
        erroresSeguidos = 0;
        if (r.tomados > 0) continue;
        c = await rpc("cerrar_envio", { p_rendirse: false }) || {};
      } catch (e) {
        // Supabase o la red fallaron un momento: se reintenta dentro de esta misma ejecución.
        erroresSeguidos++;
        registrar("Falla temporal del emisor (" + erroresSeguidos + "):", e.message);
        if (erroresSeguidos >= 5 || ahora() + 30000 > fin) throw e;
        await dormir(Math.min(30000 * erroresSeguidos, 120000));
        continue;
      }
      if (!c.siguiente) break;
      let esperaMs = Math.max(1000, Number(c.espera_seg || 0) * 1000);
      if (ahora() + esperaMs > fin) { await rpc("cerrar_envio", { p_rendirse: true }); total.sin_tiempo = true; break; }
      total.esperas++;
      // Espera por tramos: cada latido avisa que sigue vivo y pregunta si ya hay algo listo.
      while (esperaMs > 0) {
        const tramo = Math.min(esperaMs, latidoMs);
        await dormir(tramo);
        esperaMs -= tramo;
        const l = await rpc("latido_envio") || {};
        if (l.listos) break;
      }
    }
    return total;
  }

  return { procesarLote, procesarHastaVaciar, entrar, _interno: { firmarJwt, mensajeFcm, clasificarRespuestaFcm } };
}

module.exports = { crearNotificador, firmarJwt, mensajeFcm, clasificarRespuestaFcm, describirRechazo };
