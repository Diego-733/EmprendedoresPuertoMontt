/**
 * Entrega 7 — Ferias, seguidores y notificaciones (web).
 *
 *  - Calendario de próximas ferias con acceso a su ficha (RF-CMP-04).
 *  - Ficha pública: participantes confirmadas o agregadas y una muestra de su
 *    catálogo público (RF-FER-10). Nunca rechazadas ni pendientes.
 *  - Seguir / dejar de seguir tiendas y asociaciones (RF-CMP-06). Sin sesión, la
 *    acción queda pendiente y se completa sola al iniciar sesión (RF-ACC-08).
 *  - Consentimiento explícito para avisos y su retiro (RNF-PRI-02), y avisos del
 *    navegador: push de Firebase si está configurado; si no, mientras la página
 *    está abierta (RF-CMP-08).
 *  - Administración: publicar, cancelar, invitar o agregar emprendedoras y
 *    asociaciones, checklist con búsqueda y filtros, recordatorios, avisos y
 *    asistencia (RF-FER-01 a 07).
 *  - Emprendedora: sus invitaciones con «Participo» / «No participo» (RF-FER-08).
 *
 * Todo pasa por js/servicio.js; el servidor valida cada permiso (07_ferias_seguidores.sql).
 * Los textos del servidor se insertan como texto, nunca como HTML.
 */
