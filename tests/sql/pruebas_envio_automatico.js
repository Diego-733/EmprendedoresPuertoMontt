"use strict";
// Envío automático de avisos (supabase/08_envio_automatico.sql) de punta a punta, en local:
//  · PostgreSQL local y desechable con 01–07 ya aplicadas y probadas.
//  · pg_net SIMULADO: net.http_post solo anota la solicitud (no sale nada a GitHub).
//  · El emisor REAL (servicios/notificador) con un fetch que traduce sus llamadas a
//    Supabase en SQL local como la cuenta del notificador, y Firebase SIMULADO.
//  · Reloj virtual: «esperar 2 minutos» adelanta la cola en vez de esperar de verdad.
// Cada criterio mira lo que realmente «llegó» a Firebase: qué token, qué título y qué feria.
const path = require("path");
const fs = require("fs");
const crypto = require("crypto");
const { UID, como, error } = require("./ayudas");
const { RAIZ } = require("./postgres_local");
const { crearNotificador } = require(path.join(RAIZ, "servicios", "notificador", "notificador.js"));

const N = "00000000-0000-0000-0000-00000000000f";    // cuenta del notificador (creada en las pruebas E7)
const C2 = "00000000-0000-0000-0000-00000000000e";   // compradora 2 (creada en las pruebas E7)
const T = "00000000-0000-0000-0000-000000000010";    // dueña de Tejidos
const C3 = "00000000-0000-0000-0000-000000000011";   // compradora que sigue tienda y asociación

const ANDROID_MARTA = "fcm-android-marta-" + "a".repeat(30);
const ANDROID_BARB = "fcm-android-barberia-" + "b".repeat(30);
const ANDROID_TEJ = "fcm-android-tejidos-" + "t".repeat(30);
const ANDROID_C2 = "fcm-android-compradora2-" + "c".repeat(30);
const WEB_C2 = "fcm-web-compradora2-" + "w".repeat(30);
const ANDROID_V = "fcm-android-vecina-" + "v".repeat(30);
const ANDROID_C3 = "fcm-android-compradora3-" + "x".repeat(30);

const { privateKey } = crypto.generateKeyPairSync("rsa", { modulusLength: 2048 });
const CONFIG = { supabaseUrl: "https://local.supabase.test", anonKey: "clave-publica-local", email: "notificador@alerce.app",
  clave: "local", urlWeb: "https://diego-733.github.io/EmprendedoresPuertoMontt/",
  cuentaServicio: { project_id: "alerce-local", client_email: "envio@alerce-local.iam.gserviceaccount.com",
                    private_key: privateKey.export({ type: "pkcs8", format: "pem" }) } };

function resp(status, cuerpo) {
  return { ok: status >= 200 && status < 300, status, text: async () => JSON.stringify(cuerpo) };
}

const STUB_PG_NET = `
  create schema if not exists net;
  create table if not exists net.solicitudes (id bigserial primary key, url text, body jsonb, headers jsonb, creada_en timestamptz default now());
  create table if not exists net._http_response (id bigint primary key, status_code int, error_msg text);
  create or replace function net.http_post(url text, body jsonb default '{}', params jsonb default '{}',
                                           headers jsonb default '{}', timeout_milliseconds int default 5000)
  returns bigint language plpgsql as $$
  declare v bigint;
  begin
    insert into net.solicitudes (url, body, headers) values (url, body, headers) returning id into v;
    insert into net._http_response (id, status_code) values (v, 204);
    return v;
  end;
  $$;`;

