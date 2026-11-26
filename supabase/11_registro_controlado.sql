-- =====================================================================
-- 11_registro_controlado.sql · Registro controlado de emprendedoras (RN-01)
--
-- PREPARADA Y PROBADA SOLO EN LOCAL (tests/sql). NO está aplicada en Supabase real.
-- Requiere 01–07, 09 y 10. No depende de 08. Se puede ejecutar dos veces.
-- Para deshacerla: 11_revertir.sql.
--
-- Problema que corrige:
--  · 04 dejaba que CUALQUIERA fuera emprendedora: bastaba enviar tipo_cuenta=EMPRENDEDORA en los
--    metadatos del registro o llamar a crear_mi_tienda() con una cuenta compradora.
--  · La instancia del curso tiene el registro por correo AUTOCONFIRMADO (docs/entrega5): el correo
--    no prueba que la persona sea su dueña. Por eso autorizar «un correo» no basta.
--
-- Nuevo procedimiento (no necesita SMTP ni servicios externos):
--  1. La administración autoriza el correo con autorizar_emprendedora(). El servidor genera un
--     CÓDIGO DE ACTIVACIÓN de un solo uso (8 caracteres, vence en 14 días) y lo devuelve UNA vez.
--     Solo se guarda su huella (sha256 con el correo); nadie puede leerlo después.
--  2. La administración entrega el código en persona (o por su canal de confianza).
--  3. La emprendedora crea SU cuenta con ese correo y SU contraseña (o entra con Google).
--     La administración nunca conoce ni guarda la contraseña.
--  4. Con sesión iniciada, ingresa el código: activar_con_codigo() verifica que el correo de la
--     sesión (auth.users, no lo que diga el cliente) coincide, que el código es correcto, vigente y
--     sin usar, y que no se superaron 5 intentos.
--  5. El servidor asigna el rol EMPRENDEDORA, crea su tienda y la suma a la asociación indicada.
--
-- Lo que ya NO otorga permisos de emprendedora:
--  · Los metadatos del registro (tipo_cuenta, rol…).
--  · crear_mi_tienda() desde una cuenta compradora.
--  · Una fila de cuentas_autorizadas sin código (las antiguas o las que inserten clientes antiguos):
--    quedan como «pendientes» y la administración debe generar un código.
--
-- Cuentas existentes: NO se toca ningún rol, perfil, tienda, producto, venta ni participación.
-- Las emprendedoras registradas por la vía abierta siguen igual; listar_cuentas_emprendedoras()
-- las marca «sin autorización registrada» y regularizar_emprendedora() deja constancia si la
-- administración decide conservarlas (decisión documentada en docs/administracion/).
-- Administradores: el procedimiento de 09 (administradores + cuentas_autorizadas ADMIN) no cambia.
--
-- Apropiación previa de la cuenta (revisión 2.0): con el correo autoconfirmado, un tercero puede crear
-- ANTES una cuenta con el correo de la emprendedora (con su propia contraseña). Para que esa cuenta no
-- herede los permisos:
--  · activar_con_codigo() rechaza (CUENTA_ANTERIOR) una cuenta creada antes de emitirse el código, salvo
--    que la administración confirme en persona que la cuenta es de ella (permitir_cuenta_existente).
--  · Al activar, se cierran las OTRAS sesiones de la cuenta (auth.sessions) para que nadie que haya
--    entrado antes conserve acceso; las apps además piden cerrar las otras sesiones (logout scope=others).
--  · Requisito de publicación: «Confirm email» en Supabase Auth (ver docs/version-2.0). Sin él, quien
--    conozca un correo puede ocuparlo y bloquear a su dueña (no obtiene permisos, pero sí la molestia).
-- =====================================================================

-- ---------- 1. Columnas de la autorización (agregar; no se borra nada) ----------
alter table public.cuentas_autorizadas add column if not exists codigo_hash    text;
alter table public.cuentas_autorizadas add column if not exists codigo_expira  timestamptz;
alter table public.cuentas_autorizadas add column if not exists intentos       int not null default 0;
alter table public.cuentas_autorizadas add column if not exists autorizado_por text;
alter table public.cuentas_autorizadas add column if not exists usada_en       timestamptz;
alter table public.cuentas_autorizadas add column if not exists usuario_id     uuid;
alter table public.cuentas_autorizadas add column if not exists codigo_emitido_en timestamptz;
alter table public.cuentas_autorizadas add column if not exists permitir_cuenta_existente boolean not null default false;

