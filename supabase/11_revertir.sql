-- =====================================================================
-- 11_revertir.sql · Deshace 11_registro_controlado.sql (solo si hace falta volver atrás)
--
-- Revisión 2.0: revertir NO reabre el registro libre de 04. Se vuelve a la regla original de RN-01:
--  · Al registrarse, el rol sale SOLO de cuentas_autorizadas (ADMIN o EMPRENDEDORA); los metadatos se ignoran.
--  · crear_mi_tienda solo sirve a una emprendedora sin tienda o a un correo autorizado como EMPRENDEDORA.
-- Es más débil que el código de activación (el correo autoconfirmado no prueba titularidad), pero no es libre.
-- Quita las funciones nuevas. NO borra datos: las columnas agregadas quedan sin uso y las cuentas activadas
-- con código conservan su rol y su tienda. Las apps 1.10+ siguen funcionando (usan las funciones de 10).
-- =====================================================================

create or replace function public.al_crear_usuario()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_rol    text;
  v_nombre text;
  v_tienda text;
begin
  select rol, nombre, tienda_id into v_rol, v_nombre, v_tienda
    from public.cuentas_autorizadas where lower(email) = lower(new.email);
  v_rol := coalesce(v_rol, 'VISITANTE');   -- tipo_cuenta de los metadatos NO cuenta
  v_nombre := coalesce(nullif(btrim(new.raw_user_meta_data ->> 'nombre'), ''), v_nombre, split_part(new.email, '@', 1));
  if v_rol = 'EMPRENDEDORA' then
    if v_tienda is not null then
      update public.tiendas set owner_id = new.id where id = v_tienda and (owner_id is null or owner_id = new.id);
      if not found then v_tienda := null; end if;
    end if;
    if v_tienda is null then
      v_tienda := 'tienda_' || replace(new.id::text, '-', '');
      insert into public.tiendas (id, owner_id, nombre, rubro, sector, bio)
      values (v_tienda, new.id, v_nombre, 'Comercio y servicios', 'Alerce', 'Emprendimiento local de Alerce.')
      on conflict (id) do nothing;
    end if;
  else
    v_tienda := null;
  end if;
  insert into public.perfiles (id, email, nombre, rol, tienda_id)
  values (new.id, new.email, v_nombre, v_rol, v_tienda)
  on conflict (id) do nothing;
  return new;
end;
$$;

create or replace function public.crear_mi_tienda(p_nombre text)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_tienda text;
  v_rol text;
  v_email text;
begin
  if v_uid is null then raise exception 'Inicia sesión para crear tu tienda.' using errcode = '42501'; end if;
  if nullif(btrim(p_nombre), '') is null then raise exception 'Escribe el nombre de tu tienda.'; end if;
  select rol, tienda_id, lower(email) into v_rol, v_tienda, v_email from public.perfiles where id = v_uid;
  if not found then raise exception 'No encontramos tu perfil.'; end if;
  if v_rol = 'ADMIN' then raise exception 'La cuenta de administración no puede abrir una tienda.'; end if;
  if v_rol <> 'EMPRENDEDORA' and not exists (
       select 1 from public.cuentas_autorizadas c where lower(c.email) = v_email and c.rol = 'EMPRENDEDORA') then
    raise exception 'NO_AUTORIZADA: para vender en Alerce, la administración debe autorizar tu correo.' using errcode = '42501';
  end if;
  if v_tienda is not null then return; end if;
  v_tienda := 'tienda_' || replace(v_uid::text, '-', '');
  insert into public.tiendas (id, owner_id, nombre, rubro, sector, bio)
  values (v_tienda, v_uid, btrim(p_nombre), 'Comercio y servicios', 'Alerce', 'Emprendimiento local de Alerce.')
  on conflict (id) do nothing;
  if not exists (select 1 from public.tiendas where id = v_tienda and owner_id = v_uid) then
    raise exception 'No se pudo crear una tienda propia.';
  end if;
  update public.perfiles set rol = 'EMPRENDEDORA', tienda_id = v_tienda, nombre = btrim(p_nombre) where id = v_uid;
end;
$$;
revoke all on function public.crear_mi_tienda(text) from public, anon;
grant execute on function public.crear_mi_tienda(text) to authenticated;

drop function if exists public.autorizar_emprendedora(text, text, text);
drop function if exists public.regenerar_codigo_emprendedora(text);
drop function if exists public.revocar_autorizacion(text);
drop function if exists public.regularizar_emprendedora(uuid);
drop function if exists public.permitir_cuenta_existente(text);
drop function if exists public.activar_con_codigo(text);
drop function if exists public.estado_mi_cuenta();
drop function if exists public.listar_cuentas_emprendedoras();
drop function if exists public.generar_codigo_activacion();
drop function if exists public.huella_codigo(text, text);
drop function if exists public.correo_de_cuenta(uuid);

delete from public.migraciones_aplicadas where nombre = '11_registro_controlado';
notify pgrst, 'reload schema';
