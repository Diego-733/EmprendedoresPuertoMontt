-- =====================================================================
-- Entrega 8 · 09_administracion.sql
-- RF-ADM-04 (gestionar otras cuentas de administrador) y RNF-SEG-07
-- (bitácora de las acciones del administrador: quién, qué y cuándo).
--
-- · Requiere 01–07. NO depende de 08 (envío automático), que queda fuera.
-- · Se puede ejecutar más de una vez. Para deshacerla: 09_revertir.sql.
-- · No cambia datos existentes: solo agrega tablas, funciones y disparadores.
--   Las cuentas ADMIN actuales se copian a la lista de administradores.
--
-- Cómo funciona
--  · Los derechos de administración siguen siendo los de siempre: perfiles.rol = 'ADMIN'
--    (es_admin()) y, para quien aún no se registra, cuentas_autorizadas con rol ADMIN.
--  · public.administradores es la lista que se gestiona desde la web: crear (autoriza el
--    correo), editar el nombre y desactivar/reactivar. Desactivar quita el rol ADMIN y la
--    autorización; nunca borra la cuenta. No se puede desactivar la propia cuenta ni la
--    última administradora activa.
--  · public.bitacora_admin (creada en 07 para los avisos de feria) se amplía con quién
--    (correo), el tipo y el id del objeto. La llenan SOLO disparadores y funciones del servidor, cuando
--    quien actúa es una administradora (auth.uid() con rol ADMIN). Nadie puede escribirla
--    ni borrarla desde un cliente; solo las administradoras pueden leerla.
-- =====================================================================

