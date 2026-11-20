/* Service worker de avisos del navegador (Entrega 7, RF-CMP-08).
 * Solo se registra si js/config.js trae la configuración pública de Firebase
 * (apiKey, projectId, messagingSenderId, appId) y la persona dio permiso.
 * Muestra el aviso cuando la página está cerrada o en segundo plano. No contiene
 * credenciales de envío: el envío lo hace servicios/notificador desde un servidor. */
/* global importScripts, firebase */
importScripts("https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging-compat.js");

var config = {};
try { config = JSON.parse(new URL(self.location.href).searchParams.get("config") || "{}"); } catch (e) { config = {}; }

if (config.apiKey && config.projectId && config.messagingSenderId && config.appId) {
  firebase.initializeApp(config);
  var messaging = firebase.messaging();
  // Los mensajes con "notification" los muestra Firebase; aquí se cubren los que solo traen datos.
  messaging.onBackgroundMessage(function (payload) {
    if (payload.notification) return;
    var d = payload.data || {};
    self.registration.showNotification(d.titulo || "Aviso de Alerce", { body: d.cuerpo || "", tag: d.aviso_id || undefined, data: d });
  });
}

self.addEventListener("notificationclick", function (event) {
  event.notification.close();
  var datos = event.notification.data || {};
  var destino = (datos.FCM_MSG && datos.FCM_MSG.data && datos.FCM_MSG.data.enlace) || datos.enlace || self.registration.scope;
  event.waitUntil(clients.openWindow(destino));
});
