#!/usr/bin/env node
"use strict";
/**
 * Entrega 8 · Prepara la vitrina web para Firebase Hosting en dist/hosting (fuera de git).
 *
 *   npm run hosting:preparar                       # usa js/config.js local o las variables de entorno
 *   npm run hosting:preparar -- --apk "<ruta.apk>"  # además publica ese APK como descarga de la web
 *   node scripts/preparar-hosting.js --destino <carpeta>   # otra carpeta (lo usan las pruebas, para no tocar dist/hosting)
 *
 * - Copia «LA PAgina (segun)/proyectode10» tal cual (la misma web de GitHub Pages).
 * - Escribe js/config.js con la URL y la clave PÚBLICA (anon) de Supabase, desde
 *   SUPABASE_URL/SUPABASE_ANON_KEY o desde el js/config.js local. Rechaza claves secretas.
 * - Con --apk, el APK debe pasar scripts/verificar-apk.js (firmado con la clave de entrega, no
 *   depurable, sin el proveedor de depuración de App Check). Si no, no prepara nada.
 *   Escribe apk-sha256.txt para comparar el APK descargado con el instalado.
 * - No sube nada: la publicación la hace la persona con `npx firebase-tools deploy`.
 * Nunca imprime la clave.
 */
const fs = require("fs");
const path = require("path");
function argumento(nombre) { const i = process.argv.indexOf(nombre); return i >= 0 ? process.argv[i + 1] : undefined; }

const RAIZ = path.resolve(__dirname, "..");
const ORIGEN = path.join(RAIZ, "LA PAgina (segun)", "proyectode10");
const DESTINO = path.resolve(argumento("--destino") || path.join(RAIZ, "dist", "hosting"));
const NOMBRE_APK = "app-debug.apk";   // nombre que enlaza index.html (se descarga como AlerceEmprende.apk)

function fallar(mensaje) { console.error("No se preparó Hosting: " + mensaje); process.exit(1); }

/** Valida la configuración pública de Supabase (misma regla que el workflow de GitHub Pages). */
function validarSupabase(url, clave) {
  let host = "";
  try { const u = new URL(url); if (u.protocol !== "https:") throw 0; host = u.hostname; } catch (e) { fallar("SUPABASE_URL no es una URL https válida."); }
  if (/^sb_secret_/.test(clave)) fallar("la clave parece secreta. Usa solo la clave pública anon.");
  const esJwt = /^eyJ[\w-]+\.[\w-]+\.[\w-]+$/.test(clave);
  if (!esJwt && !/^sb_publishable_[\w-]+$/.test(clave)) fallar("la clave no tiene formato de clave pública (anon o sb_publishable_).");
  if (esJwt) {
    let rol = "";
    try { rol = JSON.parse(Buffer.from(clave.split(".")[1], "base64url").toString("utf8")).role || ""; } catch (e) { /* sin rol */ }
    if (rol && rol !== "anon") fallar("la clave no es la anon (rol distinto). Nunca uses service_role en la web.");
  }
  return host;
}

function leerConfigLocal() {
  const archivo = path.join(ORIGEN, "js", "config.js");
  if (!fs.existsSync(archivo)) return null;
  const texto = fs.readFileSync(archivo, "utf8");
  const m = texto.match(/window\.__CONFIG__\s*=\s*(\{[\s\S]*\})\s*;?\s*$/);
  if (!m) fallar("js/config.js local no tiene el formato window.__CONFIG__ = {...}.");
  try { return JSON.parse(m[1]); } catch (e) { fallar("js/config.js local no es JSON válido."); }
}

function copiar(origen, destino) {
  const st = fs.statSync(origen);
  if (st.isDirectory()) {
    fs.mkdirSync(destino, { recursive: true });
    for (const nombre of fs.readdirSync(origen)) copiar(path.join(origen, nombre), path.join(destino, nombre));
  } else {
    fs.copyFileSync(origen, destino);
  }
}

function main() {
  if (!fs.existsSync(ORIGEN)) fallar("no existe la carpeta de la web.");
  let cfg;
  const url = (process.env.SUPABASE_URL || "").trim(), clave = (process.env.SUPABASE_ANON_KEY || "").trim();
  if (url || clave) {
    if (!url || !clave) fallar("define SUPABASE_URL y SUPABASE_ANON_KEY juntas.");
    cfg = { supabase: { url, anonKey: clave } };
  } else {
    cfg = leerConfigLocal();
    if (!cfg || !cfg.supabase || !cfg.supabase.url || !cfg.supabase.anonKey) {
      fallar("falta la configuración de Supabase: crea js/config.js (ver js/config.ejemplo.js) o define SUPABASE_URL y SUPABASE_ANON_KEY.");
    }
  }
  const host = validarSupabase(cfg.supabase.url, cfg.supabase.anonKey);
  const apk = argumento("--apk");
  let informeApk = null;
  if (apk) {
    if (!fs.existsSync(apk)) fallar("no existe el APK " + apk);
    informeApk = require("./verificar-apk").verificar(apk);
    if (!informeApk.apto) fallar("el APK no es el de distribución:\n  - " + informeApk.problemas.join("\n  - "));
  }
  if (cfg.firebase && /private_key|client_email/.test(JSON.stringify(cfg.firebase))) fallar("la configuración de Firebase contiene una cuenta de servicio.");

  fs.rmSync(DESTINO, { recursive: true, force: true });
  copiar(ORIGEN, DESTINO);
  fs.rmSync(path.join(DESTINO, ".nojekyll"), { force: true });
  fs.writeFileSync(path.join(DESTINO, "js", "config.js"), "window.__CONFIG__ = " + JSON.stringify(cfg) + ";\n");

  if (apk) {
    fs.copyFileSync(apk, path.join(DESTINO, NOMBRE_APK));
    fs.writeFileSync(path.join(DESTINO, "apk-sha256.txt"), informeApk.sha256_archivo + "  AlerceEmprende.apk (" + informeApk.version + ")\n");
    console.log("APK de descarga: " + informeApk.version + " · firmado por " + informeApk.certificado + " · SHA-256 " + informeApk.sha256_archivo);
  } else {
    console.log("APK de descarga: el que ya está en la web (usa --apk <ruta> para publicar el APK firmado).");
  }
  console.log("Supabase: " + host.replace(/^([^.]{3})[^.]*/, "$1***") + (cfg.firebase ? " · push del navegador configurado" : " · sin push del navegador"));
  console.log("Listo en " + path.relative(RAIZ, DESTINO) + ". Publica con: npx firebase-tools deploy --only hosting --project <id-del-proyecto>");
}

main();
