/**
 * «Guíame paso a paso»: guía opcional para agregar un producto en la web.
 *
 * - Va ENCIMA del formulario real «Añadir producto» (mismos campos, reglas y guardado). Quien ya
 *   sabe usarlo no ve nada distinto hasta que toca «Guíame paso a paso».
 * - Entiende unas pocas frases escritas o dictadas («quiero vender algo», «¿cómo agrego una foto?»).
 *   Si la frase es ambigua, ofrece dos opciones. Sin IA de pago ni servidor: reglas locales.
 * - Avanzar, volver o salir no borra lo escrito. El guardado y la visibilidad los confirma la persona
 *   con el botón de siempre, «Guardar producto».
 * - La voz es opcional (js/busquedaVoz.js): todo se puede hacer tocando o escribiendo.
 */
(function (raiz, fabrica) {
  var modulo = fabrica();
  if (typeof module === "object" && module.exports) module.exports = modulo;
  else raiz.AlerceGuiaProducto = modulo;
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  // ------------------------------------------------------------------ Frases → qué quiere hacer

  var INTENCIONES = {
    producto: { titulo: "Subir un producto nuevo", paso: "foto" },
    foto: { titulo: "Agregar una foto", paso: "foto" },
    datos: { titulo: "Poner el nombre y el precio", paso: "datos" },
    visibilidad: { titulo: "Elegir quién ve el producto", paso: "publicacion" },
    venta: { titulo: "Registrar una venta", requiere: "venta" }
  };

  function normalizar(t) {
    var s = String(t == null ? "" : t).toLowerCase();
    return (s.normalize ? s.normalize("NFD").replace(/[̀-ͯ]/g, "") : s).replace(/[¿?¡!.,;:]/g, " ").replace(/\s+/g, " ").trim();
  }

  // Orden = prioridad cuando la frase calza con varias.
  var REGLAS = [
    { id: "foto", re: /\b(foto|fotos|imagen|imagenes|camara|fotografia)\b/ },
    { id: "visibilidad", re: /(quien lo ve|quienes lo ven|solo yo|lo ven todos|ocult|escond|que no lo vea|privad)/ },
    { id: "datos", re: /\b(precio|cobrar|cuanto cobro|nombre del producto|cambiar el nombre)\b/ },
    { id: "venta", re: /\b(vendi|registrar (una )?venta|anotar (una )?venta|registro de venta)\b/ },
    { id: "producto", re: /\b(subir|agregar|anadir|publicar|poner|cargar|crear|nuevo)\b.*\b(producto|productos|algo|articulo|cosa)\b|\bvender (algo|un|una|mis|productos?|cosas)\b|\bproducto nuevo\b|\bprimer producto\b/ }
  ];

  /**
   * Interpreta una frase. disponibles: lista de intenciones que esta cuenta puede usar.
   * Devuelve { tipo: "ir", intencion } o { tipo: "elegir", opciones: [a, b] } (nunca más de dos).
   */
  function interpretar(frase, disponibles) {
    var puede = function (id) { return !disponibles || disponibles.indexOf(id) >= 0; };
    var t = normalizar(frase);
    var hallados = REGLAS.filter(function (r) { return r.re.test(t) && puede(r.id); }).map(function (r) { return r.id; });
    // «quiero subir un producto con foto»: es subir un producto (empieza igual, en la foto).
    if (hallados.indexOf("producto") >= 0 && hallados.indexOf("foto") >= 0) hallados = ["producto"];
    if (hallados.length === 1) return { tipo: "ir", intencion: hallados[0] };
    if (hallados.length > 1) return { tipo: "elegir", opciones: hallados.slice(0, 2) };
    // «vender» a secas: ¿subir algo para vender o anotar una venta?
    var base = /\bvend/.test(t) ? ["producto", "venta"] : ["producto", "foto"];
    var opciones = base.filter(puede);
    if (opciones.length === 1) return { tipo: "ir", intencion: opciones[0] };
    return { tipo: "elegir", opciones: opciones.length ? opciones : ["producto", "foto"].filter(puede) };
  }

  // ------------------------------------------------------------------ Pasos dentro del formulario

  var PASOS = [
    { id: "foto", titulo: "Paso 1 de 4 · Foto", campo: "pub-foto-input",
      indicacion: "Toca «📁 Elegir foto» y elige una foto del producto. Si ahora no tienes una, puedes seguir sin foto.",
      ayuda: "En el celular, «📁 Elegir foto» te deja usar la cámara o la galería. Si no das permiso a la cámara, elige la galería o tus archivos. También puedes seguir sin foto y agregarla después." },
    { id: "datos", titulo: "Paso 2 de 4 · Nombre y precio", campo: "pub-nombre",
      indicacion: "Escribe el «Nombre del producto» y el «Precio en pesos». Puedes dictar el nombre con 🎤 y corregirlo antes de guardar.",
      ejemplo: "Nombre: «Chal de lana gris». Precio: 15000 (sin puntos ni signo $).",
      ayuda: "La categoría y la descripción ayudan a que te encuentren, pero puedes dejarlas como están. Tallas, colores y cantidades están en «3. Opciones»." },
    { id: "publicacion", titulo: "Paso 3 de 4 · Quién lo ve", campo: "pub-publicado",
      indicacion: "Marca «Lo ven todos» para mostrarlo en la vitrina. Déjalo sin marcar si por ahora solo tú lo ves.",
      ayuda: "Puedes cambiarlo cuando quieras desde «Mis productos». «4. Disponibilidad» puede quedar como está." },
    { id: "guardar", titulo: "Paso 4 de 4 · Revisar y guardar", campo: "pub-guardar",
      indicacion: "Revisa los datos y toca «Guardar producto». Se guarda solo cuando tocas ese botón.",
      ayuda: "Si algo falta, el formulario te avisa en rojo y no se guarda. Lo escrito no se pierde." }
  ];

  function indicePaso(id) { for (var i = 0; i < PASOS.length; i++) if (PASOS[i].id === id) return i; return 0; }

  /** Ilustración liviana (SVG) de cómo encuadrar: producto al centro, fondo liso, buena luz. */
  var EJEMPLO_FOTO_SVG =
    '<svg viewBox="0 0 240 160" width="240" height="160" role="img" aria-label="Ejemplo de encuadre: el producto al centro, ocupando casi toda la foto, sobre un fondo liso y con luz de frente" xmlns="http://www.w3.org/2000/svg">' +
    '<rect width="240" height="160" rx="12" fill="#F4F1EA"/>' +
    '<path d="M0 118 H240 V160 H0 Z" fill="#E6E0D3"/>' +
    '<g stroke="#1F5C46" stroke-width="1" stroke-dasharray="4 4" opacity=".45"><line x1="80" y1="8" x2="80" y2="152"/><line x1="160" y1="8" x2="160" y2="152"/><line x1="8" y1="53" x2="232" y2="53"/><line x1="8" y1="107" x2="232" y2="107"/></g>' +
    '<ellipse cx="120" cy="124" rx="46" ry="8" fill="#000" opacity=".12"/>' +
    '<path d="M86 52 Q86 40 98 40 H142 Q154 40 154 52 V112 Q154 124 142 124 H98 Q86 124 86 112 Z" fill="#8C3B22"/>' +
    '<rect x="96" y="62" width="48" height="30" rx="4" fill="#F4F1EA"/>' +
    '<path d="M104 72 H136 M104 81 H128" stroke="#8C3B22" stroke-width="3" stroke-linecap="round"/>' +
    '<circle cx="206" cy="30" r="12" fill="#F2C14E"/>' +
    '<path d="M206 12 V6 M206 54 V48 M188 30 H182 M230 30 H224" stroke="#F2C14E" stroke-width="3" stroke-linecap="round"/>' +
    '</svg>';

  // ------------------------------------------------------------------ Interfaz

  /**
   * ctx: { documento, ventana, abrirPublicar(), publicarAbierto(), abrirVenta(), ventaDisponible(), voz (AlerceVoz) }
   */
  function iniciar(ctx) {
    var doc = ctx.documento, win = ctx.ventana;
    var el = function (id) { return doc.getElementById(id); };
    var pasoActual = -1, tarjeta = null;

    function h(tag, attrs, hijos) {
      var n = doc.createElement(tag);
      Object.keys(attrs || {}).forEach(function (k) {
        if (k === "text") n.textContent = attrs[k];
        else if (k === "html") n.innerHTML = attrs[k];
        else if (k === "onclick") n.addEventListener("click", attrs[k]);
        else if (attrs[k] !== null && attrs[k] !== undefined && attrs[k] !== false) n.setAttribute(k, attrs[k] === true ? "" : attrs[k]);
      });
      (hijos || []).forEach(function (x) { if (x) n.appendChild(typeof x === "string" ? doc.createTextNode(x) : x); });
      return n;
    }
    function disponibles() {
      var lista = ["producto", "foto", "datos", "visibilidad"];
      if (ctx.ventaDisponible && ctx.ventaDisponible()) lista.push("venta");
      return lista;
    }

    // ---------- Entrada: «¿Qué quieres hacer?» (desde Mis productos)
    var entrada = el("guia-entrada");
    function pintarEntrada() {
      if (!entrada) return;
      entrada.textContent = "";
      var campo = h("input", { type: "text", id: "guia-frase", class: "input-fb-market", autocomplete: "off", enterkeyhint: "go",
        placeholder: "Ej: quiero subir un producto" });
      var mic = h("button", { type: "button", id: "guia-frase-voz", class: "btn-buscar-voz btn-voz-campo", hidden: true }, [h("span", { "aria-hidden": "true", text: "🎤" })]);
      var estadoVoz = h("p", { id: "guia-frase-estado", class: "estado-buscar-voz", role: "status", "aria-live": "polite" });
      var respuesta = h("div", { id: "guia-respuesta", class: "guia-respuesta", "aria-live": "polite" });
      entrada.appendChild(h("label", { for: "guia-frase", class: "label-fb-market", text: "¿Qué quieres hacer? Escríbelo o díctalo" }));
      entrada.appendChild(h("div", { class: "campo-con-voz" }, [campo, mic]));
      entrada.appendChild(estadoVoz);
      entrada.appendChild(h("div", { class: "guia-botones" }, [
        h("button", { type: "button", id: "guia-ir", class: "btn-accion-pill btn-negro", text: "Ir", onclick: function () { responder(campo.value); } }),
        h("button", { type: "button", class: "btn-accion-pill btn-secundario", text: "Subir un producto nuevo", onclick: function () { ir("producto"); } }),
        h("button", { type: "button", class: "btn-accion-pill btn-secundario", text: "¿Cómo agrego una foto?", onclick: function () { ir("foto"); } })
      ]));
      entrada.appendChild(respuesta);
      campo.addEventListener("keydown", function (e) { if (e.key === "Enter") { e.preventDefault(); responder(campo.value); } });
      if (ctx.voz) ctx.voz.conectar({ campo: campo, boton: mic, estado: estadoVoz, ventana: win, etiqueta: "Decir qué quieres hacer",
        textos: { escuchando: "Escuchando… di qué quieres hacer.", aplicado: function (t) { return "Entendimos «" + t + "»."; }, cancelado: "Dictado cancelado. Puedes escribir lo que necesitas.",
          permiso: "El navegador no dio permiso para usar el micrófono. Puedes escribirlo o tocar una opción.",
          sinVoz: "No escuchamos nada. Puedes escribirlo o tocar una opción.", red: "El dictado necesita conexión. Puedes escribirlo o tocar una opción.",
          sinMicrofono: "No encontramos un micrófono. Puedes escribirlo o tocar una opción.", error: "No entendimos el dictado. Puedes escribirlo o tocar una opción." },
        alTerminar: function (t) { responder(t); } });
    }
    function responder(frase) {
      var resp = el("guia-respuesta");
      if (!String(frase || "").trim()) { if (resp) resp.textContent = "Escribe lo que necesitas o toca una de las opciones."; return; }
      var r = interpretar(frase, disponibles());
      if (r.tipo === "ir") { ir(r.intencion); return; }
      if (!resp) return;
      resp.textContent = "";
      resp.appendChild(h("p", { class: "guia-pregunta", text: "¿Qué quieres hacer?" }));
      resp.appendChild(h("div", { class: "guia-botones" }, r.opciones.map(function (id) {
        return h("button", { type: "button", class: "btn-accion-pill btn-secundario", text: INTENCIONES[id].titulo, onclick: function () { ir(id); } });
      })));
    }
    function ir(intencion) {
      if (intencion === "venta") { if (ctx.abrirVenta) ctx.abrirVenta(); return; }
      var paso = (INTENCIONES[intencion] || INTENCIONES.producto).paso;
      if (!ctx.publicarAbierto()) ctx.abrirPublicar();   // formulario nuevo; si ya estaba abierto, se conserva lo escrito
      if (!ctx.publicarAbierto()) return;
      mostrarPaso(indicePaso(paso), intencion === "foto");
    }

    // ---------- Tarjeta del paso, dentro del formulario (no tapa campos, teclado ni botones)
    function destinoPaso(paso) {
      var campo = el(paso.campo);
      if (paso.id === "guardar") return { padre: campo.parentNode, antes: campo };
      var fs = campo && campo.closest ? campo.closest("fieldset") : null;
      var legend = fs ? fs.querySelector("legend") : null;
      return { padre: fs, antes: legend ? legend.nextSibling : (fs ? fs.firstChild : null) };
    }
    function resumen() {
      var publicado = el("pub-publicado") && el("pub-publicado").checked;
      var foto = el("pub-foto-preview") && el("pub-foto-preview").querySelector("img");
      return [
        "Foto: " + (foto ? "elegida" : "sin foto"),
        "Nombre: " + ((el("pub-nombre").value || "").trim() || "falta"),
        "Precio: " + ((el("pub-precio").value || "").trim() ? "$ " + el("pub-precio").value : "falta"),
        "Quién lo ve: " + (publicado ? "Lo ven todos" : "Solo yo lo veo")
      ];
    }
    function textoFoto() {
      var hay = el("pub-foto-preview") && el("pub-foto-preview").querySelector("img");
      return hay ? "Así quedó tu foto. Para cambiarla, toca «📁 Elegir foto» otra vez; para sacarla, «Quitar foto»." : "";
    }
    function textoVisibilidad() {
      return el("pub-publicado") && el("pub-publicado").checked ? "Ahora: lo ven todos." : "Ahora: solo tú lo ves.";
    }

    function mostrarPaso(i, abrirAyuda) {
      quitarTarjeta();
      pasoActual = Math.max(0, Math.min(PASOS.length - 1, i));
      var paso = PASOS[pasoActual];
      var dest = destinoPaso(paso);
      if (!dest.padre) return;
      var detalles = [];
      if (paso.id === "foto") {
        detalles.push(h("details", { class: "guia-detalle", open: abrirAyuda ? true : null }, [
          h("summary", { text: "Ver un ejemplo" }),
          h("figure", { class: "guia-ejemplo-foto" }, [
            h("div", { html: EJEMPLO_FOTO_SVG }),
            h("figcaption", { text: "Ejemplo: no es la foto de tu tienda. Producto al centro, fondo liso y luz de frente." })
          ])
        ]));
      } else if (paso.ejemplo) {
        detalles.push(h("details", { class: "guia-detalle" }, [h("summary", { text: "Ver un ejemplo" }), h("p", { text: paso.ejemplo })]));
      }
      detalles.push(h("details", { class: "guia-detalle", open: abrirAyuda && paso.id === "foto" ? true : null }, [h("summary", { text: "Necesito ayuda" }), h("p", { text: paso.ayuda })]));

      var extra = h("p", { id: "guia-estado-paso", class: "guia-estado", role: "status", "aria-live": "polite" });
      if (paso.id === "foto") extra.textContent = textoFoto();
      if (paso.id === "publicacion") extra.textContent = textoVisibilidad();
      if (paso.id === "guardar") resumen().forEach(function (linea) { extra.appendChild(h("span", { class: "guia-resumen-linea", text: linea })); });

      var hayFoto = !!textoFoto();
      var botones = [
        pasoActual > 0 ? h("button", { type: "button", id: "guia-volver", class: "btn-accion-pill btn-secundario", text: "Volver", onclick: function () { mostrarPaso(pasoActual - 1); } }) : null,
        pasoActual < PASOS.length - 1 ? h("button", { type: "button", id: "guia-siguiente", class: "btn-accion-pill btn-negro",
          text: paso.id === "foto" && !hayFoto ? "Seguir sin foto" : (paso.id === "publicacion" ? "Revisar y guardar" : "Siguiente"), onclick: siguiente }) : null,
        h("button", { type: "button", id: "guia-salir", class: "btn-accion-pill btn-terciario", text: "Salir de la guía", onclick: salir })
      ];
      tarjeta = h("section", { id: "guia-paso", class: "guia-paso", "aria-label": "Guía: " + paso.titulo, "data-paso": paso.id }, [
        h("p", { class: "guia-paso-titulo", text: "🧭 " + paso.titulo }),
        h("p", { class: "guia-paso-indicacion", text: paso.indicacion }),
        extra
      ].concat(detalles).concat([h("div", { class: "guia-botones" }, botones)]));
      dest.padre.insertBefore(tarjeta, dest.antes);
      if (dest.padre.classList) dest.padre.classList.add("guia-paso-actual");
      try { tarjeta.scrollIntoView({ block: "start", behavior: "auto" }); } catch (e) { /* sin scroll */ }
      var foco = tarjeta.querySelector(".guia-paso-titulo");
      if (foco) { foco.setAttribute("tabindex", "-1"); foco.focus({ preventScroll: true }); }
    }
    function siguiente() {
      var paso = PASOS[pasoActual];
      if (paso.id === "datos") {
        var nombre = (el("pub-nombre").value || "").trim(), precio = (el("pub-precio").value || "").trim();
        var estado = el("guia-estado-paso");
        if (!nombre || !precio) {
          if (estado) estado.textContent = !nombre ? "Falta el «Nombre del producto»." : "Falta el «Precio en pesos».";
          el(!nombre ? "pub-nombre" : "pub-precio").focus();
          return;
        }
      }
      mostrarPaso(pasoActual + 1);
    }
    function quitarTarjeta() {
      if (tarjeta && tarjeta.parentNode) {
        if (tarjeta.parentNode.classList) tarjeta.parentNode.classList.remove("guia-paso-actual");
        tarjeta.parentNode.removeChild(tarjeta);
      }
      tarjeta = null;
    }
    /** Sale de la guía; el formulario queda tal cual (nada de lo escrito se borra). */
    function salir() {
      quitarTarjeta();
      pasoActual = -1;
      var nombre = el("pub-nombre");
      if (nombre && ctx.publicarAbierto()) nombre.focus({ preventScroll: true });
    }
    function refrescarEstado() {
      if (pasoActual < 0) return;
      var paso = PASOS[pasoActual], estado = el("guia-estado-paso");
      if (!estado) return;
      if (paso.id === "foto") {
        estado.textContent = textoFoto();
        var sig = el("guia-siguiente");
        if (sig) sig.textContent = textoFoto() ? "Siguiente" : "Seguir sin foto";
      }
      if (paso.id === "publicacion") estado.textContent = textoVisibilidad();
    }

    // La vista previa cambia al elegir o quitar la foto; la casilla, al elegir quién lo ve.
    var vista = el("pub-foto-preview");
    if (vista && win.MutationObserver) new win.MutationObserver(refrescarEstado).observe(vista, { childList: true });
    var casilla = el("pub-publicado");
    if (casilla) casilla.addEventListener("change", refrescarEstado);
    // Al cerrar el formulario (guardado o ✕), la guía se va con él.
    var modal = el("modal-publicar-producto");
    if (modal && win.MutationObserver) new win.MutationObserver(function () { if (!ctx.publicarAbierto() && pasoActual >= 0) { quitarTarjeta(); pasoActual = -1; } })
      .observe(modal, { attributes: true, attributeFilter: ["class"] });

    var abrirEntrada = el("gestion-guia");
    if (abrirEntrada && entrada) abrirEntrada.addEventListener("click", function () {
      var abierta = !entrada.hidden;
      if (abierta) { entrada.hidden = true; abrirEntrada.setAttribute("aria-expanded", "false"); return; }
      pintarEntrada();
      entrada.hidden = false;
      abrirEntrada.setAttribute("aria-expanded", "true");
      var f = el("guia-frase");
      if (f) f.focus();
    });
    var desdeFormulario = el("pub-abrir-guia");
    if (desdeFormulario) desdeFormulario.addEventListener("click", function () { mostrarPaso(0); });

    // Dictar el nombre del producto (opcional; el texto queda editable).
    var micNombre = el("pub-nombre-voz");
    if (ctx.voz && micNombre) ctx.voz.conectar({ campo: el("pub-nombre"), boton: micNombre, estado: el("pub-nombre-voz-estado"), ventana: win,
      etiqueta: "Dictar el nombre del producto",
      textos: { escuchando: "Escuchando… di el nombre del producto.", aplicado: function (t) { return "Escribimos «" + t + "». Revísalo y corrígelo si hace falta."; },
        cancelado: "Dictado cancelado. El nombre sigue igual.", permiso: "El navegador no dio permiso para usar el micrófono. Puedes escribir el nombre.",
        sinVoz: "No escuchamos nada. Puedes escribir el nombre.", red: "El dictado necesita conexión. Puedes escribir el nombre.",
        sinMicrofono: "No encontramos un micrófono. Puedes escribir el nombre.", error: "No entendimos el dictado. Puedes escribir el nombre." } });

    return { interpretar: interpretar, mostrarPaso: mostrarPaso, salir: salir, ir: ir, pasoActual: function () { return pasoActual >= 0 ? PASOS[pasoActual].id : null; } };
  }

  return { INTENCIONES: INTENCIONES, PASOS: PASOS, normalizar: normalizar, interpretar: interpretar, iniciar: iniciar };
});
