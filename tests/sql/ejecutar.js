"use strict";
// Ejecuta la batería SQL en PostgreSQL local y desechable (15 y 17 si están):
//   A) instalación limpia: stub + 01 + 02 + 03 + 04 + 05 (dos veces: idempotente)
//   B) actualización: esquema y datos del formato anterior, luego 03 + 04 + 05
// Uso: npm run test:sql   (opción: --version 17)
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const { PostgresLocal, versionesDisponibles, ejecutarArchivo, RAIZ } = require("./postgres_local");
const { crearUsuarios, como, error, UID, Registro } = require("./ayudas");
const pruebasEntrega5 = require("./pruebas_entrega5");
const pruebasEntrega6 = require("./pruebas_entrega6");
const pruebasEntrega7 = require("./pruebas_entrega7");
const pruebasEnvioAutomatico = require("./pruebas_envio_automatico");
const concurrencia = require("./concurrencia");

const COMMIT_ESQUEMA_ANTERIOR = "6859b8f";

async function instalacionLimpia(pg, r) {
  await pg.crearBase("limpia");
  const c = pg.cliente("limpia");
  await c.connect();
  try {
    for (const f of ["supabase/stub_local.sql", "supabase/01_esquema.sql", "supabase/02_datos_prueba.sql",
                     "supabase/03_migracion_fotos.sql", "supabase/04_registro_vendedores.sql", "supabase/05_catalogo_ventas.sql",
                     "supabase/06_vitrina_mapas.sql", "supabase/06_vitrina_mapas.sql", "supabase/06_datos_demo.sql"]) {
      await ejecutarArchivo(c, f);
    }
    // Como en la instancia del curso: Supabase concede por defecto todo a anon/authenticated en
    // tablas nuevas, y contactos tenía además lectura, edición y borrado para anon (respaldo 22-nov).
    await c.query("alter default privileges in schema public grant all on tables to anon, authenticated");
    await c.query("grant select, update, delete on public.contactos to anon");
    for (const f of ["supabase/07_ferias_seguidores.sql", "supabase/07_ferias_seguidores.sql"]) await ejecutarArchivo(c, f);
    // Supabase del curso concede estos privilegios por defecto; RLS no protege TRUNCATE.
    await c.query("grant truncate, references, trigger on all tables in schema public to anon, authenticated");
    await ejecutarArchivo(c, "supabase/05_catalogo_ventas.sql");
    r.ok("05 se puede ejecutar dos veces", true);
    await ejecutarArchivo(c, "supabase/07_ferias_seguidores.sql");
    r.ok("07 se puede ejecutar después de reaplicar 05", true);
    const privilegiosAmplios = (await c.query(`select count(*)::int n from pg_tables t
      cross join (values ('anon'), ('authenticated')) r(rol)
      where t.schemaname = 'public' and (
        has_table_privilege(r.rol, format('%I.%I', t.schemaname, t.tablename), 'TRUNCATE') or
        has_table_privilege(r.rol, format('%I.%I', t.schemaname, t.tablename), 'TRIGGER') or
        has_table_privilege(r.rol, format('%I.%I', t.schemaname, t.tablename), 'REFERENCES'))`)).rows[0].n;
    r.igual("Clientes sin permisos de vaciar tablas ni cambiar su estructura", privilegiosAmplios, 0);

    const salida = await ejecutarArchivo(c, "supabase/pruebas_rls.sql");
    const filas = [].concat(salida).filter(x => x && x.fields && x.fields.some(f => f.name === "resultado")).flatMap(x => x.rows);
    for (const f of filas) r.ok("pruebas_rls.sql · " + f.resultado.slice(6), f.resultado.startsWith("OK"));
    r.ok("pruebas_rls.sql produjo resultados", filas.length > 30, filas.length);

    // Contrato compartido: la función del servidor da el mismo estado que web y app.
    const contrato = require("../contrato/casos_estado.json");
    for (const caso of contrato.casos) {
      const p = caso.p;
      const res = await c.query("select public.estado_publico_producto($1, $2, $3, $4, $5, $6, $7::jsonb) e",
        [p.publicado, p.disponible, p.vendida ? new Date() : null, p.manejaStock, p.modalidad, p.alAgotarse,
         JSON.stringify(p.variantes.map((s, i) => ({ id: "v" + i, stock: s })))]);
      r.igual("Contrato de estado · " + caso.nombre, res.rows[0].e, caso.estado);
    }

    await crearUsuarios(c);
    await pruebasEntrega5(c, r);
    await pruebasEntrega6(c, r);
    await pruebasEntrega7(c, r);
    await pruebasEnvioAutomatico(c, r);
    await concurrencia(pg, "limpia", c, r);
  } finally {
    await c.end();
  }
}

