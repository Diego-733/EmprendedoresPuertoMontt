/**
 * Asistente Alerce en la web (versión proporcionada a lo que la web realmente tiene).
 *
 * - Ayuda por tareas, ir a las pantallas que existen y llenar «Añadir producto» con lo dicho
 *   (nombre y precio). Nunca guarda, vende ni cambia datos por su cuenta: eso se hace con los
 *   botones de siempre («Guardar producto», «Registrar una venta»…).
 * - Respeta el rol: lo de la emprendedora solo aparece y funciona con su sesión.
 * - Interpretación local por reglas (no es inteligencia artificial), con las mismas intenciones y
 *   la misma lectura de precios que Android. Contrato compartido: tests/contrato/asistente_frases.json.
 * - Voz: reutiliza el dictado de la búsqueda (busquedaVoz.js: el micrófono se abre solo al tocarlo)
 *   y la lectura en voz alta (lecturaVoz.js). Sin esas funciones del navegador, todo sigue escrito.
 * - No flota sobre la página: vive en la sección «Ayuda».
 */
(function (raiz, fabrica) {
  var modulo = fabrica();
  if (typeof module === "object" && module.exports) module.exports = modulo;
  else raiz.AlerceAsistenteWeb = modulo;
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  // ------------------------------------------------------------------ números y precios

  function normalizar(t) {
    return String(t || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "")
      .replace(/[¿?¡!;:"«»()]/g, " ").replace(/[,.](?=\s|$)/g, " ").replace(/\s+/g, " ").trim();
  }

  var UNIDADES = { cero: 0, uno: 1, un: 1, una: 1, dos: 2, tres: 3, cuatro: 4, cinco: 5, seis: 6, siete: 7, ocho: 8, nueve: 9, diez: 10,
    once: 11, doce: 12, trece: 13, catorce: 14, quince: 15, dieciseis: 16, diecisiete: 17, dieciocho: 18, diecinueve: 19, veinte: 20,
    veintiuno: 21, veintidos: 22, veintitres: 23, veinticuatro: 24, veinticinco: 25, veintiseis: 26, veintisiete: 27, veintiocho: 28, veintinueve: 29 };
  var DECENAS = { treinta: 30, cuarenta: 40, cincuenta: 50, sesenta: 60, setenta: 70, ochenta: 80, noventa: 90 };
  var CENTENAS = { cien: 100, ciento: 100, doscientos: 200, doscientas: 200, trescientos: 300, trescientas: 300, cuatrocientos: 400,
    cuatrocientas: 400, quinientos: 500, quinientas: 500, seiscientos: 600, seiscientas: 600, setecientos: 700, setecientas: 700,
    ochocientos: 800, ochocientas: 800, novecientos: 900, novecientas: 900 };
  var AMBIGUOS = { un: 1, una: 1, uno: 1 };
  var MULTIPLICADORES = { mil: 1, luca: 1, lucas: 1, luquita: 1, luquitas: 1 };

  function esPalabraNumero(p) { return p in UNIDADES || p in DECENAS || p in CENTENAS || p === "mil"; }

  /** «cuesta quince mil pesos» → «cuesta 15000 pesos»; «$8,500» y «12.990» → miles. */
  function aDigitos(texto) {
    var tokens = normalizar(texto).split(" ").filter(Boolean);
    var salida = [];
    for (var i = 0; i < tokens.length;) {
      var crudo = tokens[i];
      var t = crudo.replace(/^\$/, "");
      if (/^\d{1,3}([.,]\d{3})+$/.test(t)) t = t.replace(/[.,]/g, "");
      if (crudo.charAt(0) === "$" && /^\d{1,3}$/.test(t)) { while (/^\d{3}$/.test(tokens[i + 1] || "")) { t += tokens[i + 1]; i++; } }
      var siguiente = tokens[i + 1];
      var previo = salida[salida.length - 1];
      if (t === "mil" && previo && /^\d+$/.test(previo)) {
        salida[salida.length - 1] = String(Number(previo) * 1000);
        if (/^\d{1,3}$/.test(siguiente || "")) { salida[salida.length - 1] = String(Number(salida[salida.length - 1]) + Number(siguiente)); i += 2; } else i++;
        continue;
      }
      var ambiguoSuelto = (t in AMBIGUOS) && !(siguiente in MULTIPLICADORES);
      if (!esPalabraNumero(t) || ambiguoSuelto) { salida.push(t); i++; continue; }
      var total = 0, actual = 0, j = i;
      while (j < tokens.length) {
        var p = tokens[j];
        if (p === "y" && j > i && (tokens[j - 1] in DECENAS) && ((UNIDADES[tokens[j + 1]] || 99) < 10)) { j++; continue; }
        if ((p in UNIDADES) && !((p in AMBIGUOS) && !(tokens[j + 1] in MULTIPLICADORES) && j > i)) actual += UNIDADES[p];
        else if (p in DECENAS) actual += DECENAS[p];
        else if (p in CENTENAS) actual += CENTENAS[p];
        else if (p === "mil") { total += (actual === 0 ? 1 : actual) * 1000; actual = 0; }
        else break;
        j++;
      }
      salida.push(String(total + actual));
      i = j;
    }
    return salida.join(" ");
  }

  function montoConUnidad(n, unidad) {
    if (!unidad) return n;
    if (/^lu[cq]/.test(unidad)) return n * 1000;
    if (/^gamba/.test(unidad)) return n * 100;
    return n;
  }

  /** Precio dicho (o null). Igual que Android: «a 5» sin unidad no es un precio creíble. */
  function precio(frase) {
    var d = aDigitos(frase).replace(/\b(?:(\d+) )?(lucas?|luquitas?) y media\b/g, function (m, n) { return ((Number(n) || 1) * 1000 + 500) + " pesos"; });
    var re = /\b(cuesta|cuestan|vale|valen|precio(?: es| de)?|cobro|a|en|por)\s+\$?(\d+)(?:\s+(pesos|peso|lucas|luca|luquitas|luquita|gambas|gamba))?/g;
    var m;
    while ((m = re.exec(d))) {
      var n = Number(m[2]);
      var explicito = !!m[3] || /^(cuesta|cuestan|vale|valen|precio|cobro)/.test(m[1]);
      if (!explicito && n < 100) continue;
      return montoConUnidad(n, m[3]);
    }
    var m2 = /\b(\d+)\s+(pesos|lucas|luca|luquitas|luquita)\b/.exec(d);
    return m2 ? montoConUnidad(Number(m2[1]), m2[2]) : null;
  }

  // ------------------------------------------------------------------ nombre del producto

  function tolerante(p) {
    return p.replace(/[aeioun]/g, function (c) { return { a: "[aá]", e: "[eé]", i: "[ií]", o: "[oó]", u: "[uúü]", n: "[nñ]" }[c]; });
  }
  var VERBOS = ["agrega", "agregar", "agregue", "agrego", "anade", "anadir", "sube", "subir", "subo", "pon", "poner", "pongo",
    "publica", "publicar", "publico", "crea", "crear", "ingresa", "ingresar", "carga", "cargar"];
  var NUM_PAL = "(?:un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieci[a-z]+|veinte|veinti[a-z]+|treinta|cuarenta|cincuenta|sesenta|setenta|ochenta|noventa|cien|ciento|[a-z]*cient[oa]s|quinient[oa]s|mil|y|media)";
  var CORTE = "(?=,|\\.|;|:| que | cuesta| cuestan| vale| valen| a \\$?\\d| en \\$?\\d| por \\$?\\d| (?:a|en|por) (?:" + NUM_PAL + " )*(?:lucas?|luquitas?|pesos|mil|gambas?)(?:\\s|$)| y tengo| tengo| precio| quiero| para vender|$)";
  var RE_NOMBRE = new RegExp("(?:^|\\s)(?:" + VERBOS.map(tolerante).join("|") + ")(?:me|le)? (?:(?:un|una|unos|unas|el|la|los|las|mis|mi) )?(.+?)" + CORTE);
  var NO_ES_NOMBRE = /^(producto|productos|algo|cosa|cosas|foto|fotos|imagen|precio|venta|ventas|tienda|feria|ferias|nuevo)\b/;

  function nombre(frase) {
    var baja = String(frase || "").toLowerCase().replace(/\s+/g, " ").trim();
    var m = RE_NOMBRE.exec(baja);
    if (!m) return null;
    var frag = m[1].trim().replace(/^((otro|otra|nuevo|nueva) )?(producto (nuevo )?(que es )?(de )?|un |una |unos |unas )?/, "").trim();
    var norm = normalizar(frag);
    if (!frag || NO_ES_NOMBRE.test(norm) || /^(a|en|por|de|para|que|y|cada) /.test(norm + " ") || /^\d/.test(norm)) return null;
    var limpio = frag.replace(/ (hech[oa]s? a mano|tejid[oa]s? a mano|para |con ).*$/, "").trim();
    var palabras = limpio.split(" ").slice(0, 8).join(" ");
    return palabras.charAt(0).toUpperCase() + palabras.slice(1);
  }

  // ------------------------------------------------------------------ intenciones

  var CLASES = {
    CREAR_PRODUCTO: "ACCION", REGISTRAR_VENTA: "ACCION", VER_PRODUCTOS: "NAVEGACION", VER_FERIAS: "NAVEGACION",
    COMPARTIR_TIENDA: "NAVEGACION", CAMBIAR_WHATSAPP: "NAVEGACION", CONSULTAR_VENTAS_MES: "CONSULTA", ENSENAME: "AYUDA",
    SALUDO: "CONVERSACION", RECHAZAR: "CONVERSACION", OTRA_COSA: "CONVERSACION", DESCONOCIDA: "CONVERSACION",
    BUSCAR_PRODUCTO: "NAVEGACION", VER_CARRITO: "NAVEGACION"
  };

  var R = {
    rechazo: /^(no quiero|no voy a|no necesito|ya no quiero|no deseo|no) (registrar|anotar|agregar|subir|vender|cambiar|ocultar|borrar|hacer|crear|publicar|participar)\b/,
    otraCosa: /\b(quiero hacer otra cosa|otra cosa|cambiemos de tema|olvida eso|dejemos eso)\b/,
    como: /\b(como|ensename|ensenar|explicame|explica|que hago|no se como|donde (se )?(cambio|veo|pongo|agrego))\b/,
    consultaVentas: /\b(cuanto (vendi|(he |e |a )?vendido|llevo vendido|llevo|gane|(he |e |a )?ganado)|mis ventas|ventas del mes|vendi este mes)\b/,
    vendi: /\b(vendi|vendimos|se vendio|se vendieron|me compraron|hice una venta|registrar (una )?venta|anotar (una )?venta)\b/,
    crear: new RegExp("\\b(" + VERBOS.join("|") + "|agregame|subeme|ponme|nuevo producto|producto nuevo)\\b"),
    objetoCrear: /\b(producto|productos|algo|cosa|cosas|articulo|articulos)\b|\ben mi tienda\b|\bnuevo\b/,
    verProductos: /\b(mis productos|ver (mis )?productos|muestrame (mis )?productos|mi catalogo)\b/,
    contacto: /\b(whatsapp|wasap|guasap|telefono|numero|celular)\b/,
    cambiar: /\b(cambiar|cambia|cambio|actualizar|poner otro|nuevo)\b/,
    compartir: /\b(compartir|comparte|qr|codigo qr|enlace|link)\b/,
    ferias: /\b(feria|ferias|participar|inscribirme|invitacion|invitaciones)\b/,
    buscar: /\b(busco|buscar|quiero comprar|donde encuentro|hay|venden)\b/,
    carrito: /\b(mi pedido|carrito|mi compra)\b/,
    saludo: /^(hola|buenas|buenos dias|buenas tardes|buenas noches|alo)\b/
  };

  /** Temas «¿Cómo…?» con los nombres de los botones de la web. rol: "todos" o "emprendedora". */
  var TEMAS = {
    AGREGAR_PRODUCTO: { titulo: "Agregar un producto", rol: "emprendedora", clave: /\b(producto|productos|agrego|agregar|subo|subir|publico|publicar|poner)\b/,
      pasos: ["En tu panel toca «Añadir producto».", "Elige una foto con «📁 Elegir foto» (o sigue sin foto).", "Escribe el «Nombre del producto» y el «Precio en pesos».", "Marca «Lo ven todos» si quieres que aparezca en la vitrina.", "Toca «Guardar producto». Antes de eso no se guarda nada."],
      ir: "publicar", etiqueta: "Abrir «Añadir producto»" },
    AGREGAR_FOTO: { titulo: "Agregar una foto", rol: "emprendedora", clave: /\b(foto|fotos|fotografia|imagen|camara)\b/,
      pasos: ["En «Añadir producto» toca «📁 Elegir foto».", "Busca buena luz y deja el producto al centro, sobre un fondo liso.", "En el celular puedes usar la cámara o la galería."],
      ir: "publicar", etiqueta: "Abrir «Añadir producto»" },
    REGISTRAR_VENTA: { titulo: "Registrar una venta", rol: "emprendedora", clave: /\b(venta|ventas|vendi|cobrar|registro)\b/,
      pasos: ["En tu panel toca «Registrar una venta».", "Agrega los productos que vendiste y revisa las cantidades.", "Elige cómo te pagaron y confirma. Las cantidades se descuentan solas."],
      ir: "venta", etiqueta: "Abrir «Registrar una venta»" },
    COMPARTIR_TIENDA: { titulo: "Compartir tu tienda", rol: "emprendedora", clave: /\b(compart|qr|enlace|link|difundir)/,
      pasos: ["En tu panel toca «📱 Código QR y enlace de mi tienda».", "Descarga el QR o copia el enlace para enviarlo por WhatsApp."],
      ir: "qr", etiqueta: "Abrir el QR de mi tienda" },
    PARTICIPAR_FERIA: { titulo: "Participar en una feria", rol: "todos", clave: /\b(feria|ferias|participo|participar|invitacion)\b/,
      pasos: ["Si eres emprendedora, toca «📅 Mis ferias» en tu panel y responde «Participo» o «No participo».", "Las ferias llegan por invitación de la administración.", "Quien compra ve las ferias en «Próximas ferias», con fecha, lugar y cómo llegar."],
      ir: "ferias", etiqueta: "Ver las ferias" },
    CAMBIAR_WHATSAPP: { titulo: "Cambiar tu WhatsApp o tus datos", rol: "emprendedora", clave: /\b(whatsapp|wasap|telefono|numero|celular|datos de (mi )?tienda)\b/,
      pasos: ["En tu panel toca «🏪 Mi tienda: datos y contacto».", "Cambia el número o los datos y guarda."],
      ir: "tienda", etiqueta: "Abrir «Mi tienda»" },
    TEXTO_Y_VOZ: { titulo: "Agrandar la letra o escuchar", rol: "todos", clave: /\b(letra|letras|agrand[a-z]*|tamano del texto|voz alta|escuch[a-z]*|lectura|leer|contraste)\b/,
      pasos: ["Arriba de la página, en «Letra», toca «A+» o «A++».", "«Contraste» deja los colores más marcados.", "En la guía de «Añadir producto» y en estas respuestas puedes tocar «🔊 Escuchar»."],
      ir: null },
    COMPRAR: { titulo: "Pedir un producto", rol: "todos", clave: /\b(comprar|compro|pedido|pedir|encargar)\b/,
      pasos: ["Busca el producto en la vitrina (puedes usar 🎤 para buscar por voz).", "Agrégalo a «Mi pedido».", "En «Mi pedido», cada tienda recibe solo sus productos por WhatsApp o mensaje."],
      ir: "buscar", etiqueta: "Ir a la vitrina" }
  };
  var ORDEN_TEMAS = ["AGREGAR_FOTO", "CAMBIAR_WHATSAPP", "COMPARTIR_TIENDA", "PARTICIPAR_FERIA", "REGISTRAR_VENTA", "TEXTO_Y_VOZ", "COMPRAR", "AGREGAR_PRODUCTO"];

  function temaDe(t) {
    for (var i = 0; i < ORDEN_TEMAS.length; i++) if (TEMAS[ORDEN_TEMAS[i]].clave.test(t)) return ORDEN_TEMAS[i];
    return null;
  }

  /** Interpreta una frase. Devuelve { intencion, clase, datos: { nombre, precio, busqueda }, tema }. */
  function interpretar(frase) {
    var t = normalizar(frase);
    var r = function (intencion, datos, tema) { return { intencion: intencion, clase: CLASES[intencion], datos: datos || {}, tema: tema || null }; };
    if (!t) return r("DESCONOCIDA");
    if (R.rechazo.test(t)) return r("RECHAZAR");
    if (R.otraCosa.test(t)) return r("OTRA_COSA");
    if (R.como.test(t)) { var tema = temaDe(t); if (tema) return r("ENSENAME", {}, tema); }
    if (R.consultaVentas.test(t)) return r("CONSULTAR_VENTAS_MES");
    if (R.vendi.test(t)) return r("REGISTRAR_VENTA");
    var n = nombre(frase), p = precio(frase);
    if (R.crear.test(t) && (R.objetoCrear.test(t) || n || p)) return r("CREAR_PRODUCTO", { nombre: n, precio: p });
    if (R.verProductos.test(t)) return r("VER_PRODUCTOS");
    if (R.contacto.test(t) && R.cambiar.test(t)) return r("CAMBIAR_WHATSAPP");
    if (R.compartir.test(t)) return r("COMPARTIR_TIENDA");
    if (R.ferias.test(t)) return r("VER_FERIAS");
    if (R.carrito.test(t)) return r("VER_CARRITO");
    if (R.buscar.test(t)) {
      var q = t.replace(/^.*?\b(busco|buscar|quiero comprar|donde encuentro|hay|venden)\b/, "").replace(/^\s*(un|una|unos|unas|el|la|los|las)\s+/, "").trim();
      if (q) return r("BUSCAR_PRODUCTO", { busqueda: q });
    }
    if (R.como.test(t)) return r("ENSENAME");
    if (R.saludo.test(t)) return r("SALUDO");
    return r("DESCONOCIDA");
  }

  // ------------------------------------------------------------------ respuestas (sin DOM)

  function pesos(n) { return "$" + String(n).replace(/\B(?=(\d{3})+(?!\d))/g, "."); }

  /**
   * Qué responder y qué hacer. esEmprendedora decide qué se ofrece.
   * Devuelve { texto, pasos, opciones: [{ etiqueta, accion, datos }], accion, datos }.
   * Las acciones son solo de navegación o de llenar «Añadir producto»; nada guarda.
   */
  function responder(frase, esEmprendedora) {
    var r = interpretar(frase);
    var soloEmp = function (texto) {
      return { texto: texto + " Para eso entra con tu cuenta de emprendedora.", opciones: [{ etiqueta: "Entrar como emprendedora", accion: "entrar" }] };
    };
    switch (r.intencion) {
      case "CREAR_PRODUCTO": {
        if (!esEmprendedora) return soloEmp("Agregar productos es para las emprendedoras.");
        var d = r.datos, partes = [];
        if (d.nombre) partes.push("nombre «" + d.nombre + "»");
        if (d.precio) partes.push("precio " + pesos(d.precio));
        return { texto: (partes.length ? "Abro «Añadir producto» con " + partes.join(" y ") + ". " : "Abro «Añadir producto». ") +
          "Revisa, agrega la foto y toca «Guardar producto»: antes de eso no se guarda nada.", accion: "publicar", datos: d };
      }
      case "REGISTRAR_VENTA":
        if (!esEmprendedora) return soloEmp("Registrar ventas es para las emprendedoras.");
        return { texto: "Te abro «Registrar una venta». Agrega los productos y confirma el pago: ahí se anota. Yo no anoto ventas por mi cuenta.", accion: "venta" };
      case "CONSULTAR_VENTAS_MES":
        return { texto: esEmprendedora ? "El resumen de tus ventas está en tu panel y en la app Android («¿Cuánto vendí este mes?» en el Asistente Alerce)." : "Las ventas solo las ve cada emprendedora en su cuenta." };
      case "VER_PRODUCTOS":
        if (!esEmprendedora) return { texto: "Te llevo a la vitrina.", accion: "buscar", datos: { busqueda: "" } };
        return { texto: "Te abro «Mis productos».", accion: "productos" };
      case "COMPARTIR_TIENDA":
        if (!esEmprendedora) return { texto: "Para compartir una tienda, ábrela en la vitrina y toca «Compartir» o «Código QR»." };
        return { texto: "Te abro el código QR y el enlace de tu tienda.", accion: "qr" };
      case "CAMBIAR_WHATSAPP":
        if (!esEmprendedora) return soloEmp("Los datos de contacto son de cada tienda.");
        return { texto: "Te abro «Mi tienda: datos y contacto». Cambia el número y guarda.", accion: "tienda" };
      case "VER_FERIAS":
        return esEmprendedora ? { texto: "Te abro «Mis ferias»: ahí respondes «Participo» o «No participo».", accion: "misFerias" }
          : { texto: "Te llevo a «Próximas ferias», con fecha, lugar y cómo llegar.", accion: "ferias" };
      case "BUSCAR_PRODUCTO":
        return { texto: "Busco «" + r.datos.busqueda + "» en la vitrina.", accion: "buscar", datos: r.datos };
      case "VER_CARRITO":
        return { texto: "Te abro «Mi pedido».", accion: "carrito" };
      case "ENSENAME": {
        var tema = r.tema && TEMAS[r.tema];
        if (!tema) return { texto: "Te puedo explicar estas cosas. ¿Cuál necesitas?", opciones: opcionesTemas(esEmprendedora) };
        if (tema.rol === "emprendedora" && !esEmprendedora) return soloEmp("Eso se hace desde el panel de la emprendedora.");
        var op = tema.ir ? [{ etiqueta: tema.etiqueta, accion: tema.ir }] : [];
        if (r.tema === "AGREGAR_PRODUCTO" || r.tema === "AGREGAR_FOTO") op.push({ etiqueta: "🧭 Guíame paso a paso", accion: "guia" });
        return { texto: "Así se hace: " + tema.titulo.toLowerCase() + ".", pasos: tema.pasos, opciones: op };
      }
      case "RECHAZAR": case "OTRA_COSA":
        return { texto: "Está bien, no hago nada. ¿Qué necesitas?", opciones: opcionesTemas(esEmprendedora) };
      case "SALUDO":
        return { texto: "Hola. ¿En qué te ayudo?", opciones: opcionesTemas(esEmprendedora) };
      default:
        return { texto: "No entendí bien, o es algo que no puedo hacer aquí. Puedo explicarte cómo hacer algo o llevarte a la pantalla.", opciones: opcionesTemas(esEmprendedora) };
    }
  }

  function opcionesTemas(esEmprendedora) {
    var lista = esEmprendedora
      ? [["Agregar un producto", "¿Cómo agrego un producto?"], ["Registrar una venta", "¿Cómo registro una venta?"], ["Compartir mi tienda", "¿Cómo comparto mi tienda?"], ["Mis ferias", "¿Cómo participo en una feria?"], ["Letra y voz", "¿Cómo agrando la letra?"]]
      : [["Pedir un producto", "¿Cómo compro un producto?"], ["Ver las ferias", "¿Cómo participo en una feria?"], ["Letra y voz", "¿Cómo agrando la letra?"]];
    return lista.map(function (x) { return { etiqueta: x[0], frase: x[1] }; });
  }

  // ------------------------------------------------------------------ interfaz

  /**
   * ctx: { documento, ventana, contenedor (elemento donde va), esEmprendedora(), voz (AlerceVoz), lectura (AlerceLectura),
   *        acciones: { publicar(datos), venta(), productos(), qr(), tienda(), misFerias(), ferias(), buscar(texto), carrito(), entrar(), guia() } }
   */
  function iniciar(ctx) {
    var doc = ctx.documento, win = ctx.ventana, cont = ctx.contenedor;
    if (!doc || !cont) return null;
    var lector = ctx.lectura && ctx.lectura.crear ? ctx.lectura.crear(win) : null;
    var n = 0;

    function el(tag, attrs, hijos) {
      var e = doc.createElement(tag);
      Object.keys(attrs || {}).forEach(function (k) { if (k === "text") e.textContent = attrs[k]; else e.setAttribute(k, attrs[k]); });
      (hijos || []).forEach(function (h) { if (h) e.appendChild(h); });
      return e;
    }

    var registro = el("div", { id: "aw-conversacion", class: "aw-conversacion", role: "log", "aria-live": "polite", "aria-label": "Conversación con el asistente" });
    var entrada = el("input", { id: "aw-entrada", type: "text", class: "aw-entrada", autocomplete: "off", enterkeyhint: "send", placeholder: "Ej: ¿cómo registro una venta?" });
    var mic = el("button", { type: "button", id: "aw-mic", class: "btn-buscar-voz aw-mic", hidden: "hidden" }, [el("span", { "aria-hidden": "true", text: "🎤" })]);
    var enviarBtn = el("button", { type: "submit", class: "btn-accion-pill btn-negro aw-enviar", text: "Enviar" });
    var estado = el("p", { id: "aw-estado", class: "aw-estado", role: "status" });
    var form = el("form", { id: "aw-form", class: "aw-form" }, [
      el("label", { for: "aw-entrada", class: "aw-etiqueta", text: "Escribe o dicta lo que necesitas" }),
      el("div", { class: "aw-fila" }, [entrada, mic, enviarBtn])
    ]);
    var panel = el("div", { id: "asistente-web", class: "tarjeta-tienda aw-panel" }, [
      el("h3", { class: "aw-titulo", id: "aw-titulo", text: "🗣 Asistente Alerce" }),
      el("p", { class: "aw-ayuda", text: "Pregúntame cómo hacer algo o pídeme que te lleve a una pantalla. No guardo ni cambio nada por mi cuenta. Interpreta con reglas en tu navegador (no es inteligencia artificial)." }),
      registro, form, estado
    ]);
    panel.setAttribute("role", "region");
    panel.setAttribute("aria-labelledby", "aw-titulo");
    cont.appendChild(panel);

    function hacer(accion, datos) {
      var a = ctx.acciones || {};
      if (accion && typeof a[accion] === "function") a[accion](datos || {});
    }

    function mostrar(resp) {
      // Las opciones viejas dejan de valer.
      Array.prototype.forEach.call(registro.querySelectorAll(".aw-opciones"), function (o) { o.parentNode.removeChild(o); });
      var id = "aw-m" + (++n);
      var burbuja = el("div", { class: "aw-burbuja aw-asistente", id: id }, [el("p", { text: resp.texto })]);
      if (resp.pasos && resp.pasos.length) {
        var ol = el("ol", { class: "aw-pasos" });
        resp.pasos.forEach(function (p) { ol.appendChild(el("li", { text: p })); });
        burbuja.appendChild(ol);
      }
      var paraLeer = resp.texto + " " + (resp.pasos || []).map(function (p, i) { return "Paso " + (i + 1) + ": " + p; }).join(" ");
      if (lector && lector.disponible && lector.hayVozEspanol() !== false) {
        var b = el("button", { type: "button", class: "btn-accion-pill btn-secundario aw-escuchar", text: "🔊 Escuchar", "aria-label": "Escuchar la respuesta en voz alta" });
        var pintar = function (leyendo) { b.textContent = leyendo ? "⏹ Detener" : "🔊 Escuchar"; b.setAttribute("aria-label", leyendo ? "Detener la lectura" : "Escuchar la respuesta en voz alta"); };
        b.addEventListener("click", function () {
          if (lector.leyendo() === id) { lector.detener(); return; }
          if (!lector.hablar(id, paraLeer, pintar)) pintar(false);
        });
        burbuja.appendChild(b);
      }
      if (resp.opciones && resp.opciones.length) {
        var caja = el("div", { class: "aw-opciones", role: "group", "aria-label": "Opciones" });
        resp.opciones.forEach(function (o) {
          var bo = el("button", { type: "button", class: "btn-accion-pill", text: o.etiqueta });
          bo.addEventListener("click", function () { if (o.frase) enviar(o.frase, o.etiqueta); else { hacer(o.accion, o.datos); } });
          caja.appendChild(bo);
        });
        burbuja.appendChild(caja);
      }
      registro.appendChild(burbuja);
      // La conversación baja hasta la última respuesta (dentro de su propia caja, sin mover la página).
      registro.scrollTop = registro.scrollHeight;
    }

    function enviar(texto, mostrarComo) {
      var limpio = String(texto || "").trim();
      if (!limpio) return;
      registro.appendChild(el("div", { class: "aw-burbuja aw-persona", "aria-label": "Tú: " + (mostrarComo || limpio) }, [el("p", { text: mostrarComo || limpio })]));
      entrada.value = "";
      var resp = responder(limpio, !!(ctx.esEmprendedora && ctx.esEmprendedora()));
      mostrar(resp);
      if (resp.accion) hacer(resp.accion, resp.datos);
    }

    form.addEventListener("submit", function (e) { e.preventDefault(); enviar(entrada.value); });

    if (ctx.voz && ctx.voz.conectar) {
      ctx.voz.conectar({ campo: entrada, boton: mic, estado: estado, ventana: win, etiqueta: "Hablar con el asistente",
        textos: { escuchando: "Escuchando… di lo que necesitas.", aplicado: function (t) { return "Escuché: «" + t + "»."; },
          cancelado: "Dictado cancelado.", sinVoz: "No escuché nada. Toca el micrófono para intentar de nuevo o escribe." },
        alTerminar: function (t) { enviar(t); } });
    }

    var esEmp = function () { return !!(ctx.esEmprendedora && ctx.esEmprendedora()); };
    var saludo = function () { return { texto: "Hola. Puedo explicarte cómo hacer algo en Alerce o llevarte a la pantalla.", opciones: opcionesTemas(esEmp()) }; };
    mostrar(saludo());
    // La sesión se confirma después de armar la página: cuando el panel aparece en pantalla y nadie ha
    // escrito todavía, el saludo se rehace con el rol de ese momento (emprendedora o quien compra).
    var rolDelSaludo = esEmp();
    if (win && win.IntersectionObserver) {
      var obs = new win.IntersectionObserver(function (entradas) {
        if (!entradas.some(function (e) { return e.isIntersecting; })) return;
        if (registro.children.length === 1 && esEmp() !== rolDelSaludo) { registro.innerHTML = ""; rolDelSaludo = esEmp(); mostrar(saludo()); }
      });
      obs.observe(panel);
    }
    return { enviar: enviar, panel: panel };
  }

  return { normalizar: normalizar, aDigitos: aDigitos, precio: precio, nombre: nombre, interpretar: interpretar, responder: responder, TEMAS: TEMAS, CLASES: CLASES, iniciar: iniciar };
});
