"use strict";
// Web local con datos FICTICIOS para recorrerla antes de publicar: npm run demo
// No usa js/config.js (credenciales reales): entrega una configuración falsa y reemplaza supabase-js
// por tests/e2e/supabase_falso.js con los datos de tests/e2e/datos_demo.js. Nada sale del computador.
//   http://localhost:4180/?demo=visitante      vitrina pública
//   http://localhost:4180/?tienda=t_tejidos_austral   enlace directo a una tienda (WhatsApp o QR)
//   http://localhost:4180/?demo=emprendedora   panel de la emprendedora en la web
//   http://localhost:4180/?demo=admin          administración
const http = require("http");
const fs = require("fs");
const path = require("path");
const { RAIZ } = require("./servidor");

const TIPOS = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8",
  ".png": "image/png", ".jpg": "image/jpeg", ".svg": "image/svg+xml", ".otf": "font/otf", ".woff2": "font/woff2" };

// Ilustraciones simples en lugar de fotos reales (los datos son ficticios).
const FOTOS = {
  pan_amasado: ["#E9C89A", "#8A4B1F", "🍞"], empanada_horno: ["#F1D7A8", "#9A5A22", "🥟"], hallulla: ["#EED9B5", "#8C5A2B", "🥯"],
  chal_lana: ["#D9C7E3", "#5B3A72", "🧣"], gorro_lana: ["#C9D8CF", "#2F5D4A", "🧶"], kuchen_murta: ["#F2C6C2", "#8E2C2C", "🥧"],
  torta_encargo: ["#F6D9E3", "#9B2F5C", "🎂"], corte_pelo: ["#D3DCE6", "#2B3A4A", "💈"],
  logo_marta: ["#8A4B1F", "#FFFFFF", "🍞"], logo_tejidos: ["#5B3A72", "#FFFFFF", "🧶"], logo_barberia: ["#2B3A4A", "#FFFFFF", "✂️"]
};
function svgFoto(nombre) {
  const [fondo, tinta, emoji] = FOTOS[nombre] || ["#E5E7EB", "#374151", "📦"];
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400"><defs><radialGradient id="g" cx="35%" cy="30%" r="80%"><stop offset="0" stop-color="#fff" stop-opacity=".55"/><stop offset="1" stop-color="${fondo}" stop-opacity="0"/></radialGradient></defs>` +
    `<rect width="400" height="400" fill="${fondo}"/><rect width="400" height="400" fill="url(#g)"/><circle cx="200" cy="215" r="120" fill="${tinta}" opacity=".12"/>` +
    `<text x="200" y="262" font-size="170" text-anchor="middle">${emoji}</text></svg>`;
}

function crearServidorDemo() {
  const falso = fs.readFileSync(path.join(__dirname, "supabase_falso.js"), "utf8");
  return http.createServer((req, res) => {
    const ruta = decodeURIComponent(new URL(req.url, "http://x").pathname);
    const enviar = (tipo, cuerpo) => { res.writeHead(200, { "Content-Type": tipo, "Cache-Control": "no-store" }); res.end(cuerpo); };
    if (ruta === "/js/config.js") return enviar(TIPOS[".js"], 'window.__CONFIG__ = {"supabase":{"url":"http://localhost/falso","anonKey":"demo-sin-credenciales"}};');
    if (ruta === "/__demo/datos.js") return enviar(TIPOS[".js"], fs.readFileSync(path.join(__dirname, "datos_demo.js")));
    if (ruta === "/__demo/supabase.js") return enviar(TIPOS[".js"], falso);
    const mFoto = ruta.match(/^\/__demo\/fotos\/([a-z_]+)\.svg$/);
    if (mFoto) return enviar(TIPOS[".svg"], svgFoto(mFoto[1]));
    let archivo = path.normalize(path.join(RAIZ, ruta === "/" ? "index.html" : ruta));
    if (!archivo.startsWith(RAIZ)) { res.writeHead(403); return res.end(); }
    fs.stat(archivo, (err, st) => {
      if (err || !st.isFile()) { res.writeHead(404, { "Content-Type": "text/plain" }); return res.end("No existe"); }
      if (archivo.endsWith("index.html")) {
        const html = fs.readFileSync(archivo, "utf8").replace(/<script src="https:\/\/cdn\.jsdelivr\.net\/npm\/@supabase\/supabase-js@[^"]+"[^>]*><\/script>/,
          '<script src="/__demo/datos.js"></script><script src="/__demo/supabase.js"></script>');
        return enviar(TIPOS[".html"], html);
      }
      res.writeHead(200, { "Content-Type": TIPOS[path.extname(archivo)] || "application/octet-stream", "Cache-Control": "no-store" });
      fs.createReadStream(archivo).pipe(res);
    });
  });
}

if (require.main === module) {
  const puerto = Number(process.argv[2] || 4180);
  crearServidorDemo().listen(puerto, "127.0.0.1", () => {
    console.log(`Demo local (datos ficticios) en http://localhost:${puerto}/`);
    console.log(`  Tienda por enlace:  http://localhost:${puerto}/?demo=visitante&tienda=t_tejidos_austral`);
    console.log(`  Emprendedora:       http://localhost:${puerto}/?demo=emprendedora`);
    console.log(`  Administración:     http://localhost:${puerto}/?demo=admin`);
  });
}
module.exports = { crearServidorDemo };
