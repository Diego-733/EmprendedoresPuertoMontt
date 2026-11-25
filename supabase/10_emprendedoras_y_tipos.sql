-- =====================================================================
-- Entrega 8 · 10_emprendedoras_y_tipos.sql
-- RF-ADM-01: el administrador edita y desactiva cuentas de emprendedora desde el servidor.
-- RF-ADM-03: el administrador mantiene los tipos de producto (crear, editar, desactivar,
--            eliminar solo si ningún producto lo usa).
--
-- · Requiere 01–07 y 09 (usa la bitácora de 09). NO depende de 08.
-- · Orden: 09_administracion.sql → 10_emprendedoras_y_tipos.sql. Se puede ejecutar dos veces.
-- · Para deshacerla: 10_revertir.sql (antes que 09_revertir.sql, si también se revierte 09).
--
-- Efecto real de desactivar una emprendedora (tiendas.activa = false):
--  · Su tienda y sus productos dejan de verse en la vitrina pública, en el directorio
--    y en la ficha de las ferias.
--  · Ella y cualquier cliente dejan de poder cambiar su tienda, sus productos o sus ventas,
--    responder invitaciones o recibir mensajes del formulario (CUENTA_DESACTIVADA).
--  · No se borra nada: al reactivarla, todo vuelve tal como estaba. Puede iniciar sesión
--    (Auth es del servidor del curso), pero no puede operar.
-- =====================================================================

-- ---------- 1. Columna de estado de la tienda ----------
alter table public.tiendas add column if not exists activa boolean not null default true;

-- La propia emprendedora no puede reactivarse: proteger_tienda (06) conserva columnas
-- protegidas; se agrega activa a esa protección con un disparador propio.
create or replace function public.proteger_activa_tienda()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.activa is distinct from old.activa and not public.es_admin() then
    new.activa := old.activa;
  end if;
  return new;
end;
$$;
drop trigger if exists proteger_activa_tienda on public.tiendas;
create trigger proteger_activa_tienda before update on public.tiendas
  for each row execute function public.proteger_activa_tienda();

-- ---------- 2. Bloqueo de operaciones de una cuenta desactivada ----------
create or replace function public.tienda_activa(p_tienda text)
returns boolean language sql stable security definer set search_path = public as $$
  select coalesce((select activa from public.tiendas where id = p_tienda), true);
$$;

create or replace function public.bloquear_cuenta_desactivada()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_tienda text;
begin
  if public.es_admin() then return coalesce(new, old); end if;
  if tg_table_name = 'tiendas' then
    if not old.activa then raise exception 'CUENTA_DESACTIVADA' using errcode = '42501'; end if;
    return new;
  end if;
  v_tienda := case when tg_op = 'DELETE' then old.tienda_id else new.tienda_id end;
  if not public.tienda_activa(v_tienda) then
    raise exception 'CUENTA_DESACTIVADA' using errcode = '42501';
  end if;
  return coalesce(new, old);
end;
$$;

