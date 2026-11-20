"use strict";
// Pruebas de la Entrega 7 (supabase/07_ferias_seguidores.sql) contra PostgreSQL real:
// ferias, invitaciones, respuestas, checklist, asistencia, seguidores, consentimiento,
// dispositivos, cola de avisos, horario de contactos, calificaciones y varias asociaciones.
// Perfiles: administrador, dos emprendedoras (Marta y Barbería), dos compradoras, visitante sin sesión
// y la cuenta del servicio de envío.
const { UID, como, error } = require("./ayudas");

const C2 = "00000000-0000-0000-0000-00000000000e";   // segunda compradora
const N = "00000000-0000-0000-0000-00000000000f";    // servicio de envío (notificador)

async function rpc(c, uid, sql, params = []) {
  const r = await como(c, uid, sql, params);
  return r.rows[0] ? Object.values(r.rows[0])[0] : null;
}

module.exports = async function pruebasEntrega7(c, r) {
  const A = UID.admin, M = UID.marta, B = UID.barberia, V = UID.vecina;
  await c.query(`insert into auth.users (id, email, raw_user_meta_data) values
    ($1, 'compradora2@correo.cl', '{}'), ($2, 'notificador@alerce.app', '{}') on conflict (id) do nothing`, [C2, N]);

  // ---------- Fechas y horario en America/Santiago (con cambios de hora) ----------
  const fechas = (await c.query(`select
      public.fecha_santiago('2026-11-20 02:59:59+00')::text a, public.fecha_santiago('2026-11-20 03:00:00+00')::text b,
      public.fecha_santiago('2026-09-06 03:59:59+00')::text c, public.fecha_santiago('2026-09-06 04:00:00+00')::text d,
      public.hora_santiago('2026-09-06 04:00:00+00')::text e,
      public.fecha_santiago('2026-04-05 03:30:00+00')::text f, public.fecha_santiago('2026-04-05 04:00:00+00')::text g`)).rows[0];
  r.igual("E7 · Límite de día en Santiago (UTC-3)", [fechas.a, fechas.b], ["2026-11-19", "2026-11-20"]);
  r.igual("E7 · Inicio del horario de verano (sep): el día cambia a la hora correcta", [fechas.c, fechas.d, fechas.e], ["2026-09-05", "2026-09-06", "01:00:00"]);
  r.igual("E7 · Fin del horario de verano (abr): la hora repetida sigue en el día anterior", [fechas.f, fechas.g], ["2026-04-04", "2026-04-05"]);
  const horario = (await c.query(`select
      public.dentro_de_horario('10:00', '09:00', '20:00') a, public.dentro_de_horario('20:00', '09:00', '20:00') b,
      public.dentro_de_horario('23:30', '22:00', '07:00') c, public.dentro_de_horario('06:59', '22:00', '07:00') d,
      public.dentro_de_horario('12:00', '22:00', '07:00') e, public.dentro_de_horario('03:00', '08:00', '08:00') f`)).rows[0];
  r.igual("E7 · Horario de avisos: normal, cruzando medianoche y todo el día", Object.values(horario), [true, false, true, true, false, true]);

  // ---------- Ferias: borrador oculto, publicar y cancelar (W01, W02) ----------
  await como(c, A, `insert into ferias (id, nombre, descripcion, ubicacion, fecha_inicio, fecha_fin, horario, organizador, estado, latitud, longitud)
    values ('e7_hoy', 'Feria E7 de hoy', 'Prueba', 'Plaza', public.fecha_santiago(), public.fecha_santiago() + 1, '10:00 a 18:00', 'Muni', 'BORRADOR', -41.39, -72.89),
           ('e7_futura', 'Feria E7 futura', 'Prueba', 'Costanera', public.fecha_santiago() + 10, public.fecha_santiago() + 10, '11 a 19', 'Muni', 'BORRADOR', -41.47, -72.94)`);
  r.igual("E7 · Una feria en borrador no la ve el visitante",
    (await como(c, null, "select count(*)::int n from ferias where id like 'e7\\_%'")).rows[0].n, 0);
  r.falla("E7 · Una emprendedora no publica ferias",
    await error(como(c, M, "select public.publicar_feria('e7_hoy')")), "SOLO_ADMIN");
  r.falla("E7 · Sin sesión no se publica",
    await error(como(c, null, "select public.publicar_feria('e7_hoy')")), "permission denied");
  await como(c, A, "select public.publicar_feria('e7_hoy'), public.publicar_feria('e7_futura')");
  const visible = (await como(c, null, "select estado, publicada_en is not null pub from ferias where id = 'e7_hoy'")).rows[0];
  r.igual("E7 · Tras publicar, el visitante la ve (W01)", visible, { estado: "PROXIMA", pub: true });
  r.falla("E7 · Estado de feria inválido rechazado",
    await error(como(c, A, "update ferias set estado = 'QUIZAS' where id = 'e7_futura'")), "ferias_estado_ck");

  // ---------- Varias asociaciones por tienda (RN-05) ----------
  await como(c, A, "insert into tienda_asociaciones (tienda_id, asociacion_id) values ('t_minimarket_rosa', 'as1'), ('t_minimarket_rosa', 'as2'), ('t_tejidos_austral', 'as1') on conflict do nothing");
  const socias = (await como(c, null, "select asociacion_id from tienda_asociaciones where tienda_id = 't_minimarket_rosa' order by 1")).rows.map(x => x.asociacion_id);
  r.igual("E7 · Una tienda pertenece a varias asociaciones y el visitante lo ve", socias, ["as1", "as2"]);
  r.ok("E7 · Una emprendedora no se agrega a una asociación",
    !!(await error(como(c, M, "insert into tienda_asociaciones (tienda_id, asociacion_id) values ('t_minimarket_rosa', 'as3')"))));
  await como(c, A, "update tiendas set asociacion_id = 'as1' where id = 't_minimarket_rosa'");
  await como(c, A, "delete from tienda_asociaciones where tienda_id = 't_minimarket_rosa' and asociacion_id = 'as1'");
  r.igual("E7 · Al quitar la asociación principal, la principal pasa a otra de sus asociaciones",
    (await c.query("select asociacion_id from tiendas where id = 't_minimarket_rosa'")).rows[0].asociacion_id, "as2");
  await como(c, A, "update tiendas set asociacion_id = 'as3' where id = 't_minimarket_rosa'");
  const trasLegado = (await c.query("select array_agg(asociacion_id order by asociacion_id) a from tienda_asociaciones where tienda_id = 't_minimarket_rosa'")).rows[0].a;
  r.igual("E7 · Un cliente antiguo que cambia asociacion_id reemplaza esa pertenencia", trasLegado, ["as3"]);
  await como(c, A, "insert into tienda_asociaciones (tienda_id, asociacion_id) values ('t_minimarket_rosa', 'as1') on conflict do nothing");

  // ---------- Seguir antes de las confirmaciones (W15, A09, A10) ----------
  r.falla("E7 · Sin sesión no se puede seguir (se pide iniciar sesión)",
    await error(como(c, null, "select public.seguir('TIENDA', 't_minimarket_rosa')")), "permission denied");
  await como(c, V, "select public.seguir('TIENDA', 't_minimarket_rosa')");
  await como(c, V, "select public.seguir('TIENDA', 't_minimarket_rosa')");
  await como(c, V, "select public.guardar_consentimiento(true)");
  r.igual("E7 · Seguir dos veces no duplica",
    (await c.query("select count(*)::int n from seguimientos where usuario_id = $1", [V])).rows[0].n, 1);
  await como(c, C2, "select public.seguir('TIENDA', 't_minimarket_rosa'), public.seguir('ASOCIACION', 'as1')");
  r.falla("E7 · No se sigue algo que no existe",
    await error(como(c, C2, "select public.seguir('TIENDA', 'no_existe')")), "NO_EXISTE");
  r.igual("E7 · Cada persona ve solo sus seguimientos",
    (await como(c, V, "select count(*)::int n from seguimientos")).rows[0].n, 1);
  r.ok("E7 · Nadie inserta seguimientos directamente (solo con seguir())",
    !!(await error(como(c, V, `insert into seguimientos (usuario_id, tipo, objetivo_id) values ('${C2}', 'TIENDA', 't_barberia_alerce')`))));

  // ---------- Invitar e incorporar (W03, W04, W05) ----------
  r.falla("E7 · Una emprendedora no invita a nadie",
    await error(como(c, M, "select public.invitar_a_feria('e7_hoy', array['t_barberia_alerce'], null, false)")), "SOLO_ADMIN");
  const inv = await rpc(c, A, "select public.invitar_a_feria('e7_hoy', array['t_minimarket_rosa', 't_barberia_alerce'], array['as1'], false)");
  r.igual("E7 · Invitar tiendas y una asociación crea una fila por tienda, sin duplicar a Marta", inv, { nuevas: 3, cambiadas: 0, sin_cambio: 1 });
  const filas = (await como(c, A, "select tienda_id, invitacion, origen, asociacion_id from participaciones where feria_id = 'e7_hoy' order by tienda_id")).rows;
  r.igual("E7 · Invitada directa conserva su origen; la integrante de la asociación queda con origen ASOCIACION", filas, [
    { tienda_id: "t_barberia_alerce", invitacion: "PENDIENTE", origen: "DIRECTA", asociacion_id: null },
    { tienda_id: "t_minimarket_rosa", invitacion: "PENDIENTE", origen: "DIRECTA", asociacion_id: null },
    { tienda_id: "t_tejidos_austral", invitacion: "PENDIENTE", origen: "ASOCIACION", asociacion_id: "as1" }]);
  const repetir = await rpc(c, A, "select public.invitar_a_feria('e7_hoy', array['t_minimarket_rosa'], array['as1'], false)");
  r.igual("E7 · Volver a invitar no duplica ni cambia nada", repetir, { nuevas: 0, cambiadas: 0, sin_cambio: 3 });
  r.falla("E7 · No se invita a una feria en borrador o pasada",
    await error(como(c, A, "insert into ferias (id, nombre, fecha_inicio, estado) values ('e7_borr', 'B', current_date + 5, 'BORRADOR'); select public.invitar_a_feria('e7_borr', array['t_barberia_alerce'], null, false)")), "FERIA_NO_ACTIVA");
  const avisoInv = (await como(c, M, "select tipo, titulo, feria_id, estado from avisos where tipo = 'INVITACION'")).rows;
  r.igual("E7 · La invitación crea un aviso para la emprendedora (A01)", avisoInv.map(a => [a.tipo, a.feria_id, a.estado]), [["INVITACION", "e7_hoy", "PENDIENTE"]]);
  r.ok("E7 · El aviso nombra la feria", avisoInv[0] && avisoInv[0].titulo.includes("Feria E7 de hoy"), avisoInv);
  r.igual("E7 · Otra emprendedora no ve los avisos ajenos",
    (await como(c, B, "select count(*)::int n from avisos where tienda_id = 't_minimarket_rosa'")).rows[0].n, 0);

  // ---------- Lo público no muestra pendientes ni rechazadas (W12) ----------
  r.igual("E7 · El visitante no lee la tabla de participaciones",
    await como(c, null, "select count(*)::int n from participaciones").then(x => x.rows[0].n, () => 0), 0);
  r.igual("E7 · Con invitaciones pendientes la ficha pública no lista a nadie",
    (await como(c, null, "select count(*)::int n from public.participantes_feria('e7_hoy')")).rows[0].n, 0);

  // ---------- Responder (A02, A03, W06, W07) ----------
  const resp = await rpc(c, M, "select public.responder_invitacion('e7_hoy', true)");
  r.igual("E7 · «Participo» con un toque deja la invitación confirmada", resp, { feria_id: "e7_hoy", invitacion: "CONFIRMADA", cambio: true });
  r.igual("E7 · Repetir «Participo» no cambia nada (reintento seguro)",
    await rpc(c, M, "select public.responder_invitacion('e7_hoy', true)"), { feria_id: "e7_hoy", invitacion: "CONFIRMADA", cambio: false });
  r.igual("E7 · El checklist del administrador muestra «Confirmada» (W07)",
    (await como(c, A, "select invitacion from public.checklist_feria('e7_hoy') where tienda_id = 't_minimarket_rosa'")).rows[0].invitacion, "CONFIRMADA");
  await como(c, B, "select public.responder_invitacion('e7_hoy', false)");
  r.igual("E7 · «No participo» deja la invitación rechazada (A03)",
    (await como(c, A, "select invitacion from participaciones where feria_id = 'e7_hoy' and tienda_id = 't_barberia_alerce'")).rows[0].invitacion, "RECHAZADA");
  const directo = await como(c, M, "update participaciones set asistencia = 'ASISTIO', invitacion = 'AGREGADA' where feria_id = 'e7_hoy'");
  r.igual("E7 · La emprendedora no modifica la fila directamente (ni asistencia ni estados reservados)", directo.rowCount, 0);
  r.falla("E7 · Una compradora no responde invitaciones",
    await error(como(c, V, "select public.responder_invitacion('e7_hoy', true)")), "SIN_TIENDA");
  r.falla("E7 · Sin invitación no se puede responder",
    await error(como(c, M, "select public.responder_invitacion('e7_futura', true)")), "SIN_INVITACION");
  const publicos = (await como(c, null, "select tienda_id from public.participantes_feria('e7_hoy')")).rows.map(x => x.tienda_id);
  r.igual("E7 · La ficha pública muestra confirmadas y excluye rechazadas y pendientes (W12)", publicos, ["t_minimarket_rosa"]);

  // ---------- Aviso a seguidores solo al confirmar, con consentimiento (W18, A13, A14) ----------
  const avisosV = (await como(c, V, "select tipo, titulo, feria_id, estado from avisos where tipo = 'PARTICIPACION_SEGUIDA'")).rows;
  r.igual("E7 · La compradora que sigue y consintió recibe el aviso con el nombre de la feria", avisosV.length, 1);
  r.ok("E7 · El aviso identifica la feria", avisosV[0] && avisosV[0].titulo.includes("Feria E7 de hoy") && avisosV[0].feria_id === "e7_hoy", avisosV);
  r.igual("E7 · Sin consentimiento no se crea el aviso",
    (await como(c, C2, "select count(*)::int n from avisos")).rows[0].n, 0);
  await como(c, M, "select public.responder_invitacion('e7_hoy', false)");
  await como(c, M, "select public.responder_invitacion('e7_hoy', true)");
  r.igual("E7 · Cambiar de respuesta y volver a confirmar no repite el aviso",
    (await como(c, V, "select count(*)::int n from avisos where tipo = 'PARTICIPACION_SEGUIDA'")).rows[0].n, 1);
  await como(c, A, "update ferias set descripcion = 'Editada' where id = 'e7_hoy'");
  r.igual("E7 · Editar la feria no avisa a seguidores",
    (await como(c, V, "select count(*)::int n from avisos")).rows[0].n, 1);

  // ---------- Agregar directamente (W05, W06) ----------
  await como(c, C2, "select public.guardar_consentimiento(true)");
  const agr = await rpc(c, A, "select public.invitar_a_feria('e7_hoy', array['t_barberia_alerce'], array['as1'], true)");
  r.igual("E7 · Agregar directamente: la rechazada elegida pasa a agregada; la pendiente de la asociación también", agr, { nuevas: 0, cambiadas: 2, sin_cambio: 1 });
  const estados = (await como(c, A, "select tienda_id, invitacion from public.checklist_feria('e7_hoy') order by tienda_id")).rows.map(x => x.invitacion);
  r.igual("E7 · El checklist distingue agregada, confirmada y agregada vía asociación", estados, ["AGREGADA", "CONFIRMADA", "AGREGADA"]);
  r.falla("E7 · Quien fue agregada por administración no cambia ese estado",
    await error(como(c, B, "select public.responder_invitacion('e7_hoy', false)")), "AGREGADA_POR_ADMINISTRACION");
  const c2 = (await como(c, C2, "select titulo from avisos where tipo = 'PARTICIPACION_SEGUIDA' order by titulo")).rows.map(x => x.titulo);
  r.ok("E7 · La seguidora de la asociación recibe un aviso cuando una integrante invitada por ella participa", c2.some(t => t.startsWith("Manos del Sur") || t.includes("estará en")), c2);
  r.igual("E7 · Un aviso por feria y por lo que sigue (no se repite)", c2.length, 1);

  // ---------- Recordatorios y avisos (W09, W10) ----------
  await como(c, A, "select public.invitar_a_feria('e7_futura', array['t_minimarket_rosa', 't_barberia_alerce'], null, false)");
  await como(c, M, "select public.responder_invitacion('e7_futura', true)");
  const sol = "11111111-1111-1111-1111-111111111111";
  const rec = await rpc(c, A, "select public.enviar_aviso_feria('e7_futura', 'RECORDATORIO', '', $1)", [sol]);
  r.igual("E7 · El recordatorio llega solo a quienes no respondieron", rec, { destinatarias: 1, repetida: false });
  const rec2 = await rpc(c, A, "select public.enviar_aviso_feria('e7_futura', 'RECORDATORIO', '', $1)", [sol]);
  r.igual("E7 · Repetir la misma solicitud (doble clic) no envía de nuevo", rec2, { destinatarias: 1, repetida: true });
  r.igual("E7 · La confirmada no recibe el recordatorio",
    (await como(c, M, "select count(*)::int n from avisos where tipo = 'RECORDATORIO'")).rows[0].n, 0);
  const avi = await rpc(c, A, "select public.enviar_aviso_feria('e7_hoy', 'AVISO_FERIA', 'Lleguen a las 9:00', $1)", ["22222222-2222-2222-2222-222222222222"]);
  r.igual("E7 · El aviso llega a confirmadas y agregadas", avi.destinatarias, 2);
  r.falla("E7 · Una emprendedora no envía avisos",
    await error(como(c, M, "select public.enviar_aviso_feria('e7_hoy', 'AVISO_FERIA', 'x', gen_random_uuid())")), "SOLO_ADMIN");
  r.igual("E7 · Queda registro de quién envió (bitácora)",
    (await como(c, A, "select count(*)::int n from bitacora_admin where accion in ('RECORDATORIO', 'AVISO_FERIA') and usuario_id = $1", [A])).rows[0].n, 2);

  // ---------- Asistencia (W11) ----------
  const asis = await rpc(c, A, "select public.registrar_asistencia('e7_hoy', 't_minimarket_rosa', 'ASISTIO')");
  r.igual("E7 · El día de la feria se registra la asistencia", asis.asistencia, "ASISTIO");
  r.igual("E7 · La asistencia no cambia la confirmación",
    (await como(c, A, "select invitacion, asistencia from participaciones where feria_id = 'e7_hoy' and tienda_id = 't_minimarket_rosa'")).rows[0], { invitacion: "CONFIRMADA", asistencia: "ASISTIO" });
  r.falla("E7 · No se registra asistencia antes del día",
    await error(como(c, A, "select public.registrar_asistencia('e7_futura', 't_minimarket_rosa', 'ASISTIO')")), "AUN_NO_ES_EL_DIA");
  await como(c, A, "select public.invitar_a_feria('e7_hoy', array['t_reposteria_surena'], null, false)");
  r.falla("E7 · Solo se marca asistencia a quien participa",
    await error(como(c, A, "select public.registrar_asistencia('e7_hoy', 't_reposteria_surena', 'ASISTIO')")), "NO_PARTICIPA");
  r.falla("E7 · La emprendedora no registra su asistencia",
    await error(como(c, M, "select public.registrar_asistencia('e7_hoy', 't_minimarket_rosa', 'ASISTIO')")), "SOLO_ADMIN");

  // ---------- Dejar de seguir y retirar el consentimiento (W17, A11, A14) ----------
  await como(c, V, "select public.dejar_de_seguir('TIENDA', 't_minimarket_rosa')");
  await como(c, V, "select public.dejar_de_seguir('TIENDA', 't_minimarket_rosa')");
  r.igual("E7 · Dejar de seguir (también repetido) funciona",
    (await como(c, V, "select count(*)::int n from seguimientos")).rows[0].n, 0);
  await como(c, A, `insert into ferias (id, nombre, fecha_inicio, estado) values ('e7_otra', 'Otra feria', public.fecha_santiago() + 3, 'PROXIMA')`);
  await como(c, A, "select public.invitar_a_feria('e7_otra', array['t_minimarket_rosa'], null, false)");
  await como(c, M, "select public.responder_invitacion('e7_otra', true)");
  r.igual("E7 · Tras dejar de seguir no llegan avisos nuevos",
    (await como(c, V, "select count(*)::int n from avisos where feria_id = 'e7_otra'")).rows[0].n, 0);
  const c2Otra = (await como(c, C2, "select estado from avisos where feria_id = 'e7_otra'")).rows;
  r.igual("E7 · Quien sigue y consiente sí lo recibe", c2Otra.map(x => x.estado), ["PENDIENTE"]);
  await como(c, C2, "select public.guardar_consentimiento(false)");
  r.igual("E7 · Al retirar el consentimiento, lo pendiente ya no se envía",
    (await como(c, C2, "select estado || ':' || motivo e from avisos where feria_id = 'e7_otra'")).rows[0].e, "OMITIDO:CONSENTIMIENTO_RETIRADO");
  r.igual("E7 · Cada persona ve solo su consentimiento",
    (await como(c, V, "select count(*)::int n from preferencias_avisos")).rows[0].n, 1);

  // ---------- Dispositivos (separados por cuenta) ----------
  const token = "token-de-prueba-" + "x".repeat(40);
  await como(c, V, "select public.registrar_dispositivo($1, 'ANDROID')", [token]);
  await como(c, V, "select public.registrar_dispositivo($1, 'ANDROID')", [token]);
  r.igual("E7 · Registrar el mismo token dos veces no duplica",
    (await c.query("select count(*)::int n from dispositivos where token = $1", [token])).rows[0].n, 1);
  await como(c, C2, "select public.registrar_dispositivo($1, 'ANDROID')", [token]);
  r.igual("E7 · Si otra cuenta entra en el mismo celular, el token pasa a esa cuenta",
    [(await como(c, V, "select count(*)::int n from dispositivos")).rows[0].n, (await como(c, C2, "select count(*)::int n from dispositivos")).rows[0].n], [0, 1]);
  await como(c, V, "select public.quitar_dispositivo($1)", [token]);
  r.igual("E7 · Una cuenta no quita el dispositivo de otra",
    (await c.query("select usuario_id::text u from dispositivos where token = $1", [token])).rows[0].u, C2);
  r.falla("E7 · Sin sesión no se registran dispositivos",
    await error(como(c, null, "select public.registrar_dispositivo($1, 'WEB')", [token])), "permission denied");
  r.igual("E7 · El visitante no lee dispositivos, avisos ni seguimientos",
    await como(c, null, "select (select count(*) from dispositivos) + (select count(*) from avisos) + (select count(*) from seguimientos) n").then(x => Number(x.rows[0].n), () => 0), 0);

  // ---------- Cola de envío: solo el servicio autorizado ----------
  r.falla("E7 · Una cuenta común no lee la cola de envío",
    await error(como(c, V, "select * from public.avisos_por_enviar(10)")), "SOLO_NOTIFICADOR");
  r.falla("E7 · Ni el administrador lee tokens de otros por la cola",
    await error(como(c, A, "select * from public.avisos_por_enviar(10)")), "SOLO_NOTIFICADOR");
  await c.query("insert into notificadores (usuario_id, nota) values ($1, 'prueba') on conflict do nothing", [N]);
  await como(c, C2, "select public.guardar_consentimiento(true)");
  await como(c, C2, "select public.registrar_dispositivo($1, 'WEB')", ["token-web-" + "y".repeat(40)]);
  // Nueva confirmación con el consentimiento otra vez activo: genera un aviso para C2.
  await como(c, A, `insert into ferias (id, nombre, fecha_inicio, estado) values ('e7_cuarta', 'Cuarta feria', public.fecha_santiago() + 4, 'PROXIMA')`);
  await como(c, A, "select public.invitar_a_feria('e7_cuarta', array['t_minimarket_rosa'], null, false)");
  await como(c, M, "select public.responder_invitacion('e7_cuarta', true)");
  const lote = (await como(c, N, "select * from public.avisos_por_enviar(200)")).rows;
  r.ok("E7 · El servicio recibe los avisos pendientes con los dispositivos activos", lote.length > 0, lote.length);
  const conTokens = lote.find(a => a.usuario_id === C2);
  r.ok("E7 · Los tokens llegan solo para el envío", conTokens && conTokens.tokens.length === 2, conTokens);
  r.igual("E7 · Lo tomado queda «enviando» (no lo toma otro proceso)",
    (await como(c, N, "select count(*)::int n from public.avisos_por_enviar(200)")).rows[0].n, 0);
  const sinConsent = (await c.query("select count(*)::int n from avisos where estado = 'ENVIANDO' and tipo = 'PARTICIPACION_SEGUIDA' and usuario_id = $1", [V])).rows[0].n;
  r.igual("E7 · Quien dejó de seguir no recibe envíos pendientes", sinConsent, 0);
  const id1 = conTokens.id;
  const tokenWeb = "token-web-" + "y".repeat(40);
  // Dos dispositivos: el navegador lo acepta y el celular falla temporalmente.
  const parcial = await rpc(c, N, "select public.resultado_envio($1, array[$2], array[$3], '{}', 'prueba')", [id1, tokenWeb, token]);
  r.igual("E7 · Aceptado en un dispositivo y falla temporal en otro: queda pendiente de reintento", [parcial.estado, parcial.aceptados, parcial.por_reintentar], ["PENDIENTE", 1, 1]);
  const tras = (await c.query("select estado, intentos, proximo_intento > now() espera, enviado_en is not null e from avisos where id = $1", [id1])).rows[0];
  r.igual("E7 · Se anota que ya fue aceptado una vez y se espera antes de reintentar", tras, { estado: "PENDIENTE", intentos: 1, espera: true, e: true });
  await c.query("update avisos set proximo_intento = now() where id = $1", [id1]);
  const reintento = (await como(c, N, "select * from public.avisos_por_enviar(200)")).rows.find(a => a.id === id1);
  r.igual("E7 · El reintento va SOLO al dispositivo que falló (no se duplica en el que aceptó)", reintento && reintento.tokens.map(t => t.token), [token]);
  const fin = await rpc(c, N, "select public.resultado_envio($1, '{}', '{}', array[$2], 'prueba')", [id1, token]);
  r.igual("E7 · Si el que falló resulta inválido, el aviso queda ENVIADO (el otro ya lo aceptó)", fin.estado, "ENVIADO");
  r.igual("E7 · Un token inválido se desactiva",
    (await c.query("select activo from dispositivos where token = $1", [token])).rows[0].activo, false);
  r.igual("E7 · ENVIADO = el proveedor lo aceptó (aún sin confirmar recepción)",
    (await c.query("select estado, enviado_en is not null e, recibido_en is null r from avisos where id = $1", [id1])).rows[0], { estado: "ENVIADO", e: true, r: true });
  r.ok("E7 · Nadie fuera del servidor lee el detalle por dispositivo",
    !!(await error(como(c, N, "select * from entregas_aviso"))) && !!(await error(como(c, C2, "select * from entregas_aviso"))));
  await como(c, C2, "select public.marcar_aviso($1, false, true)", [id1]);
  r.ok("E7 · El dispositivo confirma la recepción por separado",
    (await c.query("select recibido_en is not null r from avisos where id = $1", [id1])).rows[0].r);
  const marcarAjeno = await rpc(c, V, "select public.marcar_aviso($1, true, true)", [id1]);
  r.igual("E7 · Nadie marca avisos ajenos", marcarAjeno, { actualizado: false });

  // ---------- Contactos con horario (A15) ----------
  await como(c, M, "select public.guardar_avisos_contacto(true, '00:00', '00:00')");
  await como(c, null, "insert into contactos (tienda_id, nombre, mensaje) values ('t_minimarket_rosa', 'Vecina', 'Dentro de horario')");
  const dentro = (await como(c, M, "select estado from avisos where tipo = 'CONTACTO' order by creado_en desc limit 1")).rows[0];
  r.igual("E7 · Dentro de horario, el contacto genera aviso", dentro && dentro.estado, "PENDIENTE");
  const ahora = (await c.query("select public.hora_santiago() h")).rows[0].h;
  const desde = (await c.query("select ($1::time + interval '2 hours')::time d, ($1::time + interval '3 hours')::time h", [ahora])).rows[0];
  await como(c, M, "select public.guardar_avisos_contacto(true, $1, $2)", [desde.d, desde.h]);
  await como(c, null, "insert into contactos (tienda_id, nombre, mensaje) values ('t_minimarket_rosa', 'Vecina', 'Fuera de horario')");
  const fuera = (await como(c, M, "select estado, motivo from avisos where tipo = 'CONTACTO' order by creado_en desc limit 1")).rows[0];
  r.igual("E7 · Fuera de horario se guarda sin notificar", fuera, { estado: "OMITIDO", motivo: "FUERA_DE_HORARIO" });
  r.igual("E7 · El mensaje siempre llega a la bandeja",
    (await como(c, M, "select count(*)::int n from contactos where mensaje in ('Dentro de horario', 'Fuera de horario')")).rows[0].n, 2);
  await como(c, M, "select public.guardar_avisos_contacto(false, '09:00', '20:00')");
  await como(c, null, "insert into contactos (tienda_id, nombre, mensaje) values ('t_minimarket_rosa', 'Vecina', 'Avisos apagados')");
  r.igual("E7 · Con los avisos de contacto apagados no notifica",
    (await como(c, M, "select motivo from avisos where tipo = 'CONTACTO' order by creado_en desc limit 1")).rows[0].motivo, "AVISOS_DESACTIVADOS");
  r.igual("E7 · Otra emprendedora no lee ni cambia las preferencias ajenas",
    (await como(c, B, "select count(*)::int n from preferencias_tienda where tienda_id = 't_minimarket_rosa'")).rows[0].n, 0);
  // ---------- Avisos atrasados: se revalidan justo antes de enviar ----------
  const estadoAviso = async (sql, params) => (await c.query(sql, params)).rows.map(x => x.estado + ":" + (x.motivo || ""));
  // Contacto recibido dentro de horario, pero la dueña cambió el horario antes del envío.
  await como(c, M, "select public.guardar_avisos_contacto(true, '00:00', '00:00')");
  await como(c, null, "insert into contactos (tienda_id, nombre, mensaje) values ('t_minimarket_rosa', 'Vecina', 'Atrasado')");
  await como(c, M, "select public.guardar_avisos_contacto(true, $1, $2)", [desde.d, desde.h]);
  await como(c, N, "select count(*) from public.avisos_por_enviar(200)");
  r.igual("E7 · Contacto atrasado: si al enviar ya es fuera de horario, no se notifica",
    await estadoAviso("select a.estado, a.motivo from avisos a join contactos k on a.clave = 'contacto:' || k.id where k.mensaje = 'Atrasado'"), ["OMITIDO:FUERA_DE_HORARIO"]);
  // Dejar de seguir con un aviso pendiente.
  await como(c, A, "insert into ferias (id, nombre, fecha_inicio, estado) values ('e7_quinta', 'Quinta feria', public.fecha_santiago() + 5, 'PROXIMA')");
  await como(c, A, "select public.invitar_a_feria('e7_quinta', array['t_minimarket_rosa'], null, true)");
  await como(c, C2, "select public.dejar_de_seguir('TIENDA', 't_minimarket_rosa')");
  await como(c, N, "select count(*) from public.avisos_por_enviar(200)");
  r.igual("E7 · Dejó de seguir con el aviso pendiente: no se envía",
    await estadoAviso("select estado, motivo from avisos where usuario_id = $1 and feria_id = 'e7_quinta'", [C2]), ["OMITIDO:YA_NO_SIGUE"]);
  await como(c, C2, "select public.seguir('TIENDA', 't_minimarket_rosa')");
  // Cambio de cuenta en el mismo navegador con un aviso pendiente de la cuenta anterior.
  await como(c, A, "insert into ferias (id, nombre, fecha_inicio, estado) values ('e7_sexta', 'Sexta feria', public.fecha_santiago() + 6, 'PROXIMA')");
  await como(c, A, "select public.invitar_a_feria('e7_sexta', array['t_minimarket_rosa'], null, true)");
  await como(c, V, "select public.registrar_dispositivo($1, 'WEB')", [tokenWeb]);
  const loteCambio = (await como(c, N, "select * from public.avisos_por_enviar(200)")).rows.find(a => a.usuario_id === C2 && a.feria_id === "e7_sexta");
  r.igual("E7 · El token que pasó a otra cuenta ya no recibe avisos de la anterior", loteCambio && loteCambio.tokens, []);
  r.igual("E7 · Sin dispositivos de la cuenta, el aviso queda SIN_DISPOSITIVO (sigue en su bandeja)",
    (await rpc(c, N, "select public.resultado_envio($1, '{}', '{}', '{}', null)", [loteCambio.id])).estado, "SIN_DISPOSITIVO");
  // Invitación respondida antes de que saliera el aviso.
  await como(c, A, "insert into ferias (id, nombre, fecha_inicio, estado) values ('e7_septima', 'Séptima feria', public.fecha_santiago() + 7, 'PROXIMA')");
  await como(c, A, "select public.invitar_a_feria('e7_septima', array['t_barberia_alerce', 't_minimarket_rosa'], null, false)");
  await como(c, B, "select public.responder_invitacion('e7_septima', false)");
  await como(c, A, "select public.cancelar_feria('e7_septima', 'prueba')");
  await como(c, N, "select count(*) from public.avisos_por_enviar(200)");
  r.igual("E7 · Invitación ya respondida: no se envía",
    await estadoAviso("select estado, motivo from avisos where tienda_id = 't_barberia_alerce' and feria_id = 'e7_septima' and tipo = 'INVITACION'"), ["OMITIDO:YA_RESPONDIO"]);
  r.igual("E7 · Invitación a una feria que se canceló antes del envío: no se envía; el aviso de cancelación sí",
    await estadoAviso("select estado, motivo from avisos where tienda_id = 't_minimarket_rosa' and feria_id = 'e7_septima' order by tipo", []),
    ["ENVIANDO:", "OMITIDO:FERIA_NO_ACTIVA"]);
  await c.query("update avisos set creado_en = now() - interval '4 days', estado = 'PENDIENTE' where usuario_id = $1 and feria_id = 'e7_quinta'", [C2]);
  await como(c, N, "select count(*) from public.avisos_por_enviar(200)");
  r.igual("E7 · Con más de 3 días de atraso no se envía",
    await estadoAviso("select estado, motivo from avisos where usuario_id = $1 and feria_id = 'e7_quinta'", [C2]), ["OMITIDO:VENCIDO"]);

  // ---------- Una sola regla para push y para los mecanismos alternativos ----------
  const paraMostrar = async (uid) => (await como(c, uid, "select id, feria_id, tipo from public.avisos_para_mostrar(20)")).rows;
  await como(c, A, "insert into ferias (id, nombre, fecha_inicio, estado) values ('e7_octava', 'Octava feria', public.fecha_santiago() + 8, 'PROXIMA'), ('e7_novena', 'Novena feria', public.fecha_santiago() + 9, 'PROXIMA'), ('e7_decima', 'Décima feria', public.fecha_santiago() + 10, 'PROXIMA')");
  await como(c, A, "select public.invitar_a_feria('e7_octava', array['t_minimarket_rosa'], null, true)");
  const visibleC2 = (await paraMostrar(C2)).filter(a => a.feria_id === "e7_octava");
  r.igual("E7 · Sin push, la cuenta puede mostrar su aviso pendiente (misma regla que el push)", visibleC2.map(a => a.tipo), ["PARTICIPACION_SEGUIDA"]);
  r.igual("E7 · Otra cuenta no ve ni muestra avisos ajenos", (await paraMostrar(V)).filter(a => a.feria_id === "e7_octava").length, 0);
  await como(c, C2, "select public.marcar_aviso($1, true, false)", [visibleC2[0].id]);
  r.igual("E7 · Leerlo en la bandeja no cuenta como notificación recibida",
    (await c.query("select recibido_en is null r from avisos where id = $1", [visibleC2[0].id])).rows[0].r, true);
  await como(c, C2, "select public.marcar_aviso($1, false, true)", [visibleC2[0].id]);
  r.igual("E7 · Una vez mostrado, no se vuelve a mostrar", (await paraMostrar(C2)).filter(a => a.feria_id === "e7_octava").length, 0);
  await como(c, N, "select count(*) from public.avisos_por_enviar(200)");
  r.igual("E7 · …y el push no lo duplica",
    await estadoAviso("select estado, motivo from avisos where id = $1", [visibleC2[0].id]), ["OMITIDO:YA_RECIBIDO"]);
  await como(c, A, "select public.invitar_a_feria('e7_novena', array['t_minimarket_rosa'], null, true)");
  await como(c, C2, "select public.dejar_de_seguir('TIENDA', 't_minimarket_rosa')");
  r.igual("E7 · Mecanismo alternativo: tras dejar de seguir, no se muestra", (await paraMostrar(C2)).filter(a => a.feria_id === "e7_novena").length, 0);
  r.igual("E7 · …y queda omitido con el mismo motivo que usaría el push",
    await estadoAviso("select estado, motivo from avisos where usuario_id = $1 and feria_id = 'e7_novena'", [C2]), ["OMITIDO:YA_NO_SIGUE"]);
  await como(c, C2, "select public.seguir('TIENDA', 't_minimarket_rosa')");
  await como(c, M, "select public.guardar_avisos_contacto(true, '00:00', '00:00')");
  await como(c, null, "insert into contactos (tienda_id, nombre, mensaje) values ('t_minimarket_rosa', 'Vecina', 'Para mostrar')");
  await como(c, M, "select public.guardar_avisos_contacto(true, $1, $2)", [desde.d, desde.h]);
  r.igual("E7 · Mecanismo alternativo: contacto fuera de horario no se muestra",
    (await paraMostrar(M)).filter(a => a.tipo === "CONTACTO").length, 0);
  // FCM rechaza el contenido del mensaje: el dispositivo NO se desactiva.
  const token2 = "token-android-2-" + "z".repeat(40);
  await como(c, C2, "select public.registrar_dispositivo($1, 'ANDROID')", [token2]);
  await como(c, A, "select public.invitar_a_feria('e7_decima', array['t_minimarket_rosa'], null, true)");
  const loteRech = (await como(c, N, "select * from public.avisos_por_enviar(200)")).rows.find(a => a.usuario_id === C2 && a.feria_id === "e7_decima");
  const rech = await rpc(c, N, "select public.resultado_envio($1, '{}', '{}', '{}', 'contenido', array[$2])", [loteRech.id, token2]);
  r.igual("E7 · Mensaje rechazado por contenido: FALLIDO sin reintentos inútiles", [rech.estado, rech.rechazados], ["FALLIDO", 1]);
  r.igual("E7 · …y el dispositivo sigue activo (no era un token inválido)",
    (await c.query("select activo from dispositivos where token = $1", [token2])).rows[0].activo, true);

  r.igual("E7 · El horario no es público",
    await como(c, null, "select count(*)::int n from preferencias_tienda").then(x => x.rows[0].n, () => 0), 0);

  // ---------- Calificaciones (A16, A17) ----------
  r.falla("E7 · Con calificaciones desactivadas no se puede calificar (lo exige el servidor)",
    await error(como(c, V, "select public.calificar('t_minimarket_rosa', null, 5, 'Muy bueno')")), "CALIFICACIONES_DESACTIVADAS");
  await como(c, B, "update tiendas set calificaciones_activas = true where id = 't_minimarket_rosa'");
  r.igual("E7 · Otra emprendedora no activa las calificaciones ajenas",
    (await c.query("select calificaciones_activas from tiendas where id = 't_minimarket_rosa'")).rows[0].calificaciones_activas, false);
  await como(c, M, "update tiendas set calificaciones_activas = true where id = 't_minimarket_rosa'");
  await como(c, V, "select public.calificar('t_minimarket_rosa', null, 5, 'Muy bueno')");
  await como(c, V, "select public.calificar('t_minimarket_rosa', null, 4, 'Bueno')");
  await como(c, C2, "select public.calificar('t_minimarket_rosa', 'p_mini_2', 3, '')");
  const res = await rpc(c, null, "select public.resumen_calificaciones('t_minimarket_rosa')");
  r.igual("E7 · Calificar de nuevo actualiza (no duplica) y el resumen es público y anónimo",
    [res.activas, res.tienda.cantidad, Number(res.tienda.promedio), res.productos.p_mini_2.cantidad], [true, 1, 4, 1]);
  r.falla("E7 · La dueña no califica su propia tienda",
    await error(como(c, M, "select public.calificar('t_minimarket_rosa', null, 5, '')")), "PROPIA_TIENDA");
  await como(c, M, "update productos set publicado = false where id = 'p_mini_3'");
  r.falla("E7 · No se califica un producto que no es público",
    await error(como(c, V, "select public.calificar('t_minimarket_rosa', 'p_mini_3', 5, '')")), "PRODUCTO_NO_PUBLICO");
  r.falla("E7 · Estrellas fuera de rango rechazadas",
    await error(como(c, V, "select public.calificar('t_minimarket_rosa', null, 9, '')")), "ESTRELLAS_INVALIDAS");
  await como(c, M, "update tiendas set calificaciones_activas = false where id = 't_minimarket_rosa'");
  r.igual("E7 · Al desactivarlas, el resumen se oculta",
    await rpc(c, null, "select public.resumen_calificaciones('t_minimarket_rosa')"), { activas: false });
  r.falla("E7 · Y no se aceptan calificaciones nuevas",
    await error(como(c, C2, "select public.calificar('t_minimarket_rosa', null, 2, '')")), "CALIFICACIONES_DESACTIVADAS");
  r.ok("E7 · Nadie inserta calificaciones directamente",
    !!(await error(como(c, V, `insert into calificaciones (tienda_id, usuario_id, estrellas) values ('t_minimarket_rosa', '${V}', 1)`))));

  // ---------- Cancelar (W02) ----------
  r.falla("E7 · Una emprendedora no cancela ferias",
    await error(como(c, M, "select public.cancelar_feria('e7_futura', 'x')")), "SOLO_ADMIN");
  const can = await rpc(c, A, "select public.cancelar_feria('e7_futura', 'Lluvia')");
  r.igual("E7 · Cancelar avisa a confirmadas e invitadas", [can.estado, can.avisadas], ["CANCELADA", 2]);
  const vis = (await como(c, null, "select estado, public.feria_activa(estado, fecha_inicio, fecha_fin) activa from ferias where id = 'e7_futura'")).rows[0];
  r.igual("E7 · Cancelada: sigue su ficha (dice cancelada) pero deja de ser activa", vis, { estado: "CANCELADA", activa: false });
  r.falla("E7 · No se responde a una feria cancelada",
    await error(como(c, B, "select public.responder_invitacion('e7_futura', true)")), "FERIA_NO_ACTIVA");
  r.igual("E7 · Una feria cancelada no muestra participantes como «activa» pero tampoco expone rechazadas",
    (await como(c, null, "select count(*)::int n from public.participantes_feria('e7_futura')")).rows[0].n, 1);
  r.igual("E7 · Cancelar dos veces no repite avisos",
    (await rpc(c, A, "select public.cancelar_feria('e7_futura', 'Lluvia')")).avisadas, 0);

  // ---------- Mis ferias (A04) ----------
  const mias = (await como(c, M, "select feria_id, invitacion from public.mis_ferias() order by feria_id")).rows;
  r.igual("E7 · La emprendedora ve sus ferias invitadas y confirmadas", mias.map(x => x.feria_id + ":" + x.invitacion),
    ["e7_cuarta:CONFIRMADA", "e7_decima:AGREGADA", "e7_futura:CONFIRMADA", "e7_hoy:CONFIRMADA", "e7_novena:AGREGADA", "e7_octava:AGREGADA", "e7_otra:CONFIRMADA", "e7_quinta:AGREGADA", "e7_septima:PENDIENTE", "e7_sexta:AGREGADA"]);
  r.igual("E7 · Y no las de otras tiendas",
    (await como(c, V, "select count(*)::int n from public.mis_ferias()")).rows[0].n, 0);

  // ---------- Privilegios ----------
  const amplios = (await c.query(`select count(*)::int n from pg_tables t cross join (values ('anon'), ('authenticated')) r(rol)
     where t.schemaname = 'public' and (has_table_privilege(r.rol, format('%I.%I', t.schemaname, t.tablename), 'TRUNCATE')
       or has_table_privilege(r.rol, format('%I.%I', t.schemaname, t.tablename), 'TRIGGER'))`)).rows[0].n;
  r.igual("E7 · Tras 07, ningún cliente puede vaciar tablas (TRUNCATE) ni crear triggers", amplios, 0);
  r.ok("E7 · Ningún cliente crea avisos por su cuenta",
    !!(await error(como(c, A, `insert into avisos (usuario_id, tipo, titulo, clave) values ('${V}', 'AVISO_FERIA', 'falso', 'falso')`))));
};
