#!/usr/bin/env node
"use strict";
/**
 * Entrega 8 (W01, W02, W06) · Ensayo de Firebase Hosting en el EMULADOR, sin publicar nada.
 *   npm run test:hosting
 * 1. Prepara la web en una carpeta temporal (no toca dist/hosting) con una configuración
 *    de Supabase FICTICIA (falso.supabase.co): ninguna llamada llega a una instancia real.
 * 2. La sirve con el emulador de Hosting usando las mismas cabeceras de firebase.json.
 * 3. Corre tests/e2e/hosting.spec.js en Edge contra esa dirección (con Supabase simulado).
 */
const fs = require("fs");
const os = require("os");
const path = require("path");
const { spawnSync } = require("child_process");

const raiz = path.resolve(__dirname, "..");
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "alerce-ensayo-hosting-"));
const destino = path.join(tmp, "publico");
const clave = "eyJhbGciOiJIUzI1NiJ9." + Buffer.from(JSON.stringify({ role: "anon" })).toString("base64url") + ".ensayo";

const prep = spawnSync(process.execPath, [path.join(raiz, "scripts", "preparar-hosting.js"), "--destino", destino],
  { encoding: "utf8", env: Object.assign({}, process.env, { SUPABASE_URL: "https://falso.supabase.co", SUPABASE_ANON_KEY: clave }) });
process.stdout.write(prep.stdout || ""); process.stderr.write(prep.stderr || "");
if (prep.status !== 0) process.exit(prep.status || 1);

// Configuración del ensayo: la de firebase.json, pero publicando la carpeta temporal.
const cfg = JSON.parse(fs.readFileSync(path.join(raiz, "firebase.json"), "utf8"));
cfg.hosting.public = "publico";
delete cfg.database;
cfg.emulators = { hosting: { host: "127.0.0.1", port: 5005 }, ui: { enabled: false }, singleProjectMode: true };
fs.writeFileSync(path.join(tmp, "firebase.json"), JSON.stringify(cfg, null, 2));

const cli = path.join(raiz, "node_modules", "firebase-tools", "lib", "bin", "firebase.js");
const playwright = path.join(raiz, "node_modules", "@playwright", "test", "cli.js");
const prueba = '"' + process.execPath + '" "' + playwright + '" test --config "' + path.join(raiz, "tests", "e2e", "playwright.config.js") + '" "' +
  path.join(raiz, "tests", "e2e", "hosting.spec.js").replace(/\\/g, "/") + '"';
const r = spawnSync(process.execPath, [cli, "emulators:exec", "--only", "hosting", "--project", "demo-alerce", "--config", path.join(tmp, "firebase.json"), prueba],
  { cwd: raiz, stdio: "inherit", env: Object.assign({}, process.env, { ALERCE_HOSTING_URL: "http://127.0.0.1:5005" }) });
fs.rmSync(tmp, { recursive: true, force: true });
process.exit(r.status == null ? 1 : r.status);
