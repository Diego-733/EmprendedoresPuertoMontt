#!/usr/bin/env node
"use strict";
/**
 * Entrega 8 · Verifica que un APK sea el de DISTRIBUCIÓN antes de publicarlo.
 *
 *   node scripts/verificar-apk.js "<ruta.apk>"            # informe y código de salida (0 = apto)
 *   node scripts/verificar-apk.js "<ruta.apk>" --json     # lo mismo, en JSON
 *
 * Rechaza (código 1):
 *  - APK sin firma o con la firma de depuración de Android Studio («CN=Android Debug»);
 *  - APK depurable (android:debuggable);
 *  - APK que contiene el proveedor de depuración de App Check o la ruta de prueba de Firebase;
 *  - paquete distinto de com.company.ultrapp.
 * Muestra la huella SHA-256 del certificado (la que se registra en App Check → Play Integrity)
 * y el SHA-256 del archivo (para comparar el APK instalado con el descargado de Hosting).
 * Usa apksigner y aapt2 del SDK de Android (ANDROID_HOME o %LOCALAPPDATA%\Android\Sdk).
 */
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");
const crypto = require("crypto");
const { spawnSync } = require("child_process");

const PAQUETE = "com.company.ultrapp";

function sdk() {
  const candidatos = [process.env.ANDROID_HOME, process.env.ANDROID_SDK_ROOT,
    process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, "Android", "Sdk")].filter(Boolean);
  return candidatos.find(d => fs.existsSync(path.join(d, "build-tools")));
}

function herramienta(nombre) {
  const base = sdk();
  if (!base) return null;
  const versiones = fs.readdirSync(path.join(base, "build-tools")).sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
  for (const v of versiones) {
    for (const ext of process.platform === "win32" ? [".bat", ".exe", ""] : [""]) {
      const f = path.join(base, "build-tools", v, nombre + ext);
      if (fs.existsSync(f)) return f;
    }
  }
  return null;
}

/** Java para apksigner: JAVA_HOME o el JDK que trae Android Studio. */
function entornoJava() {
  const env = Object.assign({}, process.env);
  if (!env.JAVA_HOME) {
    const jbr = ["C:\\Program Files\\Android\\Android Studio\\jbr", "/Applications/Android Studio.app/Contents/jbr/Contents/Home",
                 "/opt/android-studio/jbr"].find(d => fs.existsSync(d));
    if (jbr) env.JAVA_HOME = jbr;
  }
  return env;
}

function correr(exe, args) {
  // Los .bat necesitan shell; con shell, las rutas con espacios se citan.
  const r = /\.bat$/i.test(exe)
    ? spawnSync('"' + exe + '" ' + args.map(a => '"' + a + '"').join(" "), { encoding: "utf8", shell: true, env: entornoJava() })
    : spawnSync(exe, args, { encoding: "utf8", env: entornoJava() });
  return { codigo: r.status, salida: (r.stdout || "") + (r.stderr || "") };
}

/** Lee las entradas classes*.dex de un APK (zip) sin dependencias. */
function dexDelApk(buf) {
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65557); i--) { if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; } }
  if (eocd < 0) throw new Error("no es un archivo zip/APK válido");
  const total = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const dex = [];
  for (let n = 0; n < total; n++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error("directorio del zip dañado");
    const metodo = buf.readUInt16LE(p + 10), comprimido = buf.readUInt32LE(p + 20);
    const lnom = buf.readUInt16LE(p + 28), lext = buf.readUInt16LE(p + 30), lcom = buf.readUInt16LE(p + 32);
    const local = buf.readUInt32LE(p + 42);
    const nombre = buf.toString("utf8", p + 46, p + 46 + lnom);
    if (/^classes\d*\.dex$/.test(nombre)) {
      const ini = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
      const datos = buf.subarray(ini, ini + comprimido);
      dex.push(metodo === 0 ? datos : zlib.inflateRawSync(datos));
    }
    p += 46 + lnom + lext + lcom;
  }
  return dex;
}