-- ---------- 1. Tablas ----------
create table if not exists public.administradores (
  email       text primary key check (email = lower(email) and email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
  nombre      text not null default '' check (char_length(nombre) <= 120),
  activo      boolean not null default true,
  creado_en   timestamptz not null default now(),
  creado_por  text,
  actualizado_en timestamptz not null default now()
);

-- La bitácora existe desde 07 (id, usuario_id, accion, feria_id, detalle, solicitud, creado_en).
alter table public.bitacora_admin add column if not exists admin_email text;
alter table public.bitacora_admin add column if not exists objeto_tipo text;
alter table public.bitacora_admin add column if not exists objeto_id   text;
create index if not exists bitacora_admin_creado_idx on public.bitacora_admin (creado_en desc);

alter table public.administradores enable row level security;
alter table public.bitacora_admin  enable row level security;   -- ya activa desde 07

drop policy if exists administradores_ver on public.administradores;
create policy administradores_ver on public.administradores for select to authenticated using (public.es_admin());
-- bitacora_admin conserva su política de 07 (bitacora_admin_ver: solo administradoras leen).

-- Solo lectura para clientes (las escrituras van por funciones del servidor).
revoke all on public.administradores, public.bitacora_admin from public, anon, authenticated;
grant select on public.administradores, public.bitacora_admin to authenticated;
revoke all on sequence public.bitacora_admin_id_seq from public, anon, authenticated;

-- Copia las administradoras actuales (registradas o autorizadas) a la lista.
insert into public.administradores (email, nombre, activo, creado_por)
select lower(p.email), coalesce(p.nombre, ''), true, 'migración 09'
  from public.perfiles p
 where p.rol = 'ADMIN' and p.email is not null
on conflict (email) do nothing;
insert into public.administradores (email, nombre, activo, creado_por)
select lower(c.email), coalesce(c.nombre, ''), true, 'migración 09'
  from public.cuentas_autorizadas c
 where c.rol = 'ADMIN'
on conflict (email) do nothing;

-- ---------- 2. Registro en la bitácora ----------
-- Anota una acción si quien actúa es administradora. Nunca falla la operación principal.
create or replace function public.anotar_bitacora(p_accion text, p_tipo text, p_id text, p_detalle jsonb default '{}'::jsonb)
returns void language plpgsql security definer set search_path = public as $$
declare v_email text;
begin
  if auth.uid() is null or not public.es_admin() then return; end if;
  select email into v_email from public.perfiles where id = auth.uid();
  insert into public.bitacora_admin (usuario_id, admin_email, accion, objeto_tipo, objeto_id, detalle)
  values (auth.uid(), v_email, left(p_accion, 120), p_tipo, left(p_id, 200), coalesce(p_detalle, '{}'::jsonb));
exception when others then
  raise warning 'bitácora: %', sqlerrm;
end;
$$;
revoke all on function public.anotar_bitacora(text, text, text, jsonb) from public, anon, authenticated;

-- Nombres de las columnas que cambiaron (sin copiar valores: la bitácora no duplica datos personales).
create or replace function public.columnas_cambiadas(p_viejo jsonb, p_nuevo jsonb)
returns jsonb language sql immutable as $$
  select coalesce(jsonb_agg(k order by k), '[]'::jsonb)
    from jsonb_object_keys(p_nuevo) k
   where k not in ('actualizado_en') and (p_viejo -> k) is distinct from (p_nuevo -> k);
$$;

-- Disparador genérico: traduce cada cambio en una acción legible.
create or replace function public.bitacora_cambio()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_nuevo jsonb := case when tg_op = 'DELETE' then null else to_jsonb(new) end;
  v_viejo jsonb := case when tg_op = 'INSERT' then null else to_jsonb(old) end;
  v_fila  jsonb := coalesce(v_nuevo, v_viejo);
  v_accion text; v_tipo text; v_id text; v_det jsonb := '{}'::jsonb;
begin
  if auth.uid() is null or not public.es_admin() then return null; end if;

  if tg_table_name = 'ferias' then
    v_tipo := 'feria'; v_id := v_fila ->> 'id';
    v_det := jsonb_build_object('nombre', v_fila ->> 'nombre', 'estado', v_fila ->> 'estado');
    if tg_op = 'INSERT' then v_accion := 'Creó feria';
    elsif tg_op = 'DELETE' then v_accion := 'Eliminó feria';
    elsif (v_viejo ->> 'estado') is distinct from (v_nuevo ->> 'estado') and (v_nuevo ->> 'estado') = 'CANCELADA' then v_accion := 'Canceló feria';
    elsif (v_viejo ->> 'estado') = 'BORRADOR' and (v_nuevo ->> 'estado') in ('PROXIMA', 'EN_CURSO') then v_accion := 'Publicó feria';
    else
      v_det := v_det || jsonb_build_object('campos', public.columnas_cambiadas(v_viejo, v_nuevo));
      if v_det -> 'campos' = '[]'::jsonb or v_det -> 'campos' = '["estado"]'::jsonb then return null; end if;   -- cambio automático de estado
      v_accion := 'Editó feria';
    end if;

  elsif tg_table_name = 'participaciones' then
    v_tipo := 'participación'; v_id := (v_fila ->> 'feria_id') || ' / ' || (v_fila ->> 'tienda_id');
    v_det := jsonb_build_object('feria', v_fila ->> 'feria_id', 'tienda', v_fila ->> 'tienda_id', 'invitacion', v_fila ->> 'invitacion', 'asistencia', v_fila ->> 'asistencia');
    if tg_op = 'INSERT' then
      v_accion := case when (v_fila ->> 'invitacion') in ('AGREGADA', 'CONFIRMADA') then 'Agregó directo a feria' else 'Invitó a feria' end;
    elsif tg_op = 'DELETE' then v_accion := 'Quitó de feria';
    elsif (v_viejo ->> 'asistencia') is distinct from (v_nuevo ->> 'asistencia') then v_accion := 'Registró asistencia';
    elsif (v_viejo ->> 'invitacion') is distinct from (v_nuevo ->> 'invitacion') then v_accion := 'Cambió estado de invitación';
    else return null;
    end if;

  elsif tg_table_name = 'asociaciones' then
    v_tipo := 'asociación'; v_id := v_fila ->> 'id';
    v_det := jsonb_build_object('nombre', v_fila ->> 'nombre');
    if tg_op = 'INSERT' then v_accion := 'Creó asociación';
    elsif tg_op = 'DELETE' then v_accion := 'Eliminó asociación';
    elsif (v_viejo ->> 'activa') is distinct from (v_nuevo ->> 'activa') then
      v_accion := case when (v_nuevo ->> 'activa')::boolean then 'Reactivó asociación' else 'Desactivó asociación' end;
    else
      v_det := v_det || jsonb_build_object('campos', public.columnas_cambiadas(v_viejo, v_nuevo));
      if v_det -> 'campos' = '[]'::jsonb then return null; end if;
      v_accion := 'Editó asociación';
    end if;

  elsif tg_table_name = 'tienda_asociaciones' then
    v_tipo := 'integrante'; v_id := (v_fila ->> 'tienda_id') || ' / ' || (v_fila ->> 'asociacion_id');
    v_det := jsonb_build_object('tienda', v_fila ->> 'tienda_id', 'asociacion', v_fila ->> 'asociacion_id');
    if tg_op = 'INSERT' then v_accion := 'Asignó emprendedora a asociación';
    elsif tg_op = 'DELETE' then v_accion := 'Quitó emprendedora de asociación';
    else return null;
    end if;

  elsif tg_table_name = 'cuentas_autorizadas' then
    if (v_fila ->> 'rol') = 'ADMIN' then return null; end if;   -- las de administración las anota su función
    v_tipo := 'cuenta'; v_id := v_fila ->> 'email';
    v_det := jsonb_build_object('rol', v_fila ->> 'rol');
    v_accion := case tg_op when 'INSERT' then 'Autorizó cuenta emprendedora'
                           when 'DELETE' then 'Quitó autorización de cuenta' else 'Editó cuenta autorizada' end;

  elsif tg_table_name = 'configuracion' then
    v_tipo := 'configuración'; v_id := v_fila ->> 'clave';
    v_det := jsonb_build_object('valor', left(v_fila ->> 'valor', 60));
    v_accion := 'Cambió configuración';

  elsif tg_table_name = 'tipos_producto' then
    v_tipo := 'tipo de producto'; v_id := v_fila ->> 'id';
    v_det := jsonb_build_object('nombre', v_fila ->> 'nombre');
    v_accion := case tg_op when 'INSERT' then 'Creó tipo de producto'
                           when 'DELETE' then 'Eliminó tipo de producto' else 'Editó tipo de producto' end;
  else
    return null;
  end if;

  perform public.anotar_bitacora(v_accion, v_tipo, v_id, v_det);
  return null;
end;
$$;
revoke all on function public.bitacora_cambio() from public, anon, authenticated;

do $$
declare t text;
begin
  foreach t in array array['ferias', 'participaciones', 'asociaciones', 'tienda_asociaciones',
                           'cuentas_autorizadas', 'configuracion', 'tipos_producto'] loop
    if to_regclass('public.' || t) is not null then
      execute format('drop trigger if exists bitacora_admin on public.%I', t);
      execute format('create trigger bitacora_admin after insert or update or delete on public.%I
                      for each row execute function public.bitacora_cambio()', t);
    end if;
  end loop;
end $$;

-- ---------- 3. Gestión de administradores (RF-ADM-04) ----------
create or replace function public.listar_admins()
returns table (email text, nombre text, estado text, creado_en timestamptz, es_yo boolean)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  return query
  select a.email, a.nombre,
         case when not a.activo then 'DESACTIVADA'
              when exists (select 1 from public.perfiles p where lower(p.email) = a.email and p.rol = 'ADMIN') then 'ACTIVA'
              else 'PENDIENTE_REGISTRO' end,
         a.creado_en,
         exists (select 1 from public.perfiles p where p.id = auth.uid() and lower(p.email) = a.email)
    from public.administradores a
   order by a.activo desc, a.creado_en;
end;
$$;

create or replace function public.crear_admin(p_email text, p_nombre text)
returns text language plpgsql security definer set search_path = public as $$
declare v_email text := lower(btrim(coalesce(p_email, ''))); v_nombre text := btrim(coalesce(p_nombre, '')); v_rol text;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  if v_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then raise exception 'CORREO_INVALIDO'; end if;
  if v_nombre = '' or char_length(v_nombre) > 120 then raise exception 'NOMBRE_INVALIDO'; end if;
  select rol into v_rol from public.perfiles where lower(email) = v_email;
  if v_rol = 'EMPRENDEDORA' then raise exception 'ES_EMPRENDEDORA'; end if;
  if exists (select 1 from public.administradores where email = v_email) then raise exception 'YA_EXISTE'; end if;

  insert into public.administradores (email, nombre, activo, creado_por)
  values (v_email, v_nombre, true, (select email from public.perfiles where id = auth.uid()));
  insert into public.cuentas_autorizadas (email, rol, nombre) values (v_email, 'ADMIN', v_nombre)
  on conflict (email) do update set rol = 'ADMIN', nombre = excluded.nombre, tienda_id = null;
  update public.perfiles set rol = 'ADMIN', nombre = v_nombre where lower(email) = v_email;
  perform public.anotar_bitacora('Creó cuenta de administrador', 'administrador', v_email, jsonb_build_object('nombre', v_nombre));
  return case when v_rol is null then 'PENDIENTE_REGISTRO' else 'ACTIVA' end;
end;
$$;

create or replace function public.editar_admin(p_email text, p_nombre text)
returns void language plpgsql security definer set search_path = public as $$
declare v_email text := lower(btrim(coalesce(p_email, ''))); v_nombre text := btrim(coalesce(p_nombre, ''));
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  if v_nombre = '' or char_length(v_nombre) > 120 then raise exception 'NOMBRE_INVALIDO'; end if;
  update public.administradores set nombre = v_nombre, actualizado_en = now() where email = v_email;
  if not found then raise exception 'NO_EXISTE'; end if;
  update public.cuentas_autorizadas set nombre = v_nombre where lower(email) = v_email and rol = 'ADMIN';
  update public.perfiles set nombre = v_nombre where lower(email) = v_email and rol = 'ADMIN';
  perform public.anotar_bitacora('Editó cuenta de administrador', 'administrador', v_email, jsonb_build_object('nombre', v_nombre));
end;
$$;

create or replace function public.cambiar_estado_admin(p_email text, p_activo boolean)
returns text language plpgsql security definer set search_path = public as $$
declare v_email text := lower(btrim(coalesce(p_email, ''))); v_fila public.administradores;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  if p_activo is null then raise exception 'ESTADO_INVALIDO'; end if;
  select * into v_fila from public.administradores where email = v_email for update;
  if not found then raise exception 'NO_EXISTE'; end if;
  if v_fila.activo = p_activo then return case when p_activo then 'SIN_CAMBIO_ACTIVA' else 'SIN_CAMBIO_DESACTIVADA' end; end if;

  if not p_activo then
    if exists (select 1 from public.perfiles where id = auth.uid() and lower(email) = v_email) then raise exception 'NO_PUEDES_DESACTIVARTE'; end if;
    -- Debe quedar al menos otra administradora activa y registrada.
    if not exists (select 1 from public.administradores a join public.perfiles p on lower(p.email) = a.email
                    where a.activo and a.email <> v_email and p.rol = 'ADMIN') then
      raise exception 'ULTIMA_ADMIN';
    end if;
    update public.administradores set activo = false, actualizado_en = now() where email = v_email;
    delete from public.cuentas_autorizadas where lower(email) = v_email and rol = 'ADMIN';
    update public.perfiles set rol = 'VISITANTE' where lower(email) = v_email and rol = 'ADMIN';
    perform public.anotar_bitacora('Desactivó cuenta de administrador', 'administrador', v_email, '{}'::jsonb);
    return 'DESACTIVADA';
  end if;

  update public.administradores set activo = true, actualizado_en = now() where email = v_email;
  insert into public.cuentas_autorizadas (email, rol, nombre) values (v_email, 'ADMIN', v_fila.nombre)
  on conflict (email) do update set rol = 'ADMIN', nombre = excluded.nombre, tienda_id = null;
  update public.perfiles set rol = 'ADMIN' where lower(email) = v_email and rol = 'VISITANTE';
  perform public.anotar_bitacora('Reactivó cuenta de administrador', 'administrador', v_email, '{}'::jsonb);
  return 'ACTIVA';
end;
$$;

-- Bitácora para la web (la más reciente primero; máximo 500). Incluye los avisos de feria de 07.
create or replace function public.ver_bitacora(p_limite int default 200)
returns table (en timestamptz, quien text, accion text, objeto_tipo text, objeto_id text, detalle jsonb)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  return query
  select b.creado_en, coalesce(b.admin_email, p.email, 'servidor'), b.accion,
         coalesce(b.objeto_tipo, case when b.feria_id is not null then 'feria' end),
         coalesce(b.objeto_id, b.feria_id), b.detalle
    from public.bitacora_admin b
    left join public.perfiles p on p.id = b.usuario_id
   order by b.creado_en desc, b.id desc
   limit greatest(1, least(coalesce(p_limite, 200), 500));
end;
$$;

revoke all on function public.listar_admins(), public.crear_admin(text, text), public.editar_admin(text, text),
                       public.cambiar_estado_admin(text, boolean), public.ver_bitacora(int) from public, anon;
grant execute on function public.listar_admins(), public.crear_admin(text, text), public.editar_admin(text, text),
                          public.cambiar_estado_admin(text, boolean), public.ver_bitacora(int) to authenticated;

create table if not exists public.migraciones_aplicadas (nombre text primary key, aplicada_en timestamptz not null default now());
insert into public.migraciones_aplicadas (nombre) values ('09_administracion') on conflict (nombre) do nothing;
notify pgrst, 'reload schema';
