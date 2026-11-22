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

function crearNotificador(config, deps = {}) {
  const pedir = deps.fetch || globalThis.fetch;
  const ahora = deps.ahora || (() => Date.now());
  const registrar = deps.log || ((...a) => console.log(...a));
  let sesion = null;           // { access_token, expira }
  let tokenGoogle = null;      // { valor, expira }

  async function json(r) { const t = await r.text(); try { return t ? JSON.parse(t) : null; } catch (e) { return { texto: t }; } }

  async function entrar() {
    if (sesion && sesion.expira > ahora() + 60000) return sesion.access_token;
    const r = await pedir(config.supabaseUrl + "/auth/v1/token?grant_type=password", {
      method: "POST", headers: { apikey: config.anonKey, "Content-Type": "application/json" },
      body: JSON.stringify({ email: config.email, password: config.clave })
    });
    const c = await json(r);
    if (!r.ok || !c || !c.access_token) throw new Error("No se pudo entrar con la cuenta del notificador (HTTP " + r.status + ")");
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
    const c = await json(r);
    if (!r.ok) throw new Error(nombre + " respondió HTTP " + r.status + ": " + ((c && (c.message || c.texto)) || ""));
    return c;
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

  return { procesarLote, entrar, _interno: { firmarJwt, mensajeFcm, clasificarRespuestaFcm } };
}

module.exports = { crearNotificador, firmarJwt, mensajeFcm, clasificarRespuestaFcm };
