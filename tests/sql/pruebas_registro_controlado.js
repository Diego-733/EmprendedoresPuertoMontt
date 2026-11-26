"use strict";
// 11_registro_controlado.sql: registro controlado de emprendedoras (RN-01) con código de activación.
// Solo PostgreSQL local y desechable: no toca Supabase real.
const { ejecutarArchivo } = require("./postgres_local");
const { UID, como, error } = require("./ayudas");

const U = {
  nueva: "00000000-0000-0000-0000-0000000000e1",
  intrusa: "00000000-0000-0000-0000-0000000000e2",
  falsa: "00000000-0000-0000-0000-0000000000e3",
  antigua: "00000000-0000-0000-0000-0000000000e4",
  legado: "00000000-0000-0000-0000-0000000000e5",
  agotada: "00000000-0000-0000-0000-0000000000e6",
  ocupante: "00000000-0000-0000-0000-0000000000e7",
  sesiones: "00000000-0000-0000-0000-0000000000e8",
  tardia: "00000000-0000-0000-0000-0000000000e9"
};

async function alta(c, id, email, meta = {}) {
  await c.query("insert into auth.users (id, email, raw_user_meta_data) values ($1, $2, $3::jsonb)", [id, email, JSON.stringify(meta)]);
}
const perfil = async (c, id) => (await c.query("select rol, tienda_id from perfiles where id = $1", [id])).rows[0];
const una = async (c, uid, sql, p) => (await como(c, uid, sql, p)).rows[0];