async function actualizacion(pg, r) {
  await pg.crearBase("antigua");
  const c = pg.cliente("antigua");
  await c.connect();
  try {
    await ejecutarArchivo(c, "supabase/stub_local.sql");
    const esquemaAnterior = execFileSync("git", ["show", `${COMMIT_ESQUEMA_ANTERIOR}:supabase/01_esquema.sql`], { cwd: RAIZ, encoding: "utf8" });
    await c.query(esquemaAnterior);
    await ejecutarArchivo(c, "supabase/02_datos_prueba.sql");
    await crearUsuarios(c);
    // Datos como los dejaban los clientes anteriores.
    await como(c, UID.marta, `insert into ventas (id, tienda_id, fecha_local, items, medio_pago, total)
      values ('v_antigua', 't_minimarket_rosa', '2026-10-01 10:00', '[{"productoId":"p_mini_1","varianteId":"v_min1","nombre":"Pan","detalle":"Bolsa","precio":2500,"cantidad":1}]', 'EFECTIVO', 2500)`);
    await como(c, UID.marta, `insert into productos (id, tienda_id, nombre, categoria, precio, publicado, variantes, foto_url)
      values ('p_custom_1', 't_minimarket_rosa', 'Alta web antigua', 'GENERAL', 3000, true, '[{"nombre":"Unidad","precio":3000}]', 'data:image/png;base64,AAAA')`);
    const antes = (await c.query("select (select count(*) from productos)::int p, (select count(*) from ventas)::int v, (select count(*) from tiendas)::int t")).rows[0];

    for (const f of ["supabase/03_migracion_fotos.sql", "supabase/04_registro_vendedores.sql", "supabase/05_catalogo_ventas.sql",
                     "supabase/06_vitrina_mapas.sql"]) {
      await ejecutarArchivo(c, f);
    }
    // Datos como los dejó la entrega 6: una asociación por tienda, una feria y una participación local antigua.
    await c.query("insert into asociaciones (id, nombre) values ('as_e6', 'Asociación E6') on conflict do nothing");
    await c.query("update tiendas set asociacion_id = 'as_e6' where id in ('t_minimarket_rosa', 't_tejidos_austral')");
    await c.query("insert into ferias (id, nombre, fecha_inicio, fecha_fin, estado) values ('f_e6', 'Feria E6', current_date + 5, current_date + 5, 'proxima'), ('f_rara', 'Rara', current_date + 6, null, 'ACTIVA')");
    await c.query("insert into participaciones (feria_id, tienda_id, invitacion, asistencia) values ('f_e6', 't_minimarket_rosa', 'CONFIRMADA', 'PRESENTE')");
    const tiendasAntes = (await c.query("select md5(string_agg(id || coalesce(asociacion_id, '-') || actualizado_en::text, '|' order by id)) h from tiendas")).rows[0].h;
    // Procedimiento seguro: diagnóstico y respaldo (solo lectura) antes de aplicar.
    const unaFila = (res) => [].concat(res).filter(x => x && x.rows && x.rows.length).pop().rows[0];
    const huellaDatos = async () => (await c.query(`select md5(coalesce((select string_agg(id || ':' || coalesce(estado, '-'), '|' order by id) from ferias), '')
        || '#' || coalesce((select string_agg(feria_id || tienda_id || invitacion || asistencia, '|' order by feria_id, tienda_id) from participaciones), '')
        || '#' || coalesce((select string_agg(id || coalesce(asociacion_id, '-'), '|' order by id) from tiendas), '')) h`)).rows[0].h;
    const datosAntes = await huellaDatos();
    const diagAntes = JSON.parse(unaFila(await ejecutarArchivo(c, "supabase/07_diagnostico.sql")).diagnostico_07);
    r.igual("Diagnóstico 07 (antes): detecta lo que se normalizaría y que 07 no existe",
      [diagAntes["07_aplicada"], diagAntes.ferias_que_se_normalizarian, diagAntes.participaciones_que_se_normalizarian, diagAntes.funciones_07_existentes, diagAntes.tablas_07_existentes],
      [false, 2, 1, [], []]);
    const respaldo = unaFila(await ejecutarArchivo(c, "supabase/07_respaldo.sql")).respaldo_previo_07;
    r.ok("Respaldo 07: incluye datos, políticas, permisos, triggers y restricciones",
      respaldo.datos.ferias.length >= 2 && respaldo.datos.participaciones.length === 1 && respaldo.definiciones.politicas.length > 0 &&
      respaldo.definiciones.permisos.length > 0 && respaldo.definiciones.triggers.length > 0 && respaldo.definiciones.restricciones.length > 0);
    r.igual("Diagnóstico y respaldo no modifican datos", await huellaDatos(), datosAntes);
    await ejecutarArchivo(c, "supabase/07_ferias_seguidores.sql");
    await ejecutarArchivo(c, "supabase/07_ferias_seguidores.sql");
    const diagDespues = JSON.parse(unaFila(await ejecutarArchivo(c, "supabase/07_diagnostico.sql")).diagnostico_07);
    r.igual("Diagnóstico 07 (después): aplicada, sin datos por normalizar y sin TRUNCATE para clientes",
      [diagDespues["07_aplicada"], diagDespues.ferias_que_se_normalizarian, diagDespues.participaciones_que_se_normalizarian, diagDespues.clientes_con_truncate],
      [true, 0, 0, 0]);
    const socias = (await c.query("select array_agg(tienda_id order by tienda_id) a from tienda_asociaciones where asociacion_id = 'as_e6'")).rows[0].a;
    r.igual("07 migra la asociación de cada tienda a la tabla de pertenencias", socias, ["t_minimarket_rosa", "t_tejidos_austral"]);
    r.igual("07 no modifica las tiendas existentes (ni su fecha de actualización)",
      (await c.query("select md5(string_agg(id || coalesce(asociacion_id, '-') || actualizado_en::text, '|' order by id)) h from tiendas")).rows[0].h, tiendasAntes);
    r.igual("07 normaliza estados antiguos de feria (minúsculas y desconocidos)",
      (await c.query("select array_agg(estado order by id) a from ferias where id in ('f_e6', 'f_rara')")).rows[0].a, ["PROXIMA", "BORRADOR"]);
    r.igual("07 conserva la participación y traduce la asistencia antigua",
      (await c.query("select invitacion, asistencia, origen from participaciones where feria_id = 'f_e6'")).rows[0], { invitacion: "CONFIRMADA", asistencia: "ASISTIO", origen: "DIRECTA" });

    // Recuperación: revertir, restaurar los datos del respaldo y volver a aplicar.
    await como(c, UID.vecina, "select public.seguir('TIENDA', 't_minimarket_rosa')");
    await ejecutarArchivo(c, "supabase/07_revertir.sql");
    r.ok("Tras revertir 07, el visitante vuelve a leer participaciones (comportamiento de la entrega 6)",
      (await como(c, null, "select count(*)::int n from participaciones")).rows[0].n >= 1);
    r.igual("Tras revertir 07, el APK anterior puede responder su participación directamente",
      (await como(c, UID.marta, "update participaciones set invitacion = 'CONFIRMADA' where feria_id = 'f_e6' and tienda_id = 't_minimarket_rosa'")).rowCount, 1);
    r.falla("Tras revertir 07, sus funciones no quedan disponibles para clientes",
      await error(como(c, UID.vecina, "select public.seguir('TIENDA', 't_minimarket_rosa')")), "permission denied");
    r.igual("Revertir conserva los datos nuevos (seguidores y pertenencias)",
      (await c.query("select (select count(*) from seguimientos)::int s, (select count(*) from tienda_asociaciones)::int p")).rows[0], { s: 1, p: 2 });
    r.ok("Revertir quita 07 del registro de migraciones",
      !(await c.query("select 1 from migraciones_aplicadas where nombre = '07_ferias_seguidores'")).rowCount);
    const restaurar = fs.readFileSync(path.join(RAIZ, "supabase/07_restaurar_datos.sql"), "utf8")
      .split("PEGAR_AQUI_EL_JSON_DEL_RESPALDO").join(JSON.stringify(respaldo));
    await c.query(restaurar);
    r.igual("Restaurar desde el respaldo deja los datos exactamente como antes de 07", await huellaDatos(), datosAntes);
    await ejecutarArchivo(c, "supabase/07_ferias_seguidores.sql");
    r.igual("07 se vuelve a aplicar después de revertir y restaurar",
      [(await c.query("select count(*)::int n from migraciones_aplicadas where nombre = '07_ferias_seguidores'")).rows[0].n,
       (await c.query("select estado from ferias where id = 'f_e6'")).rows[0].estado,
       (await como(c, null, "select count(*)::int n from public.participantes_feria('f_e6')")).rows[0].n],
      [1, "PROXIMA", 1]);
    const despues = (await c.query("select (select count(*) from productos)::int p, (select count(*) from ventas)::int v, (select count(*) from tiendas)::int t")).rows[0];
    r.igual("Se conservan productos, ventas y tiendas", despues, antes);
    const va = (await como(c, UID.marta, "select total, items->0->>'precio' precio, en_historial from ventas where id = 'v_antigua'")).rows[0];
    r.igual("La venta anterior sigue visible para su dueña y completa", va, { total: "2500", precio: "2500", en_historial: true });
    const pa = (await c.query("select variantes->0->>'id' id, modalidad, maneja_stock, disponible, al_agotarse from productos where id = 'p_custom_1'")).rows[0];
    r.igual("Producto antiguo: id de opción asignado y valores por defecto", pa, { id: "p_custom_1_v1", modalidad: "REGULAR", maneja_stock: true, disponible: true, al_agotarse: "MOSTRAR_AGOTADO" });
    const visibles = (await como(c, null, "select count(*)::int n from productos where id like 'p\\_%' and tienda_id like 't\\_%' and publicado")).rows[0].n;
    r.ok("Los productos publicados con unidades siguen visibles sin sesión", visibles >= 14, visibles);
    const migr = (await c.query("select nombre from migraciones_aplicadas")).rows.map(x => x.nombre);
    r.igual("Quedan registradas las migraciones", migr.sort(), ["05_catalogo_ventas", "06_vitrina_mapas", "07_ferias_seguidores"]);
    await pruebasEntrega5(c, r);

    // Diagnóstico de solo lectura: se ejecuta completo sin errores ni cambios.
    const conteo = async () => (await c.query("select (select count(*) from productos)::int p, (select count(*) from ventas)::int v, md5(string_agg(id || variantes::text, '|' order by id)) h from productos")).rows[0];
    const antesDiag = await conteo();
    await ejecutarArchivo(c, "supabase/diagnostico_solo_lectura.sql");
    r.igual("El diagnóstico no modifica datos", await conteo(), antesDiag);

    // Reversión parcial: conserva datos y devuelve el camino antiguo.
    await ejecutarArchivo(c, "supabase/05_revertir.sql");
    r.igual("La reversión conserva productos, ventas y cantidades", await conteo(), antesDiag);
    await como(c, UID.marta, "insert into ventas (id, tienda_id, total, items) values ('v_tras_revertir', 't_minimarket_rosa', 1, '[]')");
    r.ok("Tras revertir, el APK 1.2 vuelve a poder subir ventas", true);
    // Volver a aplicar 05 sobre la base revertida (con datos) funciona.
    await ejecutarArchivo(c, "supabase/05_catalogo_ventas.sql");
    const otra = await como(c, UID.marta, "select public.registrar_venta('{\"id\":\"v_reaplicada\",\"items\":[{\"productoId\":\"p_mini_1\",\"varianteId\":\"v_min1\",\"cantidad\":1}]}'::jsonb) r");
    r.igual("05 se puede volver a aplicar después de revertir", otra.rows[0].r.estado, "REGISTRADA");
  } finally {
    await c.end();
  }
}

