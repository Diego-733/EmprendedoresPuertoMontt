// Datos FICTICIOS para recorrer la web en local (npm run demo). Se cargan en el navegador antes del
// Supabase sustituto (supabase_falso.js). Nada sale del computador: no hay credenciales ni servidor real.
// Rol de la visita: ?demo=visitante | emprendedora | admin (se recuerda en sessionStorage).
(function () {
  "use strict";
  var ORIGEN = location.origin;
  var foto = function (n) { return ORIGEN + "/__demo/fotos/" + n + ".svg"; };
  var hoy = function (dias) { var d = new Date(Date.now() + dias * 86400000); return d.toISOString().slice(0, 10); };

  var q = new URLSearchParams(location.search);
  var rol = q.get("demo") || sessionStorage.getItem("demo_rol") || "visitante";
  sessionStorage.setItem("demo_rol", rol);

  var BASE = { owner_id: null, necesidades: { tieneStock: true, tieneVentaPorWhatsApp: true, participaEnFerias: true }, historial_activo: true,
    valores_producto: {}, mostrar_precios: true, contacto_modo: "AMBOS", activa: true };
  var tienda = function (o) { return Object.assign({}, BASE, o); };
  var tiendas = [
    tienda({ id: "t_minimarket_rosa", owner_id: "u-marta", nombre: "Amasandería Doña Marta", rubro: "Panadería", sector: "Alerce Histórico",
      bio: "Pan amasado, hallullas y empanadas de horno de barro. Pedidos el día anterior.", whatsapp: "+56 9 1111 2222",
      color_portada: 4285878570, color_logo: 4286336552, logo_url: foto("logo_marta"), punto_lat: -41.3959, punto_lng: -72.8955, punto_nombre: "Pasaje Los Ulmos 123" }),
    tienda({ id: "t_tejidos_austral", nombre: "Tejidos Austral", rubro: "Artesanía", sector: "Alerce Sur",
      bio: "Chales, gorros y calcetines de lana de oveja teñida con plantas del sur.", whatsapp: "+56 9 3333 4444",
      color_portada: 4281236827, color_logo: 4284232027, logo_url: foto("logo_tejidos") }),
    tienda({ id: "t_reposteria_surena", nombre: "Repostería y Pastelería Artesanal Sureña de la Señora Clementina Barrientos", rubro: "Repostería", sector: "Alerce Norte",
      bio: "Kuchenes de murta y frambuesa, tortas por encargo.", whatsapp: "", contacto_modo: "AMBOS",
      color_portada: 4290804286, color_logo: 4287523732, logo_url: "" }),
    tienda({ id: "t_barberia_alerce", nombre: "Barbería Alerce", rubro: "Servicios", sector: "Senda Central",
      bio: "Cortes y perfilado de barba. Atención con hora.", whatsapp: "+56 9 5555 6666", necesidades: { tieneAgendamiento: true, tieneStock: false },
      color_portada: 4280097085, color_logo: 4282285422, logo_url: foto("logo_barberia") })
  ];
  var prod = function (id, tiendaId, nombre, precio, stock, extra) {
    return Object.assign({ id: id, tienda_id: tiendaId, nombre: nombre, descripcion: "", categoria: "GOURMET", precio: precio, publicado: true, disponible: true,
      modalidad: "REGULAR", maneja_stock: true, al_agotarse: "MOSTRAR_AGOTADO", vendida_en: null, foto_url: foto(id), fotos: [],
      variantes: [{ id: id + "_u", nombre: "Única", stock: stock }], actualizado_en: "2026-11-20T12:00:00Z" }, extra || {});
  };
  var productos = [
    prod("pan_amasado", "t_minimarket_rosa", "Pan amasado (docena)", 3000, 6, { descripcion: "Recién salido del horno de barro, a partir de las 8:00." }),
    prod("empanada_horno", "t_minimarket_rosa", "Empanada de pino al horno", 2200, 0, { descripcion: "Se hacen los viernes." }),
    prod("hallulla", "t_minimarket_rosa", "Hallullas (kilo)", 2500, 12),
    prod("chal_lana", "t_tejidos_austral", "Chal de lana teñida con maqui", 18000, 3, { categoria: "TEXTIL", descripcion: "Hecho a telar. Cada uno es distinto.",
      variantes: [{ id: "chal_morado", nombre: "Morado", stock: 2 }, { id: "chal_gris", nombre: "Gris natural", stock: 1 }, { id: "chal_verde", nombre: "Verde", stock: 0 }] }),
    prod("gorro_lana", "t_tejidos_austral", "Gorro de lana", 8500, 7, { categoria: "TEXTIL" }),
    prod("calcetines", "t_tejidos_austral", "Calcetines de lana gruesa para el invierno del sur", 6000, 0, { categoria: "TEXTIL", modalidad: "A_PEDIDO", foto_url: "" }),
    prod("kuchen_murta", "t_reposteria_surena", "Kuchen de murta", 9000, 2, { descripcion: "Para 8 personas." }),
    prod("torta_encargo", "t_reposteria_surena", "Torta de cumpleaños por encargo", 25000, 0, { modalidad: "A_PEDIDO" }),
    prod("corte_pelo", "t_barberia_alerce", "Corte de pelo", 7000, 0, { categoria: "DECORACION", maneja_stock: false, foto_url: foto("corte_pelo") })
  ];

  var estado = {
    tablas: {
      tiendas: tiendas, productos: productos,
      asociaciones: [
        { id: "as1", nombre: "Manos del Sur", sector: "Alerce", localidad: "Puerto Montt", descripcion: "Red de artesanas de Alerce.", activa: true, color: 4287523732 },
        { id: "as2", nombre: "Red del Lago", sector: "Alerce Norte", localidad: "Puerto Montt", descripcion: "Emprendedoras de alimentos.", activa: true, color: 4282285422 }
      ],
      tienda_asociaciones: [{ tienda_id: "t_tejidos_austral", asociacion_id: "as1" }, { tienda_id: "t_minimarket_rosa", asociacion_id: "as2" }, { tienda_id: "t_reposteria_surena", asociacion_id: "as2" }],
      ferias: [
        { id: "f1", nombre: "Feria Navideña de Alerce", descripcion: "Productos locales para regalar.", imagen_url: "", ubicacion: "Plaza de Alerce", fecha_inicio: hoy(4), fecha_fin: hoy(5),
          horario: "10:00 a 19:00", organizador: "Municipalidad de Puerto Montt", estado: "PROXIMA", latitud: -41.3959, longitud: -72.8955 },
        { id: "f2", nombre: "Mercado de Verano Costanera", descripcion: "", imagen_url: "", ubicacion: "Costanera de Puerto Montt", fecha_inicio: hoy(24), fecha_fin: hoy(24),
          horario: "11:00 a 20:00", organizador: "Red del Lago", estado: "PROXIMA", latitud: -41.4717, longitud: -72.9396 }
      ],
      tipos_producto: [
        { id: "pan", categoria: "GOURMET", nombre: "Pan y masas", activo: true, orden: 1 },
        { id: "tejido", categoria: "TEXTIL", nombre: "Tejido a mano", activo: true, orden: 2 },
        { id: "torta", categoria: "GOURMET", nombre: "Tortas y kuchenes", activo: true, orden: 3 }
      ],
      configuracion: [{ clave: "navegacion_anonima", valor: "PERMITIR" }],
      perfiles: [
        { id: "u-admin", email: "admin@alerce.app", nombre: "Carolina (Municipalidad)", rol: "ADMIN", tienda_id: null },
        { id: "u-marta", email: "donamarta@alerce.app", nombre: "Marta Oyarzún", rol: "EMPRENDEDORA", tienda_id: "t_minimarket_rosa" }
      ],
      cuentas_autorizadas: [], ventas: [], contactos: [], seguimientos: [], preferencias_avisos: [], avisos: []
    },
    respuestasRpc: {}
  };
  if (rol === "admin") estado.sesion = { user: { id: "u-admin", email: "admin@alerce.app", user_metadata: {} }, access_token: "demo" };
  if (rol === "emprendedora") estado.sesion = { user: { id: "u-marta", email: "donamarta@alerce.app", user_metadata: {} }, access_token: "demo" };
  window.__FALSO__ = estado;

  // Respuestas de funciones del servidor (en memoria) para que la demo se pueda recorrer sin límite.
  var admins = [
    { email: "admin@alerce.app", nombre: "Carolina (Municipalidad)", estado: "ACTIVA", creado_en: "2026-11-01T12:00:00Z", es_yo: true },
    { email: "coord@alerce.app", nombre: "Coordinación Fomento Productivo", estado: "ACTIVA", creado_en: "2026-11-20T12:00:00Z", es_yo: false }
  ];
  var emprendedoras = [
    { usuario_id: "u-marta", email: "donamarta@alerce.app", nombre: "Marta Oyarzún", tienda_id: "t_minimarket_rosa", tienda_nombre: "Amasandería Doña Marta", estado: "ACTIVA", creado_en: "2026-11-01T12:00:00Z" },
    { usuario_id: "u-tej", email: "tejidos@alerce.app", nombre: "Ana Cárcamo", tienda_id: "t_tejidos_austral", tienda_nombre: "Tejidos Austral", estado: "ACTIVA", creado_en: "2026-11-02T12:00:00Z" },
    { usuario_id: null, email: "nueva@alerce.app", nombre: "Rosa Vargas", tienda_id: null, tienda_nombre: null, estado: "PENDIENTE_REGISTRO", creado_en: "2026-11-21T12:00:00Z" }
  ];
  var bitacora = [
    { en: "2026-11-24T15:30:00Z", quien: "admin@alerce.app", accion: "Publicó feria", objeto_tipo: "feria", objeto_id: "f1", detalle: { nombre: "Feria Navideña de Alerce" } },
    { en: "2026-11-23T10:05:00Z", quien: "coord@alerce.app", accion: "Editó asociación", objeto_tipo: "asociación", objeto_id: "as1", detalle: { nombre: "Manos del Sur", campos: ["descripcion"] } }
  ];
  var misFerias = [
    { feria_id: "f1", nombre: "Feria Navideña de Alerce", ubicacion: "Plaza de Alerce", fecha_inicio: hoy(4), fecha_fin: hoy(5), horario: "10:00 a 19:00", estado: "PROXIMA", invitacion: "PENDIENTE", asistencia: "PENDIENTE", origen: "DIRECTA" }
  ];
  var ok = function (data) { return { data: data, error: null }; };
  var MANEJADORES = {
    listar_admins: function () { return ok(admins.slice()); },
    crear_admin: function (a) { admins.push({ email: a.p_email, nombre: a.p_nombre, estado: "PENDIENTE_REGISTRO", creado_en: new Date().toISOString(), es_yo: false }); return ok(true); },
    editar_admin: function (a) { admins.forEach(function (x) { if (x.email === a.p_email) x.nombre = a.p_nombre; }); return ok(true); },
    cambiar_estado_admin: function (a) { admins.forEach(function (x) { if (x.email === a.p_email) x.estado = a.p_activa === false ? "DESACTIVADA" : "ACTIVA"; }); return ok(true); },
    listar_emprendedoras: function () { return ok(emprendedoras.slice()); },
    // Registro controlado (11), datos FICTICIOS de la demo. El código de la demo es siempre ABCD-2345.
    listar_cuentas_emprendedoras: function () {
      return ok(emprendedoras.map(function (x) {
        var pendiente = x.estado === "PENDIENTE_REGISTRO" || x.estado === "PENDIENTE_ACTIVACION";
        return Object.assign({ autorizada: x.email !== "tejidos@alerce.app", codigo_vigente: pendiente, codigo_expira: pendiente ? "2026-12-05T12:00:00Z" : null, intentos: 0, asociaciones: [] }, x);
      }));
    },
    autorizar_emprendedora: function (a) {
      emprendedoras.unshift({ usuario_id: null, email: a.p_email, nombre: a.p_nombre, tienda_id: null, tienda_nombre: null, estado: "PENDIENTE_REGISTRO", creado_en: new Date().toISOString() });
      return ok({ email: a.p_email, codigo: "ABCD-2345", expira: "2026-12-10T12:00:00Z", estado: "PENDIENTE_REGISTRO" });
    },
    regenerar_codigo_emprendedora: function (a) { return ok({ email: a.p_email, codigo: "WXYZ-6789", expira: "2026-12-10T12:00:00Z" }); },
    revocar_autorizacion: function (a) { emprendedoras = emprendedoras.filter(function (x) { return x.email !== a.p_email; }); return ok("REVOCADA"); },
    regularizar_emprendedora: function () { return ok("REGULARIZADA"); },
    estado_mi_cuenta: function () {
      var s = window.__FALSO__.sesion, uid = s && s.user && s.user.id;
      var p = estado.tablas.perfiles.find(function (x) { return x.id === uid; });
      if (!p) return ok({ rol: null, estado: "SIN_SESION" });
      if (p.rol === "EMPRENDEDORA") return ok({ rol: "EMPRENDEDORA", estado: "ACTIVA" });
      if (p.rol === "ADMIN") return ok({ rol: "ADMIN", estado: "ADMIN" });
      return ok({ rol: "VISITANTE", estado: "AUTORIZACION_PENDIENTE", codigo_vigente: true });
    },
    activar_con_codigo: function (a) { return ok(String(a.p_codigo).replace(/[^A-Za-z0-9]/g, "").toUpperCase() === "ABCD2345" ? "ACTIVADA" : "CODIGO_INCORRECTO:4"); },
    editar_emprendedora: function () { return ok(true); },
    cambiar_estado_emprendedora: function (a) { emprendedoras.forEach(function (x) { if (x.email === a.p_email || x.usuario_id === a.p_usuario) x.estado = a.p_activa === false ? "DESACTIVADA" : "ACTIVA"; }); return ok(true); },
    ver_bitacora: function () { return ok(bitacora.slice()); },
    uso_tipos_producto: function () { return ok([{ tipo_id: "pan", productos: 3 }, { tipo_id: "tejido", productos: 3 }]); },
    guardar_tipo_producto: function (a) { return ok(a.p_id || "nuevo_tipo"); },
    eliminar_tipo_producto: function () { return ok(true); },
    mis_ferias: function () { return ok(misFerias.slice()); },
    responder_invitacion: function (a) { misFerias.forEach(function (f) { if (f.feria_id === a.p_feria) f.invitacion = a.p_participa === false ? "RECHAZADA" : "CONFIRMADA"; }); return ok({ feria_id: a.p_feria, cambio: true }); },
    participantes_feria: function () { return ok([{ tienda_id: "t_tejidos_austral", nombre: "Tejidos Austral", rubro: "Artesanía", sector: "Alerce Sur", logo_url: foto("logo_tejidos"), asociacion_id: "as1" }]); },
    avisos_para_mostrar: function () { return ok([]); },
    checklist_feria: function () { return ok([]); },
    guardar_producto: function (a) { return ok(a && a.p_producto ? a.p_producto : null); }
  };
  // Se envuelve el cliente del sustituto cuando se crea (supabase_falso.js se carga después de este archivo).
  var definir = function (sb) {
    var crear = sb.createClient;
    sb.createClient = function () {
      var c = crear.apply(sb, arguments);
      var rpc = c.rpc;
      c.rpc = function (nombre, args) {
        if (MANEJADORES[nombre]) {
          window.__FALSO__.llamadas.push({ rpc: nombre, args: JSON.parse(JSON.stringify(args || {})) });
          var r = MANEJADORES[nombre](args || {});
          return new Promise(function (ok) { setTimeout(function () { ok(r); }, 60); });
        }
        return rpc.call(c, nombre, args);
      };
      var signIn = c.auth.signInWithPassword;
      c.auth.signInWithPassword = function (cred) {
        var p = estado.tablas.perfiles.find(function (x) { return x.email === String(cred.email || "").toLowerCase(); });
        if (p) window.__FALSO__.usuarioLogin = { id: p.id, email: p.email, user_metadata: {} };
        return signIn.call(c.auth, cred);
      };
      return c;
    };
    return sb;
  };
  var actual;
  Object.defineProperty(window, "supabase", { configurable: true, get: function () { return actual; }, set: function (v) { actual = definir(v); } });
})();