-- ---------- 2. Ayudas internas (no las puede llamar ningún cliente) ----------
create or replace function public.correo_de_cuenta(p_uid uuid)
returns text language sql stable security definer set search_path = public as $$
  select lower(u.email) from auth.users u where u.id = p_uid;
$$;

create or replace function public.huella_codigo(p_email text, p_codigo text)
returns text language sql immutable set search_path = public as $$
  select encode(sha256(convert_to(lower(btrim(p_email)) || ':' ||
         upper(regexp_replace(coalesce(p_codigo, ''), '[^A-Za-z0-9]', '', 'g')), 'UTF8')), 'hex');
$$;

-- Código de 8 caracteres con un alfabeto sin confusiones (sin 0/O, 1/I/L). El azar sale de
-- gen_random_uuid() (generador criptográfico del servidor), no de random().
create or replace function public.generar_codigo_activacion()
returns text language plpgsql volatile set search_path = public as $$
declare
  v_alfabeto constant text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';   -- 31 símbolos: 8 de ellos ≈ 39,6 bits
  v_bytes bytea;
  v_codigo text := '';
  v_b int;
  i int;
begin
  -- Muestreo por rechazo (sin sesgo de módulo): se descartan los bytes ≥ 248 (= 31 × 8).
  -- Los bytes 6 y 8 de cada UUID llevan bits fijos de versión y variante: no se usan.
  while length(v_codigo) < 8 loop
    v_bytes := uuid_send(gen_random_uuid());
    foreach i in array array[0, 1, 2, 3, 4, 5, 7, 9, 10, 11, 12, 13, 14, 15] loop
      v_b := get_byte(v_bytes, i);
      if v_b < 248 and length(v_codigo) < 8 then
        v_codigo := v_codigo || substr(v_alfabeto, 1 + (v_b % 31), 1);
      end if;
    end loop;
  end loop;
  return substr(v_codigo, 1, 4) || '-' || substr(v_codigo, 5, 4);
end;
$$;

revoke all on function public.correo_de_cuenta(uuid), public.huella_codigo(text, text),
                       public.generar_codigo_activacion() from public, anon, authenticated;

-- ---------- 3. Alta de usuarios: los metadatos ya no dan permisos ----------
create or replace function public.al_crear_usuario()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_rol    text;
  v_nombre text;
begin
  select rol, nombre into v_rol, v_nombre
    from public.cuentas_autorizadas
   where lower(email) = lower(new.email);

  -- Solo ADMIN se asigna al registrarse (procedimiento de 09, sin cambios). EMPRENDEDORA se
  -- obtiene únicamente con el código de activación. tipo_cuenta en metadatos se ignora.
  if v_rol is distinct from 'ADMIN' then v_rol := 'VISITANTE'; end if;
  v_nombre := coalesce(nullif(btrim(new.raw_user_meta_data ->> 'nombre'), ''), v_nombre, split_part(new.email, '@', 1));

  insert into public.perfiles (id, email, nombre, rol, tienda_id)
  values (new.id, new.email, v_nombre, v_rol, null)
  on conflict (id) do nothing;
  return new;
end;
$$;

-- ---------- 4. crear_mi_tienda: ya no convierte compradoras en emprendedoras ----------
create or replace function public.crear_mi_tienda(p_nombre text)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_tienda text;
  v_rol text;
begin
  if v_uid is null then raise exception 'Inicia sesión para crear tu tienda.' using errcode = '42501'; end if;
  if nullif(btrim(p_nombre), '') is null then raise exception 'Escribe el nombre de tu tienda.'; end if;
  select rol, tienda_id into v_rol, v_tienda from public.perfiles where id = v_uid;
  if not found then raise exception 'No encontramos tu perfil.'; end if;
  if v_rol = 'ADMIN' then raise exception 'La cuenta de administración no puede abrir una tienda.'; end if;
  if v_rol <> 'EMPRENDEDORA' then
    raise exception 'NO_AUTORIZADA: para vender en Alerce, la administración debe autorizar tu correo y darte un código de activación.'
      using errcode = '42501';
  end if;
  if v_tienda is not null then return; end if;

  -- Emprendedora autorizada que quedó sin tienda: se le crea la suya (comportamiento anterior).
  v_tienda := 'tienda_' || replace(v_uid::text, '-', '');
  insert into public.tiendas (id, owner_id, nombre, rubro, sector, bio)
  values (v_tienda, v_uid, btrim(p_nombre), 'Comercio y servicios', 'Alerce', 'Emprendimiento local de Alerce.')
  on conflict (id) do nothing;
  if not exists (select 1 from public.tiendas where id = v_tienda and owner_id = v_uid) then
    raise exception 'No se pudo crear una tienda propia.';
  end if;
  update public.perfiles set tienda_id = v_tienda where id = v_uid;
