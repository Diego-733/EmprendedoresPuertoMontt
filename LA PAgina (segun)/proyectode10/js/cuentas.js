/* ============================================================
   Cuentas (administración integral, 26-nov-2026)
   · Mostrar u ocultar la contraseña en todos los formularios (botón de 44 px con nombre).
   · «Mi perfil»: activar la tienda con el código de activación (RN-01, migración 11) y cambiar la
     contraseña con sesión (se comprueba la actual antes de cambiarla; nada se guarda en el navegador).
   · Aviso de cuenta desactivada en el panel de la emprendedora (lo informa el servidor).
   El rol y los permisos los decide SIEMPRE el servidor; aquí solo se muestra el estado.
   ============================================================ */
(function () {
  "use strict";

  function crear(tag, atributos, hijos) {
    var el = document.createElement(tag);
    Object.keys(atributos || {}).forEach(function (k) {
      if (k === "text") el.textContent = atributos[k];
      else if (k === "class") el.className = atributos[k];
      else el.setAttribute(k, atributos[k]);
    });
    (hijos || []).forEach(function (h) { if (h) el.appendChild(h); });
    return el;
  }
  function servicio() { return window.TramaWeb && window.TramaWeb.obtenerServicio ? window.TramaWeb.obtenerServicio() : null; }
  function usuario() { return window.TramaWeb && window.TramaWeb.obtenerUsuario ? window.TramaWeb.obtenerUsuario() : null; }
  function mensaje(error) { return window.AlerceCatalogo && window.AlerceCatalogo.mensajeError ? window.AlerceCatalogo.mensajeError(error) : (error && error.message) || "No se pudo completar."; }

  // ---------- 1. Mostrar u ocultar contraseñas ----------
  function agregarBotonVer(input) {
    if (!input || input.dataset.conVer) return;
    input.dataset.conVer = "1";
    var envoltura = crear("div", { class: "cuentas-clave" });
    input.parentNode.insertBefore(envoltura, input);
    envoltura.appendChild(input);
    var boton = crear("button", { type: "button", class: "cuentas-ver-clave", "aria-label": "Mostrar la contraseña", "aria-pressed": "false", text: "Mostrar" });
    boton.addEventListener("click", function () {
      var ver = input.type === "password";
      input.type = ver ? "text" : "password";
      boton.textContent = ver ? "Ocultar" : "Mostrar";
      boton.setAttribute("aria-label", ver ? "Ocultar la contraseña" : "Mostrar la contraseña");
      boton.setAttribute("aria-pressed", ver ? "true" : "false");
    });
    envoltura.appendChild(boton);
  }
  function prepararClaves() { document.querySelectorAll('input[type="password"]').forEach(agregarBotonVer); }

  // ---------- 2. «Mi perfil»: activar con código y cambiar contraseña ----------
  function seccionPerfil() {
    var modal = document.getElementById("modal-mi-perfil");
    if (!modal || document.getElementById("cuentas-seccion")) return;
    var cuerpo = modal.querySelector(".modal-cuerpo");
    var antes = document.getElementById("perfil-aviso-error");
    if (!cuerpo || !antes) return;

    var aviso = crear("p", { id: "cuentas-aviso", role: "status", class: "cuentas-aviso", hidden: "" });
    var activar = crear("div", { id: "cuentas-activar", class: "cuentas-bloque", hidden: "" }, [
      crear("strong", { text: "Activar mi tienda con un código" }),
      crear("p", { class: "cuentas-ayuda", id: "cuentas-activar-ayuda", text: "La administración autoriza tu correo y te entrega un código de 8 letras y números. Escríbelo aquí." }),
      crear("label", { for: "cuentas-codigo", class: "label-fb-market", text: "Código de activación" }),
      crear("input", { id: "cuentas-codigo", class: "input-fb-market", placeholder: "ABCD-2345", maxlength: "9", autocomplete: "one-time-code" }),
      crear("button", { type: "button", id: "cuentas-btn-activar", class: "btn-accion-pill btn-negro", text: "Activar mi tienda" })
    ]);
    var clave = crear("details", { id: "cuentas-clave", class: "cuentas-bloque" }, [
      crear("summary", { text: "Cambiar mi contraseña" }),
      crear("label", { for: "cuentas-clave-actual", class: "label-fb-market", text: "Contraseña actual" }),
      crear("input", { id: "cuentas-clave-actual", type: "password", class: "input-fb-market", autocomplete: "current-password" }),
      crear("label", { for: "cuentas-clave-nueva", class: "label-fb-market", text: "Contraseña nueva (mínimo 8)" }),
      crear("input", { id: "cuentas-clave-nueva", type: "password", class: "input-fb-market", autocomplete: "new-password" }),
      crear("label", { for: "cuentas-clave-repetir", class: "label-fb-market", text: "Repite la contraseña nueva" }),
      crear("input", { id: "cuentas-clave-repetir", type: "password", class: "input-fb-market", autocomplete: "new-password" }),
      crear("button", { type: "button", id: "cuentas-btn-clave", class: "btn-accion-pill", text: "Cambiar contraseña" })
    ]);
    var seccion = crear("div", { id: "cuentas-seccion", class: "cuentas-seccion" }, [activar, clave, aviso]);
    cuerpo.insertBefore(seccion, antes);
    prepararClaves();

    function decir(texto, ok) { aviso.hidden = false; aviso.textContent = texto; aviso.className = "cuentas-aviso " + (ok ? "ok" : "error"); }

    document.getElementById("cuentas-btn-activar").addEventListener("click", function () {
      var s = servicio(), boton = this;
      var codigo = document.getElementById("cuentas-codigo").value.replace(/[^A-Za-z0-9]/g, "");
      if (!s) { decir("Esta página no está conectada al servidor.", false); return; }
      if (codigo.length !== 8) { decir("El código tiene 8 letras y números (por ejemplo, ABCD-2345).", false); return; }
      boton.disabled = true; boton.textContent = "Activando…";
      s.activarConCodigo(codigo).then(function (r) {
        boton.disabled = false; boton.textContent = "Activar mi tienda";
        if (!r.ok) { decir(mensaje(r.error), false); return; }
        var res = String(r.datos || "");
        if (res.indexOf("CODIGO_INCORRECTO") === 0) {
          var quedan = Number(res.split(":")[1]);
          decir("El código no coincide." + (quedan > 0 ? " Te quedan " + quedan + " intentos." : " Ya no quedan intentos: pide un código nuevo."), false);
          return;
        }
        // Seguridad: quien haya entrado antes con esta cuenta en otro dispositivo pierde el acceso.
        try { var cl = window.supabase && window.TramaWeb.clienteAuth ? window.TramaWeb.clienteAuth() : null; if (cl) cl.signOut({ scope: "others" }); } catch (e) { /* no bloquea */ }
        decir("¡Listo! Tu tienda quedó activada. La página se vuelve a cargar con tu panel.", true);
        setTimeout(function () { location.reload(); }, 1500);
      });
    });

    document.getElementById("cuentas-btn-clave").addEventListener("click", function () {
      var s = servicio(), u = usuario(), boton = this;
      var actual = document.getElementById("cuentas-clave-actual").value;
      var nueva = document.getElementById("cuentas-clave-nueva").value;
      var repetir = document.getElementById("cuentas-clave-repetir").value;
      if (!s || !u || !u.email) { decir("Inicia sesión para cambiar tu contraseña.", false); return; }
      if (nueva.length < 8) { decir("La contraseña nueva debe tener al menos 8 caracteres.", false); return; }
      if (nueva !== repetir) { decir("Las contraseñas nuevas no coinciden.", false); return; }
      if (nueva === actual) { decir("La contraseña nueva debe ser distinta de la actual.", false); return; }
      boton.disabled = true; boton.textContent = "Cambiando…";
      s.cambiarContrasena(u.email, actual, nueva).then(function (r) {
        boton.disabled = false; boton.textContent = "Cambiar contraseña";
        ["cuentas-clave-actual", "cuentas-clave-nueva", "cuentas-clave-repetir"].forEach(function (id) { document.getElementById(id).value = ""; });
        decir(r.ok ? "Tu contraseña se cambió. Úsala la próxima vez que entres." : mensaje(r.error), r.ok);
      });
    });
  }

  // Estado de la cuenta: muestra «Activar» solo a compradoras con sesión y avisa si la tienda está desactivada.
  function actualizarEstado() {
    var s = servicio(), u = usuario();
    var activar = document.getElementById("cuentas-activar");
    var clave = document.getElementById("cuentas-clave");
    if (clave) clave.hidden = !(u && u.email);
    if (!s || !u || !u.email) { if (activar) activar.hidden = true; return; }
    s.estadoMiCuenta().then(function (r) {
      var e = r.ok && r.datos ? r.datos : null;
      if (activar) {
        activar.hidden = !(u.rol === "VISITANTE" || (e && e.rol === "VISITANTE"));
        var ayuda = document.getElementById("cuentas-activar-ayuda");
        if (ayuda && e && e.estado === "AUTORIZACION_PENDIENTE") {
          ayuda.textContent = e.codigo_vigente ? "La administración autorizó tu correo. Escribe tu código de activación."
            : "La administración autorizó tu correo, pero tu código venció o se agotaron los intentos. Pide uno nuevo.";
        }
      }
      var panel = document.getElementById("banner-emprendedoras-cta");
      var existente = document.getElementById("cuentas-desactivada");
      if (e && e.estado === "DESACTIVADA" && panel && !existente) {
        var nota = crear("div", { id: "cuentas-desactivada", class: "aviso-estado aviso-acabo", role: "alert" },
          [crear("span", { text: "La administración desactivó tu tienda: no se muestra en la vitrina y no puedes hacer cambios. Lo que guardaste se conserva." })]);
        panel.insertBefore(nota, panel.children[1] || null);
      }
    });
  }

  function iniciar() {
    prepararClaves();
    seccionPerfil();
    // Los formularios se abren y se llenan después: se revisa al abrir «Mi perfil» y cada cierto tiempo breve al inicio.
    document.addEventListener("click", function () { setTimeout(function () { prepararClaves(); seccionPerfil(); }, 50); }, true);
    var perfil = document.getElementById("modal-mi-perfil");
    if (perfil && window.MutationObserver) {
      new MutationObserver(function () { if (perfil.style.display !== "none") actualizarEstado(); }).observe(perfil, { attributes: true, attributeFilter: ["style", "class"] });
    }
    setTimeout(actualizarEstado, 2500);
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", iniciar); else iniciar();
})();