function verificar(ruta) {
  const r = { archivo: path.resolve(ruta), problemas: [], avisos: [] };
  if (!fs.existsSync(ruta)) { r.problemas.push("No existe el archivo."); return r; }
  const buf = fs.readFileSync(ruta);
  r.sha256_archivo = crypto.createHash("sha256").update(buf).digest("hex").toUpperCase();
  r.tamano_mb = Math.round(buf.length / 104857.6) / 10;

  // Contenido: proveedor de depuración de App Check.
  try {
    const dex = dexDelApk(buf);
    const marca = Buffer.from("Lcom/google/firebase/appcheck/debug/");
    r.proveedor_depuracion = dex.some(d => d.indexOf(marca) >= 0);
    if (r.proveedor_depuracion) r.problemas.push("Contiene el proveedor de depuración de App Check (es un APK de prueba, no el público).");
  } catch (e) { r.problemas.push("No se pudo leer el APK: " + e.message); }

  // Firma.
  const apksigner = herramienta("apksigner");
  if (!apksigner) { r.problemas.push("No se encontró apksigner (instala Android SDK Build-Tools)."); }
  else {
    const v = correr(apksigner, ["verify", "--print-certs", "--verbose", ruta]);
    r.firmado = v.codigo === 0 && /Verifies/.test(v.salida);
    if (/JAVA_HOME|no 'java' command/i.test(v.salida)) r.problemas.push("apksigner no encontró Java: define JAVA_HOME con la carpeta jbr de Android Studio.");
    else if (!r.firmado) r.problemas.push("El APK no está firmado (o la firma no es válida). Firma con la clave de entrega: gradlew assembleRelease con keystore.properties.");
    const dn = (v.salida.match(/Signer #1 certificate DN: (.+)/) || [])[1];
    const sha = (v.salida.match(/Signer #1 certificate SHA-256 digest: ([0-9a-f]+)/i) || [])[1];
    r.esquemas = ["v1", "v2", "v3"].filter(x => new RegExp("using " + x + " scheme \\(.*\\): true", "i").test(v.salida));
    if (dn) r.certificado = dn.trim();
    if (sha) r.sha256_certificado = sha.toUpperCase().match(/.{2}/g).join(":");
    if (dn && /CN=Android Debug/i.test(dn)) r.problemas.push("Está firmado con la clave de DEPURACIÓN de Android Studio, no con la clave de entrega.");
  }

  // Manifiesto: paquete, versión y depurable.
  const aapt2 = herramienta("aapt2");
  if (!aapt2) r.avisos.push("No se encontró aapt2: no se revisó si es depurable.");
  else {
    const b = correr(aapt2, ["dump", "badging", ruta]);
    r.paquete = (b.salida.match(/package: name='([^']+)'/) || [])[1];
    r.version = (b.salida.match(/versionName='([^']+)'/) || [])[1];
    r.version_codigo = (b.salida.match(/versionCode='([^']+)'/) || [])[1];
    r.min_sdk = (b.salida.match(/(?:minSdkVersion|sdkVersion):'(\d+)'/) || [])[1];
    r.depurable = /application-debuggable/.test(b.salida);
    if (r.depurable) r.problemas.push("El APK es depurable (android:debuggable): es un APK de prueba.");
    if (r.paquete && r.paquete !== PAQUETE) r.problemas.push("Paquete inesperado: " + r.paquete + " (se esperaba " + PAQUETE + ").");
  }
  r.apto = r.problemas.length === 0;
  return r;
}

if (require.main === module) {
  const ruta = process.argv[2];
  if (!ruta) { console.error("Uso: node scripts/verificar-apk.js <ruta.apk> [--json]"); process.exit(2); }
  const r = verificar(ruta);
  if (process.argv.includes("--json")) console.log(JSON.stringify(r, null, 2));
  else {
    console.log("APK: " + r.archivo + (r.tamano_mb ? " (" + r.tamano_mb + " MB)" : ""));
    if (r.paquete) console.log("Paquete " + r.paquete + " · versión " + r.version + " (" + r.version_codigo + ") · Android mínimo API " + r.min_sdk);
    if (r.certificado) console.log("Firmado por: " + r.certificado + " · esquemas " + (r.esquemas || []).join(", "));
    if (r.sha256_certificado) console.log("SHA-256 del certificado (para App Check → Play Integrity): " + r.sha256_certificado);
    if (r.sha256_archivo) console.log("SHA-256 del archivo (para comparar con el instalado y el de Hosting): " + r.sha256_archivo);
    r.avisos.forEach(a => console.log("Aviso: " + a));
    r.problemas.forEach(p => console.log("PROBLEMA: " + p));
    console.log(r.apto ? "Resultado: APTO para publicar." : "Resultado: NO APTO. No lo publiques.");
  }
  process.exit(r.apto ? 0 : 1);
}
module.exports = { verificar, dexDelApk };
