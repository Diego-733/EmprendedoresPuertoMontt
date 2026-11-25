"use strict";
// Entrega 8 · scripts/preparar-hosting.js y scripts/verificar-apk.js.
// Las pruebas escriben en una carpeta TEMPORAL (--destino): nunca tocan dist/hosting,
// que puede tener la web real preparada para publicar.
const test = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const crypto = require("crypto");
const { spawnSync } = require("child_process");

const RAIZ = path.resolve(__dirname, "..", "..");
const SCRIPT = path.join(RAIZ, "scripts", "preparar-hosting.js");
const DIST_REAL = path.join(RAIZ, "dist", "hosting");
const jwt = (rol) => "eyJhbGciOiJIUzI1NiJ9." + Buffer.from(JSON.stringify({ role: rol })).toString("base64url") + ".firma";

function huellaCarpeta(dir) {
  if (!fs.existsSync(dir)) return "NO_EXISTE";
  const h = crypto.createHash("sha256");
  (function rec(d) {
    for (const n of fs.readdirSync(d).sort()) {
      const p = path.join(d, n), st = fs.statSync(p);
      if (st.isDirectory()) rec(p); else h.update(path.relative(dir, p) + ":" + st.size + ":" + st.mtimeMs + "\n");
    }
  })(dir);
  return h.digest("hex");
}

const temporal = () => fs.mkdtempSync(path.join(os.tmpdir(), "alerce-hosting-"));
const correr = (env, destino, extra = []) => spawnSync(process.execPath, [SCRIPT, "--destino", destino].concat(extra), { encoding: "utf8",
  env: Object.assign({ PATH: process.env.PATH, SystemRoot: process.env.SystemRoot || "", LOCALAPPDATA: process.env.LOCALAPPDATA || "" }, env) });

const huellaAntes = huellaCarpeta(DIST_REAL);
test.after(() => {
  assert.strictEqual(huellaCarpeta(DIST_REAL), huellaAntes, "las pruebas no deben cambiar dist/hosting");
});

test("Hosting: copia la web en la carpeta indicada y escribe config.js solo con la URL y la clave anon", () => {
  const destino = path.join(temporal(), "hosting");
  const clave = jwt("anon");
  const r = correr({ SUPABASE_URL: "https://falso.supabase.co", SUPABASE_ANON_KEY: clave }, destino);
  assert.strictEqual(r.status, 0, r.stderr);
  assert.ok(!(r.stdout + r.stderr).includes(clave), "no imprime la clave");
  for (const f of ["index.html", "js/main.js", "js/servicio.js", "css/styles.css", "firebase-messaging-sw.js"]) {
    assert.ok(fs.existsSync(path.join(destino, f)), "falta " + f);
  }
  assert.strictEqual(fs.readFileSync(path.join(destino, "js", "config.js"), "utf8"),
    'window.__CONFIG__ = {"supabase":{"url":"https://falso.supabase.co","anonKey":"' + clave + '"}};\n');
  assert.ok(!fs.existsSync(path.join(destino, ".nojekyll")));
  assert.ok(!fs.existsSync(path.join(destino, "app-debug.apk")));
  fs.rmSync(path.dirname(destino), { recursive: true, force: true });
});

test("Hosting: rechaza claves secretas o service_role sin mostrarlas, y exige https", () => {
  const destino = path.join(temporal(), "hosting");
  for (const clave of ["sb_secret_CENTINELA123", jwt("service_role")]) {
    const r = correr({ SUPABASE_URL: "https://falso.supabase.co", SUPABASE_ANON_KEY: clave }, destino);
    assert.strictEqual(r.status, 1);
    assert.match(r.stderr, /No se preparó Hosting/);
    assert.ok(!(r.stdout + r.stderr).includes(clave));
  }
  assert.strictEqual(correr({ SUPABASE_URL: "http://falso.supabase.co", SUPABASE_ANON_KEY: jwt("anon") }, destino).status, 1);
  assert.ok(!fs.existsSync(destino), "si falla, no deja una carpeta a medias");
  fs.rmSync(path.dirname(destino), { recursive: true, force: true });
});

test("Hosting: un APK que no es de distribución (sin firma o de prueba) detiene la preparación", () => {
  const destino = path.join(temporal(), "hosting");
  const falso = path.join(path.dirname(destino), "no-es-un-apk.apk");
  fs.writeFileSync(falso, "esto no es un APK");
  const r = correr({ SUPABASE_URL: "https://falso.supabase.co", SUPABASE_ANON_KEY: jwt("anon") }, destino, ["--apk", falso]);
  assert.strictEqual(r.status, 1);
  assert.match(r.stderr, /el APK no es el de distribución/);
  assert.ok(!fs.existsSync(destino));
  // Con el APK release sin firmar de una compilación local (si existe), también se rechaza.
  const sinFirmar = path.join(RAIZ, "App para el celular", "app", "build", "outputs", "apk", "release", "app-release-unsigned.apk");
  if (fs.existsSync(sinFirmar)) {
    const r2 = correr({ SUPABASE_URL: "https://falso.supabase.co", SUPABASE_ANON_KEY: jwt("anon") }, destino, ["--apk", sinFirmar]);
    assert.strictEqual(r2.status, 1);
    assert.match(r2.stderr, /no está firmado|no encontró/);
  }
  fs.rmSync(path.dirname(destino), { recursive: true, force: true });
});

test("verificar-apk: un archivo que no es APK no es apto y muestra su SHA-256", () => {
  const { verificar } = require(path.join(RAIZ, "scripts", "verificar-apk.js"));
  const dir = temporal(), f = path.join(dir, "x.apk");
  fs.writeFileSync(f, "hola");
  const r = verificar(f);
  assert.strictEqual(r.apto, false);
  assert.strictEqual(r.sha256_archivo, crypto.createHash("sha256").update("hola").digest("hex").toUpperCase());
  assert.ok(r.problemas.some(p => /No se pudo leer el APK/.test(p)));
  fs.rmSync(dir, { recursive: true, force: true });
});

test("firebase.json publica dist/hosting con cabeceras de seguridad y declara reglas y emuladores", () => {
  const f = JSON.parse(fs.readFileSync(path.join(RAIZ, "firebase.json"), "utf8"));
  assert.strictEqual(f.hosting.public, "dist/hosting");
  const todas = f.hosting.headers.find(h => h.regex === ".*").headers.map(h => h.key);
  assert.deepStrictEqual(todas.sort(), ["Referrer-Policy", "X-Content-Type-Options", "X-Frame-Options"]);
  assert.strictEqual(f.database.rules, "firebase/database.rules.json");
  assert.ok(f.emulators && f.emulators.database && f.emulators.hosting);
  assert.match(fs.readFileSync(path.join(RAIZ, ".gitignore"), "utf8"), /^dist\/$/m, "dist/ no va a git");
});
