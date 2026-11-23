#!/usr/bin/env node
"use strict";
/**
 * Ejecuta el servicio de envío de avisos.
 *
 *   npm run notificar            # procesa lo pendiente una vez (lo usa el workflow)
 *   npm run notificar:vigilar    # sigue revisando cada 15 s (solo durante la demostración)
 *
 * Opciones: --vigilar [segundos]  ·  --env <archivo> (por defecto servicios/notificador/.env)
 *           --solo-entrar  (diagnóstico: un intento de inicio de sesión y termina; no toca la cola)
 *
 * Variables (en el entorno o en el archivo .env, que está en .gitignore; NUNCA en el
 * repositorio, la app ni la web):
 *   SUPABASE_URL, SUPABASE_ANON_KEY       URL y clave PÚBLICA (anon) del proyecto
 *   NOTIFICADOR_EMAIL, NOTIFICADOR_CLAVE  cuenta registrada en la tabla notificadores
 *   FIREBASE_CUENTA_SERVICIO_ARCHIVO      ruta del JSON de la cuenta de servicio de Firebase
 *     (o FIREBASE_CUENTA_SERVICIO con el JSON completo)
 *   URL_WEB                               dirección pública de la vitrina (para los enlaces)
 */
const fs = require("fs");
const path = require("path");
const { crearNotificador } = require("./notificador");

function argumento(nombre) {
  const i = process.argv.indexOf(nombre);
  return i >= 0 ? process.argv[i + 1] : undefined;
}