(function () {
  "use strict";

  var ctx = null;
  var usuarioId = null;
  var SEGUIDOS = {};                 // "TIENDA:id" → true
  var consentimiento = null;         // true | false | null (desconocido)
  var CLAVE_PENDIENTE = "alerce_accion_pendiente";
  var CLAVE_TOKEN_WEB = "alerce_token_web";
  var temporizadorAvisos = null;
  var avisosMostrados = {};
  var checklistActual = { feriaId: null, filas: [], temporizador: null };

  function el(id) { return document.getElementById(id); }
  function h() { return ctx.h.apply(null, arguments); }
  function toast(m) { ctx.mostrarToast(m); }
  function real() { return ctx.modo === "real" && !!ctx.servicio; }
  function msg(e) { return ctx.Cat.mensajeError(e); }
  function nuevaSolicitud() {
    if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID();
    return "10000000-1000-4000-8000-100000000000".replace(/[018]/g, function (c) {
      return (c ^ (Math.random() * 16) >> (c / 4)).toString(16);
    });
  }
  function leerJson(clave) { try { return JSON.parse(localStorage.getItem(clave) || "null"); } catch (e) { return null; } }
  function escribirJson(clave, v) { try { if (v === null) localStorage.removeItem(clave); else localStorage.setItem(clave, JSON.stringify(v)); } catch (e) { /* sin almacenamiento */ } }

  function ferias() { return window.AlerceVitrina6 && window.AlerceVitrina6.ferias ? window.AlerceVitrina6.ferias() : []; }
  function asociaciones() { return window.AlerceVitrina6 && window.AlerceVitrina6.asociaciones ? window.AlerceVitrina6.asociaciones() : []; }
  function nombreTienda(id) { var t = ctx.tiendas().find(function (x) { return x.id === id; }); return t ? t.nombre : id; }

  // ================================================================ Calendario (W14)

  function pintarCalendario() {
    var c = el("e7-calendario");
    if (!c || !ctx) return;
    c.textContent = "";
    var grupos = ctx.Cat.calendarioFerias(ferias());
    if (!grupos.length) return;
    c.appendChild(h("h3", { class: "e7-subtitulo", text: "🗓️ Calendario de próximas ferias" }));
    grupos.forEach(function (g) {
      var lista = h("ul", { class: "e7-calendario-lista" });
      g.ferias.forEach(function (f) {
        var dia = f.fechaInicio ? String(f.fechaInicio).slice(8, 10) : "—";
        lista.appendChild(h("li", null, [
          h("button", {
            type: "button", class: "e7-calendario-item", "aria-label": "Ver ficha de " + f.nombre,
            onclick: function () { window.AlerceVitrina6.abrirFeria(f.id); }
          }, [
            h("span", { class: "e7-dia", "aria-hidden": "true", text: dia }),
            h("span", { class: "e7-calendario-texto" }, [
              h("strong", { text: f.nombre }),
              h("small", { text: (f.horario ? f.horario + " · " : "") + (f.lugar || "Lugar por confirmar") })
            ])
          ])
        ]));
      });
      c.appendChild(h("section", { class: "e7-mes", "aria-label": g.etiqueta }, [h("h4", { class: "e7-mes-titulo", text: g.etiqueta }), lista]));
    });
  }

  // ================================================================ Ficha: participantes (W12, W13)

  function pintarParticipantes(feria, contenedor) {
    if (!contenedor) return;
    var caja = h("section", { class: "e7-participantes", "aria-label": "Participantes confirmadas" }, [
      h("h3", { class: "e6-subtitulo", text: "Participan" }),
      h("p", { class: "tarjeta-sub", text: "Cargando participantes…" })
    ]);
    contenedor.appendChild(caja);
    if (feria.estado === "CANCELADA") {
      contenedor.insertBefore(h("p", { class: "e7-cancelada", role: "status", text: "Esta feria fue cancelada. No se realizará." }), contenedor.firstChild);
    }
    if (!real()) { caja.lastChild.textContent = "Conecta la vitrina al servidor para ver las participantes."; return; }
    ctx.servicio.participantesFeria(feria.id).then(function (r) {
      caja.removeChild(caja.lastChild);
      if (!r.ok) { caja.appendChild(h("p", { class: "e6-aviso", text: "No se pudieron cargar las participantes. " + msg(r.error) })); return; }
      var filas = r.datos || [];
      if (!filas.length) { caja.appendChild(h("p", { class: "tarjeta-sub", text: "Aún no hay participantes confirmadas." })); return; }
      filas.forEach(function (p) {
        var muestra = ctx.Cat.muestraCatalogo(ctx.productos(), p.tienda_id, 3);
        caja.appendChild(h("article", { class: "e7-participante" }, [
          h("div", { class: "e7-participante-cabeza" }, [
            h("strong", { text: p.nombre }),
            h("span", { class: "tarjeta-sub", text: [p.rubro, p.sector].filter(Boolean).join(" · ") }),
            h("button", { type: "button", class: "btn-accion-pill", text: "Ver tienda", onclick: function () { ctx.cerrarModal("modal-feria"); ctx.abrirVitrinaTienda(p.tienda_id); } })
          ]),
          muestra.length ? h("div", { class: "e7-muestra" }, muestra.map(function (prod) {
            var tienda = ctx.tiendas().find(function (t) { return t.id === prod.tiendaId; });
            return h("div", { class: "e7-muestra-item" }, [
              ctx.imagenSegura(prod.fotoUrl, prod.nombre, "e7-muestra-foto") || h("div", { class: "e7-muestra-foto e7-sin-foto", "aria-hidden": "true", text: "🛍️" }),
              h("span", { text: prod.nombre }),
              h("small", { text: ctx.Cat.textoPrecio(tienda, prod.precio, ctx.formatearMoneda) })
            ]);
          })) : h("p", { class: "tarjeta-sub", text: "Sin productos públicos por ahora." })
        ]));
      });
    });
  }

  // ================================================================ Seguir (W15, W16, W17)

  function claveSeguido(tipo, id) { return tipo + ":" + id; }

  function botonSeguir(tipo, id, nombre) {
    var b = h("button", { type: "button", class: "btn-accion-pill e7-seguir", "data-seguir": claveSeguido(tipo, id) });
    function pintar() {
      var siguiendo = !!SEGUIDOS[claveSeguido(tipo, id)];
      b.textContent = siguiendo ? "✓ Siguiendo" : "＋ Seguir";
      b.setAttribute("aria-pressed", siguiendo ? "true" : "false");
      b.setAttribute("aria-label", (siguiendo ? "Dejar de seguir a " : "Seguir a ") + nombre);
    }
    b.addEventListener("click", function () {
      if (SEGUIDOS[claveSeguido(tipo, id)]) dejarDeSeguir(tipo, id, nombre, b).then(pintar);
      else seguirOPedirSesion(tipo, id, nombre, b).then(pintar);
    });
    b._pintar = pintar;
    pintar();
    return b;
  }

  function repintarBotones() {
    document.querySelectorAll("[data-seguir]").forEach(function (b) { if (b._pintar) b._pintar(); });
  }

  function seguirOPedirSesion(tipo, id, nombre, boton) {
    if (!real()) { toast("Conecta la vitrina al servidor para seguir tiendas."); return Promise.resolve(); }
    if (!usuarioId) {
      escribirJson(CLAVE_PENDIENTE, ctx.Cat.crearAccionPendiente("seguir", tipo, id, nombre));
      ctx.abrirLogin("Inicia sesión o crea tu cuenta para seguir a " + nombre + ". Al volver quedarás siguiéndola.");
      vigilarCancelacionLogin();
      return Promise.resolve();
    }
    return seguir(tipo, id, nombre, boton);
  }

  function seguir(tipo, id, nombre, boton) {
    if (boton) boton.disabled = true;
    return ctx.servicio.seguir(tipo, id).then(function (r) {
      if (boton) boton.disabled = false;
      if (!r.ok) { toast("No se pudo seguir: " + msg(r.error)); return; }
      SEGUIDOS[claveSeguido(tipo, id)] = true;
      repintarBotones();
      toast("Ahora sigues a " + nombre + ".");
      if (consentimiento !== true) ofrecerConsentimiento(nombre);
    });
  }

  function dejarDeSeguir(tipo, id, nombre, boton) {
    if (boton) boton.disabled = true;
    return ctx.servicio.dejarDeSeguir(tipo, id).then(function (r) {
      if (boton) boton.disabled = false;
      if (!r.ok) { toast("No se pudo dejar de seguir: " + msg(r.error)); return; }
      delete SEGUIDOS[claveSeguido(tipo, id)];
      repintarBotones();
      toast("Dejaste de seguir a " + nombre + ". No recibirás sus avisos.");
      if (el("modal-avisos") && el("modal-avisos").classList.contains("abierto")) pintarPanelAvisos();
    });
  }

  /** Si se cierra el inicio de sesión sin entrar, la acción pendiente se descarta. */
  var observadorLogin = null;
  function vigilarCancelacionLogin() {
    var modal = el("modal-auth-web");
    if (!modal || observadorLogin || !window.MutationObserver) return;
    observadorLogin = new MutationObserver(function () {
      if (modal.classList.contains("abierto")) return;
      observadorLogin.disconnect(); observadorLogin = null;
      setTimeout(function () {
        ctx.sesionActual().then(function (uid) {
          var p = leerJson(CLAVE_PENDIENTE);
          if (!uid && p) { escribirJson(CLAVE_PENDIENTE, null); toast("No iniciaste sesión: no se siguió a " + p.nombre + "."); }
        });
      }, 400);
    });
    observadorLogin.observe(modal, { attributes: true, attributeFilter: ["class"] });
  }

  /** Ejecuta una sola vez la acción que el visitante intentó antes de entrar. */
  function completarAccionPendiente() {
    var p = leerJson(CLAVE_PENDIENTE);
    if (!p) return;
    escribirJson(CLAVE_PENDIENTE, null);          // se limpia antes: nunca se ejecuta dos veces
    if (!ctx.Cat.accionPendienteVigente(p)) return;
    if (p.accion === "seguir") seguir(p.tipo, p.id, p.nombre, null);
  }

  // ================================================================ Consentimiento y avisos (W18, RNF-PRI-02)

  function ofrecerConsentimiento(nombre) {
    var caja = el("e7-consentimiento");
    if (!caja) {
      caja = h("div", { id: "e7-consentimiento", class: "e7-consentimiento", role: "dialog", "aria-modal": "false", "aria-labelledby": "e7-consent-titulo" });
      document.body.appendChild(caja);
    }
    caja.textContent = "";
    caja.appendChild(h("h3", { id: "e7-consent-titulo", text: "¿Quieres recibir avisos?" }));
    caja.appendChild(h("p", { text: "Te avisaremos cuando " + (nombre || "las tiendas y asociaciones que sigues") +
      " confirme su participación en una feria. Solo eso: no enviamos publicidad. Puedes retirarlo cuando quieras en «Mis avisos»." }));
    caja.appendChild(h("div", { class: "e6-fila" }, [
      h("button", { type: "button", class: "btn-accion-pill btn-negro", text: "Sí, avisarme", onclick: function () { guardarConsentimiento(true); caja.remove(); } }),
      h("button", { type: "button", class: "btn-accion-pill", text: "Ahora no", onclick: function () { caja.remove(); } })
    ]));
    var primero = caja.querySelector("button");
    if (primero) primero.focus();
  }

  function guardarConsentimiento(si) {
    return ctx.servicio.guardarConsentimiento(si).then(function (r) {
      if (!r.ok) { toast("No se guardó tu preferencia: " + msg(r.error)); return; }
      consentimiento = si;
      if (si) {
        toast("Listo: te avisaremos. Permite las notificaciones del navegador para verlas.");
        activarAvisosNavegador();
      } else {
        toast("Ya no recibirás avisos de ferias. Puedes volver a activarlos cuando quieras.");
        quitarTokenWeb();
      }
      if (el("modal-avisos") && el("modal-avisos").classList.contains("abierto")) pintarPanelAvisos();
    });
  }

  function configFirebase() {
    var c = (window.__CONFIG__ || {}).firebase || {};
    return c.apiKey && c.projectId && c.messagingSenderId && c.appId && c.vapidKey ? c : null;
  }

  function cargarScript(src) {
    return new Promise(function (ok, mal) {
      var s = document.createElement("script"); s.src = src; s.async = true;
      s.onload = ok; s.onerror = function () { mal(new Error("No se pudo cargar " + src)); };
      document.head.appendChild(s);
    });
  }

  /** Pide permiso del navegador y, si hay Firebase configurado, registra este navegador para push. */
  function activarAvisosNavegador() {
    if (!("Notification" in window)) { toast("Este navegador no muestra notificaciones."); return Promise.resolve(false); }
    var permiso = Notification.permission === "granted" ? Promise.resolve("granted") : Notification.requestPermission();
    return Promise.resolve(permiso).then(function (p) {
      if (p !== "granted") { toast("Sin permiso del navegador: verás los avisos en «Mis avisos»."); return false; }
      if (!temporizadorAvisos) iniciarRevisionAvisos();
      var fb = configFirebase();
      if (!fb || !("serviceWorker" in navigator)) return true;   // avisos mientras la página está abierta
      var v = "10.14.1";
      return cargarScript("https://www.gstatic.com/firebasejs/" + v + "/firebase-app-compat.js")
        .then(function () { return cargarScript("https://www.gstatic.com/firebasejs/" + v + "/firebase-messaging-compat.js"); })
        .then(function () {
          var app = window.firebase.apps.length ? window.firebase.app() : window.firebase.initializeApp(fb);
          var parametros = encodeURIComponent(JSON.stringify({ apiKey: fb.apiKey, projectId: fb.projectId, messagingSenderId: fb.messagingSenderId, appId: fb.appId }));
          return navigator.serviceWorker.register("firebase-messaging-sw.js?config=" + parametros).then(function (reg) {
            return window.firebase.messaging(app).getToken({ vapidKey: fb.vapidKey, serviceWorkerRegistration: reg });
          });
        })
        .then(function (token) {
          if (!token) return true;
          return ctx.servicio.registrarDispositivo(token, "WEB").then(function (r) {
            if (r.ok) escribirJson(CLAVE_TOKEN_WEB, { token: token, uid: usuarioId });
            return true;
          });
        })
        .catch(function (e) { console.warn("Push web no disponible:", e && e.message); return true; });
    });
  }

  function quitarTokenWeb() {
    var t = leerJson(CLAVE_TOKEN_WEB);
    if (!t || !real()) return Promise.resolve();
    escribirJson(CLAVE_TOKEN_WEB, null);
    return ctx.servicio.quitarDispositivo(t.token).then(function () {}, function () {});
  }

  /** Con la página abierta: muestra como notificación los avisos nuevos que aún no se confirmaron como recibidos. */
  var INTERVALO_AVISOS_MS = 60000;
  var nuevosDesdeQueAbrio = 0;
  function permisoNotificaciones() { return "Notification" in window && Notification.permission === "granted"; }

  /**
   * Con la página abierta y visible: pide al servidor los avisos que CORRESPONDE mostrar
   * ahora (misma regla que el push: consentimiento, seguir, horario, feria activa) y los
   * muestra. Solo si mostrarlos funcionó, este destino confirma «mostrado» (y «abierto» al tocarla).
   */
  /**
   * Destino de ESTE navegador: su token de push si se registró; si no, un identificador
   * propio guardado en el navegador. Así la web y el celular confirman cada uno lo suyo.
   */
  var CLAVE_DESTINO_WEB = "alerce_destino_web";
  function destinoWeb() {
    var t = leerJson(CLAVE_TOKEN_WEB);
    if (t && t.token && t.uid === usuarioId) return t.token;
    var d = leerJson(CLAVE_DESTINO_WEB);
    if (!d || typeof d !== "string") { d = "web:" + nuevaSolicitud(); escribirJson(CLAVE_DESTINO_WEB, d); }
    return d;
  }

  function revisarAvisos() {
    if (!usuarioId || !real() || !permisoNotificaciones() || document.visibilityState === "hidden") return;
    var destino = destinoWeb();
    ctx.servicio.avisosParaMostrar(destino, 10).then(function (r) {
      if (!r.ok) return;
      (r.datos || []).forEach(function (a) {
        if (avisosMostrados[a.id]) return;
        avisosMostrados[a.id] = true;
        var mostrada = false;
        try {
          var n = new Notification(a.titulo, { body: a.cuerpo || "", tag: a.id });
          n.onclick = function () {
            ctx.servicio.confirmarAviso(a.id, destino, "ABIERTO");
            window.focus(); if (a.feria_id) window.AlerceVitrina6.abrirFeria(a.feria_id);
          };
          mostrada = true;
        } catch (e) { /* algunos navegadores solo permiten notificaciones desde el service worker: queda en la bandeja */ }
        if (mostrada) {
          ctx.servicio.confirmarAviso(a.id, destino, "MOSTRADO");
          nuevosDesdeQueAbrio++;
        }
      });
      var insignia = el("e7-insignia-avisos");
      if (insignia) { insignia.textContent = nuevosDesdeQueAbrio ? String(nuevosDesdeQueAbrio) : ""; insignia.style.display = nuevosDesdeQueAbrio ? "" : "none"; }
    });
  }

  /** Un solo temporizador; solo con sesión, permiso del navegador y la pestaña visible. */
  function iniciarRevisionAvisos() {
    detenerRevisionAvisos();
    if (!usuarioId || !real() || !permisoNotificaciones() || document.visibilityState === "hidden") return;
    revisarAvisos();
    temporizadorAvisos = setInterval(revisarAvisos, INTERVALO_AVISOS_MS);
  }
  function detenerRevisionAvisos() { if (temporizadorAvisos) clearInterval(temporizadorAvisos); temporizadorAvisos = null; }
  document.addEventListener("visibilitychange", function () {
    if (!ctx) return;
    if (document.visibilityState === "hidden") detenerRevisionAvisos(); else iniciarRevisionAvisos();
  });

  // ---- Panel «Mis avisos»: consentimiento, lo que sigo y bandeja
  function asegurarModalAvisos() {
    if (el("modal-avisos")) return;
    document.body.appendChild(h("div", { class: "modal-overlay", id: "modal-avisos", role: "dialog", "aria-modal": "true", "aria-labelledby": "modal-avisos-titulo" }, [
      h("div", { class: "modal-panel-editorial", style: "max-width: 560px;" }, [
        h("div", { class: "modal-cabecera" }, [
          h("h2", { id: "modal-avisos-titulo", style: "font-size: 17px; font-weight: 800;", text: "🔔 Mis avisos" }),
          h("button", { type: "button", class: "btn-cerrar-x", "aria-label": "Cerrar", text: "✕", onclick: function () { ctx.cerrarModal("modal-avisos"); } })
        ]),
        h("div", { class: "modal-cuerpo", id: "modal-avisos-cuerpo" })
      ])
    ]));
  }

  function abrirPanelAvisos() {
    if (!usuarioId) { ctx.abrirLogin("Inicia sesión para ver tus avisos."); return; }
    nuevosDesdeQueAbrio = 0;
    var insignia = el("e7-insignia-avisos");
    if (insignia) { insignia.textContent = ""; insignia.style.display = "none"; }
    asegurarModalAvisos();
    pintarPanelAvisos();
    ctx.abrirModal("modal-avisos");
  }

  function pintarPanelAvisos() {
    var c = el("modal-avisos-cuerpo");
    if (!c) return;
    c.textContent = "";
    c.appendChild(h("h3", { class: "e6-subtitulo", text: "Avisos de ferias" }));
    var estadoTxt = consentimiento === true ? "Activados: te avisamos cuando alguien que sigues confirma su participación en una feria." :
      "Desactivados: no recibirás avisos de ferias.";
    c.appendChild(h("p", { class: "e6-aviso" + (consentimiento === true ? " ok" : ""), role: "status", text: estadoTxt }));
    c.appendChild(h("div", { class: "e6-fila" }, [
      consentimiento === true
        ? h("button", { type: "button", class: "btn-accion-pill", text: "Darme de baja de los avisos", onclick: function () { guardarConsentimiento(false); } })
        : h("button", { type: "button", class: "btn-accion-pill btn-negro", text: "Sí, quiero recibir avisos", onclick: function () { guardarConsentimiento(true); } }),
      ("Notification" in window && Notification.permission !== "granted" && consentimiento === true)
        ? h("button", { type: "button", class: "btn-accion-pill", text: "Permitir notificaciones del navegador", onclick: activarAvisosNavegador }) : null
    ]));
    if (!configFirebase()) {
      c.appendChild(h("p", { class: "tarjeta-sub", text: "En esta vitrina las notificaciones del navegador aparecen mientras la página está abierta (el envío con la página cerrada aún no está configurado)." }));
    }
    c.appendChild(h("h3", { class: "e6-subtitulo", text: "Lo que sigues" }));
    var claves = Object.keys(SEGUIDOS);
    if (!claves.length) c.appendChild(h("p", { class: "tarjeta-sub", text: "Aún no sigues tiendas ni asociaciones." }));
    claves.forEach(function (k) {
      var partes = k.split(":"), tipo = partes[0], id = partes.slice(1).join(":");
      var nombre = tipo === "TIENDA" ? nombreTienda(id) : ((asociaciones().find(function (a) { return a.id === id; }) || {}).nombre || id);
      c.appendChild(h("div", { class: "e6-admin-item" }, [
        h("div", null, [h("strong", { text: nombre }), h("small", { text: tipo === "TIENDA" ? "Tienda" : "Asociación" })]),
        h("button", { type: "button", class: "btn-accion-pill", text: "Dejar de seguir", onclick: function () { dejarDeSeguir(tipo, id, nombre, null); } })
      ]));
    });
    c.appendChild(h("h3", { class: "e6-subtitulo", text: "Últimos avisos" }));
    var lista = h("div", { class: "e7-bandeja" }, [h("p", { class: "tarjeta-sub", text: "Cargando…" })]);
    c.appendChild(lista);
    var POR_PAGINA = 20;
    function cargarPagina(desde) {
    ctx.servicio.misAvisos(POR_PAGINA, desde).then(function (r) {
      if (desde === 0) lista.textContent = "";
      var mas = lista.querySelector(".e7-ver-mas");
      if (mas) mas.remove();
      if (!r.ok) { lista.appendChild(h("p", { class: "e6-aviso", text: msg(r.error) })); return; }
      var filas = r.datos || [];
      var visibles = filas.filter(function (a) { return a.estado !== "OMITIDO" || a.tipo === "CONTACTO"; });
      if (!visibles.length && desde === 0 && filas.length < POR_PAGINA) { lista.appendChild(h("p", { class: "tarjeta-sub", text: "No tienes avisos." })); return; }
      visibles.forEach(function (a) {
        lista.appendChild(h("button", {
          type: "button", class: "e7-aviso-item" + (a.leido_en ? "" : " nuevo"),
          onclick: function () {
            ctx.servicio.marcarLeido(a.id);   // leído en la bandeja (no es una notificación mostrada)
            if (a.feria_id) { ctx.cerrarModal("modal-avisos"); window.AlerceVitrina6.abrirFeria(a.feria_id); }
          }
        }, [
          h("strong", { text: a.titulo }),
          h("span", { text: a.cuerpo || "" }),
          h("small", { text: new Date(a.creado_en).toLocaleString("es-CL") })
        ]));
      });
      if (filas.length === POR_PAGINA) {
        lista.appendChild(h("button", { type: "button", class: "btn-accion-pill e7-ver-mas", text: "Ver más", onclick: function () { cargarPagina(desde + POR_PAGINA); } }));
      }
    });
    }
    cargarPagina(0);
  }

  // ================================================================ Emprendedora: mis invitaciones (RF-FER-08/09)

  function pintarMisFerias(cont) {
    if (!cont) return;
    cont.textContent = "";
    if (!real()) { cont.appendChild(h("p", { class: "e6-aviso", text: "Conecta la vitrina al servidor para ver tus ferias." })); return; }
    cont.appendChild(h("p", { class: "tarjeta-sub", text: "Las ferias son solo por invitación de la administración. Responde con un toque." }));
    var lista = h("div", null, [h("p", { class: "tarjeta-sub", text: "Cargando tus ferias…" })]);
    cont.appendChild(lista);
    ctx.servicio.misFerias().then(function (r) {
      lista.textContent = "";
      if (!r.ok) { lista.appendChild(h("p", { class: "e6-aviso", text: msg(r.error) })); return; }
      var filas = r.datos || [];
      if (!filas.length) { lista.appendChild(h("p", { class: "tarjeta-sub", text: "Todavía no tienes invitaciones a ferias." })); return; }
      filas.forEach(function (f) {
        var feria = ctx.Cat.normalizarFeria({ id: f.feria_id, nombre: f.nombre, ubicacion: f.ubicacion, fecha_inicio: f.fecha_inicio, fecha_fin: f.fecha_fin, horario: f.horario, estado: f.estado });
        var activa = ctx.Cat.feriaActiva(feria);
        var aviso = h("p", { class: "e6-aviso", role: "status" });
        function boton(texto, participo, principal) {
          return h("button", {
            type: "button", class: "btn-accion-pill" + (principal ? " btn-negro" : ""), text: texto,
            disabled: !activa || f.invitacion === "AGREGADA" ? true : null,
            onclick: function (ev) {
              var b = ev.currentTarget; b.disabled = true; aviso.textContent = "Enviando…";
              ctx.servicio.responderInvitacion(f.feria_id, participo).then(function (res) {
                b.disabled = false;
                if (!res.ok) { aviso.textContent = "No se guardó: " + msg(res.error); return; }
                toast(participo ? "Confirmaste tu participación." : "Respondiste que no participas.");
                pintarMisFerias(cont);
              });
            }
          });
        }
        lista.appendChild(h("div", { class: "tarjeta-feria-gestion" + (f.invitacion === "CONFIRMADA" || f.invitacion === "AGREGADA" ? " inscrita" : "") }, [
          h("div", { style: "flex: 1; min-width: 220px;" }, [
            h("strong", { text: f.nombre }),
            h("p", { class: "tarjeta-sub", text: (f.fecha_inicio || "Fecha por confirmar") + (f.horario ? " · " + f.horario : "") + " · " + (f.ubicacion || "") }),
            h("span", { class: "e7-chip e7-" + String(f.invitacion).toLowerCase(), text: f.estado === "CANCELADA" ? "Feria cancelada" : ctx.Cat.etiquetaInvitacion(f.invitacion) })
          ]),
          h("div", { class: "e6-fila" }, [boton("Participo", true, true), boton("No participo", false, false)]),
          aviso
        ]));
      });
    });
  }

  // ================================================================ Administración de ferias (W01–W11)

  var ESTADOS_FERIA = { BORRADOR: "Borrador (no publicada)", PROXIMA: "Publicada", EN_CURSO: "Publicada (en curso)", FINALIZADA: "Finalizada", CANCELADA: "Cancelada" };

  /** Botones de una feria en la lista del administrador. */
  function accionesAdminFeria(f, alCambiar) {
    var acciones = [];
    if (f.estado === "BORRADOR" || f.estado === "CANCELADA") {
      acciones.push(h("button", { type: "button", class: "btn-accion-pill btn-negro", text: "📣 Publicar", onclick: function (ev) {
        var b = ev.currentTarget; b.disabled = true;
        ctx.servicio.publicarFeria(f.id).then(function (r) {
          b.disabled = false;
          if (!r.ok) { toast("No se publicó: " + msg(r.error)); return; }
          toast("Feria publicada: ya la ve cualquier visitante.");
          alCambiar();
        });
      } }));
    }
    if (ctx.Cat.feriaActiva(f)) {
      acciones.push(h("button", { type: "button", class: "btn-accion-pill", text: "📋 Invitaciones y checklist", onclick: function () { abrirChecklist(f); } }));
      acciones.push(h("button", { type: "button", class: "btn-accion-pill e7-peligro", text: "Cancelar feria", onclick: function (ev) {
        var motivo = window.prompt("¿Por qué se cancela «" + f.nombre + "»? (se avisará a invitadas y confirmadas)", "");
        if (motivo === null) return;
        var b = ev.currentTarget; b.disabled = true;
        ctx.servicio.cancelarFeria(f.id, motivo).then(function (r) {
          b.disabled = false;
          if (!r.ok) { toast("No se canceló: " + msg(r.error)); return; }
          toast("Feria cancelada. Avisos guardados para " + (r.datos && r.datos.avisadas || 0) + " emprendedoras.");
          alCambiar();
        });
      } }));
    } else if (f.estado === "FINALIZADA" || (f.estado !== "BORRADOR" && f.estado !== "CANCELADA")) {
      acciones.push(h("button", { type: "button", class: "btn-accion-pill", text: "📋 Checklist y asistencia", onclick: function () { abrirChecklist(f); } }));
    }
    return acciones;
  }

  function asegurarModalChecklist() {
    if (el("modal-checklist-feria")) return;
    document.body.appendChild(h("div", { class: "modal-overlay", id: "modal-checklist-feria", role: "dialog", "aria-modal": "true", "aria-labelledby": "modal-checklist-titulo" }, [
      h("div", { class: "modal-panel-editorial", style: "max-width: 860px;" }, [
        h("div", { class: "modal-cabecera" }, [
          h("h2", { id: "modal-checklist-titulo", style: "font-size: 17px; font-weight: 800;", text: "Checklist de la feria" }),
          h("button", { type: "button", class: "btn-cerrar-x", "aria-label": "Cerrar", text: "✕", onclick: cerrarChecklist })
        ]),
        h("div", { class: "modal-cuerpo", id: "modal-checklist-cuerpo" })
      ])
    ]));
  }

  var INTERVALO_CHECKLIST_MS = 20000;
  function detenerChecklist() {
    if (checklistActual.temporizador) clearInterval(checklistActual.temporizador);
    checklistActual.temporizador = null;
  }
  function checklistAbierto() {
    var m = el("modal-checklist-feria");
    return !!(m && m.classList.contains("abierto"));
  }
  /** Mientras el checklist está abierto y visible, se actualiza solo (W07); al cerrarlo se detiene. */
  function tickChecklist() {
    if (!checklistAbierto()) { detenerChecklist(); return; }
    if (document.visibilityState === "hidden") return;
    recargarChecklist();
  }
  function cerrarChecklist() {
    detenerChecklist();
    ctx.cerrarModal("modal-checklist-feria");
  }

  function abrirChecklist(f) {
    asegurarModalChecklist();
    detenerChecklist();
    checklistActual = { feriaId: f.id, feria: f, filas: [], filtro: { texto: "", estado: "", tipo: "", asociacion: "" }, temporizador: null };
    el("modal-checklist-titulo").textContent = "Checklist · " + f.nombre;
    construirChecklist();
    ctx.abrirModal("modal-checklist-feria");
    recargarChecklist();
    // Mientras está abierto se actualiza solo: así se ve la respuesta que llega desde la app (W07).
    checklistActual.temporizador = setInterval(tickChecklist, INTERVALO_CHECKLIST_MS);
  }

  function construirChecklist() {
    var c = el("modal-checklist-cuerpo");
    var f = checklistActual.feria;
    c.textContent = "";
    var activa = ctx.Cat.feriaActiva(f);
    c.appendChild(h("p", { class: "tarjeta-sub", text: (f.fechaInicio || "") + (f.fechaFin && f.fechaFin !== f.fechaInicio ? " al " + f.fechaFin : "") + " · " + (f.lugar || "") + " · " + (ESTADOS_FERIA[f.estado] || f.estado) }));

    // ---- Invitar o agregar (W03, W04, W05)
    if (activa) {
      var buscar = h("input", { type: "search", class: "e7-input", placeholder: "Buscar emprendedora…", "aria-label": "Buscar emprendedora para invitar" });
      var listaTiendas = h("div", { class: "e7-seleccion", id: "e7-sel-tiendas", role: "group", "aria-label": "Emprendedoras" });
      var listaAsoc = h("div", { class: "e7-seleccion", id: "e7-sel-asoc", role: "group", "aria-label": "Asociaciones" });
      var avisoInv = h("p", { class: "e6-aviso", role: "status", id: "e7-aviso-invitar" });
      buscar.addEventListener("input", function () { pintarSeleccionTiendas(buscar.value); });
      asociaciones().filter(function (a) { return a.activa !== false; }).forEach(function (a) {
        listaAsoc.appendChild(h("label", { class: "e7-check" }, [h("input", { type: "checkbox", value: a.id }), h("span", { text: a.nombre })]));
      });
      function enviar(directo) {
        var tiendas = Array.prototype.map.call(listaTiendas.querySelectorAll("input:checked"), function (i) { return i.value; });
        var asocs = Array.prototype.map.call(listaAsoc.querySelectorAll("input:checked"), function (i) { return i.value; });
        if (!tiendas.length && !asocs.length) { avisoInv.textContent = "Marca al menos una emprendedora o asociación."; return; }
        avisoInv.textContent = "Guardando…";
        ctx.servicio.invitarAFeria(f.id, tiendas, asocs, directo).then(function (r) {
          if (!r.ok) { avisoInv.textContent = "No se guardó: " + msg(r.error); return; }
          var d = r.datos || {};
          avisoInv.textContent = (directo ? "Agregadas: " : "Invitaciones nuevas: ") + (d.nuevas || 0) + " · actualizadas: " + (d.cambiadas || 0) +
            (d.sin_cambio ? " · ya estaban (sin duplicar): " + d.sin_cambio : "") + (directo ? "" : ". Cada una recibe el aviso en su app.");
          listaTiendas.querySelectorAll("input:checked").forEach(function (i) { i.checked = false; });
          listaAsoc.querySelectorAll("input:checked").forEach(function (i) { i.checked = false; });
          recargarChecklist();
        });
      }
      c.appendChild(h("section", { class: "e6-admin-tarjeta" }, [
        h("h3", { class: "e6-admin-titulo", text: "Invitar o agregar" }),
        h("p", { class: "tarjeta-sub", text: "Invitar: la emprendedora responde en su app. Agregar directamente: queda participando sin invitación. Al elegir una asociación se incluye a cada integrante; quien ya estaba no se duplica." }),
        buscar, listaTiendas,
        h("strong", { class: "e7-etiqueta", text: "Asociaciones" }), listaAsoc,
        h("div", { class: "e6-fila" }, [
          h("button", { type: "button", class: "btn-accion-pill btn-negro", text: "✉️ Invitar seleccionadas", onclick: function () { enviar(false); } }),
          h("button", { type: "button", class: "btn-accion-pill", text: "➕ Agregar directamente", onclick: function () { enviar(true); } })
        ]),
        avisoInv
      ]));
    } else {
      c.appendChild(h("p", { class: "e6-aviso", text: "La feria no está activa: no se pueden enviar invitaciones nuevas." }));
    }

    // ---- Checklist con búsqueda y filtros (W06, W08)
    var fil = checklistActual.filtro;
    var texto = h("input", { type: "search", class: "e7-input", placeholder: "Buscar en el checklist…", "aria-label": "Buscar en el checklist" });
    var selEstado = h("select", { "aria-label": "Filtrar por estado" }, [h("option", { value: "", text: "Todos los estados" })].concat(
      Object.keys(ctx.Cat.ETIQUETA_INVITACION).map(function (k) { return h("option", { value: k, text: ctx.Cat.ETIQUETA_INVITACION[k] }); })));
    var selTipo = h("select", { "aria-label": "Filtrar por tipo de emprendimiento", id: "e7-filtro-tipo" });
    var selAsoc = h("select", { "aria-label": "Filtrar por asociación", id: "e7-filtro-asoc" });
    [[texto, "texto", "input"], [selEstado, "estado", "change"], [selTipo, "tipo", "change"], [selAsoc, "asociacion", "change"]].forEach(function (x) {
      x[0].addEventListener(x[2], function () { fil[x[1]] = x[0].value; pintarFilasChecklist(); });
    });
    c.appendChild(h("section", { class: "e6-admin-tarjeta" }, [
      h("div", { class: "e7-cabeza-checklist" }, [
        h("h3", { class: "e6-admin-titulo", text: "Checklist" }),
        h("button", { type: "button", class: "btn-accion-pill", text: "↻ Actualizar", onclick: recargarChecklist })
      ]),
      h("p", { class: "tarjeta-sub", id: "e7-resumen-checklist", role: "status" }),
      h("div", { class: "e7-filtros" }, [texto, selEstado, selTipo, selAsoc]),
      h("div", { id: "e7-filas-checklist", class: "e7-filas" })
    ]));

    // ---- Recordatorios y avisos (W09, W10)
    if (activa) {
      var mensaje = h("textarea", { rows: "2", maxlength: "500", class: "e7-input", placeholder: "Ej: Lleguen a las 9:00 para armar los puestos.", "aria-label": "Mensaje del aviso a confirmadas" });
      var avisoEnvio = h("p", { class: "e6-aviso", role: "status", id: "e7-aviso-envio" });
      function enviarAviso(tipo, boton) {
        if (tipo === "AVISO_FERIA" && !mensaje.value.trim()) { avisoEnvio.textContent = "Escribe el mensaje del aviso."; return; }
        boton.disabled = true;
        var solicitud = boton.dataset.solicitud || (boton.dataset.solicitud = nuevaSolicitud());   // un doble clic reusa la misma
        ctx.servicio.enviarAvisoFeria(f.id, tipo, tipo === "AVISO_FERIA" ? mensaje.value.trim() : "", solicitud).then(function (r) {
          boton.disabled = false;
          if (!r.ok) { avisoEnvio.textContent = "No se envió: " + msg(r.error); return; }
          delete boton.dataset.solicitud;
          var n = (r.datos && r.datos.destinatarias) || 0;
          avisoEnvio.textContent = (tipo === "RECORDATORIO" ? "Recordatorio" : "Aviso") + " guardado para " + n + " emprendedora" + (n === 1 ? "" : "s") +
            ". Les llega a su app cuando el servicio de envío lo despacha.";
          if (tipo === "AVISO_FERIA") mensaje.value = "";
        });
      }
      c.appendChild(h("section", { class: "e6-admin-tarjeta" }, [
        h("h3", { class: "e6-admin-titulo", text: "Recordatorios y avisos" }),
        h("div", { class: "e6-fila" }, [
          h("button", { type: "button", class: "btn-accion-pill", id: "e7-btn-recordatorio", text: "⏰ Recordar a quienes no han respondido", onclick: function (ev) { enviarAviso("RECORDATORIO", ev.currentTarget); } })
        ]),
        mensaje,
        h("div", { class: "e6-fila" }, [
          h("button", { type: "button", class: "btn-accion-pill btn-negro", id: "e7-btn-aviso", text: "📢 Enviar aviso a las confirmadas", onclick: function (ev) { enviarAviso("AVISO_FERIA", ev.currentTarget); } })
        ]),
        avisoEnvio
      ]));
    }
  }

  function pintarSeleccionTiendas(textoBusqueda) {
    var cont = el("e7-sel-tiendas");
    if (!cont) return;
    var marcadas = {};
    cont.querySelectorAll("input:checked").forEach(function (i) { marcadas[i.value] = true; });
    cont.textContent = "";
    var yaEstan = {};
    checklistActual.filas.forEach(function (fl) { yaEstan[fl.tienda_id] = fl.invitacion; });
    var t = String(textoBusqueda || "").toLowerCase();
    ctx.tiendas().filter(function (ti) { return !t || (ti.nombre + " " + ti.rubro).toLowerCase().indexOf(t) >= 0; }).forEach(function (ti) {
      var estado = yaEstan[ti.id];
      cont.appendChild(h("label", { class: "e7-check" }, [
        h("input", { type: "checkbox", value: ti.id, checked: marcadas[ti.id] ? true : null }),
        h("span", { text: ti.nombre + (estado ? " · " + ctx.Cat.etiquetaInvitacion(estado) : "") })
      ]));
    });
  }

  function recargarChecklist() {
    var feriaId = checklistActual.feriaId;
    if (!feriaId) return;
    ctx.servicio.checklistFeria(feriaId).then(function (r) {
      if (checklistActual.feriaId !== feriaId) return;
      var resumen = el("e7-resumen-checklist");
      if (!r.ok) { if (resumen) resumen.textContent = "No se pudo leer el checklist: " + msg(r.error); return; }
      checklistActual.filas = r.datos || [];
      var tipos = {}, asocs = {};
      checklistActual.filas.forEach(function (f) { if (f.rubro) tipos[f.rubro] = true; (f.asociaciones || []).forEach(function (a) { asocs[a] = true; }); });
      var selTipo = el("e7-filtro-tipo"), selAsoc = el("e7-filtro-asoc");
      if (selTipo) {
        var vt = selTipo.value; selTipo.textContent = "";
        selTipo.appendChild(h("option", { value: "", text: "Todos los tipos" }));
        Object.keys(tipos).sort().forEach(function (k) { selTipo.appendChild(h("option", { value: k, text: k })); });
        selTipo.value = vt;
      }
      if (selAsoc) {
        var va = selAsoc.value; selAsoc.textContent = "";
        selAsoc.appendChild(h("option", { value: "", text: "Todas las asociaciones" }));
        selAsoc.appendChild(h("option", { value: "__ninguna", text: "Sin asociación" }));
        Object.keys(asocs).sort().forEach(function (k) {
          var a = asociaciones().find(function (x) { return x.id === k; });
          selAsoc.appendChild(h("option", { value: k, text: a ? a.nombre : k }));
        });
        selAsoc.value = va;
      }
      pintarSeleccionTiendas((document.querySelector("#modal-checklist-cuerpo input[type=search]") || {}).value);
      pintarFilasChecklist();
    });
  }

  function pintarFilasChecklist() {
    var cont = el("e7-filas-checklist");
    if (!cont) return;
    var f = checklistActual.feria;
    var hoy = ctx.Cat.fechaSantiago();
    var diaFeria = f.fechaInicio && hoy >= String(f.fechaInicio).slice(0, 10) && f.estado !== "CANCELADA";
    var cuenta = {};
    checklistActual.filas.forEach(function (x) { cuenta[x.invitacion] = (cuenta[x.invitacion] || 0) + 1; });
    var resumen = el("e7-resumen-checklist");
    if (resumen) resumen.textContent = Object.keys(ctx.Cat.ETIQUETA_INVITACION).map(function (k) { return ctx.Cat.ETIQUETA_INVITACION[k] + ": " + (cuenta[k] || 0); }).join(" · ");
    var botonRec = el("e7-btn-recordatorio");
    if (botonRec) botonRec.textContent = "⏰ Recordar a quienes no han respondido (" + (cuenta.PENDIENTE || 0) + ")";
    var botonAv = el("e7-btn-aviso");
    if (botonAv) botonAv.textContent = "📢 Enviar aviso a las confirmadas (" + ((cuenta.CONFIRMADA || 0) + (cuenta.AGREGADA || 0)) + ")";
    cont.textContent = "";
    var filas = ctx.Cat.filtrarChecklist(checklistActual.filas, checklistActual.filtro);
    if (!filas.length) { cont.appendChild(h("p", { class: "tarjeta-sub", text: checklistActual.filas.length ? "Nadie coincide con la búsqueda." : "Aún no hay invitadas." })); return; }
    filas.forEach(function (fl) {
      var participa = fl.invitacion === "CONFIRMADA" || fl.invitacion === "AGREGADA";
      var asis = h("select", { "aria-label": "Asistencia de " + fl.nombre, disabled: (!participa || !diaFeria) ? true : null }, [
        h("option", { value: "PENDIENTE", text: "Asistencia: sin registrar" }),
        h("option", { value: "ASISTIO", text: "Asistió" }),
        h("option", { value: "NO_ASISTIO", text: "No asistió" })
      ]);
      asis.value = fl.asistencia || "PENDIENTE";
      asis.title = !participa ? "Solo para confirmadas o agregadas" : (!diaFeria ? "Se registra desde el día de la feria" : "");
      asis.addEventListener("change", function () {
        var valor = asis.value; asis.disabled = true;
        ctx.servicio.registrarAsistencia(f.id, fl.tienda_id, valor).then(function (r) {
          asis.disabled = false;
          if (!r.ok) { toast("No se registró: " + msg(r.error)); asis.value = fl.asistencia || "PENDIENTE"; return; }
          fl.asistencia = valor;
          toast("Asistencia guardada: " + fl.nombre + " · " + ctx.Cat.ETIQUETA_ASISTENCIA[valor]);
        });
      });
      var nombresAsoc = (fl.asociaciones || []).map(function (a) { var x = asociaciones().find(function (y) { return y.id === a; }); return x ? x.nombre : a; });
      cont.appendChild(h("div", { class: "e7-fila", "data-tienda": fl.tienda_id }, [
        h("div", { class: "e7-fila-datos" }, [
          h("strong", { text: fl.nombre }),
          h("small", { text: [fl.rubro, fl.sector, nombresAsoc.join(", ")].filter(Boolean).join(" · ") +
            (fl.origen === "ASOCIACION" && fl.via_asociacion ? " · vía asociación" : "") })
        ]),
        h("span", { class: "e7-chip e7-" + String(fl.invitacion).toLowerCase(), "data-estado": fl.invitacion, text: ctx.Cat.etiquetaInvitacion(fl.invitacion) }),
        asis
      ]));
    });
  }

  // ================================================================ Sesión

  function alCambiarSesion(usuario) {
    var nuevo = usuario && usuario.id ? usuario.id : null;
    if (nuevo === usuarioId) return;
    usuarioId = nuevo;
    SEGUIDOS = {}; consentimiento = null; avisosMostrados = {};
    var boton = el("e7-boton-avisos");
    if (boton) boton.style.display = nuevo ? "" : "none";
    if (!nuevo) { detenerRevisionAvisos(); detenerChecklist(); repintarBotones(); return; }
    if (!real()) return;
    // El token web guardado de otra cuenta no se reutiliza.
    var t = leerJson(CLAVE_TOKEN_WEB);
    if (t && t.uid !== nuevo) escribirJson(CLAVE_TOKEN_WEB, null);
    Promise.all([ctx.servicio.misSeguimientos(), ctx.servicio.miConsentimiento()]).then(function (rs) {
      if (usuarioId !== nuevo) return;
      if (rs[0].ok) (rs[0].datos || []).forEach(function (s) { SEGUIDOS[claveSeguido(s.tipo, s.objetivo_id)] = true; });
      if (rs[1].ok) consentimiento = !!(rs[1].datos && rs[1].datos.avisos_ferias);
      repintarBotones();
      completarAccionPendiente();
      iniciarRevisionAvisos();
      if (consentimiento && "Notification" in window && Notification.permission === "granted") activarAvisosNavegador();
    });
  }

  /** Antes de cerrar sesión: este navegador deja de recibir avisos de la cuenta. */
  function antesDeCerrarSesion() {
    detenerRevisionAvisos();
    escribirJson(CLAVE_PENDIENTE, null);
    return quitarTokenWeb();
  }

  window.AlerceFerias7 = {
    iniciar: function (contexto) {
      ctx = contexto;
      var boton = el("e7-boton-avisos");
      if (boton) boton.addEventListener("click", abrirPanelAvisos);
      // Volvió del proveedor de inicio de sesión sin entrar: la acción pendiente se descarta.
      if (leerJson(CLAVE_PENDIENTE) && ctx.sesionActual) {
        setTimeout(function () {
          ctx.sesionActual().then(function (uid) {
            var p = leerJson(CLAVE_PENDIENTE);
            if (!uid && p && !(el("modal-auth-web") && el("modal-auth-web").classList.contains("abierto"))) {
              escribirJson(CLAVE_PENDIENTE, null);
              toast("No se completó el inicio de sesión: no se siguió a " + p.nombre + ".");
            }
          });
        }, 1500);
      }
    },
    alCambiarSesion: function (u) { if (ctx) alCambiarSesion(u); },
    antesDeCerrarSesion: function () { return ctx ? antesDeCerrarSesion() : Promise.resolve(); },
    alCargarFerias: function () { if (ctx) pintarCalendario(); },
    pintarParticipantes: function (f, c) { if (ctx) pintarParticipantes(f, c); },
    botonSeguir: function (tipo, id, nombre) { return ctx ? botonSeguir(tipo, id, nombre) : null; },
    accionesAdminFeria: function (f, alCambiar) { return ctx ? accionesAdminFeria(f, alCambiar) : []; },
    pintarMisFerias: function (c) { if (ctx) pintarMisFerias(c); },
    abrirPanelAvisos: function () { if (ctx) abrirPanelAvisos(); }
  };
})();