module.exports = async function pruebasEnvioAutomatico(c, r) {
  const A = UID.admin, M = UID.marta, B = UID.barberia, V = UID.vecina;
  const q = async (sql, p) => (await c.query(sql, p)).rows;
  const uno = async (sql, p) => { const f = (await c.query(sql, p)).rows[0]; return f ? Object.values(f)[0] : null; };

  // Como en Supabase: las funciones nuevas quedan ejecutables por anon/authenticated salvo revocación.
  await c.query("alter default privileges in schema public grant execute on functions to anon, authenticated");
  await c.query(`insert into auth.users (id, email, raw_user_meta_data) values
    ($1, 'tejidos@alerce.app', '{}'), ($2, 'compradora3@correo.cl', '{}') on conflict (id) do nothing`, [T, C3]);
  // Tejidos con dueña propia (en las pruebas anteriores no tenía cuenta).
  await c.query("update tiendas set owner_id = $1 where id = 't_tejidos_austral'", [T]);
  await c.query(`insert into perfiles (id, email, nombre, rol, tienda_id) values ($1, 'tejidos@alerce.app', 'Tejidos Austral', 'EMPRENDEDORA', 't_tejidos_austral')
    on conflict (id) do update set rol = 'EMPRENDEDORA', tienda_id = 't_tejidos_austral'`, [T]);
  await c.query("insert into notificadores (usuario_id, nota) values ($1, 'prueba 08') on conflict do nothing", [N]);
  // Punto de partida limpio para estas pruebas (base desechable): sin cola, sin seguimientos ni dispositivos previos.
  await c.query("update avisos set estado = 'OMITIDO', motivo = 'ANTES_DE_08' where estado in ('PENDIENTE', 'ENVIANDO')");
  await c.query("delete from seguimientos where usuario_id in ($1, $2, $3)", [C2, V, C3]);
  await c.query("delete from dispositivos");
  await c.query("insert into asociaciones (id, nombre) values ('as_e8', 'Asociación Ocho') on conflict (id) do nothing");
  await c.query("delete from tienda_asociaciones where asociacion_id = 'as_e8'");
  await c.query("insert into tienda_asociaciones (tienda_id, asociacion_id) values ('t_barberia_alerce', 'as_e8'), ('t_tejidos_austral', 'as_e8') on conflict do nothing");

  // ---------- 08 sin pg_net: se aplica (dos veces) y nada se rompe ----------
  await c.query(fs.readFileSync(path.join(RAIZ, "supabase", "08_envio_automatico.sql"), "utf8"));
  await c.query(fs.readFileSync(path.join(RAIZ, "supabase", "08_envio_automatico.sql"), "utf8"));
  r.ok("E8 · 08 se aplica dos veces sobre 07 sin errores", true);
  r.igual("E8 · Queda registrada la migración 08", await uno("select count(*)::int from migraciones_aplicadas where nombre = '08_envio_automatico'"), 1);
  r.igual("E8 · Recién aplicada queda apagada (no dispara nada hasta activarla)", await uno("select activo from envio_automatico"), false);
  r.igual("E8 · Siguen los mismos 7 tipos de aviso",
    await uno(`select pg_get_constraintdef(oid) ~ 'INVITACION.*AGREGADA.*RECORDATORIO.*AVISO_FERIA.*FERIA_CANCELADA.*PARTICIPACION_SEGUIDA.*CONTACTO'
               and (select count(*) from regexp_matches(pg_get_constraintdef(oid), '''[A-Z_]+''', 'g')) = 7
               from pg_constraint where conrelid = 'public.avisos'::regclass and contype = 'c' and pg_get_constraintdef(oid) like '%tipo%'`), true);

  const feria = async (id, nombre, dias = 3) => {
    await como(c, A, `insert into ferias (id, nombre, fecha_inicio, estado) values ($1, $2, public.fecha_santiago() + $3::int, 'PROXIMA')`, [id, nombre, dias]);
  };
  await feria("e8_0", "Feria Ocho Cero");
  await feria("e8_a", "Feria Ocho A");
  await c.query("update envio_automatico set activo = true");
  await como(c, A, "select public.invitar_a_feria('e8_0', array['t_minimarket_rosa'], null, false)");
  r.igual("E8 · Activa pero sin pg_net: la invitación igual se guarda y queda anotado por qué no se disparó",
    [await uno("select count(*)::int from avisos where feria_id = 'e8_0' and tipo = 'INVITACION' and estado = 'PENDIENTE'"),
     await uno("select ultimo_resultado from envio_automatico")], [1, "SIN_PG_NET"]);

  // ---------- Con pg_net (simulado) ----------
  await c.query(STUB_PG_NET);
  const solicitudes = async () => (await q("select url, body, headers from net.solicitudes order by id"));
  await c.query("update avisos set estado = 'OMITIDO', motivo = 'PRUEBA' where feria_id = 'e8_0'");
  await como(c, A, "select public.invitar_a_feria('e8_0', array['t_barberia_alerce'], null, false)");
  r.igual("E8 · Sin token de GitHub no dispara y lo anota", [(await solicitudes()).length, await uno("select ultimo_resultado from envio_automatico")], [0, "SIN_TOKEN"]);

  await c.query("update envio_automatico set token_github = 'github_pat_FALSO_local'");
  await c.query("update avisos set estado = 'OMITIDO', motivo = 'PRUEBA' where feria_id = 'e8_0'");
  // pg_net envía después del commit: si la acción se deshace, no sale ninguna solicitud.
  await c.query("begin");
  await c.query("set local role authenticated");
  await c.query("select set_config('request.jwt.claim.sub', $1, true)", [A]);
  await c.query("select public.invitar_a_feria('e8_a', array['t_tejidos_austral'], null, false)");
  await c.query("rollback");
  r.igual("E8 · Acción deshecha: no queda ni aviso ni disparo", [(await solicitudes()).length,
    await uno("select count(*)::int from participaciones where feria_id = 'e8_a' and tienda_id = 't_tejidos_austral'")], [0, 0]);

  // Invitar una tienda y una asociación de dos integrantes en una sola acción: tres avisos, UN disparo.
  await como(c, A, "select public.invitar_a_feria('e8_a', array['t_minimarket_rosa'], array['as_e8'], false)");
  const s1 = await solicitudes();
  r.igual("E8 · Varios avisos en una acción piden UNA sola ejecución",
    [await uno("select count(*)::int from avisos where feria_id = 'e8_a' and estado = 'PENDIENTE'"), s1.length], [3, 1]);
  r.igual("E8 · La solicitud es el workflow_dispatch de «Enviar avisos» en main",
    [s1[0].url, s1[0].body, s1[0].headers["Accept"], s1[0].headers["Authorization"]],
    ["https://api.github.com/repos/Diego-733/EmprendedoresPuertoMontt/actions/workflows/notificador.yml/dispatches",
     { ref: "main" }, "application/vnd.github+json", "Bearer github_pat_FALSO_local"]);
  await como(c, A, "select public.invitar_a_feria('e8_0', array['t_tejidos_austral'], null, false)");
  r.igual("E8 · Con una ejecución viva, un aviso nuevo no pide otra",
    [await uno("select count(*)::int from avisos where feria_id = 'e8_0' and tienda_id = 't_tejidos_austral' and estado = 'PENDIENTE'"), (await solicitudes()).length], [1, 1]);

  // ---------- Permisos: el token y el disparo quedan fuera del alcance de los clientes ----------
  r.ok("E8 · Ni el visitante ni una cuenta común leen la configuración (token)",
    !!(await error(como(c, null, "select token_github from envio_automatico"))) && !!(await error(como(c, V, "select token_github from envio_automatico"))) &&
    !!(await error(como(c, A, "select token_github from envio_automatico"))));
  r.ok("E8 · Nadie desde la API pide ejecuciones, lee el token ni el diagnóstico",
    !!(await error(como(c, V, "select public.solicitar_envio('x')"))) && !!(await error(como(c, A, "select public.token_disparo_github()"))) &&
    !!(await error(como(c, null, "select public.recuperar_envio()"))) && !!(await error(como(c, A, "select public.estado_envio_automatico()"))));
  r.falla("E8 · Una cuenta común no envía señales del emisor", await error(como(c, V, "select public.latido_envio()")), "SOLO_NOTIFICADOR");
  r.falla("E8 · El administrador tampoco cierra el emisor", await error(como(c, A, "select public.cerrar_envio(false)")), "SOLO_NOTIFICADOR");
  const diag = await uno("select public.estado_envio_automatico()");
  r.ok("E8 · El diagnóstico (Studio) muestra el estado y la respuesta de GitHub sin el token",
    diag.tiene_token === true && diag.pg_net === true && diag.respuesta_github === 204 && !JSON.stringify(diag).includes("github_pat"), diag);

  // ---------- Emisor real con Supabase local y Firebase simulado ----------
  let reloj = Date.now();
  const plan = {};                 // token -> [status...] para simular fallas de Firebase
  const llegados = [];             // lo que Firebase aceptó: { token, titulo, feria, enlace, tipo }
  const intentosFcm = [];
  const fetchLocal = async (url, op) => {
    const cuerpo = op && typeof op.body === "string" && /json/.test((op.headers || {})["Content-Type"] || "") ? JSON.parse(op.body) : null;
    if (url.includes("/auth/v1/token")) return resp(200, { access_token: "sesion-local", expires_in: 3600 });
    if (url.includes("oauth2.googleapis.com")) return resp(200, { access_token: "google-local", expires_in: 3600 });
    if (url.includes("fcm.googleapis.com")) {
      const m = cuerpo.message;
      const status = (plan[m.token] || []).shift() || 200;
      intentosFcm.push({ token: m.token, status, aviso: m.data.aviso_id });
      if (status !== 200) return resp(status, { error: { status: "UNAVAILABLE" } });
      llegados.push({ token: m.token, titulo: m.notification.title, feria: m.data.feria_id, enlace: m.data.enlace, tipo: m.data.tipo, aviso: m.data.aviso_id });
      return resp(200, { name: "projects/alerce-local/messages/" + llegados.length });
    }
    const rpc = url.match(/\/rest\/v1\/rpc\/(\w+)$/);
    if (rpc) {
      const fila = (await q("select proretset from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = $1", [rpc[1]]))[0];
      if (!fila) return resp(404, { code: "PGRST202", message: "Could not find the function public." + rpc[1] });
      const claves = Object.keys(cuerpo || {});
      try {
        const res = await como(c, N, `select * from public.${rpc[1]}(${claves.map((k, i) => k + " => $" + (i + 1)).join(", ")})`, claves.map(k => cuerpo[k]));
        return resp(200, fila.proretset ? res.rows : (res.rows[0] ? Object.values(res.rows[0])[0] : null));
      } catch (e) { return resp(400, { message: e.message }); }
    }
    throw new Error("URL inesperada en la prueba: " + url);
  };
  const dormir = async (ms) => {   // el tiempo pasa: la cola se adelanta lo mismo
    reloj += ms;
    await c.query("update avisos set proximo_intento = proximo_intento - make_interval(secs => $1::double precision / 1000) where estado in ('PENDIENTE', 'ENVIANDO')", [ms]);
  };
  const emitir = async (presupuestoMin = 40) => {
    const antes = llegados.length;
    const total = await crearNotificador(CONFIG, { fetch: fetchLocal, log: () => {}, ahora: () => reloj, dormir })
      .procesarHastaVaciar({ presupuestoMs: presupuestoMin * 60000 });
    return { total, nuevos: llegados.slice(antes) };
  };
  const de = (lista, token) => lista.filter(x => x.token === token);

  // Dispositivos: tres emprendedoras con Android; compradoras con Android (y web la compradora 2).
  await como(c, M, "select public.registrar_dispositivo($1, 'ANDROID')", [ANDROID_MARTA]);
  await como(c, B, "select public.registrar_dispositivo($1, 'ANDROID')", [ANDROID_BARB]);
  await como(c, T, "select public.registrar_dispositivo($1, 'ANDROID')", [ANDROID_TEJ]);
  await como(c, C2, "select public.registrar_dispositivo($1, 'ANDROID')", [ANDROID_C2]);
  await como(c, C2, "select public.registrar_dispositivo($1, 'WEB')", [WEB_C2]);
  await como(c, V, "select public.registrar_dispositivo($1, 'ANDROID')", [ANDROID_V]);
  await como(c, C3, "select public.registrar_dispositivo($1, 'ANDROID')", [ANDROID_C3]);

  // 1) Lo que disparó la invitación anterior: tienda + asociación (W03, W04, A01).
  const e1 = await emitir();
  r.igual("E8 · Invitar una tienda: llega «Te invitaron a…» a su dueña (A01, W03)",
    de(e1.nuevos, ANDROID_MARTA).map(x => [x.tipo, x.titulo, x.feria, x.enlace]),
    [["INVITACION", "Te invitaron a Feria Ocho A", "e8_a", "https://diego-733.github.io/EmprendedoresPuertoMontt/?feria=e8_a"]]);
  r.igual("E8 · Invitar una asociación: llega a la dueña de cada integrante (W04)",
    [de(e1.nuevos, ANDROID_BARB).map(x => x.titulo), de(e1.nuevos, ANDROID_TEJ).filter(x => x.feria === "e8_a").map(x => x.titulo)],
    [["Te invitaron a Feria Ocho A"], ["Te invitaron a Feria Ocho A"]]);
  r.igual("E8 · Nadie más recibe la invitación", e1.nuevos.filter(x => ![ANDROID_MARTA, ANDROID_BARB, ANDROID_TEJ].includes(x.token)).length, 0);
  r.igual("E8 · El aviso que llegó con la ejecución ya en marcha también sale en ella", de(e1.nuevos, ANDROID_TEJ).map(x => x.titulo).sort(),
    ["Te invitaron a Feria Ocho A", "Te invitaron a Feria Ocho Cero"]);
  r.igual("E8 · Cola vacía: el emisor se apaga solo", [await uno("select en_curso from envio_automatico"), await uno("select ultimo_resultado from envio_automatico")], [false, "COLA VACÍA"]);
  r.igual("E8 · Un emisor sin nada pendiente no vuelve a enviar nada", (await emitir()).nuevos.length, 0);

  // 2) Seguidores (A13, W18): tienda y asociación por separado, con consentimiento previo.
  await como(c, C2, "select public.guardar_consentimiento(true)");
  await como(c, V, "select public.guardar_consentimiento(true)");
  await como(c, C3, "select public.guardar_consentimiento(true)");
  const avisosAntesDeSeguir = await uno("select count(*)::int from avisos");
  await como(c, C2, "select public.seguir('TIENDA', 't_minimarket_rosa')");
  await como(c, V, "select public.seguir('ASOCIACION', 'as_e8')");
  await como(c, C3, "select public.seguir('TIENDA', 't_barberia_alerce')");
  await como(c, C3, "select public.seguir('ASOCIACION', 'as_e8')");
  r.igual("E8 · Seguir no crea avisos (ni «nuevo seguidor» para la tienda)", (await uno("select count(*)::int from avisos")) - avisosAntesDeSeguir, 0);
  await como(c, A, "update ferias set nombre = 'Feria Ocho A', descripcion = 'editada' where id = 'e8_a'");
  await como(c, B, "select public.responder_invitacion('e8_a', false)");
  const e2 = await emitir();
  r.igual("E8 · Seguir, editar la feria o rechazar la invitación no anuncian participación", e2.nuevos.filter(x => x.tipo === "PARTICIPACION_SEGUIDA").length, 0);
  const disparosAntes = (await solicitudes()).length;
  await como(c, M, "select public.responder_invitacion('e8_a', true)");
  r.igual("E8 · «Participo» desde la app despierta al emisor (sin cron)", (await solicitudes()).length, disparosAntes + 1);
  const e3 = await emitir();
  r.igual("E8 · Sigue la tienda: push al Android con el nombre de la feria (A13)",
    de(e3.nuevos, ANDROID_C2).map(x => [x.tipo, x.titulo, x.feria]), [["PARTICIPACION_SEGUIDA", "Minimarket Doña Marta estará en Feria Ocho A", "e8_a"]]);
  r.igual("E8 · …y al navegador registrado de la misma persona (W18 con push web)", de(e3.nuevos, WEB_C2).map(x => x.titulo), ["Minimarket Doña Marta estará en Feria Ocho A"]);
  r.igual("E8 · Tocar el aviso abre la feria correcta (datos feria_id y enlace)",
    [...new Set(e3.nuevos.map(x => x.feria + "|" + x.enlace))], ["e8_a|https://diego-733.github.io/EmprendedoresPuertoMontt/?feria=e8_a"]);
  r.igual("E8 · Quien sigue otras cosas no recibe ese anuncio", [de(e3.nuevos, ANDROID_V).length, de(e3.nuevos, ANDROID_C3).length], [0, 0]);

  await feria("e8_b", "Feria Ocho B");
  await como(c, A, "select public.invitar_a_feria('e8_b', null, array['as_e8'], false)");
  await emitir();
  await como(c, B, "select public.responder_invitacion('e8_b', true)");
  const e4 = await emitir();
  r.igual("E8 · Sigue la asociación: llega cuando una integrante invitada por ella confirma (A13)",
    de(e4.nuevos, ANDROID_V).map(x => [x.titulo, x.feria]), [["Asociación Ocho estará en Feria Ocho B", "e8_b"]]);
  r.igual("E8 · Sigue la tienda Y su asociación: un solo push por esa confirmación", de(e4.nuevos, ANDROID_C3).map(x => x.titulo), ["Barbería & Estilo Alerce estará en Feria Ocho B"]);
  await como(c, T, "select public.responder_invitacion('e8_b', true)");
  const e5 = await emitir();
  r.igual("E8 · Otra integrante de la misma asociación confirma: no se repite el aviso de la asociación",
    de(e5.nuevos, ANDROID_V).length, 0);
  r.igual("E8 · …quien siguió la tienda y la asociación recibe ahora (una vez) el de la asociación, que aún no tenía",
    de(e5.nuevos, ANDROID_C3).map(x => x.titulo), ["Asociación Ocho estará en Feria Ocho B"]);

  // 3) Administración → emprendedoras (W05, W09, W10, W02).
  await feria("e8_c", "Feria Ocho C");
  await como(c, A, "select public.invitar_a_feria('e8_c', array['t_minimarket_rosa', 't_barberia_alerce'], null, false)");
  await como(c, A, "select public.invitar_a_feria('e8_c', array['t_tejidos_austral'], null, true)");
  const e6 = await emitir();
  r.igual("E8 · Agregar directamente avisa «Participas en…» (W05)", de(e6.nuevos, ANDROID_TEJ).map(x => [x.tipo, x.titulo]), [["AGREGADA", "Participas en Feria Ocho C"]]);
  await como(c, M, "select public.responder_invitacion('e8_c', true)");
  await emitir();
  await como(c, A, "select public.enviar_aviso_feria('e8_c', 'RECORDATORIO', '', gen_random_uuid())");
  const e7 = await emitir();
  r.igual("E8 · El recordatorio llega solo a quien sigue pendiente (W09)", e7.nuevos.map(x => [x.token, x.tipo]), [[ANDROID_BARB, "RECORDATORIO"]]);
  await como(c, A, "select public.enviar_aviso_feria('e8_c', 'AVISO_FERIA', 'Llegar a las 9:00', gen_random_uuid())");
  const e8 = await emitir();
  r.igual("E8 · El mensaje a participantes llega a confirmadas y agregadas, no a la pendiente (W10)",
    e8.nuevos.map(x => x.token).sort(), [ANDROID_MARTA, ANDROID_TEJ].sort());
  await como(c, A, "select public.cancelar_feria('e8_c', 'lluvia')");
  const e9 = await emitir();
  r.igual("E8 · Cancelar avisa a invitadas, confirmadas y agregadas (W02)",
    e9.nuevos.filter(x => x.tipo === "FERIA_CANCELADA").map(x => x.token).sort(), [ANDROID_BARB, ANDROID_MARTA, ANDROID_TEJ].sort());

  // 4) Consentimiento, dejar de seguir, celular compartido y revalidación antes de entregar (A14).
  await feria("e8_d", "Feria Ocho D");
  await como(c, A, "select public.invitar_a_feria('e8_d', array['t_minimarket_rosa'], null, true)");   // anuncia a C2 (sigue la tienda)
  await como(c, C2, "select public.guardar_consentimiento(false)");
  const e10 = await emitir();
  r.igual("E8 · Retiró el consentimiento con el aviso aún pendiente: no sale", [de(e10.nuevos, ANDROID_C2).length, de(e10.nuevos, WEB_C2).length], [0, 0]);
  await como(c, C2, "select public.guardar_consentimiento(true)");

  await feria("e8_e", "Feria Ocho E");
  plan[ANDROID_C2] = [503];   // la primera entrega al celular falla un momento
  await como(c, A, "select public.invitar_a_feria('e8_e', array['t_minimarket_rosa'], null, true)");
  // Antes de que llegue el reintento, la compradora deja de seguir: el reintento se revalida y no sale.
  const nAntes = intentosFcm.length;
  const parcial = await crearNotificador(CONFIG, { fetch: fetchLocal, log: () => {}, ahora: () => reloj,
    dormir: async (ms) => { await como(c, C2, "select public.dejar_de_seguir('TIENDA', 't_minimarket_rosa')"); await dormir(ms); } })
    .procesarHastaVaciar({ presupuestoMs: 40 * 60000 });
  const aC2 = intentosFcm.slice(nAntes).filter(x => x.token === ANDROID_C2);
  r.igual("E8 · Un envío pendiente se revalida antes de reintentarse: dejó de seguir y no se reintenta",
    [aC2.map(x => x.status), await uno("select estado || ':' || coalesce(motivo, '') from avisos where feria_id = 'e8_e' and usuario_id = $1", [C2])],
    [[503], "OMITIDO:YA_NO_SIGUE"]);
  r.ok("E8 · …y el emisor esperó el reintento en vez de terminar", parcial.esperas >= 1, parcial);
  await como(c, C2, "select public.seguir('TIENDA', 't_minimarket_rosa')");

  // Celular compartido: otra cuenta entra en el mismo teléfono antes del envío.
  await feria("e8_f", "Feria Ocho F");
  await como(c, A, "select public.invitar_a_feria('e8_f', array['t_minimarket_rosa'], null, true)");
  await como(c, V, "select public.registrar_dispositivo($1, 'ANDROID')", [ANDROID_C2]);
  const e11 = await emitir();
  r.igual("E8 · Celular compartido: tras cambiar de cuenta no recibe el aviso de la cuenta anterior",
    de(e11.nuevos, ANDROID_C2).length, 0);
  r.igual("E8 · …la cuenta anterior lo sigue recibiendo en su navegador", de(e11.nuevos, WEB_C2).map(x => x.feria), ["e8_f"]);
  await como(c, C2, "select public.registrar_dispositivo($1, 'ANDROID')", [ANDROID_C2]);

  // 5) Duplicados por evento y destino.
  await feria("e8_g", "Feria Ocho G");
  plan[WEB_C2] = [503];
  await como(c, A, "select public.invitar_a_feria('e8_g', array['t_minimarket_rosa'], null, true)");
  const idG = await uno("select id::text from avisos where feria_id = 'e8_g' and usuario_id = $1", [C2]);
  // La web abierta lo muestra primero (canal sin push, otro destino).
  const mostrables = (await como(c, C2, "select id::text from public.avisos_para_mostrar('web:pestana-compradora2', 10)")).rows.map(x => x.id);
  await como(c, C2, "select public.confirmar_aviso($1, 'web:pestana-compradora2', 'MOSTRADO')", [idG]);
  const e12 = await emitir();
  r.ok("E8 · La web abierta puede mostrar el aviso antes del push", mostrables.includes(idG), mostrables);
  r.igual("E8 · Verlo en el navegador no impide el push al Android", de(e12.nuevos, ANDROID_C2).map(x => x.feria), ["e8_g"]);
  r.igual("E8 · Falla temporal en un destino: el reintento va solo a ese destino; el Android no lo recibe dos veces",
    [intentosFcm.filter(x => x.aviso === idG && x.token === ANDROID_C2).length,
     intentosFcm.filter(x => x.aviso === idG && x.token === WEB_C2).map(x => x.status)], [1, [503, 200]]);
  r.igual("E8 · El fallo temporal se recuperó sin que nadie creara otro aviso", [e12.total.esperas >= 1, await uno("select estado from avisos where id = $1", [idG])], [true, "ENVIADO"]);

  // Firebase cae varias veces seguidas: se reintenta con espera creciente hasta 5 intentos y queda FALLIDO.
  await feria("e8_h", "Feria Ocho H");
  plan[ANDROID_TEJ] = [503, 503, 503, 503, 503];
  await como(c, A, "select public.invitar_a_feria('e8_h', array['t_tejidos_austral'], null, false)");
  const e13 = await emitir();
  r.igual("E8 · Falla persistente: 5 intentos y queda FALLIDO (sin reintentos infinitos)",
    [intentosFcm.filter(x => x.token === ANDROID_TEJ && x.status === 503).length >= 5,
     await uno("select estado from avisos where feria_id = 'e8_h' and tipo = 'INVITACION'"), await uno("select en_curso from envio_automatico")], [true, "FALLIDO", false]);
  r.ok("E8 · …esperando entre intentos (2, 4, 8 y 16 minutos virtuales)", e13.total.esperas >= 4, e13.total);

  // 6) Contacto (A15): bandeja siempre; notificación solo con avisos activos y dentro de horario.
  await como(c, M, "select public.guardar_avisos_contacto(true, '00:00', '00:00')");
  await como(c, null, "insert into contactos (tienda_id, nombre, mensaje) values ('t_minimarket_rosa', 'Vecino', 'Hola, ¿tiene pan?')");
  const e14 = await emitir();
  r.igual("E8 · Contacto con avisos activos y dentro de horario: notifica a la dueña", de(e14.nuevos, ANDROID_MARTA).map(x => [x.tipo, x.titulo]), [["CONTACTO", "Nuevo mensaje de Vecino"]]);
  const ahora = await uno("select public.hora_santiago()::text");
  const fuera = (await q("select ($1::time + interval '2 hours')::time::text d, ($1::time + interval '3 hours')::time::text h", [ahora]))[0];
  await como(c, M, "select public.guardar_avisos_contacto(true, $1, $2)", [fuera.d, fuera.h]);
  const disparosFuera = (await solicitudes()).length;
  await como(c, null, "insert into contactos (tienda_id, nombre, mensaje) values ('t_minimarket_rosa', 'Vecina', 'E8 fuera de horario')");
  r.igual("E8 · Contacto fuera de horario: queda en la bandeja, sin aviso pendiente ni disparo",
    [await uno("select count(*)::int from contactos where mensaje = 'E8 fuera de horario'"),
     await uno("select a.estado || ':' || a.motivo from avisos a join contactos k on a.clave = 'contacto:' || k.id where k.mensaje = 'E8 fuera de horario'"),
     (await solicitudes()).length], [1, "OMITIDO:FUERA_DE_HORARIO", disparosFuera]);
  await como(c, M, "select public.guardar_avisos_contacto(true, '00:00', '00:00')");   // vuelve a estar dentro de horario
  await emitir();
  r.igual("E8 · …y no se notifica después, aunque vuelva el horario permitido",
    llegados.filter(x => x.tipo === "CONTACTO" && x.titulo === "Nuevo mensaje de Vecina").length, 0);
  await como(c, M, "select public.guardar_avisos_contacto(false, '00:00', '00:00')");
  await como(c, null, "insert into contactos (tienda_id, nombre, mensaje) values ('t_minimarket_rosa', 'Vecino 2', 'E8 avisos apagados')");
  await emitir();
  r.igual("E8 · Contacto con avisos desactivados: se guarda sin notificar",
    [llegados.filter(x => x.titulo === "Nuevo mensaje de Vecino 2").length, await uno("select count(*)::int from contactos where mensaje = 'E8 avisos apagados'")], [0, 1]);

  // 7) Recuperación si un disparo se pierde (GitHub no inició la ejecución).
  await feria("e8_i", "Feria Ocho I");
  await c.query("update envio_automatico set en_curso = true, disparado_en = now(), latido_en = null");   // «ejecución viva»
  const dAntes = (await solicitudes()).length;
  await como(c, A, "select public.invitar_a_feria('e8_i', array['t_barberia_alerce'], null, true)");
  r.igual("E8 · Mientras la ejecución pedida está viva, no se pide otra", (await solicitudes()).length, dAntes);
  await como(c, V, "select count(*) from public.avisos_para_mostrar('web:pestana-vecina-0001', 10)");
  r.igual("E8 · Revisar avisos con una ejecución viva no pide otra", (await solicitudes()).length, dAntes);
  // Pasan 6 minutos sin señales: la ejecución se perdió.
  await c.query("update envio_automatico set disparado_en = now() - interval '6 minutes'");
  await c.query("update avisos set proximo_intento = now() - interval '6 minutes' where feria_id = 'e8_i' and estado = 'PENDIENTE'");
  await como(c, V, "select count(*) from public.avisos_para_mostrar('web:pestana-vecina-0001', 10)");
  r.igual("E8 · Disparo perdido: la siguiente revisión de la web o la app lo pide de nuevo (sin cron)", (await solicitudes()).length, dAntes + 1);
  await como(c, V, "select count(*) from public.avisos_para_mostrar('web:pestana-vecina-0001', 10)");
  r.igual("E8 · …una sola vez", (await solicitudes()).length, dAntes + 1);
  const e15 = await emitir();
  r.igual("E8 · El aviso retenido sale en esa ejecución", de(e15.nuevos, ANDROID_BARB).map(x => x.feria), ["e8_i"]);
  await como(c, V, "select count(*) from public.avisos_para_mostrar('web:pestana-vecina-0001', 10)");
  r.igual("E8 · Sin nada pendiente, revisar avisos no pide ejecuciones", (await solicitudes()).length, dAntes + 1);

  // Sin tiempo: el emisor se apaga dejando el reintento para el próximo disparo.
  await feria("e8_j", "Feria Ocho J");
  plan[ANDROID_MARTA] = [503];
  await como(c, A, "select public.invitar_a_feria('e8_j', array['t_minimarket_rosa'], null, false)");
  const e16 = await emitir(1);   // 1 minuto de presupuesto y el reintento es a los 2
  r.igual("E8 · Sin tiempo para el reintento: se apaga y lo deja pendiente", [e16.total.sin_tiempo, await uno("select en_curso from envio_automatico"),
    await uno("select estado from avisos where feria_id = 'e8_j' and tipo = 'INVITACION'")], [true, false, "PENDIENTE"]);
  const dSinTiempo = (await solicitudes()).length;
  await feria("e8_k", "Feria Ocho K");
  await como(c, A, "select public.invitar_a_feria('e8_k', array['t_barberia_alerce'], null, true)");
  r.igual("E8 · El siguiente aviso vuelve a despertar al emisor", (await solicitudes()).length, dSinTiempo + 1);
  const e17 = await emitir();
  r.igual("E8 · …que entrega el nuevo y el reintento pendiente", [de(e17.nuevos, ANDROID_BARB).map(x => x.feria), de(e17.nuevos, ANDROID_MARTA).map(x => x.feria)], [["e8_k"], ["e8_j"]]);

  // 9) Vigía con pg_cron (simulado): la PRIMERA petición a GitHub se pierde y nadie abre la web ni la app.
  r.igual("E8 · Sin pg_cron, programar el vigía no falla (queda anotado)", await uno("select public.programar_vigia(true)"), "SIN_PG_CRON");
  await c.query(`
    create schema if not exists cron;
    create table if not exists cron.job (jobid bigserial primary key, jobname text unique, schedule text, command text, username text default current_user);
    create or replace function cron.schedule(job_name text, schedule text, command text) returns bigint language sql as $$
      insert into cron.job (jobname, schedule, command) values (job_name, schedule, command)
      on conflict (jobname) do update set schedule = excluded.schedule, command = excluded.command returning jobid $$;
    create or replace function cron.unschedule(job_name text) returns boolean language plpgsql as $$
      begin delete from cron.job where jobname = job_name; return found; end $$;`);
  const trabajos = async () => q("select jobname, schedule, command from cron.job");
  // «Pasa un minuto»: pg_cron ejecuta el comando del trabajo, si existe (como en el servidor, sin sesión de nadie).
  const minuto = async () => { const t = await trabajos(); return t.length ? uno(t[0].command) : "SIN_TRABAJO"; };
  const pasan = async (min) => {
    await c.query("update envio_automatico set disparado_en = disparado_en - make_interval(mins => $1)", [min]);
    await c.query("update avisos set proximo_intento = proximo_intento - make_interval(mins => $1) where estado in ('PENDIENTE', 'ENVIANDO')", [min]);
  };
  r.igual("E8 · Con el vigía apagado (por defecto), un disparo no programa nada", await (async () => {
    await feria("e8_v0", "Feria Vigía Cero");
    await como(c, A, "select public.invitar_a_feria('e8_v0', array['t_barberia_alerce'], null, true)");
    const t = (await trabajos()).length; await emitir(); return t;
  })(), 0);
  await c.query("update envio_automatico set vigia_activa = true");
  r.ok("E8 · Ningún cliente ejecuta el vigía ni lo programa",
    !!(await error(como(c, V, "select public.vigilar_envio()"))) && !!(await error(como(c, A, "select public.programar_vigia(false)"))));

  // Recorrido 1 · invitación a la emprendedora, con la primera petición perdida.
  await feria("e8_v1", "Feria Vigía");
  const dV0 = (await solicitudes()).length;
  await como(c, A, "select public.invitar_a_feria('e8_v1', array['t_minimarket_rosa'], null, false)");
  r.igual("E8 · Al pedir la ejecución se programa el vigía (cada minuto, solo esta base)",
    [(await solicitudes()).length - dV0, await trabajos()], [1, [{ jobname: "alerce_vigia_envio", schedule: "* * * * *", command: "select public.vigilar_envio()" }]]);
  // La petición se pierde: GitHub nunca inicia la ejecución. Nadie usa la web ni la app.
  r.igual("E8 · Vigía al minuto: espera a que GitHub arranque, sin pedir otra", [await minuto(), (await solicitudes()).length - dV0], ["ESPERANDO_GITHUB", 1]);
  await pasan(6);
  r.igual("E8 · Pasados 5 min sin señales, el vigía repite la petición (sin otro usuario ni otro aviso)",
    [await minuto(), (await solicitudes()).length - dV0], ["DISPARADO", 2]);
  await pasan(6);
  r.igual("E8 · Si también se pierde, espera más antes de repetir (10 min)", [await minuto(), (await solicitudes()).length - dV0], ["ESPERANDO_GITHUB", 2]);
  await pasan(5);
  r.igual("E8 · …y repite a los 10 min", [await minuto(), (await solicitudes()).length - dV0], ["DISPARADO", 3]);
  const v1 = await emitir();   // por fin corre una ejecución
  r.igual("E8 · Recorrido 1: llega «Te invitaron a Feria Vigía» al Android de la emprendedora",
    de(v1.nuevos, ANDROID_MARTA).map(x => [x.titulo, x.feria]), [["Te invitaron a Feria Vigía", "e8_v1"]]);
  r.igual("E8 · Cola vacía: el vigía se borra solo (no queda ninguna revisión programada)", [await trabajos(), await minuto()], [[], "SIN_TRABAJO"]);
  r.igual("E8 · La ejecución que dio señales reinicia la espera creciente", await uno("select reintentos_disparo from envio_automatico"), 0);
  await como(c, M, "select public.responder_invitacion('e8_v1', true)");
  r.igual("E8 · Recorrido 1: «Participo» deja el checklist en «Confirmada»",
    (await como(c, A, "select invitacion from public.checklist_feria('e8_v1') where tienda_id = 't_minimarket_rosa'")).rows.map(x => x.invitacion), ["CONFIRMADA"]);

  // Recorrido 2 · compradora que sigue la tienda (consintió antes): esa confirmación ya pidió una ejecución.
  r.igual("E8 · La confirmación vuelve a programar el vigía", (await trabajos()).length, 1);
  await pasan(6);   // y esa petición también se pierde
  r.igual("E8 · El vigía la repite", await minuto(), "DISPARADO");
  const v2 = await emitir();
  r.igual("E8 · Recorrido 2 (tienda): push al Android de la compradora con el nombre de la feria",
    de(v2.nuevos, ANDROID_C2).map(x => [x.titulo, x.feria]), [["Minimarket Doña Marta estará en Feria Vigía", "e8_v1"]]);
  r.igual("E8 · Recorrido 2 (tienda): y a su navegador registrado", de(v2.nuevos, WEB_C2).map(x => x.titulo), ["Minimarket Doña Marta estará en Feria Vigía"]);
  r.igual("E8 · Recorrido 2: la web abierta también lo puede mostrar (W18, sin push)",
    (await como(c, C2, "select titulo from public.avisos_para_mostrar('web:otra-pestana-c2', 20) where feria_id = 'e8_v1'")).rows.map(x => x.titulo),
    ["Minimarket Doña Marta estará en Feria Vigía"]);
  // Recorrido 2 · compradora que sigue la asociación.
  await feria("e8_v2", "Feria Vigía Dos");
  await como(c, A, "select public.invitar_a_feria('e8_v2', null, array['as_e8'], false)");
  await emitir();
  await como(c, B, "select public.responder_invitacion('e8_v2', true)");
  const v3 = await emitir();
  r.igual("E8 · Recorrido 2 (asociación): push con el nombre de la feria a quien sigue la asociación",
    de(v3.nuevos, ANDROID_V).map(x => [x.titulo, x.feria]), [["Asociación Ocho estará en Feria Vigía Dos", "e8_v2"]]);
  r.igual("E8 · Cola vacía otra vez: sin trabajo programado", (await trabajos()).length, 0);

  // Reintento de Firebase que quedó para después (el emisor se quedó sin tiempo): el vigía lo retoma.
  await feria("e8_v3", "Feria Vigía Tres");
  plan[ANDROID_MARTA] = [503];
  await como(c, A, "select public.invitar_a_feria('e8_v3', array['t_minimarket_rosa'], null, false)");
  const v4 = await emitir(1);
  r.igual("E8 · Sin tiempo y con un reintento pendiente: el vigía sigue programado", [v4.total.sin_tiempo, (await trabajos()).length], [true, 1]);
  r.igual("E8 · Mientras el reintento no toca, el vigía no pide nada", [await minuto(), await uno("select count(*)::int from net.solicitudes")],
    ["ESPERANDO_REINTENTO", (await solicitudes()).length]);
  await pasan(3);
  r.igual("E8 · Cuando toca, el vigía pide la ejecución", await minuto(), "DISPARADO");
  const v5 = await emitir();
  r.igual("E8 · …y el reintento llega", de(v5.nuevos, ANDROID_MARTA).map(x => x.feria), ["e8_v3"]);
  r.igual("E8 · …y el vigía se borra", (await trabajos()).length, 0);

  // Apagar el vigía con un trabajo programado: en su siguiente minuto se borra.
  await feria("e8_v4", "Feria Vigía Cuatro");
  await como(c, A, "select public.invitar_a_feria('e8_v4', array['t_barberia_alerce'], null, true)");
  await c.query("update envio_automatico set vigia_activa = false");
  r.igual("E8 · Vigía apagado: en su siguiente minuto se borra", [await minuto(), (await trabajos()).length], ["APAGADO", 0]);
  await emitir();
  const diagV = await uno("select public.estado_envio_automatico()");
  r.ok("E8 · El diagnóstico informa pg_cron y si el vigía está programado", diagV.pg_cron === true && diagV.vigia_programado === false && diagV.vigia_activa === false, diagV);

  // 8) Sin avisos nuevos fuera de la pauta.
  await c.query("update tiendas set calificaciones_activas = true where id = 't_minimarket_rosa'");
  const n0 = await uno("select count(*)::int from avisos");
  const errCalificar = await error(como(c, C3, "select public.calificar('t_minimarket_rosa', null, 5, 'bien')"));
  r.igual("E8 · Calificar funciona y no genera avisos", [errCalificar, (await uno("select count(*)::int from avisos")) - n0], [null, 0]);

  // Compatibilidad: el emisor con una base SIN 08 procesa un lote y termina (como antes).
  const sin08 = await crearNotificador(CONFIG, { fetch: async (url, op) => /latido_envio|cerrar_envio/.test(url) ? resp(404, { code: "PGRST202" }) : fetchLocal(url, op),
    log: () => {}, ahora: () => reloj, dormir }).procesarHastaVaciar();
  r.igual("E8 · Emisor nuevo con una base sin 08: procesa un lote y termina", [sin08.modo, sin08.pasadas], ["un_lote (08 sin aplicar)", 1]);

  // Reversión: deja 07 tal cual y se puede volver a aplicar 08.
  const leer = f => fs.readFileSync(path.join(RAIZ, "supabase", f), "utf8");
  const def07 = async () => uno("select md5(regexp_replace(pg_get_functiondef('public.avisos_para_mostrar(text,int)'::regprocedure), '\\s+', ' ', 'g'))");
  // Con el vigía programado en el momento de revertir.
  await c.query("update envio_automatico set vigia_activa = true");
  await c.query("select public.programar_vigia(true)");
  await c.query(leer("08_revertir.sql"));
  const tras = await q(`select to_regclass('public.envio_automatico') t,
      (select count(*)::int from pg_trigger where tgname = 'avisos_despertar_emisor') g,
      (select count(*)::int from migraciones_aplicadas where nombre = '08_envio_automatico') m,
      (select count(*)::int from cron.job) j,
      (select count(*)::int from pg_proc where proname in ('vigilar_envio', 'programar_vigia', 'solicitar_envio', 'latido_envio', 'cerrar_envio')) f`);
  r.igual("E8 · 08_revertir quita tabla, trigger, funciones, trabajo del vigía y registro", tras[0], { t: null, g: 0, m: 0, j: 0, f: 0 });
  const huellaRevertida = await def07();
  const avisosTrasRevertir = (await como(c, C2, "select count(*)::int n from public.avisos_para_mostrar('web:pestana-compradora2', 10)")).rows[0].n;
  r.ok("E8 · Tras revertir, la web sigue pudiendo pedir sus avisos", avisosTrasRevertir >= 0);
  // Se aplica SOLO la definición de avisos_para_mostrar de 07 (no se repite la migración) y se compara.
  const inicio07 = leer("07_ferias_seguidores.sql").indexOf("create or replace function public.avisos_para_mostrar");
  const fin07 = leer("07_ferias_seguidores.sql").indexOf("$$;", inicio07) + 3;
  await c.query(leer("07_ferias_seguidores.sql").slice(inicio07, fin07));
  r.igual("E8 · avisos_para_mostrar revertida es idéntica a la de 07", await def07(), huellaRevertida);
  await c.query(leer("08_envio_automatico.sql"));
  r.igual("E8 · Después de revertir, 08 se vuelve a aplicar", await uno("select activo from envio_automatico"), false);
};