/** Lee un archivo KEY=valor sin pisar variables ya definidas en el entorno. */
function cargarEnv(archivo) {
  if (!archivo || !fs.existsSync(archivo)) return false;
  for (const linea of fs.readFileSync(archivo, "utf8").split(/\r?\n/)) {
    const m = linea.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (!m || linea.trim().startsWith("#")) continue;
    const valor = m[2].replace(/^["']|["']$/g, "");
    if (process.env[m[1]] === undefined || process.env[m[1]] === "") process.env[m[1]] = valor;
  }
  return true;
}

/** Termina con un mensaje claro (en GitHub Actions, como error visible). Nunca incluye valores. */
function fallarConfig(mensaje) {
  if (process.env.GITHUB_ACTIONS === "true") console.log("::error title=Configuración del notificador::" + mensaje);
  console.error(mensaje);
  process.exit(2);
}

function leerConfig() {
  const archivoEnv = argumento("--env") || path.join(__dirname, ".env");
  if (cargarEnv(archivoEnv)) console.log("Configuración leída de", path.relative(process.cwd(), archivoEnv) || archivoEnv);
  const faltan = [];
  const env = (n) => { const v = (process.env[n] || "").trim(); if (!v) faltan.push(n); return v; };
  const config = {
    supabaseUrl: env("SUPABASE_URL").replace(/\/$/, ""),
    anonKey: env("SUPABASE_ANON_KEY"),
    email: env("NOTIFICADOR_EMAIL"),
    clave: env("NOTIFICADOR_CLAVE"),
    urlWeb: (process.env.URL_WEB || "https://diego-733.github.io/EmprendedoresPuertoMontt/").trim()
  };
  // Diagnóstico de inicio de sesión (--solo-entrar): no usa Firebase, así que no lo pide.
  const soloEntrar = process.argv.includes("--solo-entrar");
  let cuenta = (process.env.FIREBASE_CUENTA_SERVICIO || "").trim();
  if (!cuenta && process.env.FIREBASE_CUENTA_SERVICIO_ARCHIVO) {
    const ruta = process.env.FIREBASE_CUENTA_SERVICIO_ARCHIVO.trim();
    if (!fs.existsSync(ruta)) faltan.push("FIREBASE_CUENTA_SERVICIO_ARCHIVO (no existe " + ruta + ")");
    else cuenta = fs.readFileSync(ruta, "utf8");
  }
  if (!soloEntrar && !cuenta && !faltan.some(f => f.startsWith("FIREBASE"))) faltan.push("FIREBASE_CUENTA_SERVICIO_ARCHIVO o FIREBASE_CUENTA_SERVICIO");
  if (faltan.length) {
    fallarConfig("Faltan variables: " + faltan.join(", ") + ". Ver docs/entrega7/ENVIO_AUTOMATICO.md (paso B).");
  }
  if (!soloEntrar) {
    try { config.cuentaServicio = JSON.parse(cuenta); } catch (e) { fallarConfig("La cuenta de servicio de Firebase no es JSON válido (pega el contenido COMPLETO del archivo)."); }
    if (!config.cuentaServicio.client_email || !config.cuentaServicio.private_key || !config.cuentaServicio.project_id) {
      fallarConfig("La cuenta de servicio no tiene client_email, private_key y project_id.");
    }
  }
  // Defensa: este servicio usa la clave pública y su propia cuenta, nunca la service_role.
  let rol = "";
  try { rol = JSON.parse(Buffer.from(config.anonKey.split(".")[1], "base64url").toString()).role || ""; } catch (e) { /* clave sb_publishable_ */ }
  if (/^sb_secret_/.test(config.anonKey) || (rol && rol !== "anon")) {
    fallarConfig("SUPABASE_ANON_KEY no es la clave pública anon. No se usa la service_role.");
  }
  return config;
}

/**
 * --solo-entrar: hace UN intento de inicio de sesión con la cuenta del notificador y termina.
 * No lee ni procesa la cola, no llama a ninguna RPC ni a Firebase. Muestra solo el resultado
 * (HTTP, formato y código reconocido), nunca el cuerpo, la clave, el token ni las cabeceras.
 */
async function diagnosticarInicioDeSesion(n) {
  const enActions = process.env.GITHUB_ACTIONS === "true";
  try {
    await n.entrar();
    console.log("Diagnóstico: la cuenta del notificador inició sesión correctamente desde aquí.");
    console.log("No se leyó ni se procesó la cola de avisos.");
    return 0;
  } catch (e) {
    const d = e.diagnostico || { origen: "desconocido" };
    const conclusion = {
      servidor: "Bloqueo del servidor que recibe la petición (firewall, proxy o filtro por IP o país): respondió una página HTML, no Supabase. La contraseña no alcanzó a revisarse.",
      supabase: "Supabase recibió la petición y la rechazó" + (d.codigo ? " con el código " + d.codigo : "") + ".",
      red: "No hubo respuesta HTTP: la conexión no se completó.",
      desconocido: "La respuesta no permite saber si vino de Supabase o de otro servidor."
    }[d.origen] || "Resultado no reconocido.";
    if (enActions) console.log("::error title=Inicio de sesión del notificador::" + conclusion);
    console.error("Diagnóstico:", e.message);
    console.error("HTTP:", d.status == null ? "sin respuesta" : d.status, "· formato:", d.formato || "ninguno", "· código reconocido:", d.codigo || "ninguno");
    console.error("Conclusión:", conclusion);
    console.error("No se leyó ni se procesó la cola de avisos.");
    return 1;
  }
}

(async () => {
  const config = leerConfig();
  const n = crearNotificador(config);
  if (process.argv.includes("--solo-entrar")) process.exit(await diagnosticarInicioDeSesion(n));
  if (process.argv.includes("--hasta-vaciar")) {
    // Modo automático (lo usa el workflow que dispara Supabase): vacía la cola y espera reintentos.
    const minutos = Math.min(Math.max(1, Number(argumento("--hasta-vaciar")) || 40), 300);
    try {
      const t = await n.procesarHastaVaciar({ presupuestoMs: minutos * 60000 });
      console.log(new Date().toISOString(), "modo", t.modo, "· pasadas", t.pasadas, "· tomados", t.tomados,
        "· aceptados por FCM", t.enviados, "· sin dispositivo", t.sin_dispositivo, "· fallidos", t.fallidos,
        "· reintentos programados", t.por_reintentar, "· esperas", t.esperas, t.sin_tiempo ? "· terminó por tiempo (lo pendiente espera el próximo disparo)" : "");
    } catch (e) {
      console.error(new Date().toISOString(), "Error:", e.message);
      process.exit(1);
    }
    return;
  }
  const vigilar = process.argv.includes("--vigilar");
  const pausaBase = Math.max(5, Number(argumento("--vigilar")) || 30) * 1000;   // demostración: npm run notificar:vigilar (15 s)
  let detener = false, errores = 0;
  const salir = () => { if (detener) process.exit(0); detener = true; console.log("Deteniendo después del lote actual… (Ctrl+C otra vez para salir ya)"); };
  process.on("SIGINT", salir);
  process.on("SIGTERM", salir);
  if (vigilar) console.log("Vigilando la cola cada", pausaBase / 1000, "s. Proyecto FCM:", config.cuentaServicio.project_id);
  do {
    try {
      const r = await n.procesarLote(50);
      errores = 0;
      if (!vigilar || r.tomados) {
        console.log(new Date().toISOString(), "tomados", r.tomados, "· aceptados por FCM", r.enviados,
          "· por reintentar", r.por_reintentar, "· sin dispositivo", r.sin_dispositivo, "· fallidos", r.fallidos);
      }
    } catch (e) {
      errores++;
      console.error(new Date().toISOString(), "Error:", e.message);
      if (!vigilar) process.exit(1);
    }
    if (vigilar && !detener) {
      // Si el servidor o la red fallan seguido, se espera más (hasta 5 minutos).
      const espera = Math.min(pausaBase * Math.pow(2, Math.min(errores, 5)), 300000);
      await new Promise(res => setTimeout(res, espera));
    }
  } while (vigilar && !detener);
})();
