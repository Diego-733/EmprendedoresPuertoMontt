"use strict";
// Entrega 8 · supabase/09_administracion.sql: gestión de otras cuentas de administrador
// (RF-ADM-04, criterio W07) y bitácora de acciones del administrador (RNF-SEG-07, W08).
// Corre sobre la base local con 01–07 aplicadas y SIN la 08 (que queda fuera de esta entrega).
const { UID, como, error } = require("./ayudas");
const { ejecutarArchivo } = require("./postgres_local");

module.exports = async function pruebasAdministracion(c, r) {
  const A = UID.admin, M = UID.marta, V = UID.vecina;
  const A2 = "00000000-0000-0000-0000-0000000000a2";   // segunda administradora (se registra en la prueba)
  const R = (n) => "E8 · " + n;
  const filas = async (uid, sql, p) => (await como(c, uid, sql, p)).rows;
  const uno = async (uid, sql, p) => { const f = (await como(c, uid, sql, p)).rows[0]; return f ? Object.values(f)[0] : null; };

  r.igual(R("La 08 no está aplicada en esta base (09 no depende de ella)"),
    (await c.query("select to_regclass('public.envio_automatico') is null s")).rows[0].s, true);
  await ejecutarArchivo(c, "supabase/09_administracion.sql");
  await ejecutarArchivo(c, "supabase/09_administracion.sql");
  r.ok(R("09 se puede aplicar dos veces"), true);
  r.igual(R("Las administradoras existentes quedan en la lista"),
    (await filas(A, "select email, estado, es_yo from public.listar_admins()")).filter(x => x.email === "admin@alerce.app"),
    [{ email: "admin@alerce.app", estado: "ACTIVA", es_yo: true }]);

  // ---------- Permisos ----------
  r.falla(R("Una emprendedora no ve la lista de administradores"), await error(como(c, M, "select * from public.listar_admins()")), "SOLO_ADMIN");
  r.falla(R("Sin sesión no se crea un administrador"), await error(como(c, null, "select public.crear_admin('x@alerce.app', 'X')")), "permission denied");
  r.falla(R("Una compradora no crea administradores"), await error(como(c, V, "select public.crear_admin('x@alerce.app', 'X')")), "SOLO_ADMIN");
  r.igual(R("Una emprendedora no lee la tabla de administradores"), await uno(M, "select count(*)::int from public.administradores"), 0);
  r.ok(R("Nadie inserta en la bitácora desde un cliente"),
    !!(await error(como(c, A, "insert into public.bitacora_admin (accion, objeto_tipo) values ('falsa', 'x')"))));
  r.ok(R("Nadie borra la bitácora desde un cliente"),
    !!(await error(como(c, A, "delete from public.bitacora_admin"))));
  r.igual(R("Una emprendedora no lee la bitácora"), await uno(M, "select count(*)::int from public.bitacora_admin"), 0);
  r.falla(R("Una emprendedora no usa ver_bitacora"), await error(como(c, M, "select * from public.ver_bitacora(10)")), "SOLO_ADMIN");

  // ---------- Crear, editar, desactivar y reactivar otra administradora (W07) ----------
  r.falla(R("Correo inválido rechazado"), await error(como(c, A, "select public.crear_admin('no-es-correo', 'X')")), "CORREO_INVALIDO");
  r.falla(R("Nombre vacío rechazado"), await error(como(c, A, "select public.crear_admin('coord@alerce.app', '  ')")), "NOMBRE_INVALIDO");
  r.falla(R("Una emprendedora no se convierte en administradora"),
    await error(como(c, A, "select public.crear_admin('donamarta@alerce.app', 'Marta')")), "ES_EMPRENDEDORA");
  r.igual(R("Crear una administradora aún no registrada queda PENDIENTE_REGISTRO"),
    await uno(A, "select public.crear_admin('Coord@Alerce.app', 'Coordinación Comunal')"), "PENDIENTE_REGISTRO");
  r.falla(R("No se crea dos veces"), await error(como(c, A, "select public.crear_admin('coord@alerce.app', 'Otra')")), "YA_EXISTE");
  await c.query("insert into auth.users (id, email, raw_user_meta_data) values ($1, 'coord@alerce.app', '{}') on conflict (id) do nothing", [A2]);
  r.igual(R("Al registrarse recibe el rol ADMIN (autorización del servidor)"),
    (await c.query("select rol, nombre from perfiles where id = $1", [A2])).rows[0], { rol: "ADMIN", nombre: "Coordinación Comunal" });
  r.igual(R("Ya registrada aparece ACTIVA"),
    (await filas(A, "select estado from public.listar_admins() where email = 'coord@alerce.app'"))[0].estado, "ACTIVA");
  r.igual(R("La nueva administradora ya puede operar ferias"), await uno(A2, "select public.es_admin()"), true);

  await como(c, A, "select public.editar_admin('coord@alerce.app', 'Coordinación Alerce')");
  r.igual(R("Editar cambia el nombre en la lista y en el perfil"),
    [(await filas(A, "select nombre from public.listar_admins() where email = 'coord@alerce.app'"))[0].nombre,
     (await c.query("select nombre from perfiles where id = $1", [A2])).rows[0].nombre], ["Coordinación Alerce", "Coordinación Alerce"]);

  r.falla(R("No puede desactivarse a sí misma"),
    await error(como(c, A, "select public.cambiar_estado_admin('admin@alerce.app', false)")), "NO_PUEDES_DESACTIVARTE");
  r.igual(R("Desactivar otra administradora"), await uno(A, "select public.cambiar_estado_admin('coord@alerce.app', false)"), "DESACTIVADA");
  r.igual(R("Desactivada pierde el rol ADMIN y la autorización (persistente en el servidor)"),
    [(await c.query("select rol from perfiles where id = $1", [A2])).rows[0].rol,
     (await c.query("select count(*)::int n from cuentas_autorizadas where email = 'coord@alerce.app'")).rows[0].n,
     await uno(A2, "select public.es_admin()")], ["VISITANTE", 0, false]);
  r.falla(R("Desactivada ya no administra"), await error(como(c, A2, "select * from public.listar_admins()")), "SOLO_ADMIN");
  r.falla(R("La última administradora activa no se puede desactivar"),
    await error(como(c, A2, "select public.cambiar_estado_admin('admin@alerce.app', false)")), "SOLO_ADMIN");
  r.igual(R("Reactivar devuelve el rol"), await uno(A, "select public.cambiar_estado_admin('coord@alerce.app', true)"), "ACTIVA");
  r.igual(R("Reactivada vuelve a ser ADMIN"), await uno(A2, "select public.es_admin()"), true);
  r.igual(R("Desactivar dos veces no hace nada"), await uno(A, "select public.cambiar_estado_admin('coord@alerce.app', true)"), "SIN_CAMBIO_ACTIVA");
  // La otra administradora sí puede desactivar a la primera si queda ella activa; aquí solo se comprueba la regla de la última.
  await como(c, A, "select public.cambiar_estado_admin('coord@alerce.app', false)");
  r.falla(R("Sin otra administradora activa no se puede quedar en cero"),
    await error(como(c, A, "select public.cambiar_estado_admin('admin@alerce.app', false)")), "NO_PUEDES_DESACTIVARTE");
  await como(c, A, "select public.cambiar_estado_admin('coord@alerce.app', true)");
  r.igual(R("Una administradora desactiva a otra si queda al menos una activa"),
    await uno(A2, "select public.cambiar_estado_admin('coord@alerce.app', true)"), "SIN_CAMBIO_ACTIVA");

  // ---------- Bitácora (W08): quién, qué y cuándo ----------
  await como(c, A, `insert into ferias (id, nombre, descripcion, ubicacion, fecha_inicio, fecha_fin, horario, organizador, estado)
                    values ('e8_feria', 'Feria E8', 'Prueba', 'Costanera', public.fecha_santiago(), public.fecha_santiago(), '10 a 18', 'Muni', 'BORRADOR')`);
  await como(c, A, "update ferias set horario = '11 a 19' where id = 'e8_feria'");
  await como(c, A, "select public.publicar_feria('e8_feria')");
  await como(c, A, "select public.invitar_a_feria('e8_feria', array['t_barberia_alerce'], null, false)");
  await como(c, A, "select public.invitar_a_feria('e8_feria', array['t_minimarket_rosa'], null, true)");
  await como(c, A, "select public.registrar_asistencia('e8_feria', 't_minimarket_rosa', 'ASISTIO')");
  await como(c, A2, "update asociaciones set descripcion = 'Ficha E8' where id = 'as2'");
  await como(c, A2, "update asociaciones set activa = false where id = 'as2'");
  await como(c, A2, "update asociaciones set activa = true where id = 'as2'");
  await como(c, A, "insert into tienda_asociaciones (tienda_id, asociacion_id) values ('t_barberia_alerce', 'as2') on conflict do nothing");
  await como(c, A, "delete from tienda_asociaciones where tienda_id = 't_barberia_alerce' and asociacion_id = 'as2'");
  await como(c, A, "insert into cuentas_autorizadas (email, rol, nombre) values ('nueva.emp@alerce.app', 'EMPRENDEDORA', 'Nueva') on conflict do nothing");
  await como(c, A, "update configuracion set valor = 'INVITAR' where clave = 'navegacion_anonima'");
  await como(c, A, "select public.cancelar_feria('e8_feria', 'Lluvia')");
  // Acciones que NO son de administración no se anotan.
  await como(c, M, "select public.responder_invitacion('e8_feria', true)").catch(() => {});

  const b = await filas(A, "select quien, accion, objeto_tipo, objeto_id, en from public.ver_bitacora(200)");
  const acciones = b.map(x => x.accion);
  for (const [accion, nombre] of [
    ["Creó cuenta de administrador", "crear administradora"], ["Editó cuenta de administrador", "editar administradora"],
    ["Desactivó cuenta de administrador", "desactivar administradora"], ["Reactivó cuenta de administrador", "reactivar administradora"],
    ["Creó feria", "crear feria"], ["Editó feria", "editar feria"], ["Publicó feria", "publicar feria"],
    ["Invitó a feria", "invitar"], ["Agregó directo a feria", "agregar directo"], ["Registró asistencia", "asistencia"],
    ["Canceló feria", "cancelar feria"], ["Editó asociación", "editar asociación"], ["Desactivó asociación", "desactivar asociación"],
    ["Reactivó asociación", "reactivar asociación"], ["Asignó emprendedora a asociación", "asignar integrante"],
    ["Quitó emprendedora de asociación", "quitar integrante"], ["Autorizó cuenta emprendedora", "autorizar emprendedora"],
    ["Cambió configuración", "configuración"]]) {
    r.ok(R("Bitácora anota: " + nombre), acciones.includes(accion), acciones);
  }
  const deA2 = b.filter(x => x.quien === "coord@alerce.app").map(x => x.accion);
  r.ok(R("Bitácora guarda quién (la segunda administradora en sus acciones)"), deA2.includes("Desactivó asociación"), deA2);
  r.ok(R("Bitácora guarda cuándo (fecha del servidor, la más reciente primero)"),
    b.length > 1 && b.every(x => x.en instanceof Date) && b[0].en >= b[b.length - 1].en, b.slice(0, 2));
  r.ok(R("Bitácora guarda qué objeto"), b.some(x => x.objeto_tipo === "feria" && x.objeto_id === "e8_feria"), b.slice(0, 3));
  r.ok(R("La respuesta de la emprendedora no se anota como acción de administrador"),
    !b.some(x => x.quien === "donamarta@alerce.app"), b.map(x => x.quien));
  r.igual(R("El cambio automático de estado de una feria no se anota como edición"),
    b.filter(x => x.accion === "Editó feria").length, 1);
  const det = (await c.query("select detalle from bitacora_admin where accion = 'Editó feria' limit 1")).rows[0].detalle;
  r.igual(R("Una edición anota qué campos cambiaron, sin copiar sus valores"), det.campos, ["horario"]);

  // ---------- Revertir ----------
  await ejecutarArchivo(c, "supabase/09_revertir.sql");
  r.igual(R("Revertir quita lo de 09 (conserva la bitácora de 07) sin tocar ferias ni perfiles"),
    (await c.query(`select not exists (select 1 from information_schema.columns where table_name = 'bitacora_admin' and column_name = 'objeto_tipo') b, to_regclass('public.administradores') is null a,
                    (select count(*)::int from pg_trigger where tgname = 'bitacora_admin') t,
                    (select count(*)::int from ferias where id = 'e8_feria') f,
                    (select rol from perfiles where id = $1) rol`, [A2])).rows[0], { b: true, a: true, t: 0, f: 1, rol: "ADMIN" });
  await ejecutarArchivo(c, "supabase/09_administracion.sql");
  r.igual(R("Tras revertir se puede volver a aplicar y recupera la lista"),
    (await filas(A, "select count(*)::int n from public.listar_admins() where estado = 'ACTIVA'"))[0].n, 2);
};
