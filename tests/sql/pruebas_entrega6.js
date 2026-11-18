"use strict";
// Pruebas de la Entrega 6 (supabase/06_vitrina_mapas.sql) contra PostgreSQL real:
// navegación anónima, contactos por formulario, punto de retiro, asociaciones y ferias.
const { UID, como, error } = require("./ayudas");

module.exports = async function pruebasEntrega6(c, r) {
  const M = UID.marta, B = UID.barberia, A = UID.admin, V = UID.vecina;

  // ---------- Navegación anónima (RF-ADM-02 / RF-ACC-06) ----------
  const inicial = (await como(c, null, "select valor from configuracion where clave = 'navegacion_anonima'")).rows[0];
  r.igual("E6 · Sin sesión se lee la navegación anónima (valor inicial PERMITIR)", inicial && inicial.valor, "PERMITIR");
  for (const valor of ["INVITAR", "OBLIGAR", "PERMITIR"]) {
    await como(c, A, "update configuracion set valor = $1 where clave = 'navegacion_anonima'", [valor]);
    const leido = (await como(c, null, "select valor from configuracion where clave = 'navegacion_anonima'")).rows[0].valor;
    r.igual("E6 · El administrador guarda " + valor + " y queda guardado", leido, valor);
  }
  r.falla("E6 · Valor inválido de navegación rechazado",
    await error(como(c, A, "update configuracion set valor = 'CUALQUIERA' where clave = 'navegacion_anonima'")), "no válido");
  const cambiaEmp = await como(c, M, "update configuracion set valor = 'OBLIGAR' where clave = 'navegacion_anonima'");
  r.igual("E6 · Una emprendedora no cambia la navegación anónima", cambiaEmp.rowCount, 0);
  r.ok("E6 · Sin sesión no se cambia la navegación anónima",
    !!(await error(como(c, null, "update configuracion set valor = 'OBLIGAR' where clave = 'navegacion_anonima'"))) ||
    (await c.query("select valor from configuracion where clave = 'navegacion_anonima'")).rows[0].valor === "PERMITIR");

  // ---------- Configuración de la tienda (RF-CFG-01/02, RF-MAP-04) ----------
  const upd = await como(c, M, `update tiendas set mostrar_precios = false, contacto_modo = 'FORMULARIO',
      punto_lat = -41.3959, punto_lng = -72.8955, punto_nombre = 'Mi local', asociacion_id = 'as3'
      where id = 't_minimarket_rosa' returning asociacion_id`);
  r.igual("E6 · La dueña cambia precios, contacto y punto de retiro", upd.rowCount, 1);
  const t = (await como(c, null, "select mostrar_precios, contacto_modo, punto_lat, punto_nombre, asociacion_id from tiendas where id = 't_minimarket_rosa'")).rows[0];
  r.igual("E6 · El visitante ve la configuración nueva", [t.mostrar_precios, t.contacto_modo, t.punto_lat, t.punto_nombre], [false, "FORMULARIO", -41.3959, "Mi local"]);
  r.ok("E6 · La emprendedora no se asigna una asociación sola", t.asociacion_id !== "as3", t.asociacion_id);
  r.falla("E6 · Forma de contacto inválida rechazada",
    await error(como(c, M, "update tiendas set contacto_modo = 'TELEPATIA' where id = 't_minimarket_rosa'")), "contacto_modo");
  r.falla("E6 · Coordenadas imposibles rechazadas",
    await error(como(c, M, "update tiendas set punto_lat = 200, punto_lng = 0 where id = 't_minimarket_rosa'")), "tiendas_punto_ck");
  const ajena = await como(c, B, "update tiendas set mostrar_precios = true where id = 't_minimarket_rosa'");
  r.igual("E6 · Otra emprendedora no cambia la tienda ajena", ajena.rowCount, 0);

  // ---------- Asociaciones (RF-ASO-03) ----------
  await como(c, A, "update tiendas set asociacion_id = 'as1' where id in ('t_minimarket_rosa', 't_tejidos_austral')");
  const integrantes = (await como(c, null, "select id from tiendas where asociacion_id = 'as1' order by id")).rows.map(x => x.id);
  r.igual("E6 · Sin sesión se ven las integrantes de una asociación", integrantes, ["t_minimarket_rosa", "t_tejidos_austral"]);

  // ---------- Contactos (RF-CMP-05, RF-CFG-03) ----------
  await como(c, null, "insert into contactos (tienda_id, nombre, medio, mensaje) values ('t_minimarket_rosa', 'Vecina', 'vecina@correo.cl', '¿Tienen pan hoy?')");
  r.ok("E6 · Sin sesión se envía un mensaje por formulario", true);
  await como(c, V, "insert into contactos (tienda_id, nombre, mensaje) values ('t_minimarket_rosa', 'Compradora', 'Hola')");
  r.ok("E6 · Con sesión de compradora también se envía", true);
  r.ok("E6 · No se envía a una tienda que solo usa WhatsApp",
    !!(await error(como(c, A, "update tiendas set contacto_modo = 'WHATSAPP' where id = 't_barberia_alerce'").then(() =>
      como(c, null, "insert into contactos (tienda_id, nombre, mensaje) values ('t_barberia_alerce', 'X', 'Hola')")))));
  r.ok("E6 · Mensaje vacío rechazado",
    !!(await error(como(c, null, "insert into contactos (tienda_id, nombre, mensaje) values ('t_minimarket_rosa', 'X', '   ')"))));
  const anonLee = await como(c, null, "select count(*)::int n from contactos").then(x => x.rows[0].n, () => 0);
  r.igual("E6 · Sin sesión no se leen mensajes", anonLee, 0);
  const otraLee = (await como(c, B, "select count(*)::int n from contactos")).rows[0].n;
  r.igual("E6 · Otra emprendedora no lee los mensajes ajenos", otraLee, 0);
  const adminLee = (await como(c, A, "select count(*)::int n from contactos")).rows[0].n;
  r.igual("E6 · Ni el administrador lee la bandeja de una tienda", adminLee, 0);
  const bandeja = (await como(c, M, "select nombre, leido from contactos order by creado_en")).rows;
  r.igual("E6 · La dueña ve sus mensajes en la bandeja", bandeja.length, 2);
  await como(c, M, "update contactos set leido = true, mensaje = 'reescrito' where tienda_id = 't_minimarket_rosa'");
  const marcados = (await como(c, M, "select leido, mensaje from contactos where nombre = 'Vecina'")).rows[0];
  r.igual("E6 · La dueña marca leído sin reescribir el mensaje", marcados, { leido: true, mensaje: "¿Tienen pan hoy?" });

  // ---------- Ferias con ubicación (RF-FER-01, RF-MAP-01/02) ----------
  await como(c, A, `insert into ferias (id, nombre, descripcion, imagen_url, ubicacion, fecha_inicio, fecha_fin, horario, organizador, estado, latitud, longitud)
    values ('e6_feria', 'Feria E6', 'Descripción', 'https://ejemplo.invalid/f.jpg', 'Plaza', current_date + 3, current_date + 3, '10 a 18', 'Muni', 'PROXIMA', -41.47, -72.94)`);
  await como(c, A, "update ferias set nombre = 'Feria E6 editada', latitud = -41.48 where id = 'e6_feria'");
  const f = (await como(c, null, "select nombre, organizador, latitud, longitud from ferias where id = 'e6_feria'")).rows[0];
  r.igual("E6 · La feria creada y editada se ve sin sesión con su ubicación", f, { nombre: "Feria E6 editada", organizador: "Muni", latitud: -41.48, longitud: -72.94 });
  r.ok("E6 · Una emprendedora no crea ferias",
    !!(await error(como(c, M, "insert into ferias (id, nombre) values ('e6_x', 'X')"))));
  r.falla("E6 · Ubicación de feria imposible rechazada",
    await error(como(c, A, "update ferias set latitud = 120 where id = 'e6_feria'")), "ferias_coordenadas_ck");

  // ---------- RNF-PRI-04: no hay columnas para la ubicación de la persona ----------
  const cols = (await c.query(`select table_name || '.' || column_name n from information_schema.columns
     where table_schema = 'public' and table_name in ('perfiles', 'contactos') and column_name ~ '(lat|lng|lon|ubicacion)'`)).rows;
  r.igual("E6 · La ubicación de la persona no se guarda en el servidor", cols, []);
};