module.exports = async function pruebasRegistroControlado(c, r) {
  // Datos de antes de 11 (incluye una emprendedora creada por la vía abierta de 04).
  await alta(c, U.antigua, "antigua@correo.cl", { tipo_cuenta: "EMPRENDEDORA", nombre: "Antigua" });
  const antigua04 = await perfil(c, U.antigua);
  r.igual("Antes de 11: la vía abierta de 04 convertía a cualquiera en emprendedora (el problema)", antigua04.rol, "EMPRENDEDORA");
  const huella = async () => (await c.query(`select md5(coalesce((select string_agg(id::text || rol || coalesce(tienda_id, '-'), '|' order by id) from perfiles), '')
      || '#' || coalesce((select string_agg(id || coalesce(owner_id::text, '-') || activa::text, '|' order by id) from tiendas), '')
      || '#' || (select count(*) from productos) || '#' || (select count(*) from ventas)
      || '#' || (select count(*) from participaciones) || '#' || (select count(*) from seguimientos)) h`)).rows[0].h;
  const antes = await huella();

  await ejecutarArchivo(c, "supabase/11_registro_controlado.sql");
  await ejecutarArchivo(c, "supabase/11_registro_controlado.sql");
  r.ok("11 se puede ejecutar dos veces", true);
  r.igual("11 conserva perfiles, roles, tiendas, productos, ventas, participaciones y seguimientos", await huella(), antes);
  r.igual("11 no cambia el rol de la emprendedora antigua", (await perfil(c, U.antigua)).rol, "EMPRENDEDORA");

  // --- Registro: los metadatos ya no dan permisos ---
  await alta(c, U.falsa, "falsa@correo.cl", { tipo_cuenta: "EMPRENDEDORA", rol: "ADMIN" });
  r.igual("Registrarse pidiendo ser emprendedora (o admin) en los metadatos deja una cuenta compradora sin tienda",
    await perfil(c, U.falsa), { rol: "VISITANTE", tienda_id: null });
  r.falla("Una compradora no se convierte en emprendedora con crear_mi_tienda",
    await error(como(c, U.falsa, "select public.crear_mi_tienda('Mi tienda')")), "NO_AUTORIZADA");
  r.igual("…y su perfil queda igual", (await perfil(c, U.falsa)).rol, "VISITANTE");
  r.ok("Una compradora no puede cambiar su propio rol por la API",
    (await como(c, U.falsa, "update perfiles set rol = 'EMPRENDEDORA' where id = auth.uid()")).rowCount === 0);
  r.falla("Una compradora no puede autorizarse insertando en cuentas_autorizadas",
    await error(como(c, U.falsa, "insert into cuentas_autorizadas (email, rol) values ('falsa@correo.cl', 'EMPRENDEDORA')")), "row-level security");
  r.falla("Una compradora no puede crear una tienda directamente", await error(como(c, U.falsa,
    "insert into tiendas (id, owner_id, nombre) values ('t_falsa', auth.uid(), 'Falsa')")), "row-level security");

  // --- Autorizar: solo administración ---
  r.falla("Sin sesión no se puede autorizar", await error(como(c, null, "select public.autorizar_emprendedora('x@y.cl', 'X')")), "permission denied");
  r.falla("Una emprendedora no puede autorizar", await error(como(c, UID.marta, "select public.autorizar_emprendedora('x@y.cl', 'X')")), "SOLO_ADMIN");
  r.falla("Una compradora no puede autorizar", await error(como(c, U.falsa, "select public.autorizar_emprendedora('falsa@correo.cl', 'Yo')")), "SOLO_ADMIN");
  await c.query("insert into asociaciones (id, nombre) values ('as_11', 'Asociación 11') on conflict do nothing");
  const aut = (await una(c, UID.admin, "select public.autorizar_emprendedora('Nueva@Correo.cl', 'Nueva Emprendedora', 'as_11') r")).r;
  r.ok("Autorizar devuelve un código de 8 caracteres (XXXX-XXXX) y el estado PENDIENTE_REGISTRO",
    /^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$/.test(aut.codigo) && aut.estado === "PENDIENTE_REGISTRO", aut);
  const fila = (await c.query("select * from cuentas_autorizadas where email = 'nueva@correo.cl'")).rows[0];
  r.ok("El código no se guarda: solo su huella (y nunca en texto)", fila.codigo_hash && fila.codigo_hash.length === 64 &&
    !JSON.stringify(fila).includes(aut.codigo.replace("-", "")) && !JSON.stringify(fila).includes(aut.codigo));
  r.ok("La bitácora no guarda el código ni su huella",
    !(await c.query("select count(*)::int n from bitacora_admin where detalle::text like '%' || $1 || '%'", [fila.codigo_hash])).rows[0].n);
  r.falla("No se puede autorizar a quien ya es emprendedora", await error(como(c, UID.admin, "select public.autorizar_emprendedora('donamarta@alerce.app', 'Marta')")), "YA_ES_EMPRENDEDORA");
  r.falla("No se puede convertir a una administradora en emprendedora", await error(como(c, UID.admin, "select public.autorizar_emprendedora('admin@alerce.app', 'A')")), "ES_ADMINISTRADOR");
  r.falla("Correo inválido", await error(como(c, UID.admin, "select public.autorizar_emprendedora('sin-arroba', 'A')")), "CORREO_INVALIDO");

  // --- Registro de la emprendedora autorizada y activación ---
  await alta(c, U.nueva, "nueva@correo.cl", { tipo_cuenta: "EMPRENDEDORA" });
  r.igual("Registrarse con un correo autorizado NO basta (correo autoconfirmado no prueba identidad)", (await perfil(c, U.nueva)).rol, "VISITANTE");
  r.igual("estado_mi_cuenta avisa que hay una autorización pendiente con código vigente",
    (await una(c, U.nueva, "select public.estado_mi_cuenta() e")).e, { rol: "VISITANTE", estado: "AUTORIZACION_PENDIENTE", codigo_vigente: true });
  r.igual("Código incorrecto: no activa y avisa los intentos que quedan",
    (await una(c, U.nueva, "select public.activar_con_codigo('AAAA-AAAA') r")).r, "CODIGO_INCORRECTO:4");
  r.igual("El intento fallido queda registrado", (await c.query("select intentos from cuentas_autorizadas where email = 'nueva@correo.cl'")).rows[0].intentos, 1);
  await alta(c, U.intrusa, "intrusa@correo.cl");
  r.falla("Otra cuenta no puede usar el código (queda ligado al correo de la sesión)",
    await error(como(c, U.intrusa, "select public.activar_con_codigo($1)", [aut.codigo])), "NO_AUTORIZADA");
  r.igual("Con el código correcto (aunque se escriba en minúsculas y sin guion) la cuenta queda activada",
    (await una(c, U.nueva, "select public.activar_con_codigo($1) r", [aut.codigo.toLowerCase().replace("-", "")])).r, "ACTIVADA");
  const pn = await perfil(c, U.nueva);
  r.ok("Queda emprendedora con una tienda propia", pn.rol === "EMPRENDEDORA" && pn.tienda_id &&
    (await c.query("select owner_id from tiendas where id = $1", [pn.tienda_id])).rows[0].owner_id === U.nueva, pn);
  r.igual("Su tienda se suma a la asociación indicada", (await c.query("select count(*)::int n from tienda_asociaciones where tienda_id = $1 and asociacion_id = 'as_11'", [pn.tienda_id])).rows[0].n, 1);
  r.igual("El código queda usado (sin huella) y anotado quién lo usó",
    (await c.query("select codigo_hash is null sin, usada_en is not null usada, usuario_id::text u from cuentas_autorizadas where email = 'nueva@correo.cl'")).rows[0],
    { sin: true, usada: true, u: U.nueva });
  r.igual("Activar otra vez responde YA_ACTIVA", (await una(c, U.nueva, "select public.activar_con_codigo($1) r", [aut.codigo])).r, "YA_ACTIVA");
  r.ok("Ya activada, puede vender: guardar un producto en su tienda", (await error(como(c, U.nueva,
    "insert into productos (id, tienda_id, nombre, precio, variantes) values ('p_nueva_11', $1, 'Producto', 1000, '[]')", [pn.tienda_id]))) === null);
  r.falla("No se puede revocar una autorización ya usada", await error(como(c, UID.admin, "select public.revocar_autorizacion('nueva@correo.cl')")), "YA_ACTIVADA");

  // --- Límite de intentos, vencimiento, regeneración y revocación ---
  const aut2 = (await una(c, UID.admin, "select public.autorizar_emprendedora('agotada@correo.cl', 'Agotada') r")).r;
  await alta(c, U.agotada, "agotada@correo.cl");
  for (let i = 0; i < 5; i++) await como(c, U.agotada, "select public.activar_con_codigo('ZZZZ-ZZZZ')");
  r.falla("Tras 5 intentos fallidos ni el código correcto sirve", await error(como(c, U.agotada, "select public.activar_con_codigo($1)", [aut2.codigo])), "DEMASIADOS_INTENTOS");
  const aut3 = (await una(c, UID.admin, "select public.regenerar_codigo_emprendedora('agotada@correo.cl') r")).r;
  r.falla("El código anterior deja de servir al regenerar", await error(como(c, U.agotada, "select public.activar_con_codigo($1)", [aut2.codigo]).then((x) => {
    if (x.rows[0].activar_con_codigo !== "ACTIVADA") throw new Error(x.rows[0].activar_con_codigo); return x; })), "CODIGO_INCORRECTO");
  await c.query("update cuentas_autorizadas set codigo_expira = now() - interval '1 minute' where email = 'agotada@correo.cl'");
  r.falla("Un código vencido no sirve", await error(como(c, U.agotada, "select public.activar_con_codigo($1)", [aut3.codigo])), "CODIGO_VENCIDO");
  r.falla("Una emprendedora no puede regenerar códigos", await error(como(c, UID.marta, "select public.regenerar_codigo_emprendedora('agotada@correo.cl')")), "SOLO_ADMIN");
  r.igual("Revocar una autorización sin usar la elimina",
    [(await una(c, UID.admin, "select public.revocar_autorizacion('agotada@correo.cl') r")).r,
     (await c.query("select count(*)::int n from cuentas_autorizadas where email = 'agotada@correo.cl'")).rows[0].n], ["REVOCADA", 0]);
  r.falla("Revocada, la cuenta no puede activarse", await error(como(c, U.agotada, "select public.activar_con_codigo($1)", [aut3.codigo])), "NO_AUTORIZADA");
  r.igual("…y queda compradora", (await perfil(c, U.agotada)).rol, "VISITANTE");

  // --- Autorizaciones antiguas sin código (clientes anteriores) ---
  await como(c, UID.admin, "insert into cuentas_autorizadas (email, rol, nombre) values ('legado@correo.cl', 'EMPRENDEDORA', 'Legado')");
  await alta(c, U.legado, "legado@correo.cl", { tipo_cuenta: "EMPRENDEDORA" });
  r.igual("Una autorización antigua (sin código) no activa sola al registrarse", (await perfil(c, U.legado)).rol, "VISITANTE");
  r.falla("…y pide que la administración genere un código", await error(como(c, U.legado, "select public.activar_con_codigo('ABCD-EFGH')")), "SIN_CODIGO");

  // --- Lista para la administración ---
  r.falla("La lista de cuentas es solo para administración", await error(como(c, UID.marta, "select * from public.listar_cuentas_emprendedoras()")), "SOLO_ADMIN");
  const lista = (await como(c, UID.admin, "select email, estado, autorizada, codigo_vigente from public.listar_cuentas_emprendedoras()")).rows;
  const de = (e) => lista.find((x) => x.email === e);
  r.igual("Lista: activada, pendiente de activación (sin código) y emprendedora antigua sin autorización",
    [de("nueva@correo.cl"), de("legado@correo.cl"), de("antigua@correo.cl")],
    [{ email: "nueva@correo.cl", estado: "ACTIVA", autorizada: true, codigo_vigente: false },
     { email: "legado@correo.cl", estado: "PENDIENTE_ACTIVACION", autorizada: true, codigo_vigente: false },
     { email: "antigua@correo.cl", estado: "ACTIVA", autorizada: false, codigo_vigente: false }]);
  await una(c, UID.admin, "select public.autorizar_emprendedora('sinregistro@correo.cl', 'Aún sin cuenta') r");
  r.igual("Lista: autorizada que aún no crea su cuenta",
    (await como(c, UID.admin, "select estado, codigo_vigente from public.listar_cuentas_emprendedoras() where email = 'sinregistro@correo.cl'")).rows[0],
    { estado: "PENDIENTE_REGISTRO", codigo_vigente: true });
  r.igual("Regularizar a la emprendedora antigua deja constancia sin cambiar su rol ni su tienda",
    [(await una(c, UID.admin, "select public.regularizar_emprendedora($1) r", [U.antigua])).r, await perfil(c, U.antigua),
     (await como(c, UID.admin, "select autorizada from public.listar_cuentas_emprendedoras() where email = 'antigua@correo.cl'")).rows[0].autorizada],
    ["REGULARIZADA", antigua04, true]);
  r.ok("La función anterior listar_emprendedoras sigue funcionando para apps antiguas",
    (await como(c, UID.admin, "select count(*)::int n from public.listar_emprendedoras()")).rows[0].n >= 3);

  // --- Cuenta desactivada: no recupera permisos por ninguna vía ---
  await como(c, UID.admin, "select public.cambiar_estado_emprendedora($1, false)", [U.nueva]);
  r.igual("Desactivada: estado_mi_cuenta lo dice", (await una(c, U.nueva, "select public.estado_mi_cuenta() e")).e, { rol: "EMPRENDEDORA", estado: "DESACTIVADA" });
  r.falla("Desactivada: no puede guardar productos (API directa)", await error(como(c, U.nueva,
    "update productos set nombre = 'x' where id = 'p_nueva_11'")), "CUENTA_DESACTIVADA");
  r.falla("Desactivada: no puede reactivar su tienda por la API", await error(como(c, U.nueva,
    "update tiendas set activa = true where owner_id = auth.uid()")), "CUENTA_DESACTIVADA");
  r.igual("…y la tienda sigue desactivada", (await c.query("select activa from tiendas where owner_id = $1", [U.nueva])).rows[0].activa, false);
  r.ok("Desactivada: crear_mi_tienda no le da otra tienda", (await error(como(c, U.nueva, "select public.crear_mi_tienda('Otra')"))) === null &&
    (await c.query("select count(*)::int n from tiendas where owner_id = $1", [U.nueva])).rows[0].n === 1);
  r.igual("Desactivada: el código usado no reactiva nada", (await una(c, U.nueva, "select public.activar_con_codigo($1) r", [aut.codigo])).r, "YA_ACTIVA");
  await como(c, UID.admin, "select public.cambiar_estado_emprendedora($1, true)", [U.nueva]);

  // --- Revisión 2.0: apropiación previa de la cuenta (correo autoconfirmado) ---
  // Un tercero crea ANTES una cuenta con el correo de la emprendedora (con su propia contraseña).
  await alta(c, U.ocupante, "victima@correo.cl");
  const autV = (await una(c, UID.admin, "select public.autorizar_emprendedora('victima@correo.cl', 'Víctima') r")).r;
  r.igual("Autorizar un correo que ya tenía cuenta lo informa (PENDIENTE_ACTIVACION)", autV.estado, "PENDIENTE_ACTIVACION");
  r.falla("Una cuenta creada ANTES del código no se activa aunque tenga el código correcto (podría ser de un tercero)",
    await error(como(c, U.ocupante, "select public.activar_con_codigo($1)", [autV.codigo])), "CUENTA_ANTERIOR");
  r.igual("…y no consume intentos ni da pistas del código", (await c.query("select intentos from cuentas_autorizadas where email = 'victima@correo.cl'")).rows[0].intentos, 0);
  r.igual("La lista marca esa cuenta como «anterior a la autorización»",
    (await como(c, UID.admin, "select cuenta_anterior from public.listar_cuentas_emprendedoras() where email = 'victima@correo.cl'")).rows[0].cuenta_anterior, true);
  r.falla("Solo la administración puede permitir una cuenta existente", await error(como(c, U.ocupante, "select public.permitir_cuenta_existente('victima@correo.cl')")), "SOLO_ADMIN");
  const autV2 = (await una(c, UID.admin, "select public.regenerar_codigo_emprendedora('victima@correo.cl') r")).r;
  r.falla("Regenerar el código no vuelve válida a la cuenta anterior", await error(como(c, U.ocupante, "select public.activar_con_codigo($1)", [autV2.codigo])), "CUENTA_ANTERIOR");
  r.igual("Con la confirmación en persona de la administración, esa cuenta sí se activa",
    [(await una(c, UID.admin, "select public.permitir_cuenta_existente('victima@correo.cl') r")).r,
     (await una(c, U.ocupante, "select public.activar_con_codigo($1) r", [autV2.codigo])).r], ["PERMITIDA", "ACTIVADA"]);

  // Una cuenta creada DESPUÉS del código sí se activa; al activar se cierran sus OTRAS sesiones.
  const autS = (await una(c, UID.admin, "select public.autorizar_emprendedora('sesiones@correo.cl', 'Sesiones') r")).r;
  await alta(c, U.sesiones, "sesiones@correo.cl");
  const S1 = "00000000-0000-0000-0000-00000000aa01", S2 = "00000000-0000-0000-0000-00000000aa02";
  await c.query("insert into auth.sessions (id, user_id) values ($1, $3), ($2, $3)", [S1, S2, U.sesiones]);
  await c.query("begin");
  await c.query("set local role authenticated");
  await c.query("select set_config('request.jwt.claim.sub', $1, true), set_config('request.jwt.claims', $2, true)",
    [U.sesiones, JSON.stringify({ sub: U.sesiones, session_id: S1 })]);
  const resS = (await c.query("select public.activar_con_codigo($1) r", [autS.codigo])).rows[0].r;
  await c.query("commit");
  r.igual("Al activar se conserva la sesión actual y se cierran las demás (quien entró antes pierde el acceso)",
    [resS, (await c.query("select array_agg(id::text) a from auth.sessions where user_id = $1", [U.sesiones])).rows[0].a], ["ACTIVADA", [S1]]);

  // El código: 8 símbolos del alfabeto sin confusiones, sin repetirse.
  const codigos = (await c.query("select public.generar_codigo_activacion() c from generate_series(1, 400)")).rows.map((x) => x.c);
  const simbolos = new Set(codigos.join("").replace(/-/g, "").split(""));
  r.ok("400 códigos: formato correcto, todos distintos y usan todo el alfabeto (azar del servidor, sin sesgo visible)",
    codigos.every((x) => /^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$/.test(x)) && new Set(codigos).size === 400 && simbolos.size === 31,
    { distintos: new Set(codigos).size, simbolos: simbolos.size });

  // --- Escalamiento y privacidad ---
  r.falla("Una emprendedora no puede crear administradores", await error(como(c, U.nueva, "select public.crear_admin('yo@correo.cl', 'Yo')")), "SOLO_ADMIN");
  r.falla("Una compradora no puede activar o desactivar tiendas", await error(como(c, U.falsa, "select public.cambiar_estado_emprendedora($1, false)", [UID.marta])), "SOLO_ADMIN");
  r.igual("La administración sigue sin ver ventas de las emprendedoras (RN-10)", (await como(c, UID.admin, "select count(*)::int n from ventas")).rows[0].n, 0);
  r.igual("Una emprendedora no ve ventas de otra", (await como(c, U.nueva, "select count(*)::int n from ventas where tienda_id = 't_minimarket_rosa'")).rows[0].n, 0);
  r.falla("activar_con_codigo exige sesión", await error(como(c, null, "select public.activar_con_codigo('ABCD-EFGH')")), "permission denied");
  r.falla("Las ayudas internas no se pueden llamar desde la API", await error(como(c, UID.admin, "select public.generar_codigo_activacion()")), "permission denied");

  // --- Reversión local y nueva aplicación ---
  const antesRev = await huella();
  await ejecutarArchivo(c, "supabase/11_revertir.sql");
  r.igual("Revertir 11 conserva todos los datos y roles (incluidas las cuentas activadas con código)", await huella(), antesRev);
  r.falla("Tras revertir, las funciones nuevas no existen", await error(como(c, UID.admin, "select public.listar_cuentas_emprendedoras()")), "does not exist");
  r.falla("Revertir NO reabre el registro libre: una compradora sigue sin poder crear tienda",
    await error(como(c, U.falsa, "select public.crear_mi_tienda('Vuelve 04')")), "NO_AUTORIZADA");
  await alta(c, U.tardia, "tardia@correo.cl", { tipo_cuenta: "EMPRENDEDORA" });
  r.igual("Revertido, los metadatos del registro siguen sin dar permisos", (await perfil(c, U.tardia)).rol, "VISITANTE");
  await c.query("insert into cuentas_autorizadas (email, rol, nombre) values ('tardia@correo.cl', 'EMPRENDEDORA', 'Tardía')");
  r.ok("Revertido, solo un correo autorizado por la administración puede abrir tienda (regla original de RN-01)",
    (await error(como(c, U.tardia, "select public.crear_mi_tienda('Tienda tardía')"))) === null && (await perfil(c, U.tardia)).rol === "EMPRENDEDORA");
  await ejecutarArchivo(c, "supabase/11_registro_controlado.sql");
  r.ok("11 se vuelve a aplicar después de revertir",
    (await c.query("select count(*)::int n from migraciones_aplicadas where nombre = '11_registro_controlado'")).rows[0].n === 1 &&
    (await error(como(c, U.falsa, "select public.crear_mi_tienda('Otra vez')"))).includes("NO_AUTORIZADA"));
};
