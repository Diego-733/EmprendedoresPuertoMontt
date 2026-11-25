"use strict";
// Entrega 8 · supabase/10_emprendedoras_y_tipos.sql
// RF-ADM-01: editar y desactivar cuentas de emprendedora desde el servidor (efecto real).
// RF-ADM-03: mantener los tipos de producto (crear, editar, desactivar, eliminar sin uso).
// Corre después de pruebas_administracion.js (09 aplicada) y sin la 08.
const { UID, como, error, guardarProducto } = require("./ayudas");
const { ejecutarArchivo } = require("./postgres_local");

module.exports = async function pruebasEmprendedorasTipos(c, r) {
  const A = UID.admin, M = UID.marta, B = UID.barberia, V = UID.vecina;
  const R = (n) => "E8-10 · " + n;
  const filas = async (uid, sql, p) => (await como(c, uid, sql, p)).rows;
  const uno = async (uid, sql, p) => { const f = (await como(c, uid, sql, p)).rows[0]; return f ? Object.values(f)[0] : null; };

  await ejecutarArchivo(c, "supabase/10_emprendedoras_y_tipos.sql");
  await ejecutarArchivo(c, "supabase/10_emprendedoras_y_tipos.sql");
  r.ok(R("10 se puede aplicar dos veces (después de 09)"), true);
  r.igual(R("Las tiendas existentes quedan activas"),
    (await c.query("select count(*)::int n from tiendas where not activa")).rows[0].n, 0);

  // ---------- RF-ADM-01: lista, permisos y edición ----------
  r.falla(R("Una emprendedora no lista cuentas"), await error(como(c, M, "select * from public.listar_emprendedoras()")), "SOLO_ADMIN");
  r.falla(R("Sin sesión no se edita"), await error(como(c, null, `select public.editar_emprendedora('${M}', 'X', 'Y')`)), "permission denied");
  r.falla(R("Una compradora no desactiva cuentas"), await error(como(c, V, `select public.cambiar_estado_emprendedora('${M}', false)`)), "SOLO_ADMIN");
  r.falla(R("Una emprendedora no desactiva a otra"), await error(como(c, B, `select public.cambiar_estado_emprendedora('${M}', false)`)), "SOLO_ADMIN");
  const lista = await filas(A, "select email, tienda_id, estado from public.listar_emprendedoras()");
  r.ok(R("La lista del servidor incluye a Marta activa con su tienda"),
    lista.some(x => x.email === "donamarta@alerce.app" && x.tienda_id === "t_minimarket_rosa" && x.estado === "ACTIVA"), lista);
  await como(c, A, "insert into cuentas_autorizadas (email, rol, nombre) values ('pendiente.e8@alerce.app', 'EMPRENDEDORA', 'Pendiente E8') on conflict do nothing");
  r.ok(R("Un correo autorizado sin registrar aparece PENDIENTE_REGISTRO"),
    (await filas(A, "select estado from public.listar_emprendedoras() where email = 'pendiente.e8@alerce.app'"))[0].estado === "PENDIENTE_REGISTRO");

  const nombreAntes = (await c.query("select nombre from tiendas where id = 't_minimarket_rosa'")).rows[0].nombre;
  const perfilAntes = (await c.query("select nombre from perfiles where id = $1", [M])).rows[0].nombre;
  r.falla(R("Nombre vacío rechazado"), await error(como(c, A, `select public.editar_emprendedora('${M}', '  ', 'Tienda')`)), "NOMBRE_INVALIDO");
  await como(c, A, `select public.editar_emprendedora('${M}', 'Doña Marta Soto', 'Minimarket Doña Marta (E8)')`);
  r.igual(R("Editar persiste en el perfil y en la tienda (se ve en la vitrina)"),
    [(await c.query("select nombre from perfiles where id = $1", [M])).rows[0].nombre,
     (await como(c, null, "select nombre from tiendas where id = 't_minimarket_rosa'")).rows[0].nombre],
    ["Doña Marta Soto", "Minimarket Doña Marta (E8)"]);

  // ---------- RF-ADM-01: desactivar con efecto real ----------
  const publicosAntes = await uno(null, "select count(*)::int from productos where tienda_id = 't_minimarket_rosa'");
  r.ok(R("Antes de desactivar, el público ve sus productos"), publicosAntes > 0, publicosAntes);
  r.igual(R("Desactivar devuelve DESACTIVADA"), await uno(A, `select public.cambiar_estado_emprendedora('${M}', false)`), "DESACTIVADA");
  r.igual(R("Desactivada: su tienda no se ve sin sesión ni para otras cuentas"),
    [await uno(null, "select count(*)::int from tiendas where id = 't_minimarket_rosa'"),
     await uno(V, "select count(*)::int from tiendas where id = 't_minimarket_rosa'")], [0, 0]);
  r.igual(R("Desactivada: sus productos no se ven en la vitrina"),
    await uno(null, "select count(*)::int from productos where tienda_id = 't_minimarket_rosa'"), 0);
  r.ok(R("Desactivada: ella sigue viendo su tienda y sus productos (nada se borra)"),
    (await uno(M, "select count(*)::int from productos where tienda_id = 't_minimarket_rosa'")) >= publicosAntes);
  r.falla(R("Desactivada: no guarda productos"),
    await error(guardarProducto(c, M, { id: "e8_bloqueado", nombre: "No debería", precio: 1000, categoria: "GOURMET",
      modalidad: "REGULAR", manejaStock: true, alAgotarse: "MOSTRAR_AGOTADO", publicado: true, disponible: true,
      variantes: [{ id: "u", nombre: "Única", stock: 1 }] })), "CUENTA_DESACTIVADA");
  r.falla(R("Desactivada: no registra ventas"),
    await error(como(c, M, `select public.registrar_venta('{"id":"v_e8_bloq","items":[{"productoId":"p_mini_1","varianteId":"v_min1","cantidad":1}]}'::jsonb)`)),
    "CUENTA_DESACTIVADA");
  r.falla(R("Desactivada: no cambia su tienda"),
    await error(como(c, M, "update tiendas set bio = 'intento' where id = 't_minimarket_rosa'")), "CUENTA_DESACTIVADA");
  r.igual(R("Desactivada: no puede reactivarse sola"),
    (await como(c, M, "update tiendas set activa = true where id = 't_minimarket_rosa'").catch(() => ({ rowCount: 0 }))).rowCount === 0 ||
    !(await c.query("select activa from tiendas where id = 't_minimarket_rosa'")).rows[0].activa, true);
  r.ok(R("Desactivada: el público no le escribe por el formulario"),
    !!(await error(como(c, null, "insert into contactos (tienda_id, nombre, mensaje) values ('t_minimarket_rosa', 'Vecina', 'Hola')"))));
  r.igual(R("La lista del servidor la muestra DESACTIVADA tras recargar"),
    (await filas(A, "select estado from public.listar_emprendedoras() where email = 'donamarta@alerce.app'"))[0].estado, "DESACTIVADA");
  r.igual(R("Reactivar devuelve todo como estaba"),
    [await uno(A, `select public.cambiar_estado_emprendedora('${M}', true)`),
     await uno(null, "select count(*)::int from productos where tienda_id = 't_minimarket_rosa'")], ["ACTIVA", publicosAntes]);
  await como(c, A, "select public.editar_emprendedora($1, $2, $3)", [M, perfilAntes, nombreAntes]);   // deja los nombres como estaban
  r.igual(R("Desactivar dos veces no cambia nada"), await uno(A, `select public.cambiar_estado_emprendedora('${M}', true)`), "SIN_CAMBIO_ACTIVA");
  const bit = (await filas(A, "select accion from public.ver_bitacora(50)")).map(x => x.accion);
  r.ok(R("Bitácora anota editar, desactivar y reactivar emprendedora"),
    ["Editó cuenta emprendedora", "Desactivó cuenta emprendedora", "Reactivó cuenta emprendedora"].every(a => bit.includes(a)), bit.slice(0, 8));

  // ---------- RF-ADM-03: tipos de producto ----------
  r.falla(R("Una emprendedora no crea tipos"),
    await error(como(c, M, "select public.guardar_tipo_producto('e8_velas', 'Velas', 'DECORACION')")), "SOLO_ADMIN");
  r.ok(R("Una emprendedora no escribe la tabla de tipos directamente"),
    !!(await error(como(c, M, "insert into tipos_producto (id, categoria, nombre) values ('e8_x', 'TEXTIL', 'X')"))) ||
    (await c.query("select count(*)::int n from tipos_producto where id = 'e8_x'")).rows[0].n === 0);
  r.falla(R("Categoría inválida rechazada"),
    await error(como(c, A, "select public.guardar_tipo_producto('e8_velas', 'Velas', 'OTRA')")), "CATEGORIA_INVALIDA");
  r.falla(R("Id inválido rechazado"),
    await error(como(c, A, "select public.guardar_tipo_producto('Velas Aromáticas', 'Velas', 'DECORACION')")), "ID_INVALIDO");
  await como(c, A, "select public.guardar_tipo_producto('e8_velas', 'Velas aromáticas', 'DECORACION', 5)");
  r.igual(R("Crear un tipo: lo ve la emprendedora en el alta (activos)"),
    (await filas(M, "select nombre, categoria from tipos_producto where id = 'e8_velas' and activo"))[0], { nombre: "Velas aromáticas", categoria: "DECORACION" });
  await como(c, A, "select public.guardar_tipo_producto('e8_velas', 'Velas de soya', 'DECORACION', 6)");
  r.igual(R("Editar un tipo persiste tras recargar"),
    (await filas(null, "select nombre, orden from tipos_producto where id = 'e8_velas'"))[0], { nombre: "Velas de soya", orden: 6 });
  // Un producto usa el tipo.
  await guardarProducto(c, M, { id: "e8_vela_1", nombre: "Vela de lavanda", precio: 3500, categoria: "DECORACION", tipo: "e8_velas",
    modalidad: "REGULAR", manejaStock: true, alAgotarse: "MOSTRAR_AGOTADO", publicado: true, disponible: true,
    variantes: [{ id: "u", nombre: "Única", stock: 3 }] });
  r.igual(R("El producto quedó con ese tipo"), (await c.query("select tipo from productos where id = 'e8_vela_1'")).rows[0].tipo, "e8_velas");
  r.igual(R("El uso por tipo cuenta sus productos (solo admin)"),
    (await filas(A, "select productos from public.uso_tipos_producto() where tipo_id = 'e8_velas'"))[0].productos, 1);
  r.falla(R("No se elimina un tipo en uso"), await error(como(c, A, "select public.eliminar_tipo_producto('e8_velas')")), "TIPO_EN_USO");
  await como(c, A, "select public.guardar_tipo_producto('e8_velas', 'Velas de soya', 'DECORACION', 6, null, false)");
  r.igual(R("Desactivar un tipo en uso: deja de ofrecerse, pero el producto lo conserva y sigue visible"),
    [await uno(M, "select count(*)::int from tipos_producto where id = 'e8_velas' and activo"),
     (await c.query("select tipo from productos where id = 'e8_vela_1'")).rows[0].tipo,
     await uno(null, "select count(*)::int from productos where id = 'e8_vela_1'")], [0, "e8_velas", 1]);
  await como(c, A, "select public.guardar_tipo_producto('e8_sin_uso', 'Sin uso', 'TEXTIL')");
  r.igual(R("Un tipo sin uso se elimina"), await uno(A, "select public.eliminar_tipo_producto('e8_sin_uso')"), "ELIMINADO");
  const bit2 = (await filas(A, "select accion from public.ver_bitacora(80)")).map(x => x.accion);
  r.ok(R("Bitácora anota crear, editar y eliminar tipos (disparador de 09)"),
    ["Creó tipo de producto", "Editó tipo de producto", "Eliminó tipo de producto"].every(a => bit2.includes(a)), bit2.slice(0, 10));

  // ---------- Revertir 10 y volver a aplicarla ----------
  await como(c, A, `select public.cambiar_estado_emprendedora('${M}', false)`);
  await ejecutarArchivo(c, "supabase/10_revertir.sql");
  r.igual(R("Revertir 10: la tienda vuelve a verse, sin perder productos ni tipos"),
    [await uno(null, "select count(*)::int from tiendas where id = 't_minimarket_rosa'"),
     (await c.query("select count(*)::int n from productos where id = 'e8_vela_1'")).rows[0].n,
     (await c.query("select count(*)::int n from tipos_producto where id = 'e8_velas'")).rows[0].n,
     (await c.query("select count(*)::int n from information_schema.columns where table_name = 'tiendas' and column_name = 'activa'")).rows[0].n],
    [1, 1, 1, 0]);
  await ejecutarArchivo(c, "supabase/10_emprendedoras_y_tipos.sql");
  r.igual(R("Tras revertir se vuelve a aplicar (todas activas)"),
    (await c.query("select count(*)::int n from tiendas where not activa")).rows[0].n, 0);
};