/** La migración 07 aplicada en una transacción: si algo falla, no queda nada a medias. */
async function transaccion07(pg, r) {
  await pg.crearBase("transaccion");
  const c = pg.cliente("transaccion");
  await c.connect();
  try {
    for (const f of ["supabase/stub_local.sql", "supabase/01_esquema.sql", "supabase/02_datos_prueba.sql", "supabase/03_migracion_fotos.sql",
                     "supabase/04_registro_vendedores.sql", "supabase/05_catalogo_ventas.sql", "supabase/06_vitrina_mapas.sql"]) {
      await ejecutarArchivo(c, f);
    }
    const sql07 = fs.readFileSync(path.join(RAIZ, "supabase/07_ferias_seguidores.sql"), "utf8");
    const fallo = await c.query("begin; set local lock_timeout = '5s'; set local statement_timeout = '120s';\n" + sql07 + "\n;select 1/0;").then(() => null, e => e.message);
    await c.query("rollback");
    r.ok("07 en transacción: un error al final deshace todo", !!fallo &&
      !(await c.query("select to_regclass('public.avisos') t")).rows[0].t &&
      !(await c.query("select 1 from migraciones_aplicadas where nombre = '07_ferias_seguidores'")).rowCount, fallo);
    await c.query("begin; set local lock_timeout = '5s'; set local statement_timeout = '120s';\n" + sql07 + "\n;commit;");
    r.ok("07 en transacción: aplicada y confirmada", (await c.query("select 1 from migraciones_aplicadas where nombre = '07_ferias_seguidores'")).rowCount === 1);
  } finally {
    await c.end();
  }
}