end;
$$;
revoke all on function public.crear_mi_tienda(text) from public, anon;
grant execute on function public.crear_mi_tienda(text) to authenticated;

-- ---------- 5. Administración: autorizar, regenerar, revocar, regularizar ----------
create or replace function public.autorizar_emprendedora(p_email text, p_nombre text, p_asociacion text default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_email  text := lower(btrim(coalesce(p_email, '')));
  v_nombre text := btrim(coalesce(p_nombre, ''));
  v_codigo text;
  v_actual public.cuentas_autorizadas;
  v_admin  text := public.correo_de_cuenta(auth.uid());
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  if v_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then raise exception 'CORREO_INVALIDO'; end if;
  if v_nombre = '' or char_length(v_nombre) > 120 then raise exception 'NOMBRE_INVALIDO'; end if;
  if p_asociacion is not null and not exists (select 1 from public.asociaciones where id = p_asociacion) then
    raise exception 'ASOCIACION_NO_EXISTE';
  end if;
  if exists (select 1 from public.administradores where email = v_email)
     or exists (select 1 from public.perfiles where lower(email) = v_email and rol = 'ADMIN') then
    raise exception 'ES_ADMINISTRADOR';
  end if;
  if exists (select 1 from public.perfiles where lower(email) = v_email and rol = 'EMPRENDEDORA') then
    raise exception 'YA_ES_EMPRENDEDORA';
  end if;
  select * into v_actual from public.cuentas_autorizadas where lower(email) = v_email for update;
  if found and v_actual.rol = 'ADMIN' then raise exception 'ES_ADMINISTRADOR'; end if;

  v_codigo := public.generar_codigo_activacion();
  insert into public.cuentas_autorizadas (email, rol, nombre, asociacion_id, codigo_hash, codigo_expira, intentos, autorizado_por,
                                         usada_en, usuario_id, codigo_emitido_en, permitir_cuenta_existente)
  values (v_email, 'EMPRENDEDORA', v_nombre, p_asociacion, public.huella_codigo(v_email, v_codigo), now() + interval '14 days', 0, v_admin,
          null, null, clock_timestamp(), false)
  on conflict (email) do update set
    rol = 'EMPRENDEDORA', nombre = excluded.nombre, asociacion_id = excluded.asociacion_id,
    codigo_hash = excluded.codigo_hash, codigo_expira = excluded.codigo_expira, intentos = 0,
    autorizado_por = excluded.autorizado_por, usada_en = null, usuario_id = null,
    codigo_emitido_en = excluded.codigo_emitido_en, permitir_cuenta_existente = false;

  return jsonb_build_object(
    'email', v_email, 'codigo', v_codigo, 'expira', now() + interval '14 days',
    'estado', case when exists (select 1 from public.perfiles where lower(email) = v_email)
                   then 'PENDIENTE_ACTIVACION' else 'PENDIENTE_REGISTRO' end);
end;
$$;

-- Nuevo código para una autorización pendiente (el anterior deja de servir).
create or replace function public.regenerar_codigo_emprendedora(p_email text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_email text := lower(btrim(coalesce(p_email, '')));
  v_fila public.cuentas_autorizadas;
  v_codigo text;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  select * into v_fila from public.cuentas_autorizadas where lower(email) = v_email for update;
  if not found or v_fila.rol <> 'EMPRENDEDORA' then raise exception 'NO_EXISTE'; end if;
  if v_fila.usada_en is not null or exists (select 1 from public.perfiles where lower(email) = v_email and rol = 'EMPRENDEDORA') then
    raise exception 'YA_ACTIVADA';
  end if;
  v_codigo := public.generar_codigo_activacion();
  -- El código anterior deja de servir. La fecha de emisión NO se adelanta: una cuenta creada después de la
  -- primera autorización sigue siendo válida; una anterior sigue necesitando la confirmación en persona.
  update public.cuentas_autorizadas
     set codigo_hash = public.huella_codigo(v_email, v_codigo), codigo_expira = now() + interval '14 days', intentos = 0,
         autorizado_por = public.correo_de_cuenta(auth.uid()), codigo_emitido_en = coalesce(codigo_emitido_en, clock_timestamp())
   where email = v_fila.email;
  return jsonb_build_object('email', v_email, 'codigo', v_codigo, 'expira', now() + interval '14 days');
end;
$$;

-- Revoca una autorización que todavía no se usó. Una cuenta ya activada se DESACTIVA (10), no se revoca.
create or replace function public.revocar_autorizacion(p_email text)
returns text language plpgsql security definer set search_path = public as $$
declare
  v_email text := lower(btrim(coalesce(p_email, '')));
  v_fila public.cuentas_autorizadas;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  select * into v_fila from public.cuentas_autorizadas where lower(email) = v_email for update;
  if not found then raise exception 'NO_EXISTE'; end if;
  if v_fila.rol <> 'EMPRENDEDORA' then raise exception 'USA_ADMINISTRADORES'; end if;
  if v_fila.usada_en is not null or exists (select 1 from public.perfiles where lower(email) = v_email and rol = 'EMPRENDEDORA') then
    raise exception 'YA_ACTIVADA';
  end if;
  delete from public.cuentas_autorizadas where email = v_fila.email;   -- la bitácora (09) lo anota
  return 'REVOCADA';
end;
$$;

-- La administración confirmó EN PERSONA que la cuenta que ya existía con ese correo es de la emprendedora
-- (por ejemplo, ella entra a esa cuenta frente a la administración). Solo así una cuenta anterior al código puede activarse.
create or replace function public.permitir_cuenta_existente(p_email text)
returns text language plpgsql security definer set search_path = public as $$
declare v_email text := lower(btrim(coalesce(p_email, ''))); v_fila public.cuentas_autorizadas;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  select * into v_fila from public.cuentas_autorizadas where lower(email) = v_email and rol = 'EMPRENDEDORA' for update;
  if not found then raise exception 'NO_EXISTE'; end if;
  if v_fila.usada_en is not null then raise exception 'YA_ACTIVADA'; end if;
  update public.cuentas_autorizadas set permitir_cuenta_existente = true where email = v_fila.email;
  return 'PERMITIDA';
end;
$$;

-- Deja constancia de que la administración conserva a una emprendedora registrada por la vía abierta.
-- No cambia su rol, su tienda ni sus datos.
create or replace function public.regularizar_emprendedora(p_usuario uuid)
returns text language plpgsql security definer set search_path = public as $$
declare v_perfil public.perfiles;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  select * into v_perfil from public.perfiles where id = p_usuario and rol = 'EMPRENDEDORA';
  if not found then raise exception 'NO_EXISTE'; end if;
  if exists (select 1 from public.cuentas_autorizadas where lower(email) = lower(v_perfil.email)) then return 'YA_AUTORIZADA'; end if;
  insert into public.cuentas_autorizadas (email, rol, nombre, tienda_id, autorizado_por, usada_en, usuario_id)
  values (lower(v_perfil.email), 'EMPRENDEDORA', v_perfil.nombre, v_perfil.tienda_id,
          public.correo_de_cuenta(auth.uid()), now(), v_perfil.id);
  return 'REGULARIZADA';
end;
$$;

-- ---------- 6. La emprendedora activa su cuenta con el código ----------
create or replace function public.activar_con_codigo(p_codigo text)
returns text language plpgsql security definer set search_path = public as $$
declare
  v_uid    uuid := auth.uid();
  v_email  text;
  v_perfil public.perfiles;
  v_fila   public.cuentas_autorizadas;
  v_tienda text;
  v_creada timestamptz;
  v_sesion text;
begin
  if v_uid is null then raise exception 'SIN_SESION' using errcode = '42501'; end if;
  v_email := public.correo_de_cuenta(v_uid);          -- el correo de la sesión, nunca uno enviado por el cliente
  select * into v_perfil from public.perfiles where id = v_uid for update;
  if not found then raise exception 'SIN_PERFIL'; end if;
  if v_perfil.rol = 'ADMIN' then raise exception 'ES_ADMINISTRADOR'; end if;
  if v_perfil.rol = 'EMPRENDEDORA' then return 'YA_ACTIVA'; end if;

  select * into v_fila from public.cuentas_autorizadas where lower(email) = v_email and rol = 'EMPRENDEDORA' for update;
  if not found then raise exception 'NO_AUTORIZADA' using errcode = '42501'; end if;
  if v_fila.usada_en is not null then raise exception 'YA_ACTIVADA'; end if;
  if v_fila.codigo_hash is null then raise exception 'SIN_CODIGO'; end if;
  if v_fila.intentos >= 5 then raise exception 'DEMASIADOS_INTENTOS'; end if;
  if v_fila.codigo_expira is null or v_fila.codigo_expira < now() then raise exception 'CODIGO_VENCIDO'; end if;
  -- Cuenta creada ANTES de emitirse el código: pudo crearla otra persona con ese correo (autoconfirmado).
  -- Se rechaza antes de mirar el código (no da pistas sobre él) hasta que la administración confirme en persona.
  select u.created_at into v_creada from auth.users u where u.id = v_uid;
  if not v_fila.permitir_cuenta_existente and (v_fila.codigo_emitido_en is null or v_creada is null or v_creada < v_fila.codigo_emitido_en) then
    raise exception 'CUENTA_ANTERIOR' using errcode = '42501';
  end if;
  if public.huella_codigo(v_email, p_codigo) <> v_fila.codigo_hash then
    -- El intento fallido se guarda aunque la llamada termine en error.
    update public.cuentas_autorizadas set intentos = intentos + 1 where email = v_fila.email;
    return 'CODIGO_INCORRECTO:' || greatest(0, 4 - v_fila.intentos);
  end if;

  -- Tienda propia y nueva (una tienda preasignada no cambia de dueña: proteger_tienda de 06 lo impide).
  v_tienda := 'tienda_' || replace(v_uid::text, '-', '');
  insert into public.tiendas (id, owner_id, nombre, rubro, sector, bio)
  values (v_tienda, v_uid, coalesce(nullif(btrim(v_fila.nombre), ''), v_perfil.nombre, split_part(v_email, '@', 1)),
          'Comercio y servicios', 'Alerce', 'Emprendimiento local de Alerce.')
  on conflict (id) do nothing;
  if not exists (select 1 from public.tiendas where id = v_tienda and owner_id = v_uid) then raise exception 'TIENDA_NO_DISPONIBLE'; end if;
  if v_fila.asociacion_id is not null and exists (select 1 from public.asociaciones where id = v_fila.asociacion_id) then
    insert into public.tienda_asociaciones (tienda_id, asociacion_id) values (v_tienda, v_fila.asociacion_id) on conflict do nothing;
  end if;
  update public.perfiles
     set rol = 'EMPRENDEDORA', tienda_id = v_tienda, nombre = coalesce(nullif(btrim(v_fila.nombre), ''), nombre)
   where id = v_uid;
  update public.cuentas_autorizadas
     set usada_en = now(), usuario_id = v_uid, codigo_hash = null, intentos = 0
   where email = v_fila.email;
  -- Se cierran las OTRAS sesiones de esta cuenta: quien haya entrado antes con ella pierde el acceso
  -- (sus tokens de acceso vigentes caducan solos, máximo 1 hora). La sesión actual se conserva.
  v_sesion := nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'session_id';
  begin
    if to_regclass('auth.sessions') is not null then
      execute 'delete from auth.sessions where user_id = $1 and ($2::text is null or id::text <> $2)' using v_uid, v_sesion;
    end if;
  exception when insufficient_privilege or undefined_column or undefined_table then
    return 'ACTIVADA_SIN_CERRAR_SESIONES';   -- la app igual pide cerrar las otras sesiones (logout scope=others)
  end;
  return 'ACTIVADA';
end;
$$;

-- Estado de la cuenta con sesión, para que las apps expliquen qué falta (sin datos de otras personas).
create or replace function public.estado_mi_cuenta()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
  v_perfil public.perfiles;
  v_fila public.cuentas_autorizadas;
begin
  if v_uid is null then return jsonb_build_object('rol', null, 'estado', 'SIN_SESION'); end if;
  v_email := public.correo_de_cuenta(v_uid);
  select * into v_perfil from public.perfiles where id = v_uid;
  if not found then return jsonb_build_object('rol', null, 'estado', 'SIN_PERFIL'); end if;
  if v_perfil.rol = 'ADMIN' then return jsonb_build_object('rol', 'ADMIN', 'estado', 'ADMIN'); end if;
  if v_perfil.rol = 'EMPRENDEDORA' then
    return jsonb_build_object('rol', 'EMPRENDEDORA',
      'estado', case when v_perfil.tienda_id is null then 'SIN_TIENDA'
                     when public.tienda_activa(v_perfil.tienda_id) then 'ACTIVA' else 'DESACTIVADA' end);
  end if;
  select * into v_fila from public.cuentas_autorizadas where lower(email) = v_email and rol = 'EMPRENDEDORA' and usada_en is null;
  if found then
    return jsonb_build_object('rol', 'VISITANTE', 'estado', 'AUTORIZACION_PENDIENTE',
      'codigo_vigente', v_fila.codigo_hash is not null and v_fila.codigo_expira >= now() and v_fila.intentos < 5);
  end if;
  return jsonb_build_object('rol', 'VISITANTE', 'estado', 'COMPRADOR');
end;
$$;

-- ---------- 7. Lista para la administración (estados que existen en los datos) ----------
-- La lista cambió de columnas (cuenta_anterior): se recrea la función (no hay datos en ella).
drop function if exists public.listar_cuentas_emprendedoras();
create or replace function public.listar_cuentas_emprendedoras()
returns table (usuario_id uuid, email text, nombre text, tienda_id text, tienda_nombre text, estado text,
               creado_en timestamptz, autorizada boolean, codigo_vigente boolean, codigo_expira timestamptz,
               intentos int, asociaciones text[], cuenta_anterior boolean)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  return query
  select p.id, p.email, p.nombre, t.id, t.nombre,
         case when t.id is null then 'SIN_TIENDA' when t.activa then 'ACTIVA' else 'DESACTIVADA' end,
         p.creado_en,
         exists (select 1 from public.cuentas_autorizadas c where lower(c.email) = lower(p.email) and c.rol = 'EMPRENDEDORA'),
         false, null::timestamptz, 0,
         coalesce((select array_agg(ta.asociacion_id order by ta.asociacion_id) from public.tienda_asociaciones ta where ta.tienda_id = t.id), '{}'),
         false
    from public.perfiles p
    left join public.tiendas t on t.id = p.tienda_id and t.owner_id = p.id
   where p.rol = 'EMPRENDEDORA'
  union all
  select pv.id, c.email, c.nombre, null, null,
         case when pv.id is null then 'PENDIENTE_REGISTRO' else 'PENDIENTE_ACTIVACION' end,
         c.creado_en, true,
         c.codigo_hash is not null and c.codigo_expira >= now() and c.intentos < 5, c.codigo_expira, c.intentos,
         case when c.asociacion_id is null then '{}'::text[] else array[c.asociacion_id] end,
         pv.id is not null and not c.permitir_cuenta_existente and
           (c.codigo_emitido_en is null or coalesce((select u.created_at from auth.users u where u.id = pv.id), '-infinity') < c.codigo_emitido_en)
    from public.cuentas_autorizadas c
    left join public.perfiles pv on lower(pv.email) = lower(c.email)
   where c.rol = 'EMPRENDEDORA' and c.usada_en is null
     and not exists (select 1 from public.perfiles p2 where lower(p2.email) = lower(c.email) and p2.rol = 'EMPRENDEDORA')
   order by 7 desc;
end;
$$;


revoke all on function public.permitir_cuenta_existente(text) from public, anon;
grant execute on function public.permitir_cuenta_existente(text) to authenticated;
revoke all on function public.autorizar_emprendedora(text, text, text), public.regenerar_codigo_emprendedora(text),
                       public.revocar_autorizacion(text), public.regularizar_emprendedora(uuid),
                       public.activar_con_codigo(text), public.estado_mi_cuenta(),
                       public.listar_cuentas_emprendedoras() from public, anon;
grant execute on function public.autorizar_emprendedora(text, text, text), public.regenerar_codigo_emprendedora(text),
                          public.revocar_autorizacion(text), public.regularizar_emprendedora(uuid),
                          public.activar_con_codigo(text), public.estado_mi_cuenta(),
                          public.listar_cuentas_emprendedoras() to authenticated;

insert into public.migraciones_aplicadas (nombre) values ('11_registro_controlado') on conflict (nombre) do nothing;
notify pgrst, 'reload schema';
