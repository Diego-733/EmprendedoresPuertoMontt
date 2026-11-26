/**
 * Buscar por voz en la vitrina (Web Speech API del navegador).
 *
 * - El micrófono solo se enciende cuando la persona toca el botón. No se graba ni se guarda audio:
 *   el navegador entrega texto y ese texto va al mismo buscador que lo escrito.
 * - Según el navegador, el reconocimiento puede hacerse en los servidores de su proveedor
 *   (por ejemplo, Google en Chrome). Eso se explica en el aviso de privacidad.
 * - Si el navegador no lo permite (sin API o página sin HTTPS), el botón no aparece.
 * - Cancelar, negar el permiso o un error NO tocan el buscador: queda la búsqueda anterior.
 */
(function (raiz, fabrica) {
  var modulo = fabrica();
  if (typeof module === "object" && module.exports) module.exports = modulo;
  else raiz.AlerceVoz = modulo;
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  var MENSAJES = {
    escuchando: "Escuchando… di lo que buscas.",
    cancelado: "Dictado cancelado. Tu búsqueda sigue igual.",
    sinVoz: "No escuchamos nada. Toca el micrófono para intentar de nuevo o escribe.",
    permiso: "El navegador no dio permiso para usar el micrófono. Puedes escribir tu búsqueda.",
    sinMicrofono: "No encontramos un micrófono. Puedes escribir tu búsqueda.",
    red: "El dictado necesita conexión. Puedes escribir tu búsqueda.",
    error: "No pudimos entender el dictado. Puedes escribir tu búsqueda."
  };
  var ETIQUETA = "Buscar por voz";
  var ETIQUETA_ESCUCHANDO = "Detener el dictado";

  /** Constructor del reconocimiento o null si este navegador/página no lo permite. */
  function reconocimientoDisponible(ventana) {
    if (!ventana || ventana.isSecureContext === false) return null;
    return ventana.SpeechRecognition || ventana.webkitSpeechRecognition || null;
  }

  /** Código de error de la Web Speech API → frase breve para la persona. */
  function mensajeError(codigo) {
    switch (codigo) {
      case "aborted": return MENSAJES.cancelado;
      case "no-speech": return MENSAJES.sinVoz;
      case "not-allowed": case "service-not-allowed": return MENSAJES.permiso;
      case "audio-capture": return MENSAJES.sinMicrofono;
      case "network": return MENSAJES.red;
      default: return MENSAJES.error;
    }
  }

  /**
   * Conecta el botón del micrófono a un campo de búsqueda.
   * opciones: { campo, boton, estado (elemento role=status), ventana (window),
   *   etiqueta (nombre accesible), textos (frases propias del campo), alTerminar(texto) }
   */
  function conectar(opciones) {
    var campo = opciones.campo, boton = opciones.boton, estado = opciones.estado, ventana = opciones.ventana;
    var Reconocimiento = reconocimientoDisponible(ventana);
    if (!campo || !boton) return { disponible: false };
    if (!Reconocimiento) { boton.hidden = true; return { disponible: false }; }
    boton.hidden = false;
    var etiqueta = opciones.etiqueta || ETIQUETA;
    boton.setAttribute("aria-label", etiqueta);
    boton.setAttribute("aria-pressed", "false");

    // Textos propios por campo (opciones.textos); por omisión, los del buscador.
    var T = { aplicado: function (t) { return "Buscando «" + t + "». Puedes corregirlo en el buscador."; } };
    Object.keys(MENSAJES).forEach(function (k) { T[k] = MENSAJES[k]; });
    Object.keys(opciones.textos || {}).forEach(function (k) { T[k] = opciones.textos[k]; });
    function errorTexto(codigo) {
      var base = mensajeError(codigo);
      var clave = Object.keys(MENSAJES).filter(function (k) { return MENSAJES[k] === base; })[0];
      return clave ? T[clave] : base;
    }
    var rec = null, anterior = "", obtuvoTexto = false, avisado = false, cancelando = false;

    function decir(texto) { if (estado) estado.textContent = texto || ""; }
    function marcarEscuchando(si) {
      boton.setAttribute("aria-pressed", si ? "true" : "false");
      boton.setAttribute("aria-label", si ? ETIQUETA_ESCUCHANDO : etiqueta);
      boton.classList.toggle("escuchando", !!si);
    }
    function terminar() { rec = null; marcarEscuchando(false); }

    function aplicar(texto) {
      campo.value = texto;
      campo.dispatchEvent(new ventana.Event("input", { bubbles: true }));   // misma búsqueda que al escribir
      try { campo.focus(); campo.setSelectionRange(texto.length, texto.length); } catch (e) { /* type=search sin selección */ }
      decir(T.aplicado(texto));
      if (opciones.alTerminar) opciones.alTerminar(texto);
    }

    function iniciar() {
      // Si la página estaba leyendo en voz alta, se calla antes de abrir el micrófono.
      try { if (ventana.speechSynthesis) ventana.speechSynthesis.cancel(); } catch (e) { /* sin lectura */ }
      anterior = campo.value; obtuvoTexto = false; avisado = false; cancelando = false;
      try { rec = new Reconocimiento(); } catch (e) { rec = null; decir(T.error); return; }
      rec.lang = "es-CL";
      rec.interimResults = true;
      rec.continuous = false;
      rec.maxAlternatives = 1;
      rec.onresult = function (ev) {
        if (cancelando) return;   // tras cancelar no se aplica nada de lo escuchado
        var final = "", parcial = "";
        for (var i = ev.resultIndex || 0; i < ev.results.length; i++) {
          var r = ev.results[i];
          if (r.isFinal) final += r[0].transcript; else parcial += r[0].transcript;
        }
        if (final.trim()) { obtuvoTexto = true; aplicar(final.trim()); }
        else if (parcial.trim()) decir("Escuchando: «" + parcial.trim() + "»");   // el buscador no cambia hasta el final
      };
      rec.onerror = function (ev) {
        avisado = true;
        if (!obtuvoTexto) campo.value = anterior;
        decir(errorTexto(ev && ev.error));
      };
      rec.onend = function () {
        if (!obtuvoTexto && !avisado) decir(T.sinVoz);
        terminar();
      };
      marcarEscuchando(true);
      decir(T.escuchando);
      try { rec.start(); } catch (e) { decir(T.error); terminar(); }
    }

    function cancelar() {
      if (!rec) return;
      cancelando = true;
      try { rec.abort(); } catch (e) { avisado = true; decir(T.cancelado); terminar(); }
    }

    boton.addEventListener("click", function () { if (rec) cancelar(); else iniciar(); });
    // Escape detiene el dictado. preventDefault: en un campo type=search, Escape además borraría la búsqueda.
    ventana.document.addEventListener("keydown", function (e) { if (rec && e.key === "Escape") { e.preventDefault(); cancelar(); } }, true);
    return { disponible: true, iniciar: iniciar, cancelar: cancelar };
  }

  return { MENSAJES: MENSAJES, reconocimientoDisponible: reconocimientoDisponible, mensajeError: mensajeError, conectar: conectar };
});