(async () => {
  const pedida = process.argv.includes("--version") ? Number(process.argv[process.argv.indexOf("--version") + 1]) : null;
  const versiones = versionesDisponibles().filter(v => !pedida || v.version === pedida);
  if (!versiones.length) {
    console.error("No hay binarios de PostgreSQL. Ejecuta `npm install` (PG17) y revisa docs/desarrollo-y-pruebas.md (PG15).");
    process.exit(2);
  }
  let fallas = 0;
  let total = 0;
  for (const v of versiones) {
    const pg = new PostgresLocal({ version: v.version, bin: v.bin, puerto: 54300 + v.version });
    const r = new Registro(`PG${v.version}`);
    await pg.iniciar();
    try {
      const rl = new Registro(`PG${v.version} limpia`);
      await instalacionLimpia(pg, rl).catch(e => rl.ok("instalación limpia sin errores", false, e.message));
      const ra = new Registro(`PG${v.version} actualización`);
      await actualizacion(pg, ra).catch(e => ra.ok("actualización sin errores", false, e.message));
      const rt = new Registro(`PG${v.version} transacción`);
      await transaccion07(pg, rt).catch(e => rt.ok("transacción 07 sin errores", false, e.message));
      r.casos.push(...rl.casos, ...ra.casos, ...rt.casos);
    } finally {
      await pg.detener();
    }
    for (const caso of r.casos) {
      total++;
      if (!caso.ok) fallas++;
      console.log(`${caso.ok ? "OK   " : "FALLA"} ${caso.nombre}${caso.ok ? "" : "  → " + caso.detalle}`);
    }
  }
  console.log(`\n${total - fallas} de ${total} casos correctos (${versiones.map(v => "PG" + v.version).join(", ")})`);
  process.exit(fallas ? 1 : 0);
})();
