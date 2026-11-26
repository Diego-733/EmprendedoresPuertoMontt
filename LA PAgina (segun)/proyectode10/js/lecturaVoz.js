/**
 * Lectura en voz alta (speechSynthesis del navegador): «🔊 Escuchar» en la guía paso a paso.
 *
 * - Solo habla cuando la persona toca «Escuchar»: nunca sola.
 * - Usa una voz en español del propio navegador o sistema (Chile primero). Sin costo y sin
 *   servicios de Alerce. Algunos navegadores usan voces en línea de su proveedor.
 * - Si el navegador no tiene lectura en voz alta o no tiene voz en español, el botón no aparece
 *   y todo sigue funcionando con el texto en pantalla.
 * - Se calla al cerrar la guía y antes de abrir el micrófono (busquedaVoz.js), para que el
 *   dictado no escuche a la propia página.
 */
(function (raiz, fabrica) {
  var modulo = fabrica();
  if (typeof module === "object" && module.exports) module.exports = modulo;
  else raiz.AlerceLectura = modulo;
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  var PREFERENCIA = ["es-cl", "es-419", "es-us", "es-mx", "es-es"];

  /** true si el navegador puede leer en voz alta. */
  function disponible(ventana) {
    return !!(ventana && ventana.speechSynthesis && ventana.SpeechSynthesisUtterance);
  }

  /** La mejor voz en español de la lista (Chile primero) o null si no hay ninguna en español. */
  function elegirVoz(voces) {
    var lista = (voces || []).filter(function (v) { return v && /^es([-_]|$)/i.test(v.lang || ""); });
    if (!lista.length) return null;
    var norm = function (v) { return String(v.lang || "").toLowerCase().replace("_", "-"); };
    for (var i = 0; i < PREFERENCIA.length; i++) {
      var hallada = lista.filter(function (v) { return norm(v) === PREFERENCIA[i]; })[0];
      if (hallada) return hallada;
    }
    return lista[0];
  }

  /** «$12.000» se lee «12.000 pesos»; sin comillas angulares ni íconos. */
  function paraLeer(texto) {
    return String(texto || "")
      .replace(/\$\s?(\d{1,3}(?:\.\d{3})+|\d+)/g, "$1 pesos")
      .replace(/[«»"]/g, "")
      .replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}]/gu, "")
      .replace(/\s+/g, " ")
      .trim();
  }

  /**
   * Lector para una ventana. hablar(id, texto, alCambiar) lee (cortando lo anterior);
   * alCambiar(leyendo) avisa cuando empieza y termina, para cambiar «Escuchar» por «Detener».
   */
  function crear(ventana) {
    if (!disponible(ventana)) return { disponible: false, hablar: function () { return false; }, detener: function () {}, leyendo: function () { return null; } };
    var sintesis = ventana.speechSynthesis;
    var actual = null, avisar = null;
    // Cada lectura lleva su número: el navegador avisa tarde («onend»/«onerror») la que se cortó, y
    // ese aviso no debe dar por terminada la nueva (mismo botón, mismo id).
    var turno = 0;

    function terminar(id) {
      if (actual !== id) return;
      actual = null;
      var a = avisar; avisar = null;
      if (a) a(false);
    }

    function detener() {
      turno++;
      var id = actual;
      try { sintesis.cancel(); } catch (e) { /* sin lectura */ }
      if (id !== null) terminar(id);
    }

    function hablar(id, texto, alCambiar) {
      detener();
      var limpio = paraLeer(texto);
      if (!limpio) return false;
      var voces = [];
      try { voces = sintesis.getVoices() || []; } catch (e) { voces = []; }
      var voz = elegirVoz(voces);
      // Con lista de voces cargada y ninguna en español, mejor no leer con acento de otro idioma.
      if (voces.length && !voz) return false;
      var u = new ventana.SpeechSynthesisUtterance(limpio);
      u.lang = voz ? voz.lang : "es-CL";
      if (voz) u.voice = voz;
      u.rate = 1;
      actual = id; avisar = alCambiar || null;
      var mio = ++turno;
      u.onend = function () { if (mio === turno) terminar(id); };
      u.onerror = function () { if (mio === turno) terminar(id); };
      try { sintesis.speak(u); } catch (e) { terminar(id); return false; }
      if (avisar) avisar(true);
      return true;
    }

    /** ¿Hay voz en español? null mientras el navegador aún no carga la lista. */
    function hayVozEspanol() {
      var voces = [];
      try { voces = sintesis.getVoices() || []; } catch (e) { return null; }
      if (!voces.length) return null;
      return !!elegirVoz(voces);
    }

    // Al cambiar de pestaña o minimizar el navegador, se calla (algunos navegadores seguían hablando).
    try {
      var doc = ventana.document;
      if (doc && doc.addEventListener) doc.addEventListener("visibilitychange", function () { if (doc.hidden) detener(); });
    } catch (e) { /* sin documento (pruebas) */ }

    return { disponible: true, hablar: hablar, detener: detener, leyendo: function () { return actual; }, hayVozEspanol: hayVozEspanol };
  }

  return { disponible: disponible, elegirVoz: elegirVoz, paraLeer: paraLeer, crear: crear };
});
