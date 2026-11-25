#!/usr/bin/env node
"use strict";
/**
 * Entrega 8 (C04) · Ejecuta tests/reglas en el emulador de Realtime Database de Firebase.
 *   npm run test:reglas
 * Usa un proyecto de demostración («demo-alerce»): no necesita iniciar sesión en Firebase ni
 * se conecta a ningún proyecto real. El emulador necesita Java 11+: si no está en el PATH, se
 * usa el que trae Android Studio (jbr).
 */
const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

const env = Object.assign({}, process.env);
const jbr = [env.JAVA_HOME, "C:/Program Files/Android/Android Studio/jbr",
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home", "/opt/android-studio/jbr"].filter(Boolean)
  .find(d => fs.existsSync(path.join(d, "bin")));
if (jbr) {
  env.JAVA_HOME = jbr;
  const clave = Object.keys(env).find(k => k.toUpperCase() === "PATH") || "PATH";   // en Windows suele ser «Path»
  env[clave] = path.join(jbr, "bin") + path.delimiter + (env[clave] || "");
}

const raiz = path.resolve(__dirname, "..");
const cli = path.join(raiz, "node_modules", "firebase-tools", "lib", "bin", "firebase.js");
if (!fs.existsSync(cli)) { console.error("Falta firebase-tools: ejecuta npm install."); process.exit(2); }
const r = spawnSync(process.execPath, [cli, "emulators:exec", "--only", "database", "--project", "demo-alerce",
  '"' + process.execPath + '" --test "tests/reglas/*.test.js"'], { cwd: raiz, env, stdio: "inherit", shell: false });
process.exit(r.status == null ? 1 : r.status);
