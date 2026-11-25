/**
 * Entrega 6 — Vitrina, modo comprador, difusión y mapas (web).
 *
 *  - Directorio de emprendedoras y asociaciones con búsqueda y 3 filtros (RF-CMP-01).
 *  - Ficha pública de asociación con integrantes y acceso a sus tiendas (RF-ASO-03).
 *  - Contacto según la configuración de cada emprendedora: WhatsApp, formulario o ambos (RF-CMP-05).
 *  - Ferias del servidor con mapa y "Cómo llegar" (RF-MAP-02).
 *  - Punto de venta o retiro en la tienda (RF-MAP-04).
 *  - Enlaces ?producto= ?asociacion= ?feria= que abren su página sin sesión (RF-COM-03).
 *  - Administración: navegación anónima de la app (RF-ADM-02), ferias con ubicación
 *    marcada en el mapa o buscando la dirección (RF-FER-01, RF-MAP-01) y asociaciones.
 *
 * Usa el contexto que entrega main.js (TramaWeb) y solo escribe datos por js/servicio.js.
 * Los textos que vienen del servidor se insertan como texto (nunca como HTML).
 */
(function () {
  "use strict";

  var ctx = null;
  var ASOCIACIONES = [];
  var FERIAS = [];
  var estadoFerias = { cargando: false, error: null, cargado: false };
  var estadoAsoc = { cargando: false, error: null, cargado: false };
  var vistaDirectorio = "emprendedoras";
  var mapas = {};               // id de contenedor → mapa Leaflet
  var promesaDatos = Promise.resolve();

  var URL_PUBLICA = "https://diego-733.github.io/EmprendedoresPuertoMontt/";
  var CENTRO_PUERTO_MONTT = [-41.4693, -72.9424];

  var ASOCIACIONES_DEMO = [
    { id: "as1", nombre: "Manos del Sur", sector: "Alerce", localidad: "Puerto Montt", activa: true, whatsapp: "",
      descripcion: "Red de artesanas y productoras de Alerce que venden juntas en ferias comunales." },
    { id: "as2", nombre: "Red del Lago", sector: "Alerce Sur", localidad: "Puerto Montt", activa: true, whatsapp: "",
      descripcion: "Agrupación de emprendedoras gastronómicas de la cuenca del lago Llanquihue." }
  ];

  function hoyIso(desplazarDias) {
    var d = new Date();
    d.setDate(d.getDate() + (desplazarDias || 0));
    return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
  }

  function feriasDemo() {
    return [
      { id: "feria_alerce_plaza", nombre: "Feria de Emprendedoras de Alerce", descripcion: "Tejidos, panadería artesanal y productos de campo.",
        ubicacion: "Plaza de Alerce, Puerto Montt", fecha_inicio: hoyIso(4), fecha_fin: hoyIso(4), horario: "10:00 a 18:00",
        organizador: "Municipalidad de Puerto Montt", estado: "PROXIMA", latitud: -41.3959, longitud: -72.8955 },
      { id: "feria_costanera_pm", nombre: "Mercado Costanera Puerto Montt", descripcion: "Artesanía, diseño local y gastronomía sureña.",
        ubicacion: "Costanera de Puerto Montt", fecha_inicio: hoyIso(11), fecha_fin: hoyIso(12), horario: "11:00 a 19:00",
        organizador: "Red Trama", estado: "PROXIMA", latitud: -41.4717, longitud: -72.9396 }
    ].map(function (f) { return ctx.Cat.normalizarFeria(f); });
  }

  /** Dirección pública de la vitrina (la del sitio publicado o, en local, la oficial). */
  function urlBase() {
    var l = window.location;
    if (/^https:$/.test(l.protocol) && !/^(localhost|127\.)/.test(l.hostname)) {
      return l.origin + l.pathname.replace(/index\.html$/, "");
    }
    return URL_PUBLICA;
  }

  function urlDe(tipo, id) {
    return urlBase() + "?" + tipo + "=" + encodeURIComponent(id);
  }

  function el(id) { return document.getElementById(id); }
  function h() { return ctx.h.apply(null, arguments); }
  function toast(m) { ctx.mostrarToast(m); }
  function real() { return ctx.modo === "real" && !!ctx.servicio; }

  function fechaLegible(iso) {
    if (!iso) return "Fecha por confirmar";
    var p = String(iso).slice(0, 10).split("-");
    var d = new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
    if (isNaN(d.getTime())) return String(iso);
    return d.toLocaleDateString("es-CL", { weekday: "long", day: "numeric", month: "long" });
  }

  function rangoFechas(f) {
    if (!f.fechaInicio) return "Fecha por confirmar";
    if (f.fechaFin && String(f.fechaFin).slice(0, 10) !== String(f.fechaInicio).slice(0, 10)) {
      return fechaLegible(f.fechaInicio) + " al " + fechaLegible(f.fechaFin);
    }
    return fechaLegible(f.fechaInicio);
  }

  // ================================================================ Mapas (Leaflet)

  function crearMapa(idContenedor, lat, lng, opciones) {
    opciones = opciones || {};
    var caja = el(idContenedor);
    if (!caja) return null;
    if (mapas[idContenedor]) { try { mapas[idContenedor].remove(); } catch (e) { /* ya quitado */ } delete mapas[idContenedor]; }
    if (typeof window.L === "undefined") {
      caja.textContent = "";
      caja.appendChild(h("p", { class: "e6-aviso", text: "No se pudo cargar el mapa. Usa «Cómo llegar» para verlo en la aplicación de mapas." }));
      return null;
    }
    var centro = (lat !== null && lat !== undefined) ? [lat, lng] : CENTRO_PUERTO_MONTT;
    var mapa = window.L.map(caja, { scrollWheelZoom: false }).setView(centro, lat !== null && lat !== undefined ? 15 : 12);
    window.L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(mapa);
    var marcador = null;
    if (lat !== null && lat !== undefined) {
      marcador = window.L.marker([lat, lng], { draggable: !!opciones.editable }).addTo(mapa);
      if (opciones.etiqueta) marcador.bindPopup(opciones.etiqueta);
    }
    mapas[idContenedor] = mapa;
    // El modal termina su animación después: se recalcula el tamaño.
    setTimeout(function () { mapa.invalidateSize(); }, 250);
    setTimeout(function () { mapa.invalidateSize(); }, 600);
    return { mapa: mapa, marcador: marcador };
  }

  function botonComoLlegar(lat, lng, nombre) {
    var url = ctx.Cat.urlComoLlegar(lat, lng);
    if (!url) return null;
    return h("a", {
      href: url, target: "_blank", rel: "noopener", class: "btn-accion-pill btn-negro e6-como-llegar",
      "aria-label": "Cómo llegar a " + nombre, text: "🧭 Cómo llegar"
    });
  }

  // ================================================================ Compartir

  function compartir(tipo, id, nombre) {
    var url = urlDe(tipo, id);
    var texto = "Mira " + (nombre || "esto") + " en la vitrina de Alerce";
    if (navigator.share) {
      navigator.share({ title: nombre || "Alerce Emprende", text: texto, url: url }).catch(function () { /* cancelado */ });
      return;
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(url).then(function () { toast("Enlace copiado: " + url); }, function () { window.prompt("Copia el enlace:", url); });
    } else {
      window.prompt("Copia el enlace:", url);
    }
  }

  // ================================================================ Datos

  var PERTENENCIAS = null;      // tiendaId → [asociacionId]; null si el servidor aún no tiene 07

  function aplicarPertenencias() {
    if (!PERTENENCIAS) return;
    ctx.tiendas().forEach(function (t) { t.asociaciones = PERTENENCIAS[t.id] || []; });
  }

  function cargarPertenencias() {
    if (!real() || !ctx.servicio.listarPertenencias) return Promise.resolve();
    return ctx.servicio.listarPertenencias().then(function (r) {
      if (!r.ok) { PERTENENCIAS = null; return; }   // sin 07: se usa tiendas.asociacion_id
      PERTENENCIAS = {};
      (r.datos || []).forEach(function (p) { (PERTENENCIAS[p.tienda_id] = PERTENENCIAS[p.tienda_id] || []).push(p.asociacion_id); });
      aplicarPertenencias();
    });
  }

  function cargarAsociaciones() {
    if (!real()) {
      ASOCIACIONES = ctx.modo === "demo" ? ASOCIACIONES_DEMO.slice() : [];
      estadoAsoc = { cargando: false, error: null, cargado: true };
      return Promise.resolve();
    }
    estadoAsoc.cargando = true;
    cargarPertenencias();
    return ctx.servicio.listarAsociaciones().then(function (r) {
      if (r.ok) {
        ASOCIACIONES = (r.datos || []).map(function (a) {
          return {
            id: String(a.id), nombre: String(a.nombre || "Asociación"), sector: String(a.sector || ""),
            localidad: String(a.localidad || ""), descripcion: String(a.descripcion || ""),
            whatsapp: String(a.whatsapp || ""), activa: a.activa !== false
          };
        });
        estadoAsoc = { cargando: false, error: null, cargado: true };
      } else {
        estadoAsoc = { cargando: false, error: r.error, cargado: estadoAsoc.cargado };
      }
    });
  }

  function cargarFerias() {
    if (!real()) {
      FERIAS = ctx.modo === "demo" ? feriasDemo() : [];
      estadoFerias = { cargando: false, error: null, cargado: true };
      return Promise.resolve();
    }
    estadoFerias.cargando = true;
    pintarFerias();
    return ctx.servicio.listarFeriasCompletas().then(function (r) {
      if (r.ok) {
        FERIAS = (r.datos || []).map(ctx.Cat.normalizarFeria);
        estadoFerias = { cargando: false, error: null, cargado: true };
      } else {
        estadoFerias = { cargando: false, error: r.error, cargado: estadoFerias.cargado };
      }
    });
  }

  function asociacionesActivas() {
    return ASOCIACIONES.filter(function (a) { return a.activa !== false; });
  }

  function integrantesDe(asocId) {
    return ctx.tiendas().filter(function (t) { return ctx.Cat.perteneceA(t, asocId); });
  }

  function asociacionesDe(tienda) {
    var ids = Array.isArray(tienda.asociaciones) && tienda.asociaciones.length ? tienda.asociaciones : (tienda.asociacionId ? [tienda.asociacionId] : []);
    return ids.map(function (id) { return ASOCIACIONES.find(function (a) { return a.id === id; }); }).filter(Boolean);
  }

  function nombreAsociacion(id) {
    if (!id) return "";
    var a = ASOCIACIONES.find(function (x) { return x.id === id; });
    return a ? a.nombre : "";
  }

  // ================================================================ Directorio y filtros

  function opciones(select, valores, textoTodos, actual) {
    if (!select) return;
    select.textContent = "";
    select.appendChild(h("option", { value: "", text: textoTodos }));
    valores.forEach(function (v) {
      select.appendChild(h("option", { value: v.valor, text: v.texto }));
    });
    select.value = valores.some(function (v) { return v.valor === actual; }) ? actual : "";
  }

  function unicos(lista) {
    var vistos = {};
    return lista.filter(function (x) { if (!x || vistos[x]) return false; vistos[x] = true; return true; })
      .sort(function (a, b) { return a.localeCompare(b, "es"); });
  }

  function poblarFiltros() {
    var tiendas = ctx.tiendas();
    var tipo = el("e6-filtro-tipo"), sector = el("e6-filtro-sector"), asoc = el("e6-filtro-asociacion");
    opciones(tipo, unicos(tiendas.map(function (t) { return t.rubro; })).map(function (v) { return { valor: v, texto: v }; }), "Todos", tipo ? tipo.value : "");
    opciones(sector, unicos(tiendas.map(function (t) { return t.sector; })).map(function (v) { return { valor: v, texto: v }; }), "Todos", sector ? sector.value : "");
    var listaAsoc = asociacionesActivas().map(function (a) { return { valor: a.id, texto: a.nombre }; });
    listaAsoc.push({ valor: "__ninguna", texto: "Sin asociación" });
    opciones(asoc, listaAsoc, "Todas", asoc ? asoc.value : "");
  }

  function aplicarFiltros() {
    ctx.fijarFiltros({
      tipo: el("e6-filtro-tipo") ? el("e6-filtro-tipo").value : "",
      sector: el("e6-filtro-sector") ? el("e6-filtro-sector").value : "",
      asociacion: el("e6-filtro-asociacion") ? el("e6-filtro-asociacion").value : ""
    });
    if (vistaDirectorio === "asociaciones") pintarAsociacionesDirectorio();
  }

  function sincronizarBusqueda(valor, origen) {
    var principal = el("campo-busqueda-principal");
    var propia = el("e6-busqueda");
    if (origen !== principal && principal && principal.value !== valor) {
      principal.value = valor;
      principal.dispatchEvent(new Event("input"));
    }
    if (origen !== propia && propia && propia.value !== valor) propia.value = valor;
    if (vistaDirectorio === "asociaciones") pintarAsociacionesDirectorio();
  }

  function configurarFiltros() {
    ["e6-filtro-tipo", "e6-filtro-sector", "e6-filtro-asociacion"].forEach(function (id) {
      var s = el(id);
      if (s) s.addEventListener("change", aplicarFiltros);
    });
    var propia = el("e6-busqueda");
    if (propia) propia.addEventListener("input", function () { sincronizarBusqueda(propia.value, propia); });
    var principal = el("campo-busqueda-principal");
    if (principal) principal.addEventListener("input", function () { sincronizarBusqueda(principal.value, principal); });
    var limpiar = el("e6-limpiar-filtros");
    if (limpiar) limpiar.addEventListener("click", function () {
      ["e6-filtro-tipo", "e6-filtro-sector", "e6-filtro-asociacion"].forEach(function (id) { if (el(id)) el(id).value = ""; });
      sincronizarBusqueda("", null);
      aplicarFiltros();
    });
  }

  function verDirectorio(vista) {
    vistaDirectorio = vista === "asociaciones" ? "asociaciones" : "emprendedoras";
    var esAsoc = vistaDirectorio === "asociaciones";
    var tE = el("e6-tab-emprendedoras"), tA = el("e6-tab-asociaciones");
    if (tE) { tE.classList.toggle("activo", !esAsoc); tE.setAttribute("aria-selected", String(!esAsoc)); }
    if (tA) { tA.classList.toggle("activo", esAsoc); tA.setAttribute("aria-selected", String(esAsoc)); }
    var grilla = el("grilla-tiendas-marketplace"), lista = el("e6-lista-asociaciones-directorio");
    var aviso = el("aviso-sin-tiendas"), subtitulo = el("subtitulo-conteo-tiendas");
    if (grilla) grilla.style.display = esAsoc ? "none" : "";
    if (aviso && esAsoc) aviso.style.display = "none";
    if (lista) lista.style.display = esAsoc ? "" : "none";
    if (subtitulo && esAsoc) subtitulo.textContent = "Asociaciones y redes de emprendedoras de Alerce y Puerto Montt.";
    if (esAsoc) pintarAsociacionesDirectorio();
    else ctx.fijarFiltros({
      tipo: el("e6-filtro-tipo") ? el("e6-filtro-tipo").value : "",
      sector: el("e6-filtro-sector") ? el("e6-filtro-sector").value : "",
      asociacion: el("e6-filtro-asociacion") ? el("e6-filtro-asociacion").value : ""
    });
  }

  function tarjetaAsociacion(a) {
    var socias = integrantesDe(a.id);
    return h("article", { class: "tarjeta-tienda e6-tarjeta-asociacion" }, [
      h("span", { class: "badge-necesidad badge-pedido", style: "align-self: flex-start;", text: "Asociación" }),
      h("h3", { class: "tarjeta-nombre", text: a.nombre }),
      h("p", { class: "tarjeta-sub", text: [a.sector, a.localidad].filter(Boolean).join(" · ") + " · " + socias.length + (socias.length === 1 ? " integrante" : " integrantes") }),
      a.descripcion ? h("p", { class: "tarjeta-descripcion", text: a.descripcion }) : null,
      h("div", { class: "tarjeta-acciones-fila" }, [
        h("button", { type: "button", class: "btn-accion-pill btn-negro", text: "Ver integrantes", onclick: function () { abrirAsociacion(a.id); } }),
        h("button", { type: "button", class: "btn-accion-pill", text: "🔗 Compartir", onclick: function () { compartir("asociacion", a.id, a.nombre); } })
      ])
    ]);
  }

  function filtrarAsociaciones() {
    var texto = (el("e6-busqueda") && el("e6-busqueda").value || "").toLowerCase().trim();
    var filtroAsoc = el("e6-filtro-asociacion") ? el("e6-filtro-asociacion").value : "";
    var filtroSector = el("e6-filtro-sector") ? el("e6-filtro-sector").value : "";
    var filtroTipo = el("e6-filtro-tipo") ? el("e6-filtro-tipo").value : "";
    return asociacionesActivas().filter(function (a) {
      if (filtroAsoc && filtroAsoc !== "__ninguna" && a.id !== filtroAsoc) return false;
      if (filtroAsoc === "__ninguna") return false;
      var socias = integrantesDe(a.id);
      if (filtroSector && a.sector !== filtroSector && !socias.some(function (t) { return t.sector === filtroSector; })) return false;
      if (filtroTipo && !socias.some(function (t) { return t.rubro === filtroTipo; })) return false;
      if (!texto) return true;
      return [a.nombre, a.sector, a.descripcion].join(" ").toLowerCase().indexOf(texto) >= 0 ||
        socias.some(function (t) { return t.nombre.toLowerCase().indexOf(texto) >= 0; });
    });
  }

  function pintarListaAsociaciones(contenedor, lista) {
    contenedor.textContent = "";
    if (estadoAsoc.error && !estadoAsoc.cargado) {
      contenedor.appendChild(h("p", { class: "e6-aviso", role: "alert", text: "No se pudieron cargar las asociaciones. " + ctx.Cat.mensajeError(estadoAsoc.error) }));
      return;
    }
    if (estadoAsoc.cargando && !estadoAsoc.cargado) {
      contenedor.appendChild(h("p", { class: "subtitulo-seccion", text: "Cargando asociaciones…" }));
      return;
    }
    if (!lista.length) {
      contenedor.appendChild(h("p", { class: "subtitulo-seccion", text: "No hay asociaciones que coincidan." }));
      return;
    }
    lista.forEach(function (a) { contenedor.appendChild(tarjetaAsociacion(a)); });
  }

  function pintarAsociacionesDirectorio() {
    var c = el("e6-lista-asociaciones-directorio");
    if (c) pintarListaAsociaciones(c, filtrarAsociaciones());
  }

  function pintarAsociaciones() {
    var c = el("grilla-asociaciones-container");
    if (c) pintarListaAsociaciones(c, asociacionesActivas());
    if (vistaDirectorio === "asociaciones") pintarAsociacionesDirectorio();
  }

  function abrirAsociacion(id) {
    var a = ASOCIACIONES.find(function (x) { return x.id === id; });
    if (!a) return false;
    var titulo = el("modal-asociacion-titulo"), cuerpo = el("modal-asociacion-cuerpo");
    if (!cuerpo) return false;
    if (titulo) titulo.textContent = a.nombre;
    cuerpo.textContent = "";
    var socias = integrantesDe(a.id);
    cuerpo.appendChild(h("p", { class: "tarjeta-sub", text: [a.sector, a.localidad].filter(Boolean).join(" · ") }));
    if (a.descripcion) cuerpo.appendChild(h("p", { style: "font-size: 13.5px; line-height: 1.5;", text: a.descripcion }));
    cuerpo.appendChild(h("h3", { class: "e6-subtitulo", text: "Integrantes (" + socias.length + ")" }));
    if (!socias.length) {
      cuerpo.appendChild(h("p", { class: "subtitulo-seccion", text: "Esta asociación aún no tiene integrantes en la vitrina." }));
    }
    var lista = h("div", { class: "e6-lista-integrantes" });
    socias.forEach(function (t) {
      var logo = ctx.imagenSegura(t.logoUrl, "", "avatar-circulo", "object-fit: cover;") ||
        h("div", { class: "avatar-circulo", style: "background-color: " + ctx.Cat.colorHex(t.colorLogo, "#2B6CB0") + ";", "aria-hidden": "true", text: t.nombre.charAt(0).toUpperCase() });
      lista.appendChild(h("div", { class: "e6-integrante" }, [
        logo,
        h("div", { class: "e6-integrante-texto" }, [
          h("strong", { text: t.nombre }),
          h("span", { text: [t.rubro, t.sector].filter(Boolean).join(" · ") })
        ]),
        h("button", {
          type: "button", class: "btn-accion-pill btn-negro", text: "Ver tienda",
          "aria-label": "Ver la tienda " + t.nombre,
          onclick: function () { ctx.cerrarModal("modal-asociacion"); ctx.abrirVitrinaTienda(t.id); }
        })
      ]));
    });
    cuerpo.appendChild(lista);
    cuerpo.appendChild(h("div", { class: "e6-acciones-tienda" }, [
      window.AlerceFerias7 ? window.AlerceFerias7.botonSeguir("ASOCIACION", a.id, a.nombre) : null,
      h("button", { type: "button", class: "btn-accion-pill", text: "🔗 Compartir asociación", onclick: function () { compartir("asociacion", a.id, a.nombre); } })
    ]));
    ctx.abrirModal("modal-asociacion");
    return true;
  }

  // ================================================================ Tienda: extras

  function pintarExtrasTienda(tienda) {
    var c = el("modal-tienda-extras");
    if (!c) return;
    c.textContent = "";
    if (window.AlerceFerias7) c.appendChild(window.AlerceFerias7.botonSeguir("TIENDA", tienda.id, tienda.nombre));
    asociacionesDe(tienda).forEach(function (asoc) {
      c.appendChild(h("button", {
        type: "button", class: "e6-chip-asociacion", text: "🤝 Integrante de " + asoc.nombre,
        onclick: function () { ctx.cerrarModal("modal-tienda-vitrina"); abrirAsociacion(asoc.id); }
      }));
    });
    if (tienda.mostrarPrecios === false) {
      c.appendChild(h("p", { class: "e6-nota", text: "Esta tienda muestra «Consultar precio»: escríbele para saber el valor." }));
    }
    var punto = tienda.puntoRetiro;
    if (punto) {
      c.appendChild(h("div", { class: "e6-punto" }, [
        h("h3", { class: "e6-subtitulo", text: "📍 Punto de venta o retiro" + (punto.nombre ? ": " + punto.nombre : "") }),
        h("div", { id: "mapa-punto-tienda", class: "e6-mapa e6-mapa-chico", role: "region", "aria-label": "Mapa del punto de venta o retiro" }),
        botonComoLlegar(punto.lat, punto.lng, punto.nombre || tienda.nombre)
      ]));
      setTimeout(function () { crearMapa("mapa-punto-tienda", punto.lat, punto.lng, { etiqueta: punto.nombre || tienda.nombre }); }, 60);
    }
  }

  // ================================================================ Formulario de contacto

  var contactoActual = null;

  function abrirFormulario(tiendaId, productoId, referencia, mensajeInicial) {
    var tienda = ctx.tiendas().find(function (t) { return t.id === tiendaId; });
    if (!tienda) return;
    if (!ctx.Cat.viasContacto(tienda).formulario) {
      toast("Esta tienda no recibe mensajes por formulario.");
      return;
    }
    contactoActual = { tiendaId: tiendaId, productoId: productoId || null };
    var sub = el("modal-contacto-sub");
    if (sub) sub.textContent = tienda.nombre + (referencia ? " · Sobre: " + referencia : "");
    var mensaje = el("contacto-mensaje");
    if (mensaje) mensaje.value = mensajeInicial ? String(mensajeInicial).slice(0, 1000) : (referencia ? "Hola, me interesa «" + referencia + "». " : "");
    var u = ctx.usuario();
    var nombre = el("contacto-nombre");
    if (nombre && !nombre.value && u && u.nombre && u.nombre !== "Visitante") nombre.value = u.nombre;
    var medio = el("contacto-medio");
    if (medio && !medio.value && u && u.email) medio.value = u.email;
    var aviso = el("contacto-aviso");
    if (aviso) { aviso.textContent = ""; aviso.className = "e6-aviso"; }
    ctx.abrirModal("modal-contacto");
  }

  function enviarFormulario(evento) {
    evento.preventDefault();
    var aviso = el("contacto-aviso");
    var datos = {
      tiendaId: contactoActual && contactoActual.tiendaId,
      productoId: contactoActual && contactoActual.productoId,
      nombre: (el("contacto-nombre").value || "").trim(),
      medio: (el("contacto-medio").value || "").trim(),
      mensaje: (el("contacto-mensaje").value || "").trim()
    };
    function avisar(texto, ok) { if (aviso) { aviso.textContent = texto; aviso.className = "e6-aviso" + (ok ? " ok" : ""); } }
    if (!datos.tiendaId) return;
    if (!datos.nombre) { avisar("Escribe tu nombre."); el("contacto-nombre").focus(); return; }
    if (!datos.mensaje) { avisar("Escribe tu mensaje."); el("contacto-mensaje").focus(); return; }
    if (!real()) {
      avisar("Modo demostración: el mensaje no se envía a un servidor.", true);
      return;
    }
    var boton = el("btn-enviar-contacto");
    if (boton) { boton.disabled = true; boton.textContent = "Enviando…"; }
    ctx.servicio.enviarContacto(datos).then(function (r) {
      if (boton) { boton.disabled = false; boton.textContent = "Enviar mensaje"; }
      if (!r.ok) { avisar("No se pudo enviar: " + ctx.Cat.mensajeError(r.error)); return; }
      el("contacto-mensaje").value = "";
      ctx.cerrarModal("modal-contacto");
      toast("✅ Mensaje enviado. La emprendedora lo verá en su app.");
    });
  }

  // ================================================================ Ferias

  function feriasVisibles() {
    return ctx.Cat.feriasProximas(FERIAS);
  }

  function pintarFerias() {
    var c = el("grilla-ferias-container");
    var estadoCaja = el("e6-ferias-estado");
    if (!c) return;
    c.textContent = "";
    if (estadoCaja) {
      var msg = "";
      if (ctx.modo === "sin_config") msg = "La vitrina no está conectada a la base de datos: no se pueden mostrar las ferias.";
      else if (ctx.modo === "demo") msg = "Modo demostración: ferias de ejemplo.";
      else if (estadoFerias.error) msg = "No se pudieron cargar las ferias. " + ctx.Cat.mensajeError(estadoFerias.error);
      estadoCaja.textContent = msg;
      estadoCaja.style.display = msg ? "" : "none";
      estadoCaja.className = "estado-datos" + (estadoFerias.error || ctx.modo === "sin_config" ? " error" : (ctx.modo === "demo" ? " demo" : ""));
    }
    if (estadoFerias.cargando && !estadoFerias.cargado) {
      c.appendChild(h("p", { class: "subtitulo-seccion", text: "Cargando ferias…" }));
      return;
    }
    var lista = feriasVisibles();
    if (!lista.length) {
      c.appendChild(h("p", { class: "subtitulo-seccion", text: "No hay ferias próximas publicadas por ahora." }));
      return;
    }
    lista.forEach(function (f) {
      var img = ctx.imagenSegura(f.imagenUrl, f.nombre, "tarjeta-feria-img") ||
        h("div", { class: "tarjeta-feria-img e6-feria-sin-foto", "aria-hidden": "true", text: "🎪" });
      c.appendChild(h("article", { class: "tarjeta-feria-editorial" }, [
        img,
        h("div", { class: "tarjeta-feria-body" }, [
          h("span", { class: "badge-necesidad badge-agenda", style: "align-self: flex-start;", text: "Próxima feria" }),
          h("h3", { style: "font-size: 16px; font-weight: 800;", text: f.nombre }),
          h("p", { style: "font-size: 13px; color: var(--gris-texto);" }, [h("strong", { text: rangoFechas(f) }), f.horario ? " · " + f.horario : ""]),
          h("p", { style: "font-size: 12.5px; color: var(--gris-texto);", text: "📍 " + (f.lugar || "Lugar por confirmar") }),
          h("div", { class: "e6-acciones-feria" }, [
            h("button", { type: "button", class: "btn-accion-pill btn-negro", text: "Ver ficha y mapa", onclick: function () { abrirFeria(f.id); } }),
            botonComoLlegar(f.lat, f.lng, f.nombre)
          ])
        ])
      ]));
    });
  }

  function abrirFeria(id) {
    var f = FERIAS.find(function (x) { return x.id === id; });
    if (!f) return false;
    var cuerpo = el("modal-feria-cuerpo");
    var titulo = el("modal-feria-titulo");
    if (!cuerpo) return false;
    if (titulo) titulo.textContent = f.nombre;
    cuerpo.textContent = "";
    var img = ctx.imagenSegura(f.imagenUrl, f.nombre, "e6-feria-imagen");
    if (img) cuerpo.appendChild(img);
    cuerpo.appendChild(h("p", { class: "e6-feria-fecha", text: rangoFechas(f) + (f.horario ? " · " + f.horario : "") }));
    cuerpo.appendChild(h("p", { class: "tarjeta-sub", text: "📍 " + (f.lugar || "Lugar por confirmar") }));
    if (f.organizador) cuerpo.appendChild(h("p", { class: "tarjeta-sub", text: "Organiza: " + f.organizador }));
    if (f.descripcion) cuerpo.appendChild(h("p", { style: "font-size: 13.5px; line-height: 1.5;", text: f.descripcion }));
    if (f.lat !== null) {
      cuerpo.appendChild(h("div", { id: "mapa-ficha-feria", class: "e6-mapa", role: "region", "aria-label": "Mapa con la ubicación de " + f.nombre }));
    } else {
      cuerpo.appendChild(h("p", { class: "e6-aviso", text: "Esta feria aún no tiene su ubicación marcada en el mapa." }));
    }
    cuerpo.appendChild(h("div", { class: "e6-acciones-tienda" }, [
      botonComoLlegar(f.lat, f.lng, f.nombre),
      h("button", { type: "button", class: "btn-accion-pill", text: "🔗 Compartir feria", onclick: function () { compartir("feria", f.id, f.nombre); } })
    ]));
    if (window.AlerceFerias7) window.AlerceFerias7.pintarParticipantes(f, cuerpo);
    ctx.abrirModal("modal-feria");
    if (f.lat !== null) setTimeout(function () { crearMapa("mapa-ficha-feria", f.lat, f.lng, { etiqueta: f.nombre }); }, 60);
    return true;
  }

  // ================================================================ Producto (enlace compartido)

  function abrirProducto(id) {
    var p = ctx.productos().find(function (x) { return x.id === id; });
    if (!p || ctx.Cat.estadoPublico(p) === "OCULTO") return false;
    var tienda = ctx.tiendas().find(function (t) { return t.id === p.tiendaId; });
    if (tienda) ctx.fijarTiendaSeleccionada(tienda);
    var cuerpo = el("modal-producto-cuerpo"), titulo = el("modal-producto-titulo");
    if (!cuerpo) return false;
    if (titulo) titulo.textContent = p.nombre;
    cuerpo.textContent = "";
    cuerpo.appendChild(ctx.tarjetaProducto(p));
    if (tienda) {
      cuerpo.appendChild(h("div", { class: "e6-acciones-tienda" }, [
        h("button", { type: "button", class: "btn-accion-pill btn-negro", text: "Ver la tienda " + tienda.nombre, onclick: function () { ctx.cerrarModal("modal-producto"); ctx.abrirVitrinaTienda(tienda.id); } }),
        h("button", { type: "button", class: "btn-accion-pill", text: "🔗 Compartir", onclick: function () { compartir("producto", p.id, p.nombre); } })
      ]));
    }
    ctx.abrirModal("modal-producto");
    return true;
  }

  /** ?producto= ?asociacion= ?feria= → abre su página. Devuelve true si el enlace era de este módulo. */
  function abrirDesdeEnlace() {
    var q;
    try { q = new URLSearchParams(window.location.search); } catch (e) { return false; }
    var producto = q.get("producto"), asociacion = q.get("asociacion"), feria = q.get("feria");
    if (!producto && !asociacion && !feria) return false;
    Promise.all([ctx.promesaCatalogo(), promesaDatos]).then(function () {
      var ok = producto ? abrirProducto(producto) : (asociacion ? abrirAsociacion(asociacion) : abrirFeria(feria));
      if (!ok) toast("No encontramos lo que buscaba el enlace. Puede que ya no esté publicado.");
      if (feria) { var s = el("ferias"); if (s && s.scrollIntoView) s.scrollIntoView({ block: "start" }); }
    });
    return true;
  }

  // ================================================================ Administración

  var NAV_OPCIONES = [
    { valor: "PERMITIR", titulo: "Permitir", texto: "La app abre directo en la vitrina, sin pedir cuenta." },
    { valor: "INVITAR", titulo: "Invitar (con opción de omitir)", texto: "Al abrir, invita a ingresar con Google y deja «Omitir»." },
    { valor: "OBLIGAR", titulo: "Obligar ingreso con Google", texto: "Sin sesión, la app solo deja continuar ingresando con Google." }
  ];

  function tarjetaAdmin(titulo, subtitulo, hijos) {
    return h("section", { class: "e6-admin-tarjeta" }, [
      h("h3", { class: "e6-admin-titulo", text: titulo }),
      subtitulo ? h("p", { class: "tarjeta-sub", text: subtitulo }) : null
    ].concat(hijos));
  }

  function pintarAdmin() {
    var c = el("admin-e6");
    if (!c || !ctx.esAdmin()) return;
    c.textContent = "";
    if (!real()) {
      c.appendChild(h("p", { class: "e6-aviso", text: "Conecta la vitrina al servidor para administrar ferias, asociaciones y la navegación de la app." }));
      return;
    }
    c.appendChild(adminNavegacion());
    c.appendChild(adminFerias());
    c.appendChild(adminAsociaciones());
  }

  // ---- Navegación anónima (RF-ADM-02)
  function adminNavegacion() {
    var estadoTxt = h("p", { class: "e6-aviso", role: "status", text: "Leyendo la opción guardada…" });
    var grupo = h("div", { class: "e6-radios", role: "radiogroup", "aria-label": "Navegación anónima de la app" },
      NAV_OPCIONES.map(function (o) {
        return h("label", { class: "e6-radio" }, [
          h("input", { type: "radio", name: "e6-nav-anonima", value: o.valor }),
          h("span", null, [h("strong", { text: o.titulo }), h("small", { text: o.texto })])
        ]);
      }));
    function leer() {
      return ctx.servicio.leerConfiguracion("navegacion_anonima").then(function (r) {
        if (!r.ok) { estadoTxt.textContent = "No se pudo leer: " + ctx.Cat.mensajeError(r.error); estadoTxt.className = "e6-aviso"; return; }
        var v = r.datos || "PERMITIR";
        grupo.querySelectorAll("input").forEach(function (i) { i.checked = i.value === v; });
        var op = NAV_OPCIONES.find(function (o) { return o.valor === v; });
        estadoTxt.textContent = "Guardado en el servidor: " + (op ? op.titulo : v);
        estadoTxt.className = "e6-aviso ok";
      });
    }
    var boton = h("button", {
      type: "button", class: "btn-accion-pill btn-negro", text: "Guardar opción",
      onclick: function () {
        var marcado = grupo.querySelector("input:checked");
        if (!marcado) { estadoTxt.textContent = "Elige una opción."; return; }
        boton.disabled = true;
        ctx.servicio.guardarConfiguracion("navegacion_anonima", marcado.value).then(function (r) {
          boton.disabled = false;
          if (!r.ok) { estadoTxt.textContent = "No se guardó: " + ctx.Cat.mensajeError(r.error); estadoTxt.className = "e6-aviso"; return; }
          toast("Navegación anónima guardada. La app la aplica al abrirse.");
          leer();
        });
      }
    });
    leer();
    return tarjetaAdmin("📱 Navegación anónima de la app", "Qué ve una persona que abre la app Android sin sesión.", [grupo, h("div", { class: "e6-fila" }, [boton]), estadoTxt]);
  }

  // ---- Ferias con mapa (RF-FER-01, RF-MAP-01)
  var formFeria = null;

  // ---- Avance de «Nueva feria» guardado SOLO en este navegador, por cuenta.
  // Los campos van a localStorage y la imagen (ya comprimida) a IndexedDB, ambos con
  // el id de la cuenta en la clave: otra cuenta del mismo navegador no lo ve. Nada de
  // esto llega a Supabase; la feria se envía únicamente al pulsar «Guardar feria».
  var CLAVE_BORRADOR_FERIA = "alerce_borrador_feria_v1:";
  var BD_BORRADORES = "alerce_borradores", ALMACEN_IMAGENES = "imagenes_feria";

  function cuentaActual() { var u = ctx.usuario(); return u && u.id ? String(u.id) : null; }
  function leerBorradorFeria(cuenta) {
    if (!cuenta) return null;
    try { var b = JSON.parse(localStorage.getItem(CLAVE_BORRADOR_FERIA + cuenta) || "null"); return b && b.v === 1 ? b : null; } catch (e) { return null; }
  }
  function escribirBorradorFeria(cuenta, b) {
    try { localStorage.setItem(CLAVE_BORRADOR_FERIA + cuenta, JSON.stringify(b)); return true; } catch (e) { return false; }
  }
  function hayCambiosBorrador(b) {
    if (!b) return false;
    return ["nombre", "descripcion", "lugar", "inicio", "fin", "horario", "organizador"].some(function (k) { return String(b[k] || "").trim() !== ""; }) ||
      (b.estado && b.estado !== "PROXIMA") || b.lat !== null && b.lat !== undefined || (b.imagen && b.imagen !== "ninguna");
  }
  // Cada foto elegida recibe un número de serie creciente. IndexedDB guarda { blob, serie } y,
  // dentro de la misma transacción, una foto solo reemplaza a otra de serie menor y un borrado
  // solo quita fotos de serie menor o igual a la suya. Así el orden en que terminan las
  // compresiones o los borrados pendientes no puede dejar una foto vieja ni borrar una nueva.
  var ultimaSerie = 0;
  function nuevaSerie() { ultimaSerie = Math.max(ultimaSerie + 1, Date.now() * 1000); return ultimaSerie; }
  function serieDe(registro) { return registro && typeof registro.serie === "number" ? registro.serie : 0; }
  /** op: { tipo: "leer" } | { tipo: "guardar", blob, serie, vigente() } | { tipo: "borrar", hasta }.
   *  Resuelve con el registro (leer) o con "guardada" / "borrada" / "omitida". */
  function imagenBorrador(cuenta, op) {
    return new Promise(function (resolver, rechazar) {
      if (!window.indexedDB) return rechazar(new Error("Este navegador no permite guardar la imagen."));
      var apertura = window.indexedDB.open(BD_BORRADORES, 1);
      apertura.onupgradeneeded = function () { apertura.result.createObjectStore(ALMACEN_IMAGENES); };
      apertura.onerror = function () { rechazar(apertura.error || new Error("Sin acceso a IndexedDB")); };
      apertura.onblocked = function () { rechazar(new Error("IndexedDB bloqueado")); };
      apertura.onsuccess = function () {
        var bd = apertura.result, resultado = "omitida";
        // Una operación de una feria ya descartada o guardada, o de una foto reemplazada, no hace nada.
        if (op.vigente && !op.vigente()) { bd.close(); return resolver("omitida"); }
        var tx;
        try {
          tx = bd.transaction(ALMACEN_IMAGENES, op.tipo === "leer" ? "readonly" : "readwrite");
          var almacen = tx.objectStore(ALMACEN_IMAGENES);
          var lectura = almacen.get(cuenta);
          lectura.onsuccess = function () {
            var actual = lectura.result;
            if (op.tipo === "leer") { resultado = actual || null; return; }
            if (op.tipo === "guardar") {
              if (serieDe(actual) > op.serie) return;
              almacen.put({ blob: op.blob, serie: op.serie }, cuenta);
              resultado = "guardada";
              return;
            }
            if (actual && serieDe(actual) <= op.hasta) { almacen.delete(cuenta); resultado = "borrada"; }
          };
        } catch (e) { bd.close(); return rechazar(e); }
        tx.oncomplete = function () { bd.close(); resolver(resultado); };
        tx.onerror = tx.onabort = function () { bd.close(); rechazar(tx.error || new Error("No se pudo usar IndexedDB")); };
      };
    });
  }
  function borrarImagenBorrador(cuenta) {
    // Solo borra fotos elegidas HASTA ahora: una foto que se elija después tiene una serie mayor.
    return imagenBorrador(cuenta, { tipo: "borrar", hasta: nuevaSerie() }).catch(function () { return "omitida"; });
  }
  /** Marca el estado de la foto en el avance guardado, si sigue siendo esa misma foto. */
  function marcarImagenEnBorrador(cuenta, serie, estado) {
    var b = leerBorradorFeria(cuenta);
    if (b && b.imagenSerie === serie && b.imagen !== estado) { b.imagen = estado; escribirBorradorFeria(cuenta, b); }
  }
  function borrarBorradorFeria(cuenta) {
    if (!cuenta) return;
    try { localStorage.removeItem(CLAVE_BORRADOR_FERIA + cuenta); } catch (e) { /* sin almacenamiento */ }
    borrarImagenBorrador(cuenta);
  }
  function actualizarBotonNuevaFeria() {
    var b = el("e6-btn-nueva-feria");
    if (b) b.textContent = hayCambiosBorrador(leerBorradorFeria(cuentaActual())) ? "➕ Nueva feria · continuar la que preparabas" : "➕ Nueva feria";
  }

  function adminFerias() {
    var lista = h("div", { class: "e6-admin-lista", id: "e6-admin-lista-ferias" });
    var form = h("div", { id: "e6-form-feria", class: "e6-form", style: "display:none;" });
    var nueva = h("button", { type: "button", id: "e6-btn-nueva-feria", class: "btn-accion-pill btn-negro", text: "➕ Nueva feria", onclick: function () { abrirFormFeria(null); } });
    var tarjeta = tarjetaAdmin("🎪 Ferias", "Crea y edita ferias. La ubicación se fija marcando el mapa o buscando la dirección.", [h("div", { class: "e6-fila" }, [nueva]), form, lista]);
    setTimeout(function () { pintarListaFeriasAdmin(); actualizarBotonNuevaFeria(); }, 0);
    return tarjeta;
  }

  var ESTADO_LEGIBLE = { BORRADOR: "Borrador (no publicada)", PROXIMA: "Publicada", EN_CURSO: "Publicada (en curso)", FINALIZADA: "Finalizada", CANCELADA: "Cancelada" };

  function pintarListaFeriasAdmin() {
    var lista = el("e6-admin-lista-ferias");
    if (!lista) return;
    lista.textContent = "";
    if (!FERIAS.length) { lista.appendChild(h("p", { class: "tarjeta-sub", text: "Aún no hay ferias creadas." })); return; }
    FERIAS.slice().sort(function (a, b) { return String(a.fechaInicio || "").localeCompare(String(b.fechaInicio || "")); }).forEach(function (f) {
      lista.appendChild(h("div", { class: "e6-admin-item" }, [
        h("div", null, [
          h("strong", { text: f.nombre }),
          h("small", { text: rangoFechas(f) + " · " + (f.lugar || "sin lugar") + " · " + (ESTADO_LEGIBLE[f.estado] || f.estado) + (f.lat === null ? " · ⚠️ sin ubicación" : " · 📍 con ubicación") })
        ]),
        h("div", { class: "e6-fila" }, [
          h("button", { type: "button", class: "btn-accion-pill", text: "✏️ Editar", onclick: function () { abrirFormFeria(f.id); } }),
          h("button", { type: "button", class: "btn-accion-pill", text: "Ver ficha", onclick: function () { abrirFeria(f.id); } })
        ].concat(window.AlerceFerias7 ? window.AlerceFerias7.accionesAdminFeria(f, function () {
          cargarFerias().then(function () { pintarFerias(); pintarListaFeriasAdmin(); if (window.AlerceFerias7) window.AlerceFerias7.alCargarFerias(); });
        }) : []))
      ]));
    });
  }

  function campoForm(etiqueta, control) {
    return h("label", { class: "e6-campo" }, [h("span", { text: etiqueta }), control]);
  }

  function abrirFormFeria(id) {
    var f = id ? FERIAS.find(function (x) { return x.id === id; }) : null;
    var form = el("e6-form-feria");
    if (!form) return;
    form.textContent = "";
    form.style.display = "";
    // Una feria nueva retoma el avance guardado en este navegador por esta misma cuenta.
    var cuenta = f ? null : cuentaActual();
    var borrador = f ? null : leerBorradorFeria(cuenta);
    if (borrador && !hayCambiosBorrador(borrador)) { borrarBorradorFeria(cuenta); borrador = null; }
    var v = f || (borrador ? {
      nombre: borrador.nombre, descripcion: borrador.descripcion, lugar: borrador.lugar, fechaInicio: borrador.inicio,
      fechaFin: borrador.fin, horario: borrador.horario, organizador: borrador.organizador, estado: borrador.estado,
      lat: typeof borrador.lat === "number" ? borrador.lat : null, lng: typeof borrador.lng === "number" ? borrador.lng : null
    } : {});
    formFeria = {
      id: f ? f.id : (borrador && borrador.id) || "feria_" + Date.now().toString(36), esNueva: !f,
      lat: v.lat === undefined ? null : v.lat, lng: v.lng === undefined ? null : v.lng,
      imagenUrl: f ? f.imagenUrl : "", archivo: null,
      imagen: borrador ? (borrador.imagen || "ninguna") : "ninguna", turnoImagen: 0,
      imagenSerie: borrador && typeof borrador.imagenSerie === "number" ? borrador.imagenSerie : null,
      avanceSinGuardar: false
    };
    var formActual = formFeria;

    var nombre = h("input", { id: "e6-feria-nombre", maxlength: "120", value: v.nombre || "", required: true });
    var descripcion = h("textarea", { id: "e6-feria-descripcion", rows: "3", maxlength: "1000" });
    descripcion.value = v.descripcion || "";
    var lugar = h("input", { id: "e6-feria-lugar", maxlength: "160", value: v.lugar || "", placeholder: "Ej: Plaza de Alerce" });
    var inicio = h("input", { id: "e6-feria-inicio", type: "date", value: v.fechaInicio ? String(v.fechaInicio).slice(0, 10) : "" });
    var fin = h("input", { id: "e6-feria-fin", type: "date", value: v.fechaFin ? String(v.fechaFin).slice(0, 10) : "" });
    var horario = h("input", { id: "e6-feria-horario", maxlength: "60", value: v.horario || "", placeholder: "Ej: 10:00 a 18:00" });
    var organizador = h("input", { id: "e6-feria-organizador", maxlength: "120", value: v.organizador || "", placeholder: "Ej: Municipalidad de Puerto Montt" });
    var estadoSel = h("select", { id: "e6-feria-estado" }, [
      h("option", { value: "PROXIMA", text: "Publicada (próxima)" }),
      h("option", { value: "EN_CURSO", text: "En curso" }),
      h("option", { value: "BORRADOR", text: "Borrador (no se publica)" }),
      h("option", { value: "FINALIZADA", text: "Finalizada" }),
      h("option", { value: "CANCELADA", text: "Cancelada (deja de ofrecerse)" })
    ]);
    estadoSel.value = v.estado || "PROXIMA";
    var imagenArchivo = h("input", { id: "e6-feria-imagen", type: "file", accept: "image/*" });
    var imagenPrevia = h("div", { class: "e6-imagen-previa" });
    var avisoImagen = h("p", { id: "e6-feria-aviso-imagen", class: "e6-aviso", role: "status" });
    var avisoRecuperada = h("p", { id: "e6-feria-recuperada", class: "e6-aviso ok", role: "status" });
    var avisoAvance = h("p", { id: "e6-feria-aviso-avance", class: "e6-aviso", role: "alert" });
    function pintarPrevia(url) {
      imagenPrevia.textContent = "";
      var i = url ? h("img", { src: url, alt: "Imagen de la feria" }) : null;
      if (i) imagenPrevia.appendChild(i);
    }
    pintarPrevia(formFeria.imagenUrl);

    // Guarda el avance de una feria NUEVA (la edición de ferias existentes no cambia).
    function guardarAvance() {
      if (!formActual.esNueva || !cuenta || formActual.cerrada || formFeria !== formActual || cuentaActual() !== cuenta) return;
      var b = {
        v: 1, id: formActual.id, nombre: nombre.value, descripcion: descripcion.value, lugar: lugar.value,
        inicio: inicio.value, fin: fin.value, horario: horario.value, organizador: organizador.value,
        estado: estadoSel.value, lat: formActual.lat, lng: formActual.lng, imagen: formActual.imagen,
        imagenSerie: formActual.imagenSerie, actualizado: new Date().toISOString()
      };
      var ok = true;
      if (hayCambiosBorrador(b)) ok = escribirBorradorFeria(cuenta, b);
      else borrarBorradorFeria(cuenta);
      formActual.avanceSinGuardar = !ok;
      if (ok) avisoAvance.textContent = "";
      else avisoAvance.textContent = "⚠️ No pudimos guardar tu avance en este navegador (puede estar lleno o en modo privado). " +
        (leerBorradorFeria(cuenta) ? "Si sales o recargas, perderás los últimos cambios y al volver verás la versión guardada antes." : "Si sales o recargas, perderás lo que ingresaste.");
      actualizarBotonNuevaFeria();
    }
    imagenArchivo.addEventListener("change", function () {
      var a = imagenArchivo.files && imagenArchivo.files[0];
      formActual.archivo = a || null;
      avisoImagen.textContent = "";
      if (a) pintarPrevia(URL.createObjectURL(a));
      if (!formActual.esNueva || !cuenta) return;
      formActual.turnoImagen++;
      if (!a) { formActual.imagen = "ninguna"; formActual.imagenSerie = null; borrarImagenBorrador(cuenta); guardarAvance(); return; }
      // La imagen se guarda comprimida; si el navegador no puede, se avisa y el resto sigue guardado.
      var turno = formActual.turnoImagen, serie = nuevaSerie();
      formActual.imagen = "pendiente";
      formActual.imagenSerie = serie;
      guardarAvance();
      var vigente = function () { return turno === formActual.turnoImagen && !formActual.cerrada; };
      comprimir(a).catch(function () { return a; }).then(function (blob) {
        if (!vigente()) return "omitida";
        return imagenBorrador(cuenta, { tipo: "guardar", blob: blob, serie: serie, vigente: vigente });
      }).then(function (r) {
        if (r !== "guardada") return;
        // También si la persona ya salió del formulario: el avance guardado queda al día.
        marcarImagenEnBorrador(cuenta, serie, "guardada");
        if (!vigente()) return;
        formActual.imagen = "guardada";
        guardarAvance();
      }, function () {
        marcarImagenEnBorrador(cuenta, serie, "perdida");
        if (!vigente()) return;
        formActual.imagen = "perdida";
        guardarAvance();
        avisoImagen.className = "e6-aviso";
        avisoImagen.textContent = "Este navegador no pudo guardar la imagen: si sales antes de guardar la feria, tendrás que elegirla de nuevo.";
      });
    });
    if (borrador) {
      avisoRecuperada.textContent = "Recuperamos la feria que estabas preparando.";
      toast("Recuperamos la feria que estabas preparando.");
      if (formFeria.imagen !== "ninguna") {
        var turnoRecuperado = formActual.turnoImagen, intentos = 0;
        var recuperarImagen = function () {
          imagenBorrador(cuenta, { tipo: "leer" }).catch(function () { return null; }).then(function (registro) {
            if (turnoRecuperado !== formActual.turnoImagen || formActual.archivo || formActual.cerrada) return;
            var esLaMisma = registro && registro.blob && serieDe(registro) === formActual.imagenSerie;
            if (esLaMisma && formActual.imagen !== "perdida") {
              formActual.imagen = "guardada";
              formActual.archivo = registro.blob;
              pintarPrevia(URL.createObjectURL(registro.blob));
              guardarAvance();
              return;
            }
            // Una foto aún comprimiéndose (se salió justo después de elegirla) puede tardar un poco.
            if (formActual.imagen === "pendiente" && ++intentos < 6) { setTimeout(recuperarImagen, 400); return; }
            formActual.imagen = "perdida";
            guardarAvance();
            avisoImagen.className = "e6-aviso";
            avisoImagen.textContent = "No pudimos conservar la imagen en este navegador: elígela de nuevo. El resto de la feria está aquí.";
          });
        };
        recuperarImagen();
      }
    }

    var busqueda = h("input", { id: "e6-feria-busqueda", type: "search", placeholder: "Buscar dirección (ej: Plaza de Alerce, Puerto Montt)" });
    var resultados = h("div", { class: "e6-resultados", role: "listbox", "aria-label": "Resultados de la búsqueda" });
    var coords = h("p", { class: "e6-aviso", role: "status" });
    var aviso = h("p", { class: "e6-aviso", role: "alert" });
    var mapaRef = null;

    function fijarPunto(lat, lng, mover) {
      formFeria.lat = Math.round(lat * 1e6) / 1e6;
      formFeria.lng = Math.round(lng * 1e6) / 1e6;
      coords.textContent = "Ubicación marcada: " + formFeria.lat + ", " + formFeria.lng;
      coords.className = "e6-aviso ok";
      if (mapaRef) {
        if (!mapaRef.marcador) {
          mapaRef.marcador = window.L.marker([formFeria.lat, formFeria.lng], { draggable: true }).addTo(mapaRef.mapa);
          mapaRef.marcador.on("dragend", function (e) { var p = e.target.getLatLng(); fijarPunto(p.lat, p.lng, false); });
        } else {
          mapaRef.marcador.setLatLng([formFeria.lat, formFeria.lng]);
        }
        if (mover) mapaRef.mapa.setView([formFeria.lat, formFeria.lng], 16);
      }
      guardarAvance();
    }
    if (formFeria.lat === null) { coords.textContent = "Toca el mapa o busca la dirección para marcar la ubicación."; }

    function buscar() {
      var q = busqueda.value.trim();
      if (q.length < 3) { resultados.textContent = "Escribe al menos 3 letras."; return; }
      resultados.textContent = "Buscando…";
      var url = "https://nominatim.openstreetmap.org/search?format=json&limit=5&countrycodes=cl&accept-language=es&q=" + encodeURIComponent(q);
      fetch(url, { headers: { Accept: "application/json" } }).then(function (r) { return r.json(); }).then(function (datos) {
        resultados.textContent = "";
        if (!datos || !datos.length) { resultados.textContent = "No encontramos esa dirección. Prueba con otra o marca el punto en el mapa."; return; }
        datos.forEach(function (d) {
          resultados.appendChild(h("button", {
            type: "button", class: "e6-resultado", role: "option", text: d.display_name,
            onclick: function () {
              fijarPunto(Number(d.lat), Number(d.lon), true);
              if (!lugar.value.trim()) lugar.value = String(d.display_name).split(",").slice(0, 2).join(",");
              resultados.textContent = "";
            }
          }));
        });
      }).catch(function () { resultados.textContent = "No se pudo buscar la dirección (sin conexión). Marca el punto en el mapa."; });
    }
    busqueda.addEventListener("keydown", function (e) { if (e.key === "Enter") { e.preventDefault(); buscar(); } });

    var guardar = h("button", { type: "button", class: "btn-accion-pill btn-negro", text: "Guardar feria" });
    guardar.addEventListener("click", function () {
      aviso.textContent = "";
      var faltan = [];
      if (!nombre.value.trim()) faltan.push("nombre");
      if (!descripcion.value.trim()) faltan.push("descripción");
      if (!lugar.value.trim()) faltan.push("lugar");
      if (!inicio.value) faltan.push("fecha");
      if (!horario.value.trim()) faltan.push("horario");
      if (!organizador.value.trim()) faltan.push("organizador");
      if (formFeria.lat === null) faltan.push("ubicación en el mapa");
      if (!formFeria.archivo && !formFeria.imagenUrl) faltan.push("imagen");
      if (faltan.length) { aviso.textContent = "Falta: " + faltan.join(", ") + "."; return; }
      if (fin.value && fin.value < inicio.value) { aviso.textContent = "La fecha de término no puede ser antes del inicio."; return; }
      guardar.disabled = true; guardar.textContent = "Guardando…";
      subirImagenFeria().then(function (urlImagen) {
        var fila = {
          id: formFeria.id, nombre: nombre.value.trim(), descripcion: descripcion.value.trim(),
          imagen_url: urlImagen || null, ubicacion: lugar.value.trim(),
          fecha_inicio: inicio.value, fecha_fin: fin.value || inicio.value, horario: horario.value.trim(),
          organizador: organizador.value.trim(), estado: estadoSel.value,
          latitud: formFeria.lat, longitud: formFeria.lng
        };
        return ctx.servicio.guardarFeria(fila);
      }).then(function (r) {
        guardar.disabled = false; guardar.textContent = "Guardar feria";
        if (!r.ok) { aviso.textContent = "No se guardó: " + ctx.Cat.mensajeError(r.error); return; }
        var guardada = ctx.Cat.normalizarFeria(r.datos[0]);
        var idx = FERIAS.findIndex(function (x) { return x.id === guardada.id; });
        if (idx >= 0) FERIAS[idx] = guardada; else FERIAS.push(guardada);
        toast(formActual.esNueva ? "✅ Feria creada." : "✅ Cambios de la feria guardados.");
        // Ya está en el servidor: la siguiente «Nueva feria» empieza vacía.
        if (formActual.esNueva) { formActual.cerrada = true; borrarBorradorFeria(cuenta); actualizarBotonNuevaFeria(); }
        if (formFeria === formActual) { form.style.display = "none"; form.textContent = ""; }
        // Se vuelve a leer del servidor para confirmar que quedó guardada.
        cargarFerias().then(function () { pintarFerias(); pintarListaFeriasAdmin(); });
      }, function (e) {
        guardar.disabled = false; guardar.textContent = "Guardar feria";
        aviso.textContent = "No se guardó: " + ((e && e.message) || "error al subir la imagen");
      });
    });
    function cerrarForm() { form.style.display = "none"; form.textContent = ""; }
    var botones = [guardar];
    if (formActual.esNueva) {
      // Salir conserva el avance; descartar lo borra (con confirmación si hay algo ingresado).
      botones.push(h("button", { type: "button", id: "e6-feria-salir", class: "btn-accion-pill", text: cuenta ? "Salir (queda guardada aquí)" : "Salir", onclick: function () {
        guardarAvance();
        if (formActual.avanceSinGuardar && !window.confirm("No pudimos guardar tus últimos cambios en este navegador. Si sales, se perderán. ¿Salir de todas formas?")) return;
        cerrarForm();
      } }));
      botones.push(h("button", { type: "button", id: "e6-feria-descartar", class: "btn-accion-pill", text: "Descartar feria", onclick: function () {
        var b = leerBorradorFeria(cuenta);
        var conCambios = hayCambiosBorrador(b) || !!formActual.archivo || [nombre, descripcion, lugar, inicio, fin, horario, organizador].some(function (c) { return c.value.trim() !== ""; });
        if (conCambios && !window.confirm("¿Descartar esta feria? Se borrará lo que ingresaste y no se puede recuperar.")) return;
        formActual.turnoImagen++;
        formActual.cerrada = true;
        borrarBorradorFeria(cuenta);
        actualizarBotonNuevaFeria();
        cerrarForm();
        toast("Feria descartada.");
      } }));
    } else {
      botones.push(h("button", { type: "button", class: "btn-accion-pill", text: "Cancelar", onclick: cerrarForm }));
    }

    form.appendChild(h("h4", { class: "e6-subtitulo", text: f ? "Editar feria" : "Nueva feria" }));
    if (formActual.esNueva) {
      form.appendChild(avisoRecuperada);
      if (cuenta) form.appendChild(h("p", { class: "tarjeta-sub", text: "Tu avance se guarda solo en este navegador mientras escribes. La feria se publica al pulsar «Guardar feria»." }));
    }
    form.appendChild(campoForm("Nombre", nombre));
    form.appendChild(campoForm("Descripción", descripcion));
    form.appendChild(h("div", { class: "e6-grilla-2" }, [campoForm("Fecha de inicio", inicio), campoForm("Fecha de término", fin)]));
    form.appendChild(h("div", { class: "e6-grilla-2" }, [campoForm("Horario", horario), campoForm("Organizador", organizador)]));
    form.appendChild(h("div", { class: "e6-grilla-2" }, [campoForm("Lugar (texto)", lugar), campoForm("Estado", estadoSel)]));
    form.appendChild(campoForm("Imagen", imagenArchivo));
    form.appendChild(imagenPrevia);
    form.appendChild(avisoImagen);
    form.appendChild(h("div", { class: "e6-buscar-direccion" }, [
      campoForm("Buscar dirección", busqueda),
      h("button", { type: "button", class: "btn-accion-pill", text: "🔎 Buscar", onclick: buscar })
    ]));
    form.appendChild(resultados);
    form.appendChild(h("div", { id: "mapa-form-feria", class: "e6-mapa", role: "application", "aria-label": "Mapa: toca para marcar la ubicación de la feria" }));
    form.appendChild(coords);
    form.appendChild(aviso);
    form.appendChild(avisoAvance);
    form.appendChild(h("div", { class: "e6-fila" }, botones));
    if (formActual.esNueva) { form.addEventListener("input", guardarAvance); form.addEventListener("change", guardarAvance); }

    setTimeout(function () {
      mapaRef = crearMapa("mapa-form-feria", formFeria.lat, formFeria.lng, { editable: true });
      if (!mapaRef) return;
      if (mapaRef.marcador) mapaRef.marcador.on("dragend", function (e) { var p = e.target.getLatLng(); fijarPunto(p.lat, p.lng, false); });
      mapaRef.mapa.on("click", function (e) { fijarPunto(e.latlng.lat, e.latlng.lng, false); });
      if (formFeria.lat !== null) fijarPunto(formFeria.lat, formFeria.lng, false);
    }, 50);
    if (form.scrollIntoView) form.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  /** Comprime la imagen elegida (JPEG, 1280 px) y la sube a la carpeta del administrador. */
  function subirImagenFeria() {
    if (!formFeria.archivo) return Promise.resolve(formFeria.imagenUrl || null);
    var u = ctx.usuario();
    if (!u || !u.id) return Promise.reject(new Error("Vuelve a iniciar sesión para subir la imagen."));
    return comprimir(formFeria.archivo).then(function (blob) {
      var ruta = u.id + "/feria_" + formFeria.id + "_" + Date.now() + ".jpg";
      return ctx.servicio.subirImagen("productos", ruta, blob).then(function (r) {
        if (!r.ok) throw new Error(ctx.Cat.mensajeError(r.error));
        return r.datos;
      });
    });
  }

  function comprimir(archivo) {
    return new Promise(function (resolver, rechazar) {
      var img = new Image();
      img.onload = function () {
        var escala = Math.min(1, 1280 / Math.max(img.width, img.height));
        var canvas = document.createElement("canvas");
        canvas.width = Math.round(img.width * escala);
        canvas.height = Math.round(img.height * escala);
        canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
        canvas.toBlob(function (b) { if (b) resolver(b); else rechazar(new Error("No se pudo preparar la imagen")); }, "image/jpeg", 0.82);
      };
      img.onerror = function () { rechazar(new Error("El archivo no es una imagen válida")); };
      img.src = URL.createObjectURL(archivo);
    });
  }

  // ---- Asociaciones e integrantes (para RF-ASO-03 y el filtro por asociación)
  function adminAsociaciones() {
    var cont = h("div", { class: "e6-admin-lista", id: "e6-admin-lista-asoc" });
    var nombre = h("input", { maxlength: "120", placeholder: "Nombre de la asociación" });
    var sector = h("input", { maxlength: "80", placeholder: "Sector (ej: Alerce)" });
    var desc = h("input", { maxlength: "400", placeholder: "Descripción breve" });
    var aviso = h("p", { class: "e6-aviso", role: "alert" });
    var crear = h("button", { type: "button", class: "btn-accion-pill btn-negro", text: "➕ Crear asociación" });
    crear.addEventListener("click", function () {
      if (!nombre.value.trim()) { aviso.textContent = "Escribe el nombre."; return; }
      crear.disabled = true;
      ctx.servicio.guardarAsociacion({
        id: "asoc_" + Date.now().toString(36), nombre: nombre.value.trim(), sector: sector.value.trim(),
        descripcion: desc.value.trim(), localidad: "Puerto Montt", activa: true
      }).then(function (r) {
        crear.disabled = false;
        if (!r.ok) { aviso.textContent = "No se creó: " + ctx.Cat.mensajeError(r.error); return; }
        nombre.value = ""; sector.value = ""; desc.value = ""; aviso.textContent = "";
        recargarAsociacionesAdmin();
      });
    });
    setTimeout(pintarAsociacionesAdmin, 0);
    return tarjetaAdmin("🤝 Asociaciones (servidor)", "Agrega emprendedoras a cada asociación: así aparecen en su ficha pública y en el filtro.", [
      h("div", { class: "e6-grilla-3" }, [nombre, sector, desc]), h("div", { class: "e6-fila" }, [crear]), aviso, cont
    ]);
  }

  function recargarAsociacionesAdmin() {
    return Promise.all([cargarAsociaciones(), cargarPertenencias(), ctx.recargarCatalogo()]).then(function () {
      aplicarPertenencias();
      pintarAsociacionesAdmin(); poblarFiltros(); pintarAsociaciones();
    });
  }

  function pintarAsociacionesAdmin() {
    var cont = el("e6-admin-lista-asoc");
    if (!cont) return;
    cont.textContent = "";
    var tiendas = ctx.tiendas();
    ASOCIACIONES.forEach(function (a) {
      var socias = integrantesDe(a.id);
      var sel = h("select", { "aria-label": "Agregar emprendedora a " + a.nombre }, [h("option", { value: "", text: "Agregar emprendedora…" })].concat(
        tiendas.filter(function (t) { return !ctx.Cat.perteneceA(t, a.id); }).map(function (t) {
          var otras = asociacionesDe(t).map(function (x) { return x.nombre; });
          return h("option", { value: t.id, text: t.nombre + (otras.length ? " (en " + otras.join(", ") + ")" : "") });
        })));
      sel.addEventListener("change", function () {
        if (!sel.value) return;
        sel.disabled = true;
        (PERTENENCIAS ? ctx.servicio.agregarPertenencia(sel.value, a.id) : ctx.servicio.asignarAsociacion(sel.value, a.id)).then(function (r) {
          sel.disabled = false;
          if (!r.ok) { toast("No se asignó: " + ctx.Cat.mensajeError(r.error)); return; }
          toast("Emprendedora agregada a " + a.nombre);
          recargarAsociacionesAdmin();
        });
      });
      cont.appendChild(h("div", { class: "e6-admin-item e6-admin-asoc" }, [
        h("div", null, [
          h("strong", { text: a.nombre }),
          h("small", { text: (a.sector || "Sin sector") + " · " + socias.length + " integrantes" }),
          h("div", { class: "e6-chips" }, socias.map(function (t) {
            return h("button", {
              type: "button", class: "e6-chip-integrante", title: "Quitar de la asociación", text: t.nombre + " ✕",
              onclick: function () {
                if (!window.confirm("¿Quitar a " + t.nombre + " de " + a.nombre + "?")) return;
                (PERTENENCIAS ? ctx.servicio.quitarPertenencia(t.id, a.id) : ctx.servicio.asignarAsociacion(t.id, null)).then(function (r) {
                  if (!r.ok) { toast("No se quitó: " + ctx.Cat.mensajeError(r.error)); return; }
                  recargarAsociacionesAdmin();
                });
              }
            });
          }))
        ]),
        sel
      ]));
    });
    if (!ASOCIACIONES.length) cont.appendChild(h("p", { class: "tarjeta-sub", text: "No hay asociaciones en el servidor." }));
  }

  // ================================================================ API

  window.AlerceVitrina6 = {
    iniciar: function (contexto) {
      ctx = contexto;
      configurarFiltros();
      var form = el("form-contacto");
      if (form) form.addEventListener("submit", enviarFormulario);
      promesaDatos = Promise.all([cargarAsociaciones(), cargarFerias(), cargarPertenencias()]).then(function () {
        poblarFiltros();
        pintarAsociaciones();
        pintarFerias();
        if (window.AlerceFerias7) window.AlerceFerias7.alCargarFerias();
        if (ctx.esAdmin()) pintarAdmin();
      });
      pintarFerias();
    },
    alActualizarCatalogo: function () {
      if (!ctx) return;
      aplicarPertenencias();
      poblarFiltros();
      pintarAsociaciones();
    },
    nombreAsociacion: nombreAsociacion,
    ferias: function () { return FERIAS; },
    asociaciones: function () { return ASOCIACIONES; },
    verDirectorio: verDirectorio,
    pintarAsociaciones: function () { if (ctx) pintarAsociaciones(); },
    abrirAsociacion: abrirAsociacion,
    abrirFeria: function (id) { return ctx ? abrirFeria(id) : false; },
    abrirProducto: abrirProducto,
    pintarExtrasTienda: function (t) { if (ctx) pintarExtrasTienda(t); },
    abrirFormulario: abrirFormulario,
    compartir: compartir,
    abrirDesdeEnlace: function () { return ctx ? abrirDesdeEnlace() : false; },
    pintarAdmin: function () { if (ctx) promesaDatos.then(pintarAdmin); },
    urlDe: urlDe
  };
})();