do $$
declare t text; ops text;
begin
  foreach t in array array['productos', 'ventas', 'participaciones', 'contactos', 'tiendas'] loop
    if to_regclass('public.' || t) is not null then
      ops := case t when 'tiendas' then 'update' when 'participaciones' then 'update' else 'insert or update or delete' end;
      execute format('drop trigger if exists bloquear_cuenta_desactivada on public.%I', t);
      execute format('create trigger bloquear_cuenta_desactivada before %s on public.%I
                      for each row execute function public.bloquear_cuenta_desactivada()', ops, t);
    end if;
  end loop;
end $$;

-- ---------- 3. Lo público deja de mostrar tiendas desactivadas ----------
drop policy if exists tiendas_ver on public.tiendas;
create policy tiendas_ver on public.tiendas for select
  using (activa or owner_id = auth.uid() or public.es_admin());

drop policy if exists productos_ver on public.productos;
create policy productos_ver on public.productos
  for select using (
    (public.estado_publico_producto(publicado, disponible, vendida_en, maneja_stock, modalidad, al_agotarse, variantes) <> 'OCULTO'
     and public.tienda_activa(tienda_id))
    or public.es_duena_de(tienda_id)
  );

create or replace function public.participantes_feria(p_feria text)
returns table (tienda_id text, nombre text, rubro text, sector text, logo_url text, asociacion_id text)
language sql stable security definer set search_path = public as $$
  select t.id, t.nombre, t.rubro, t.sector, t.logo_url, pa.asociacion_id
    from public.participaciones pa
    join public.ferias f on f.id = pa.feria_id
    join public.tiendas t on t.id = pa.tienda_id
   where pa.feria_id = p_feria
     and pa.invitacion in ('CONFIRMADA', 'AGREGADA')
     and public.feria_publicada(f.estado)
     and t.activa
   order by t.nombre;
$$;
grant execute on function public.participantes_feria(text) to anon, authenticated;

-- ---------- 4. Cuentas de emprendedora (RF-ADM-01) ----------
create or replace function public.listar_emprendedoras()
returns table (usuario_id uuid, email text, nombre text, tienda_id text, tienda_nombre text,
               estado text, creado_en timestamptz)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  return query
  select p.id, p.email, p.nombre, t.id, t.nombre,
         case when t.id is null then 'SIN_TIENDA' when t.activa then 'ACTIVA' else 'DESACTIVADA' end,
         p.creado_en
    from public.perfiles p
    left join public.tiendas t on t.id = p.tienda_id and t.owner_id = p.id
   where p.rol = 'EMPRENDEDORA'
  union all
  select null::uuid, c.email, c.nombre, null, null, 'PENDIENTE_REGISTRO', c.creado_en
    from public.cuentas_autorizadas c
   where c.rol = 'EMPRENDEDORA'
     and not exists (select 1 from public.perfiles p where lower(p.email) = lower(c.email))
   order by 7 desc;
end;
$$;

create or replace function public.editar_emprendedora(p_usuario uuid, p_nombre text, p_tienda_nombre text)
returns void language plpgsql security definer set search_path = public as $$
declare v_nombre text := btrim(coalesce(p_nombre, '')); v_tn text := btrim(coalesce(p_tienda_nombre, '')); v_tienda text; v_email text;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  if v_nombre = '' or char_length(v_nombre) > 120 then raise exception 'NOMBRE_INVALIDO'; end if;
  if v_tn = '' or char_length(v_tn) > 120 then raise exception 'NOMBRE_TIENDA_INVALIDO'; end if;
  select p.tienda_id, p.email into v_tienda, v_email from public.perfiles p where p.id = p_usuario and p.rol = 'EMPRENDEDORA';
  if not found then raise exception 'NO_EXISTE'; end if;
  update public.perfiles set nombre = v_nombre where id = p_usuario;
  if v_tienda is not null then update public.tiendas set nombre = v_tn where id = v_tienda and owner_id = p_usuario; end if;
  perform public.anotar_bitacora('Editó cuenta emprendedora', 'cuenta', v_email,
    jsonb_build_object('nombre', v_nombre, 'tienda', v_tienda));
end;
$$;

create or replace function public.cambiar_estado_emprendedora(p_usuario uuid, p_activa boolean)
returns text language plpgsql security definer set search_path = public as $$
declare v_tienda text; v_email text; v_actual boolean;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  if p_activa is null then raise exception 'ESTADO_INVALIDO'; end if;
  select p.tienda_id, p.email into v_tienda, v_email from public.perfiles p where p.id = p_usuario and p.rol = 'EMPRENDEDORA';
  if not found then raise exception 'NO_EXISTE'; end if;
  select activa into v_actual from public.tiendas where id = v_tienda and owner_id = p_usuario for update;
  if not found then raise exception 'SIN_TIENDA'; end if;
  if v_actual = p_activa then return case when p_activa then 'SIN_CAMBIO_ACTIVA' else 'SIN_CAMBIO_DESACTIVADA' end; end if;
  update public.tiendas set activa = p_activa where id = v_tienda;
  perform public.anotar_bitacora(case when p_activa then 'Reactivó cuenta emprendedora' else 'Desactivó cuenta emprendedora' end,
    'cuenta', v_email, jsonb_build_object('tienda', v_tienda));
  return case when p_activa then 'ACTIVA' else 'DESACTIVADA' end;
end;
$$;

-- ---------- 5. Tipos de producto (RF-ADM-03) ----------
-- Lo escribe solo ADMIN (política tipos_admin de 05) y la bitácora lo anota (disparador de 09).
alter table public.tipos_producto add column if not exists actualizado_en timestamptz not null default now();

create or replace function public.uso_tipos_producto()
returns table (tipo_id text, productos int)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  return query select tp.id, (select count(*)::int from public.productos p where p.tipo = tp.id) from public.tipos_producto tp;
end;
$$;

create or replace function public.guardar_tipo_producto(p_id text, p_nombre text, p_categoria text, p_orden int default 0,
                                                        p_campos jsonb default null, p_activo boolean default true)
returns text language plpgsql security definer set search_path = public as $$
declare v_id text := lower(btrim(coalesce(p_id, ''))); v_nombre text := btrim(coalesce(p_nombre, ''));
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  if v_id !~ '^[a-z0-9_]{2,40}$' then raise exception 'ID_INVALIDO'; end if;
  if v_nombre = '' or char_length(v_nombre) > 80 then raise exception 'NOMBRE_INVALIDO'; end if;
  if p_categoria is null or p_categoria not in ('TEXTIL', 'CUERO', 'JOYERIA', 'COSMETICA', 'GOURMET', 'DECORACION') then
    raise exception 'CATEGORIA_INVALIDA';
  end if;
  if p_campos is not null and jsonb_typeof(p_campos) <> 'array' then raise exception 'CAMPOS_INVALIDOS'; end if;
  insert into public.tipos_producto (id, categoria, nombre, campos, activo, orden)
  values (v_id, p_categoria, v_nombre, coalesce(p_campos, '[]'::jsonb), coalesce(p_activo, true), coalesce(p_orden, 0))
  on conflict (id) do update set categoria = excluded.categoria, nombre = excluded.nombre,
    campos = coalesce(p_campos, public.tipos_producto.campos), activo = excluded.activo, orden = excluded.orden,
    actualizado_en = now();
  return v_id;
end;
$$;

-- Eliminar solo si ningún producto lo usa; si lo usan, se desactiva (los productos lo conservan).
create or replace function public.eliminar_tipo_producto(p_id text)
returns text language plpgsql security definer set search_path = public as $$
declare v_n int;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  select count(*) into v_n from public.productos where tipo = p_id;
  if v_n > 0 then raise exception 'TIPO_EN_USO' using detail = json_build_object('productos', v_n)::text; end if;
  delete from public.tipos_producto where id = p_id;
  if not found then raise exception 'NO_EXISTE'; end if;
  return 'ELIMINADO';
end;
$$;

-- ---------- 6. Permisos ----------
revoke all on function public.listar_emprendedoras(), public.editar_emprendedora(uuid, text, text),
                       public.cambiar_estado_emprendedora(uuid, boolean), public.uso_tipos_producto(),
                       public.guardar_tipo_producto(text, text, text, int, jsonb, boolean),
                       public.eliminar_tipo_producto(text) from public, anon;
grant execute on function public.listar_emprendedoras(), public.editar_emprendedora(uuid, text, text),
                          public.cambiar_estado_emprendedora(uuid, boolean), public.uso_tipos_producto(),
                          public.guardar_tipo_producto(text, text, text, int, jsonb, boolean),
                          public.eliminar_tipo_producto(text) to authenticated;
revoke all on function public.bloquear_cuenta_desactivada(), public.proteger_activa_tienda() from public, anon, authenticated;
grant execute on function public.tienda_activa(text) to anon, authenticated;

insert into public.migraciones_aplicadas (nombre) values ('10_emprendedoras_y_tipos') on conflict (nombre) do nothing;
notify pgrst, 'reload schema';
