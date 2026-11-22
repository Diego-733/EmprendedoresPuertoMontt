-- =====================================================================
-- alerceshopin · Entrega 7 — Ferias, seguidores y notificaciones
-- Ejecutar DESPUÉS de 01 a 06. Se puede volver a ejecutar: usa "if not
-- exists", recrea funciones, políticas y triggers, y no borra datos.
--
-- Contrato (detalle en docs/entrega7/README.md):
--  · Estados de feria: BORRADOR (no pública) · PROXIMA / EN_CURSO (publicada)
--    · FINALIZADA · CANCELADA. "Activa" = publicada y su último día >= hoy
--    en America/Santiago.
--  · Participación (una fila por feria y tienda, sin duplicados):
--      invitacion = PENDIENTE (invitada) | CONFIRMADA | RECHAZADA | AGREGADA
--      (incorporada por administración); asistencia = PENDIENTE | ASISTIO | NO_ASISTIO.
--    La confirmación, la incorporación directa y la asistencia son datos distintos.
--  · Asociaciones: no son cuentas (RN-04). Invitar o agregar una asociación
--    crea la participación de cada integrante actual (origen ASOCIACION);
--    cada emprendedora responde la suya. Si ya estaba invitada directamente,
--    no se duplica ni se pisa su respuesta.
--  · Seguidores, consentimiento, dispositivos y avisos viven en el servidor.
--    Ningún cliente inserta avisos: los crean triggers y funciones del servidor.
--    El envío lo hace un servicio autorizado (tabla notificadores), sin service_role.
--  · Calificaciones: solo si la tienda las tiene activas (lo verifica el servidor).
-- =====================================================================

-- ---------- 0. Fechas y horarios en America/Santiago ----------

create or replace function public.fecha_santiago(p_instante timestamptz default now())
returns date language sql stable set search_path = public as $$
  select (p_instante at time zone 'America/Santiago')::date;
$$;

create or replace function public.hora_santiago(p_instante timestamptz default now())
returns time language sql stable set search_path = public as $$
  select (p_instante at time zone 'America/Santiago')::time;
$$;

-- Horario de avisos: desde = hasta significa todo el día; desde > hasta cruza la medianoche.
create or replace function public.dentro_de_horario(p_hora time, p_desde time, p_hasta time)
returns boolean language sql immutable as $$
  select case
    when p_desde is null or p_hasta is null or p_desde = p_hasta then true
    when p_desde < p_hasta then p_hora >= p_desde and p_hora < p_hasta
    else p_hora >= p_desde or p_hora < p_hasta
  end;
$$;

grant execute on function public.fecha_santiago(timestamptz), public.hora_santiago(timestamptz),
  public.dentro_de_horario(time, time, time) to anon, authenticated;

-- ---------- 1. Ferias: estados válidos y lectura pública solo de publicadas ----------

alter table public.ferias add column if not exists publicada_en timestamptz;
alter table public.ferias add column if not exists cancelada_en timestamptz;
alter table public.ferias add column if not exists motivo_cancelacion text;

update public.ferias set estado = upper(btrim(estado)) where estado is not null and estado <> upper(btrim(estado));
update public.ferias set estado = 'BORRADOR'
 where estado is null or estado not in ('BORRADOR', 'PROXIMA', 'EN_CURSO', 'FINALIZADA', 'CANCELADA');
do $$ begin
  alter table public.ferias add constraint ferias_estado_ck
    check (estado in ('BORRADOR', 'PROXIMA', 'EN_CURSO', 'FINALIZADA', 'CANCELADA'));
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.ferias add constraint ferias_fechas_ck
    check (fecha_inicio is null or fecha_fin is null or fecha_fin >= fecha_inicio);
exception when duplicate_object then null; end $$;

create or replace function public.feria_publicada(p_estado text)
returns boolean language sql immutable as $$
  select p_estado in ('PROXIMA', 'EN_CURSO', 'FINALIZADA', 'CANCELADA');
$$;

create or replace function public.feria_activa(p_estado text, p_inicio date, p_fin date)
returns boolean language sql stable set search_path = public as $$
  select p_estado in ('PROXIMA', 'EN_CURSO')
     and coalesce(p_fin, p_inicio, public.fecha_santiago()) >= public.fecha_santiago();
$$;
grant execute on function public.feria_publicada(text), public.feria_activa(text, date, date) to anon, authenticated;

-- Fechas de publicación y cancelación las fija el servidor.
create or replace function public.marcar_estado_feria()
returns trigger language plpgsql set search_path = public as $$
begin
  if new.estado in ('PROXIMA', 'EN_CURSO') and (tg_op = 'INSERT' or old.estado not in ('PROXIMA', 'EN_CURSO')) then
    new.publicada_en := coalesce(new.publicada_en, now());
    new.cancelada_en := null;
  end if;
  if new.estado = 'CANCELADA' and (tg_op = 'INSERT' or old.estado <> 'CANCELADA') then
    new.cancelada_en := now();
  end if;
  new.actualizado_en := now();
  return new;
end;
$$;
drop trigger if exists marcar_estado_feria on public.ferias;
create trigger marcar_estado_feria before insert or update on public.ferias
  for each row execute function public.marcar_estado_feria();

-- Una feria en borrador no la ve nadie más que el administrador.
drop policy if exists ferias_ver on public.ferias;
create policy ferias_ver on public.ferias for select
  using (public.feria_publicada(estado) or public.es_admin());

-- ---------- 2. Varias asociaciones por tienda (RN-05) ----------

create table if not exists public.tienda_asociaciones (
  tienda_id     text not null references public.tiendas (id) on delete cascade,
  asociacion_id text not null references public.asociaciones (id) on delete cascade,
  creado_en     timestamptz not null default now(),
  primary key (tienda_id, asociacion_id)
);
create index if not exists tienda_asociaciones_asoc_idx on public.tienda_asociaciones (asociacion_id);

-- Se migran las asignaciones de la entrega 6 (una por tienda) sin perder ninguna.
insert into public.tienda_asociaciones (tienda_id, asociacion_id)
select t.id, t.asociacion_id from public.tiendas t
 where t.asociacion_id is not null
   and exists (select 1 from public.asociaciones a where a.id = t.asociacion_id)
on conflict do nothing;

alter table public.tienda_asociaciones enable row level security;
drop policy if exists tienda_asociaciones_ver on public.tienda_asociaciones;
create policy tienda_asociaciones_ver on public.tienda_asociaciones for select using (true);
drop policy if exists tienda_asociaciones_admin on public.tienda_asociaciones;
create policy tienda_asociaciones_admin on public.tienda_asociaciones
  for all to authenticated using (public.es_admin()) with check (public.es_admin());

-- tiendas.asociacion_id queda como "asociación principal" para clientes anteriores:
--  · si un cliente antiguo la cambia, se agrega la nueva pertenencia y se quita la anterior;
--  · si cambian las pertenencias, la principal sigue siendo una de ellas (o null).
create or replace function public.sincronizar_asociacion_principal()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_tienda text := coalesce(new.tienda_id, old.tienda_id);
  v_actual text; v_nueva text;
begin
  if pg_trigger_depth() > 1 then return null; end if;
  select asociacion_id into v_actual from public.tiendas where id = v_tienda;
  if v_actual is not null and exists (select 1 from public.tienda_asociaciones x
       where x.tienda_id = v_tienda and x.asociacion_id = v_actual) then
    return null;   -- la principal sigue siendo válida: no se toca la tienda
  end if;
  select x.asociacion_id into v_nueva from public.tienda_asociaciones x where x.tienda_id = v_tienda
   order by x.creado_en, x.asociacion_id limit 1;
  if v_nueva is distinct from v_actual then
    update public.tiendas set asociacion_id = v_nueva where id = v_tienda;
  end if;
  return null;
end;
$$;
drop trigger if exists sincronizar_asociacion_principal on public.tienda_asociaciones;
create trigger sincronizar_asociacion_principal
  after insert or delete on public.tienda_asociaciones
  for each row execute function public.sincronizar_asociacion_principal();

create or replace function public.asociacion_principal_cambiada()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if pg_trigger_depth() > 1 then return null; end if;
  if old.asociacion_id is distinct from new.asociacion_id then
    if old.asociacion_id is not null then
      delete from public.tienda_asociaciones where tienda_id = new.id and asociacion_id = old.asociacion_id;
    end if;
    if new.asociacion_id is not null then
      insert into public.tienda_asociaciones (tienda_id, asociacion_id) values (new.id, new.asociacion_id)
      on conflict do nothing;
    end if;
  end if;
  return null;
end;
$$;
drop trigger if exists asociacion_principal_cambiada on public.tiendas;
create trigger asociacion_principal_cambiada
  after update of asociacion_id on public.tiendas
  for each row execute function public.asociacion_principal_cambiada();

-- ---------- 3. Configuración de la tienda: avisos de contacto y calificaciones ----------

-- calificaciones_activas es pública (los clientes deben saber si mostrar el control).
alter table public.tiendas add column if not exists calificaciones_activas boolean not null default false;

-- Avisos de contacto y su horario son preferencias privadas de la dueña: no van en
-- tiendas (que se lee sin sesión), sino en una tabla que solo ella lee.
create table if not exists public.preferencias_tienda (
  tienda_id       text primary key references public.tiendas (id) on delete cascade,
  avisos_contacto boolean not null default false,
  avisos_desde    time not null default '09:00',
  avisos_hasta    time not null default '20:00',
  actualizado_en  timestamptz not null default now()
);
alter table public.preferencias_tienda enable row level security;
drop policy if exists preferencias_tienda_duena on public.preferencias_tienda;
create policy preferencias_tienda_duena on public.preferencias_tienda for select to authenticated
  using (public.es_duena_de(tienda_id));

-- ---------- 4. Participaciones ----------

alter table public.participaciones add column if not exists origen text not null default 'DIRECTA';
alter table public.participaciones add column if not exists asociacion_id text;
alter table public.participaciones add column if not exists invitada_en timestamptz not null default now();
alter table public.participaciones add column if not exists respondida_en timestamptz;
alter table public.participaciones add column if not exists asistencia_en timestamptz;
alter table public.participaciones add column if not exists actualizado_en timestamptz not null default now();

-- Valores escritos por versiones anteriores (datos locales del APK de prueba).
update public.participaciones set invitacion = 'PENDIENTE'
 where invitacion not in ('PENDIENTE', 'CONFIRMADA', 'RECHAZADA', 'AGREGADA');
update public.participaciones set asistencia = case asistencia
  when 'PRESENTE' then 'ASISTIO' when 'RECHAZADA' then 'NO_ASISTIO' else 'PENDIENTE' end
 where asistencia not in ('PENDIENTE', 'ASISTIO', 'NO_ASISTIO');

do $$ begin
  alter table public.participaciones add constraint participaciones_invitacion_ck
    check (invitacion in ('PENDIENTE', 'CONFIRMADA', 'RECHAZADA', 'AGREGADA'));
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.participaciones add constraint participaciones_asistencia_ck
    check (asistencia in ('PENDIENTE', 'ASISTIO', 'NO_ASISTIO'));
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.participaciones add constraint participaciones_origen_ck
    check (origen in ('DIRECTA', 'ASOCIACION'));
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.participaciones add constraint participaciones_asociacion_fk
    foreign key (asociacion_id) references public.asociaciones (id) on delete set null;
exception when duplicate_object then null; end $$;
create index if not exists participaciones_tienda_idx on public.participaciones (tienda_id);

-- Asociaciones invitadas o agregadas a una feria (para el checklist del administrador).
create table if not exists public.feria_asociaciones (
  feria_id      text not null references public.ferias (id) on delete cascade,
  asociacion_id text not null references public.asociaciones (id) on delete cascade,
  modo          text not null default 'INVITADA' check (modo in ('INVITADA', 'AGREGADA')),
  creado_en     timestamptz not null default now(),
  primary key (feria_id, asociacion_id)
);
alter table public.feria_asociaciones enable row level security;
drop policy if exists feria_asociaciones_admin on public.feria_asociaciones;
create policy feria_asociaciones_admin on public.feria_asociaciones
  for all to authenticated using (public.es_admin()) with check (public.es_admin());

-- Lectura: el administrador ve todo; la emprendedora solo las suyas.
-- El público NO lee la tabla (vería rechazadas y pendientes): usa participantes_feria().
drop policy if exists participaciones_ver on public.participaciones;
create policy participaciones_ver on public.participaciones for select to authenticated
  using (public.es_admin() or public.es_duena_de(tienda_id));
-- La emprendedora ya no escribe la fila directamente: responde con responder_invitacion().
drop policy if exists participaciones_responder on public.participaciones;
drop policy if exists participaciones_admin on public.participaciones;
create policy participaciones_admin on public.participaciones
  for all to authenticated using (public.es_admin()) with check (public.es_admin());
revoke select on public.participaciones from anon;

-- ---------- 5. Seguidores, consentimiento, dispositivos ----------

create table if not exists public.seguimientos (
  usuario_id  uuid not null references auth.users (id) on delete cascade,
  tipo        text not null check (tipo in ('TIENDA', 'ASOCIACION')),
  objetivo_id text not null,
  creado_en   timestamptz not null default now(),
  primary key (usuario_id, tipo, objetivo_id)
);
create index if not exists seguimientos_objetivo_idx on public.seguimientos (tipo, objetivo_id);
alter table public.seguimientos enable row level security;
drop policy if exists seguimientos_propios on public.seguimientos;
create policy seguimientos_propios on public.seguimientos for select to authenticated
  using (usuario_id = auth.uid());

create table if not exists public.preferencias_avisos (
  usuario_id     uuid primary key references auth.users (id) on delete cascade,
  avisos_ferias  boolean not null default false,
  consentido_en  timestamptz,
  retirado_en    timestamptz,
  actualizado_en timestamptz not null default now()
);
alter table public.preferencias_avisos enable row level security;
drop policy if exists preferencias_propias on public.preferencias_avisos;
create policy preferencias_propias on public.preferencias_avisos for select to authenticated
  using (usuario_id = auth.uid());

create table if not exists public.dispositivos (
  token          text primary key check (char_length(token) between 20 and 4096),
  usuario_id     uuid not null references auth.users (id) on delete cascade,
  plataforma     text not null check (plataforma in ('ANDROID', 'WEB')),
  activo         boolean not null default true,
  creado_en      timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);
create index if not exists dispositivos_usuario_idx on public.dispositivos (usuario_id) where activo;
alter table public.dispositivos enable row level security;
drop policy if exists dispositivos_propios on public.dispositivos;
create policy dispositivos_propios on public.dispositivos for select to authenticated
  using (usuario_id = auth.uid());

-- Cuentas del servicio de envío (las agrega el administrador; no hay alta por API).
create table if not exists public.notificadores (
  usuario_id uuid primary key references auth.users (id) on delete cascade,
  nota       text not null default '',
  creado_en  timestamptz not null default now()
);
alter table public.notificadores enable row level security;
drop policy if exists notificadores_admin on public.notificadores;
create policy notificadores_admin on public.notificadores for select to authenticated using (public.es_admin());

create or replace function public.es_notificador()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.notificadores where usuario_id = auth.uid());
$$;

-- ---------- 6. Avisos: bandeja de cada cuenta y cola de envío ----------
-- estado: PENDIENTE (guardado, falta enviar) · ENVIANDO · ENVIADO (el proveedor lo aceptó)
--         · SIN_DISPOSITIVO · OMITIDO (no corresponde enviarlo) · FALLIDO.
-- recibido_en: el celular o el navegador confirmó que lo recibió (no es lo mismo que ENVIADO).

create table if not exists public.avisos (
  id              uuid primary key default gen_random_uuid(),
  usuario_id      uuid not null references auth.users (id) on delete cascade,
  tipo            text not null check (tipo in ('INVITACION', 'AGREGADA', 'RECORDATORIO', 'AVISO_FERIA',
                                                  'FERIA_CANCELADA', 'PARTICIPACION_SEGUIDA', 'CONTACTO')),
  titulo          text not null,
  cuerpo          text not null default '',
  feria_id        text,
  tienda_id       text,
  enlace          text,
  clave           text not null unique,
  estado          text not null default 'PENDIENTE'
                  check (estado in ('PENDIENTE', 'ENVIANDO', 'ENVIADO', 'SIN_DISPOSITIVO', 'OMITIDO', 'FALLIDO')),
  motivo          text,
  intentos        int not null default 0,
  proximo_intento timestamptz not null default now(),
  creado_en       timestamptz not null default now(),
  enviado_en      timestamptz,
  recibido_en     timestamptz,
  leido_en        timestamptz
);
create index if not exists avisos_usuario_idx on public.avisos (usuario_id, creado_en desc);
create index if not exists avisos_pendientes_idx on public.avisos (proximo_intento) where estado in ('PENDIENTE', 'ENVIANDO');
alter table public.avisos enable row level security;
drop policy if exists avisos_propios on public.avisos;
create policy avisos_propios on public.avisos for select to authenticated using (usuario_id = auth.uid());

-- Registro de envíos del administrador (RNF-SEG-07: quién, qué y cuándo).
create table if not exists public.bitacora_admin (
  id         bigserial primary key,
  usuario_id uuid,
  accion     text not null,
  feria_id   text,
  detalle    jsonb not null default '{}',
  solicitud  uuid unique,
  creado_en  timestamptz not null default now()
);
alter table public.bitacora_admin enable row level security;
drop policy if exists bitacora_admin_ver on public.bitacora_admin;
create policy bitacora_admin_ver on public.bitacora_admin for select to authenticated using (public.es_admin());

-- Crea un aviso una sola vez por clave (reintentos y dobles clics no duplican).
create or replace function public.crear_aviso(
  p_usuario uuid, p_tipo text, p_titulo text, p_cuerpo text, p_feria text, p_tienda text,
  p_enlace text, p_clave text, p_estado text default 'PENDIENTE', p_motivo text default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_id uuid;
begin
  if p_usuario is null then return null; end if;
  insert into public.avisos (usuario_id, tipo, titulo, cuerpo, feria_id, tienda_id, enlace, clave, estado, motivo)
  values (p_usuario, p_tipo, left(p_titulo, 140), left(coalesce(p_cuerpo, ''), 600), p_feria, p_tienda, p_enlace, p_clave, p_estado, p_motivo)
  on conflict (clave) do nothing
  returning id into v_id;
  return v_id;
end;
$$;
revoke all on function public.crear_aviso(uuid, text, text, text, text, text, text, text, text, text) from public, anon, authenticated;

create or replace function public.texto_fecha_feria(p_inicio date, p_fin date)
returns text language sql immutable as $$
  select case when p_inicio is null then 'fecha por confirmar'
              when p_fin is null or p_fin = p_inicio then to_char(p_inicio, 'DD-MM-YYYY')
              else to_char(p_inicio, 'DD-MM-YYYY') || ' al ' || to_char(p_fin, 'DD-MM-YYYY') end;
$$;

-- Avisos que provoca un cambio de participación.
create or replace function public.avisar_participacion()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  f record; t record; s record;
  v_era_parte boolean := tg_op = 'UPDATE' and old.invitacion in ('CONFIRMADA', 'AGREGADA');
  v_es_parte  boolean := new.invitacion in ('CONFIRMADA', 'AGREGADA');
  v_enlace text;
begin
  select id, nombre, fecha_inicio, fecha_fin, estado into f from public.ferias where id = new.feria_id;
  select id, nombre, owner_id into t from public.tiendas where id = new.tienda_id;
  v_enlace := '?feria=' || new.feria_id;

  -- A la emprendedora: invitación o incorporación directa (RF-FER-08).
  if new.invitacion = 'PENDIENTE' and (tg_op = 'INSERT' or old.invitacion <> 'PENDIENTE') then
    perform public.crear_aviso(t.owner_id, 'INVITACION', 'Te invitaron a ' || f.nombre,
      'Feria del ' || public.texto_fecha_feria(f.fecha_inicio, f.fecha_fin) || '. Responde «Participo» o «No participo».',
      f.id, t.id, v_enlace, 'invitacion:' || f.id || ':' || t.id || ':' || to_char(now(), 'YYYYMMDDHH24MISSUS'));
  elsif new.invitacion = 'AGREGADA' and (tg_op = 'INSERT' or old.invitacion <> 'AGREGADA') then
    perform public.crear_aviso(t.owner_id, 'AGREGADA', 'Participas en ' || f.nombre,
      'La administración te agregó a la feria del ' || public.texto_fecha_feria(f.fecha_inicio, f.fecha_fin) || '.',
      f.id, t.id, v_enlace, 'agregada:' || f.id || ':' || t.id);
  end if;

  -- A sus seguidores: solo cuando pasa a participar (RN-11). Ediciones no avisan.
  if v_es_parte and not v_era_parte and public.feria_publicada(f.estado) and f.estado <> 'CANCELADA' then
    for s in
      select distinct on (sg.usuario_id) sg.usuario_id, sg.tipo, sg.objetivo_id
        from public.seguimientos sg
        join public.preferencias_avisos pa on pa.usuario_id = sg.usuario_id and pa.avisos_ferias
       where ((sg.tipo = 'TIENDA' and sg.objetivo_id = t.id)
          or (sg.tipo = 'ASOCIACION' and new.origen = 'ASOCIACION' and sg.objetivo_id = new.asociacion_id))
         and sg.usuario_id is distinct from t.owner_id
       order by sg.usuario_id, case sg.tipo when 'TIENDA' then 0 else 1 end
    loop
      perform public.crear_aviso(s.usuario_id, 'PARTICIPACION_SEGUIDA',
        case when s.tipo = 'TIENDA' then t.nombre || ' estará en ' || f.nombre
             else coalesce((select nombre from public.asociaciones where id = s.objetivo_id), 'Una asociación') || ' estará en ' || f.nombre end,
        'Feria del ' || public.texto_fecha_feria(f.fecha_inicio, f.fecha_fin) || '.',
        f.id, t.id, v_enlace,
        'seguidores:' || f.id || ':' || s.tipo || ':' || s.objetivo_id || ':' || s.usuario_id);
    end loop;
  end if;

  return null;
end;
$$;
drop trigger if exists avisar_participacion on public.participaciones;
create trigger avisar_participacion
  after insert or update of invitacion on public.participaciones
  for each row execute function public.avisar_participacion();

-- Dominio y fechas de cada cambio de participación.
create or replace function public.validar_participacion()
returns trigger language plpgsql set search_path = public as $$
begin
  if tg_op = 'UPDATE' then
    new.feria_id := old.feria_id; new.tienda_id := old.tienda_id; new.invitada_en := old.invitada_en;
    if new.asistencia is distinct from old.asistencia then new.asistencia_en := now(); end if;
  end if;
  new.actualizado_en := now();
  return new;
end;
$$;
drop trigger if exists validar_participacion on public.participaciones;
create trigger validar_participacion
  before insert or update on public.participaciones
  for each row execute function public.validar_participacion();

-- Contacto por formulario: siempre queda en la bandeja; solo avisa si corresponde (RF-CFG-04).
create or replace function public.avisar_contacto()
returns trigger language plpgsql security definer set search_path = public as $$
declare t record; v_estado text := 'PENDIENTE'; v_motivo text;
begin
  select ti.id, ti.nombre, ti.owner_id, coalesce(pt.avisos_contacto, false) avisos_contacto, pt.avisos_desde, pt.avisos_hasta
    into t from public.tiendas ti left join public.preferencias_tienda pt on pt.tienda_id = ti.id
   where ti.id = new.tienda_id;
  if t.owner_id is null then return null; end if;
  if not t.avisos_contacto then
    v_estado := 'OMITIDO'; v_motivo := 'AVISOS_DESACTIVADOS';
  elsif not public.dentro_de_horario(public.hora_santiago(new.creado_en), t.avisos_desde, t.avisos_hasta) then
    v_estado := 'OMITIDO'; v_motivo := 'FUERA_DE_HORARIO';
  end if;
  perform public.crear_aviso(t.owner_id, 'CONTACTO', 'Nuevo mensaje de ' || new.nombre,
    left(new.mensaje, 160), null, t.id, null, 'contacto:' || new.id, v_estado, v_motivo);
  return null;
end;
$$;
drop trigger if exists avisar_contacto on public.contactos;
create trigger avisar_contacto after insert on public.contactos
  for each row execute function public.avisar_contacto();

-- ---------- 7. Calificaciones (RF-CMP-09, RF-CFG-05) ----------

create table if not exists public.calificaciones (
  id             uuid primary key default gen_random_uuid(),
  tienda_id      text not null references public.tiendas (id) on delete cascade,
  producto_id    text references public.productos (id) on delete cascade,
  usuario_id     uuid not null references auth.users (id) on delete cascade,
  estrellas      smallint not null check (estrellas between 1 and 5),
  comentario     text not null default '' check (char_length(comentario) <= 500),
  creado_en      timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);
create unique index if not exists calificaciones_unica_idx
  on public.calificaciones (usuario_id, tienda_id, coalesce(producto_id, ''));
alter table public.calificaciones enable row level security;
drop policy if exists calificaciones_propias on public.calificaciones;
create policy calificaciones_propias on public.calificaciones for select to authenticated using (usuario_id = auth.uid());

-- ---------- 8. Funciones para clientes ----------

create or replace function public.registrar_bitacora(p_accion text, p_feria text, p_detalle jsonb, p_solicitud uuid default null)
returns boolean language plpgsql security definer set search_path = public as $$
declare v_n int;
begin
  insert into public.bitacora_admin (usuario_id, accion, feria_id, detalle, solicitud)
  values (auth.uid(), p_accion, p_feria, coalesce(p_detalle, '{}'), p_solicitud)
  on conflict (solicitud) do nothing;
  get diagnostics v_n = row_count;
  return v_n = 1;
end;
$$;
revoke all on function public.registrar_bitacora(text, text, jsonb, uuid) from public, anon, authenticated;

-- Publicar una feria (RF-FER-01). Exige los datos que el público necesita.
create or replace function public.publicar_feria(p_feria text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare f record;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  select * into f from public.ferias where id = p_feria for update;
  if not found then raise exception 'FERIA_NO_EXISTE'; end if;
  if btrim(coalesce(f.nombre, '')) = '' or f.fecha_inicio is null then raise exception 'FERIA_INCOMPLETA'; end if;
  if coalesce(f.fecha_fin, f.fecha_inicio) < public.fecha_santiago() then raise exception 'FERIA_PASADA'; end if;
  update public.ferias set estado = 'PROXIMA', motivo_cancelacion = null where id = p_feria and estado <> 'PROXIMA';
  perform public.registrar_bitacora('PUBLICAR_FERIA', p_feria, jsonb_build_object('estado_anterior', f.estado));
  return jsonb_build_object('feria_id', p_feria, 'estado', 'PROXIMA');
end;
$$;

-- Cancelar una feria (RF-FER-01): deja de ser activa y avisa a quienes participaban o estaban invitadas.
create or replace function public.cancelar_feria(p_feria text, p_motivo text default '')
returns jsonb language plpgsql security definer set search_path = public as $$
declare f record; p record; v_n int := 0;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  select * into f from public.ferias where id = p_feria for update;
  if not found then raise exception 'FERIA_NO_EXISTE'; end if;
  if f.estado = 'CANCELADA' then return jsonb_build_object('feria_id', p_feria, 'estado', 'CANCELADA', 'avisadas', 0); end if;
  update public.ferias set estado = 'CANCELADA', motivo_cancelacion = nullif(btrim(coalesce(p_motivo, '')), '') where id = p_feria;
  if public.feria_publicada(f.estado) then
    for p in select pa.tienda_id, t.owner_id from public.participaciones pa join public.tiendas t on t.id = pa.tienda_id
              where pa.feria_id = p_feria and pa.invitacion in ('PENDIENTE', 'CONFIRMADA', 'AGREGADA') loop
      if public.crear_aviso(p.owner_id, 'FERIA_CANCELADA', 'Se canceló ' || f.nombre,
           coalesce(nullif(btrim(coalesce(p_motivo, '')), ''), 'La feria no se realizará.'), f.id, p.tienda_id,
           '?feria=' || f.id, 'cancelada:' || f.id || ':' || p.tienda_id) is not null then
        v_n := v_n + 1;
      end if;
    end loop;
  end if;
  perform public.registrar_bitacora('CANCELAR_FERIA', p_feria, jsonb_build_object('motivo', p_motivo, 'avisadas', v_n));
  return jsonb_build_object('feria_id', p_feria, 'estado', 'CANCELADA', 'avisadas', v_n);
end;
$$;

-- Invitar (p_directo = false) o agregar directamente (true) tiendas y asociaciones (RF-FER-02/03).
create or replace function public.invitar_a_feria(p_feria text, p_tiendas text[], p_asociaciones text[], p_directo boolean default false)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  f record; v_tienda text; v_asoc text; v_actual text;
  v_estado text := case when p_directo then 'AGREGADA' else 'PENDIENTE' end;
  v_nuevas int := 0; v_cambiadas int := 0; v_sin_cambio int := 0;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  select * into f from public.ferias where id = p_feria for update;
  if not found then raise exception 'FERIA_NO_EXISTE'; end if;
  if not public.feria_activa(f.estado, f.fecha_inicio, f.fecha_fin) then raise exception 'FERIA_NO_ACTIVA'; end if;

  -- Tiendas elegidas una a una: la decisión directa del administrador manda.
  foreach v_tienda in array coalesce(p_tiendas, '{}') loop
    if not exists (select 1 from public.tiendas where id = v_tienda) then raise exception 'TIENDA_NO_EXISTE: %', v_tienda; end if;
    select invitacion into v_actual from public.participaciones where feria_id = p_feria and tienda_id = v_tienda for update;
    if not found then
      insert into public.participaciones (feria_id, tienda_id, invitacion, origen) values (p_feria, v_tienda, v_estado, 'DIRECTA');
      v_nuevas := v_nuevas + 1;
    elsif p_directo and v_actual in ('PENDIENTE', 'RECHAZADA') then
      update public.participaciones set invitacion = 'AGREGADA', origen = 'DIRECTA', asociacion_id = null
       where feria_id = p_feria and tienda_id = v_tienda;
      v_cambiadas := v_cambiadas + 1;
    elsif not p_directo and v_actual = 'RECHAZADA' then
      -- Volver a invitar a quien dijo que no: queda pendiente de nuevo.
      update public.participaciones set invitacion = 'PENDIENTE', respondida_en = null, invitada_en = now()
       where feria_id = p_feria and tienda_id = v_tienda;
      v_cambiadas := v_cambiadas + 1;
    else
      v_sin_cambio := v_sin_cambio + 1;
    end if;
  end loop;

  -- Asociaciones: cada integrante actual recibe su propia participación; no se pisan las existentes.
  foreach v_asoc in array coalesce(p_asociaciones, '{}') loop
    if not exists (select 1 from public.asociaciones where id = v_asoc) then raise exception 'ASOCIACION_NO_EXISTE: %', v_asoc; end if;
    insert into public.feria_asociaciones (feria_id, asociacion_id, modo)
    values (p_feria, v_asoc, case when p_directo then 'AGREGADA' else 'INVITADA' end)
    on conflict (feria_id, asociacion_id) do update
      set modo = case when excluded.modo = 'AGREGADA' then 'AGREGADA' else feria_asociaciones.modo end;
    for v_tienda in select ta.tienda_id from public.tienda_asociaciones ta where ta.asociacion_id = v_asoc order by ta.tienda_id loop
      select invitacion into v_actual from public.participaciones where feria_id = p_feria and tienda_id = v_tienda for update;
      if not found then
        insert into public.participaciones (feria_id, tienda_id, invitacion, origen, asociacion_id)
        values (p_feria, v_tienda, v_estado, 'ASOCIACION', v_asoc);
        v_nuevas := v_nuevas + 1;
      elsif p_directo and v_actual = 'PENDIENTE' then
        update public.participaciones set invitacion = 'AGREGADA' where feria_id = p_feria and tienda_id = v_tienda;
        v_cambiadas := v_cambiadas + 1;
      else
        v_sin_cambio := v_sin_cambio + 1;   -- ya invitada, confirmada o rechazada: se respeta
      end if;
    end loop;
  end loop;

  perform public.registrar_bitacora(case when p_directo then 'AGREGAR_A_FERIA' else 'INVITAR_A_FERIA' end, p_feria,
    jsonb_build_object('tiendas', p_tiendas, 'asociaciones', p_asociaciones, 'nuevas', v_nuevas, 'cambiadas', v_cambiadas));
  return jsonb_build_object('nuevas', v_nuevas, 'cambiadas', v_cambiadas, 'sin_cambio', v_sin_cambio);
end;
$$;

-- La emprendedora responde con un toque (RF-FER-08). Idempotente: repetir no cambia nada.
create or replace function public.responder_invitacion(p_feria text, p_participo boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_tienda text := public.mi_tienda_id(); f record; v_actual text;
  v_nuevo text := case when p_participo then 'CONFIRMADA' else 'RECHAZADA' end;
begin
  if auth.uid() is null then raise exception 'SIN_SESION' using errcode = '42501'; end if;
  if v_tienda is null then raise exception 'SIN_TIENDA' using errcode = '42501'; end if;
  if p_participo is null then raise exception 'RESPUESTA_INVALIDA'; end if;
  select * into f from public.ferias where id = p_feria;
  if not found then raise exception 'SIN_INVITACION'; end if;
  select invitacion into v_actual from public.participaciones where feria_id = p_feria and tienda_id = v_tienda for update;
  if not found then raise exception 'SIN_INVITACION'; end if;
  if v_actual = 'AGREGADA' then raise exception 'AGREGADA_POR_ADMINISTRACION'; end if;
  if not public.feria_activa(f.estado, f.fecha_inicio, f.fecha_fin) then raise exception 'FERIA_NO_ACTIVA'; end if;
  if v_actual = v_nuevo then
    return jsonb_build_object('feria_id', p_feria, 'invitacion', v_actual, 'cambio', false);
  end if;
  update public.participaciones set invitacion = v_nuevo, respondida_en = now()
   where feria_id = p_feria and tienda_id = v_tienda;
  return jsonb_build_object('feria_id', p_feria, 'invitacion', v_nuevo, 'cambio', true);
end;
$$;

-- Asistencia real (RF-FER-07): desde el primer día de la feria hasta 3 días después del último.
create or replace function public.registrar_asistencia(p_feria text, p_tienda text, p_asistencia text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare f record; v_actual text;
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  if p_asistencia not in ('ASISTIO', 'NO_ASISTIO', 'PENDIENTE') then raise exception 'ASISTENCIA_INVALIDA'; end if;
  select * into f from public.ferias where id = p_feria;
  if not found then raise exception 'FERIA_NO_EXISTE'; end if;
  if f.estado in ('BORRADOR', 'CANCELADA') then raise exception 'FERIA_NO_ACTIVA'; end if;
  if f.fecha_inicio is null or public.fecha_santiago() < f.fecha_inicio then raise exception 'AUN_NO_ES_EL_DIA'; end if;
  if public.fecha_santiago() > coalesce(f.fecha_fin, f.fecha_inicio) + 3 then raise exception 'PLAZO_VENCIDO'; end if;
  select invitacion into v_actual from public.participaciones where feria_id = p_feria and tienda_id = p_tienda for update;
  if not found then raise exception 'SIN_PARTICIPACION'; end if;
  if v_actual not in ('CONFIRMADA', 'AGREGADA') then raise exception 'NO_PARTICIPA'; end if;
  update public.participaciones set asistencia = p_asistencia where feria_id = p_feria and tienda_id = p_tienda;
  perform public.registrar_bitacora('ASISTENCIA', p_feria, jsonb_build_object('tienda', p_tienda, 'asistencia', p_asistencia));
  return jsonb_build_object('feria_id', p_feria, 'tienda_id', p_tienda, 'asistencia', p_asistencia);
end;
$$;

-- Recordatorio a pendientes o aviso a confirmadas (RF-FER-06). p_solicitud evita duplicar por doble clic.
create or replace function public.enviar_aviso_feria(p_feria text, p_tipo text, p_mensaje text, p_solicitud uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare f record; p record; v_n int := 0; v_mensaje text := btrim(coalesce(p_mensaje, ''));
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  if p_tipo not in ('RECORDATORIO', 'AVISO_FERIA') then raise exception 'TIPO_INVALIDO'; end if;
  if p_solicitud is null then raise exception 'FALTA_SOLICITUD'; end if;
  if p_tipo = 'AVISO_FERIA' and v_mensaje = '' then raise exception 'FALTA_MENSAJE'; end if;
  select * into f from public.ferias where id = p_feria;
  if not found then raise exception 'FERIA_NO_EXISTE'; end if;
  if not public.feria_activa(f.estado, f.fecha_inicio, f.fecha_fin) then raise exception 'FERIA_NO_ACTIVA'; end if;
  if not public.registrar_bitacora(p_tipo, p_feria, jsonb_build_object('mensaje', v_mensaje), p_solicitud) then
    -- Ya se procesó esta misma solicitud: no se envía de nuevo.
    return jsonb_build_object('destinatarias', (select coalesce((detalle->>'destinatarias')::int, 0) from public.bitacora_admin where solicitud = p_solicitud), 'repetida', true);
  end if;
  for p in
    select pa.tienda_id, t.owner_id from public.participaciones pa join public.tiendas t on t.id = pa.tienda_id
     where pa.feria_id = p_feria
       and ((p_tipo = 'RECORDATORIO' and pa.invitacion = 'PENDIENTE')
         or (p_tipo = 'AVISO_FERIA' and pa.invitacion in ('CONFIRMADA', 'AGREGADA')))
  loop
    if public.crear_aviso(p.owner_id, p_tipo,
         case when p_tipo = 'RECORDATORIO' then 'Recuerda responder: ' || f.nombre else 'Aviso de ' || f.nombre end,
         case when v_mensaje <> '' then v_mensaje when p_tipo = 'RECORDATORIO' then 'Aún no respondes la invitación. Abre la app y elige «Participo» o «No participo».' end,
         f.id, p.tienda_id, '?feria=' || f.id, lower(p_tipo) || ':' || p_solicitud || ':' || p.tienda_id) is not null then
      v_n := v_n + 1;
    end if;
  end loop;
  update public.bitacora_admin set detalle = detalle || jsonb_build_object('destinatarias', v_n) where solicitud = p_solicitud;
  return jsonb_build_object('destinatarias', v_n, 'repetida', false);
end;
$$;

-- Ficha pública: solo confirmadas o agregadas de ferias publicadas (RF-FER-10). Nunca rechazadas ni pendientes.
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
   order by t.nombre;
$$;

-- Checklist del administrador con datos para buscar y filtrar (RF-FER-04/05).
create or replace function public.checklist_feria(p_feria text)
returns table (tienda_id text, nombre text, rubro text, sector text, asociaciones text[], invitacion text,
               asistencia text, origen text, via_asociacion text, invitada_en timestamptz, respondida_en timestamptz)
language plpgsql stable security definer set search_path = public as $$
#variable_conflict use_column
begin
  if not public.es_admin() then raise exception 'SOLO_ADMIN' using errcode = '42501'; end if;
  return query
  select t.id, t.nombre, t.rubro, t.sector,
         coalesce((select array_agg(ta.asociacion_id order by ta.asociacion_id) from public.tienda_asociaciones ta where ta.tienda_id = t.id), '{}'),
         pa.invitacion, pa.asistencia, pa.origen, pa.asociacion_id, pa.invitada_en, pa.respondida_en
    from public.participaciones pa join public.tiendas t on t.id = pa.tienda_id
   where pa.feria_id = p_feria
   order by t.nombre;
end;
$$;

-- Mis ferias (RF-FER-09): invitaciones y participaciones de mi tienda con los datos de la feria.
create or replace function public.mis_ferias()
returns table (feria_id text, nombre text, ubicacion text, fecha_inicio date, fecha_fin date, horario text,
               estado text, latitud double precision, longitud double precision, invitacion text, asistencia text,
               origen text, invitada_en timestamptz, respondida_en timestamptz)
language sql stable security definer set search_path = public as $$
  select f.id, f.nombre, f.ubicacion, f.fecha_inicio, f.fecha_fin, f.horario, f.estado, f.latitud, f.longitud,
         pa.invitacion, pa.asistencia, pa.origen, pa.invitada_en, pa.respondida_en
    from public.participaciones pa join public.ferias f on f.id = pa.feria_id
   where pa.tienda_id = public.mi_tienda_id() and public.feria_publicada(f.estado)
   order by f.fecha_inicio nulls last, f.nombre;
$$;

-- Seguir y dejar de seguir (RF-CMP-06). Repetir no duplica.
create or replace function public.seguir(p_tipo text, p_id text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'SIN_SESION' using errcode = '42501'; end if;
  if p_tipo = 'TIENDA' then
    if not exists (select 1 from public.tiendas where id = p_id) then raise exception 'NO_EXISTE'; end if;
  elsif p_tipo = 'ASOCIACION' then
    if not exists (select 1 from public.asociaciones where id = p_id and activa) then raise exception 'NO_EXISTE'; end if;
  else raise exception 'TIPO_INVALIDO'; end if;
  insert into public.seguimientos (usuario_id, tipo, objetivo_id) values (auth.uid(), p_tipo, p_id) on conflict do nothing;
  return jsonb_build_object('tipo', p_tipo, 'id', p_id, 'siguiendo', true);
end;
$$;

create or replace function public.dejar_de_seguir(p_tipo text, p_id text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'SIN_SESION' using errcode = '42501'; end if;
  delete from public.seguimientos where usuario_id = auth.uid() and tipo = p_tipo and objetivo_id = p_id;
  return jsonb_build_object('tipo', p_tipo, 'id', p_id, 'siguiendo', false);
end;
$$;

-- Consentimiento explícito para avisos de ferias (RNF-PRI-02). Se puede retirar.
create or replace function public.guardar_consentimiento(p_avisos boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'SIN_SESION' using errcode = '42501'; end if;
  if p_avisos is null then raise exception 'VALOR_INVALIDO'; end if;
  insert into public.preferencias_avisos (usuario_id, avisos_ferias, consentido_en, retirado_en)
  values (auth.uid(), p_avisos, case when p_avisos then now() end, case when not p_avisos then now() end)
  on conflict (usuario_id) do update set
    avisos_ferias = excluded.avisos_ferias,
    consentido_en = case when excluded.avisos_ferias then coalesce(case when preferencias_avisos.avisos_ferias then preferencias_avisos.consentido_en end, now()) else preferencias_avisos.consentido_en end,
    retirado_en = case when excluded.avisos_ferias then null else now() end,
    actualizado_en = now();
  -- Al retirar el consentimiento, lo que aún no se envió ya no se envía.
  if not p_avisos then
    update public.avisos set estado = 'OMITIDO', motivo = 'CONSENTIMIENTO_RETIRADO'
     where usuario_id = auth.uid() and tipo = 'PARTICIPACION_SEGUIDA' and estado in ('PENDIENTE', 'ENVIANDO');
  end if;
  return jsonb_build_object('avisos_ferias', p_avisos);
end;
$$;

-- Dispositivos (token de Firebase Cloud Messaging de la app o del navegador).
-- Si el token estaba en otra cuenta (otra persona entró en el mismo celular), pasa a la actual.
create or replace function public.registrar_dispositivo(p_token text, p_plataforma text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'SIN_SESION' using errcode = '42501'; end if;
  if p_plataforma not in ('ANDROID', 'WEB') then raise exception 'PLATAFORMA_INVALIDA'; end if;
  insert into public.dispositivos (token, usuario_id, plataforma, activo)
  values (p_token, auth.uid(), p_plataforma, true)
  on conflict (token) do update set usuario_id = auth.uid(), plataforma = excluded.plataforma, activo = true, actualizado_en = now();
  return jsonb_build_object('registrado', true);
end;
$$;

create or replace function public.quitar_dispositivo(p_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'SIN_SESION' using errcode = '42501'; end if;
  delete from public.dispositivos where token = p_token and usuario_id = auth.uid();
  return jsonb_build_object('quitado', true);
end;
$$;

-- Bandeja: marcar leído (solo los propios). Que una notificación se haya MOSTRADO o ABIERTO
-- en un navegador o celular se informa por destino con confirmar_aviso() (sección 9).
drop function if exists public.marcar_leido(uuid), public.confirmar_aviso(uuid, text, text);
create or replace function public.marcar_leido(p_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_n int;
begin
  if auth.uid() is null then raise exception 'SIN_SESION' using errcode = '42501'; end if;
  update public.avisos set leido_en = coalesce(leido_en, now()) where id = p_id and usuario_id = auth.uid();
  get diagnostics v_n = row_count;
  return jsonb_build_object('actualizado', v_n = 1);
end;
$$;

-- Avisos de contacto con horario (RF-CFG-04). Solo la dueña.
create or replace function public.guardar_avisos_contacto(p_activos boolean, p_desde time, p_hasta time)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_tienda text := public.mi_tienda_id();
begin
  if v_tienda is null then raise exception 'SIN_TIENDA' using errcode = '42501'; end if;
  if p_activos is null or p_desde is null or p_hasta is null then raise exception 'VALOR_INVALIDO'; end if;
  insert into public.preferencias_tienda (tienda_id, avisos_contacto, avisos_desde, avisos_hasta)
  values (v_tienda, p_activos, p_desde, p_hasta)
  on conflict (tienda_id) do update set avisos_contacto = excluded.avisos_contacto,
    avisos_desde = excluded.avisos_desde, avisos_hasta = excluded.avisos_hasta, actualizado_en = now();
  return jsonb_build_object('avisos_contacto', p_activos, 'avisos_desde', p_desde, 'avisos_hasta', p_hasta);
end;
$$;

-- Calificar (RF-CMP-09). El servidor exige que la tienda tenga las calificaciones activas.
create or replace function public.calificar(p_tienda text, p_producto text, p_estrellas int, p_comentario text default '')
returns jsonb language plpgsql security definer set search_path = public as $$
declare t record; pr record;
begin
  if auth.uid() is null then raise exception 'SIN_SESION' using errcode = '42501'; end if;
  select id, owner_id, calificaciones_activas into t from public.tiendas where id = p_tienda;
  if not found then raise exception 'NO_EXISTE'; end if;
  if not t.calificaciones_activas then raise exception 'CALIFICACIONES_DESACTIVADAS'; end if;
  if t.owner_id = auth.uid() then raise exception 'PROPIA_TIENDA'; end if;
  if p_estrellas is null or p_estrellas not between 1 and 5 then raise exception 'ESTRELLAS_INVALIDAS'; end if;
  if p_producto is not null then
    select * into pr from public.productos where id = p_producto and tienda_id = p_tienda;
    if not found or public.estado_publico_producto(pr.publicado, pr.disponible, pr.vendida_en, pr.maneja_stock,
                      pr.modalidad, pr.al_agotarse, pr.variantes) = 'OCULTO' then
      raise exception 'PRODUCTO_NO_PUBLICO';
    end if;
  end if;
  insert into public.calificaciones (tienda_id, producto_id, usuario_id, estrellas, comentario)
  values (p_tienda, p_producto, auth.uid(), p_estrellas, left(btrim(coalesce(p_comentario, '')), 500))
  on conflict (usuario_id, tienda_id, coalesce(producto_id, '')) do update
    set estrellas = excluded.estrellas, comentario = excluded.comentario, actualizado_en = now();
  return jsonb_build_object('tienda_id', p_tienda, 'producto_id', p_producto, 'estrellas', p_estrellas);
end;
$$;

-- Resumen público: promedio y cantidad, sin identificar a quien calificó. Con las
-- calificaciones desactivadas no se muestra nada.
create or replace function public.resumen_calificaciones(p_tienda text)
returns jsonb language sql stable security definer set search_path = public as $$
  select case when not coalesce((select calificaciones_activas from public.tiendas where id = p_tienda), false)
    then jsonb_build_object('activas', false)
    else jsonb_build_object(
      'activas', true,
      'tienda', (select jsonb_build_object('promedio', round(avg(estrellas)::numeric, 1), 'cantidad', count(*))
                   from public.calificaciones where tienda_id = p_tienda and producto_id is null),
      'productos', coalesce((select jsonb_object_agg(producto_id, jsonb_build_object('promedio', prom, 'cantidad', n))
                   from (select producto_id, round(avg(estrellas)::numeric, 1) prom, count(*) n
                           from public.calificaciones where tienda_id = p_tienda and producto_id is not null
                          group by producto_id) x), '{}'::jsonb))
  end;
$$;

-- ---------- 9. ¿Corresponde notificar? y servicio de envío (nunca service_role) ----------

-- Resultado por dispositivo: así un aviso aceptado en un celular y con falla temporal
-- en otro se reintenta SOLO en el que falló (sin duplicar en el que ya lo aceptó).
--  ACEPTADO       FCM aceptó el mensaje (no prueba que el celular lo haya mostrado)
--  FALLA_TEMPORAL se reintenta
--  INVALIDO       token dado de baja: el dispositivo se desactiva
--  RECHAZADO      FCM rechazó el CONTENIDO del mensaje: el dispositivo sigue activo y no se reintenta
--  MOSTRADO       el navegador o la app de ESE destino mostró la notificación (lo informa el cliente)
--  ABIERTO        la persona la tocó en ese destino
-- El "destino" es el token FCM del celular o navegador, o el identificador de una página
-- web abierta sin push. La decisión de si la PERSONA debe recibir el aviso es una sola
-- (motivo_no_notificar); si cada destino ya lo recibió se decide aquí, destino por destino.
create table if not exists public.entregas_aviso (
  aviso_id       uuid not null references public.avisos (id) on delete cascade,
  token          text not null,
  estado         text not null,
  intentos       int not null default 1,
  actualizado_en timestamptz not null default now(),
  primary key (aviso_id, token)
);
alter table public.entregas_aviso drop constraint if exists entregas_aviso_estado_check;
alter table public.entregas_aviso drop constraint if exists entregas_aviso_estado_ck;
alter table public.entregas_aviso add constraint entregas_aviso_estado_ck
  check (estado in ('ACEPTADO', 'FALLA_TEMPORAL', 'INVALIDO', 'RECHAZADO', 'MOSTRADO', 'ABIERTO'));
alter table public.entregas_aviso enable row level security;   -- sin políticas: solo funciones del servidor
revoke all on public.entregas_aviso from anon, authenticated;

-- ÚNICA regla de si corresponde notificar un aviso AHORA. La usan el push (avisos_por_enviar)
-- y los mecanismos alternativos (avisos_para_mostrar: revisión periódica del celular y la web
-- abierta). Devuelve null si corresponde, o el motivo por el que no.
create or replace function public.motivo_no_notificar(a public.avisos)
returns text language plpgsql stable security definer set search_path = public as $$
declare f record; t record;
begin
  if a.creado_en < now() - interval '3 days' then return 'VENCIDO'; end if;
  if a.tipo = 'PARTICIPACION_SEGUIDA' then
    if not exists (select 1 from public.preferencias_avisos pa where pa.usuario_id = a.usuario_id and pa.avisos_ferias) then
      return 'SIN_CONSENTIMIENTO';
    end if;
    if not exists (select 1 from public.seguimientos sg where sg.usuario_id = a.usuario_id
                    and sg.tipo = split_part(a.clave, ':', 3) and sg.objetivo_id = split_part(a.clave, ':', 4)) then
      return 'YA_NO_SIGUE';
    end if;
  end if;
  if a.tipo = 'CONTACTO' then
    select coalesce(pt.avisos_contacto, false) activos, pt.avisos_desde, pt.avisos_hasta into t
      from public.tiendas ti left join public.preferencias_tienda pt on pt.tienda_id = ti.id where ti.id = a.tienda_id;
    if not coalesce(t.activos, false) then return 'AVISOS_DESACTIVADOS'; end if;
    if not public.dentro_de_horario(public.hora_santiago(), t.avisos_desde, t.avisos_hasta) then return 'FUERA_DE_HORARIO'; end if;
  end if;
  if a.tipo in ('INVITACION', 'RECORDATORIO') and exists (
       select 1 from public.participaciones pa where pa.feria_id = a.feria_id and pa.tienda_id = a.tienda_id and pa.invitacion <> 'PENDIENTE') then
    return 'YA_RESPONDIO';
  end if;
  if a.feria_id is not null and a.tipo <> 'FERIA_CANCELADA' then
    select estado, fecha_inicio, fecha_fin into f from public.ferias where id = a.feria_id;
    if not found or not public.feria_activa(f.estado, f.fecha_inicio, f.fecha_fin) then return 'FERIA_NO_ACTIVA'; end if;
  end if;
  return null;
end;
$$;
revoke all on function public.motivo_no_notificar(public.avisos) from public, anon, authenticated;

-- Marca OMITIDO lo pendiente que ya no corresponde (de todos o de una cuenta).
create or replace function public.revalidar_avisos(p_usuario uuid default null)
returns int language plpgsql security definer set search_path = public as $$
declare v_n int;
begin
  update public.avisos a set estado = 'OMITIDO', motivo = public.motivo_no_notificar(a)
   where a.estado = 'PENDIENTE' and (p_usuario is null or a.usuario_id = p_usuario)
     and public.motivo_no_notificar(a) is not null;
  get diagnostics v_n = row_count;
  return v_n;
end;
$$;
revoke all on function public.revalidar_avisos(uuid) from public, anon, authenticated;

-- Toma un lote y devuelve, para cada aviso, los dispositivos ACTUALES de la cuenta que
-- aún no lo aceptaron. Si un token pasó a otra cuenta, ya no aparece aquí.
create or replace function public.avisos_por_enviar(p_limite int default 50)
returns table (id uuid, usuario_id uuid, tipo text, titulo text, cuerpo text, feria_id text, enlace text,
               intentos int, tokens jsonb)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
begin
  if not public.es_notificador() then raise exception 'SOLO_NOTIFICADOR' using errcode = '42501'; end if;
  -- Envíos interrumpidos hace más de 10 minutos vuelven a la cola.
  update public.avisos a set estado = 'PENDIENTE'
   where a.estado = 'ENVIANDO' and a.proximo_intento < now() - interval '10 minutes';
  perform public.revalidar_avisos(null);
  return query
  with lote as (
    select a.id from public.avisos a
     where a.estado = 'PENDIENTE' and a.proximo_intento <= now()
     order by a.creado_en
     limit greatest(1, least(coalesce(p_limite, 50), 200))
     for update skip locked
  ), marcados as (
    update public.avisos a set estado = 'ENVIANDO', intentos = a.intentos + 1, proximo_intento = now()
      from lote where a.id = lote.id
    returning a.*
  )
  select m.id, m.usuario_id, m.tipo, m.titulo, m.cuerpo, m.feria_id, m.enlace, m.intentos,
         coalesce((select jsonb_agg(jsonb_build_object('token', d.token, 'plataforma', d.plataforma))
                     from public.dispositivos d
                    where d.usuario_id = m.usuario_id and d.activo
                      and not exists (select 1 from public.entregas_aviso e
                                       where e.aviso_id = m.id and e.token = d.token and e.estado in ('ACEPTADO', 'INVALIDO', 'RECHAZADO', 'MOSTRADO', 'ABIERTO'))),
                  '[]'::jsonb)
    from marcados m;
end;
$$;

-- Mecanismo alternativo (sin push): un destino de la propia cuenta (página web abierta o
-- revisión periódica del celular) pide qué avisos puede MOSTRAR ahora. Misma regla que el
-- push para la persona; y para el destino, solo lo que ESE destino aún no aceptó, mostró ni
-- abrió. Que otro destino ya lo haya mostrado no impide mostrarlo aquí. Máximo 20.
drop function if exists public.avisos_para_mostrar(int);
create or replace function public.avisos_para_mostrar(p_destino text, p_limite int default 10)
returns table (id uuid, tipo text, titulo text, cuerpo text, feria_id text, enlace text, creado_en timestamptz)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
begin
  if auth.uid() is null then raise exception 'SIN_SESION' using errcode = '42501'; end if;
  if p_destino is null or char_length(p_destino) not between 8 and 4096 then raise exception 'DESTINO_INVALIDO'; end if;
  perform public.revalidar_avisos(auth.uid());
  return query
  select a.id, a.tipo, a.titulo, a.cuerpo, a.feria_id, a.enlace, a.creado_en
    from public.avisos a
   where a.usuario_id = auth.uid() and a.estado not in ('OMITIDO', 'ENVIANDO')
     and public.motivo_no_notificar(a) is null
     and not exists (select 1 from public.entregas_aviso e where e.aviso_id = a.id and e.token = p_destino
                      and e.estado in ('ACEPTADO', 'MOSTRADO', 'ABIERTO'))
   order by a.creado_en desc
   limit greatest(1, least(coalesce(p_limite, 10), 20));
end;
$$;

-- Único evento de confirmación del cliente: este destino MOSTRÓ o la persona ABRIÓ el aviso.
-- Idempotente: repetirlo en el mismo destino no crea otro evento; ABIERTO no vuelve a MOSTRADO.
create or replace function public.confirmar_aviso(p_id uuid, p_destino text, p_evento text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_previo text;
begin
  if auth.uid() is null then raise exception 'SIN_SESION' using errcode = '42501'; end if;
  if p_evento not in ('MOSTRADO', 'ABIERTO') then raise exception 'EVENTO_INVALIDO'; end if;
  if p_destino is null or char_length(p_destino) not between 8 and 4096 then raise exception 'DESTINO_INVALIDO'; end if;
  if not exists (select 1 from public.avisos where id = p_id and usuario_id = auth.uid()) then
    return jsonb_build_object('registrado', false);
  end if;
  select estado into v_previo from public.entregas_aviso where aviso_id = p_id and token = p_destino for update;
  if v_previo = p_evento or (v_previo = 'ABIERTO' and p_evento = 'MOSTRADO') then
    return jsonb_build_object('registrado', false, 'estado', v_previo);
  end if;
  insert into public.entregas_aviso (aviso_id, token, estado) values (p_id, p_destino, p_evento)
  on conflict (aviso_id, token) do update set estado = excluded.estado, actualizado_en = now();
  -- Resumen de la cuenta: primera vez que se vio en algún destino / primera vez que se abrió.
  update public.avisos set recibido_en = coalesce(recibido_en, now()),
         leido_en = case when p_evento = 'ABIERTO' then coalesce(leido_en, now()) else leido_en end
   where id = p_id;
  return jsonb_build_object('registrado', true, 'estado', p_evento);
end;
$$;

-- Resultado de un intento, por dispositivo:
--  · alguno con falla temporal y quedan intentos → PENDIENTE (se reintenta solo en esos);
--  · alguno aceptado (ahora o antes) → ENVIADO;
--  · ninguno aceptado, con fallas o mensaje rechazado → FALLIDO;
--  · sin dispositivos válidos → SIN_DISPOSITIVO.
-- Los tokens inválidos se desactivan; los de un mensaje rechazado siguen activos.
drop function if exists public.resultado_envio(uuid, text, text, text[]);
drop function if exists public.resultado_envio(uuid, text[], text[], text[], text, text[]);
create or replace function public.resultado_envio(p_id uuid, p_aceptados text[], p_temporales text[], p_invalidos text[],
                                                  p_motivo text default null, p_rechazados text[] default '{}')
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_intentos int; v_estado text; v_aceptados int; t text;
  v_temporales int := coalesce(array_length(p_temporales, 1), 0);
  v_rechazados int := coalesce(array_length(p_rechazados, 1), 0);
begin
  if not public.es_notificador() then raise exception 'SOLO_NOTIFICADOR' using errcode = '42501'; end if;
  select intentos into v_intentos from public.avisos where id = p_id and estado = 'ENVIANDO' for update;
  if not found then return jsonb_build_object('actualizado', false); end if;
  foreach t in array coalesce(p_aceptados, '{}') loop
    insert into public.entregas_aviso (aviso_id, token, estado) values (p_id, t, 'ACEPTADO')
    on conflict (aviso_id, token) do update set estado = 'ACEPTADO', intentos = entregas_aviso.intentos + 1, actualizado_en = now()
      where entregas_aviso.estado not in ('MOSTRADO', 'ABIERTO');
  end loop;
  foreach t in array coalesce(p_temporales, '{}') loop
    insert into public.entregas_aviso (aviso_id, token, estado) values (p_id, t, 'FALLA_TEMPORAL')
    on conflict (aviso_id, token) do update set estado = 'FALLA_TEMPORAL', intentos = entregas_aviso.intentos + 1, actualizado_en = now()
      where entregas_aviso.estado not in ('ACEPTADO', 'MOSTRADO', 'ABIERTO');
  end loop;
  foreach t in array coalesce(p_rechazados, '{}') loop
    insert into public.entregas_aviso (aviso_id, token, estado) values (p_id, t, 'RECHAZADO')
    on conflict (aviso_id, token) do update set estado = 'RECHAZADO', actualizado_en = now()
      where entregas_aviso.estado not in ('ACEPTADO', 'MOSTRADO', 'ABIERTO');
  end loop;
  foreach t in array coalesce(p_invalidos, '{}') loop
    insert into public.entregas_aviso (aviso_id, token, estado) values (p_id, t, 'INVALIDO')
    on conflict (aviso_id, token) do update set estado = 'INVALIDO', actualizado_en = now();
  end loop;
  update public.dispositivos set activo = false, actualizado_en = now() where token = any(coalesce(p_invalidos, '{}'));
  -- "Aceptado" cuenta solo destinos FCM (un celular que ya lo mostró también lo recibió por FCM o por la revisión).
  select count(*) into v_aceptados from public.entregas_aviso e
   where e.aviso_id = p_id and (e.estado = 'ACEPTADO'
         or (e.estado in ('MOSTRADO', 'ABIERTO') and exists (select 1 from public.dispositivos d where d.token = e.token)));
  v_estado := case
    when v_temporales > 0 and v_intentos < 5 then 'PENDIENTE'
    when v_aceptados > 0 then 'ENVIADO'
    when v_temporales > 0 or v_rechazados > 0 then 'FALLIDO'
    else 'SIN_DISPOSITIVO' end;
  update public.avisos set estado = v_estado,
         motivo = left(coalesce(p_motivo, '') || ' [aceptado en ' || v_aceptados || ', por reintentar ' || v_temporales ||
                       ', mensaje rechazado en ' || v_rechazados || ']', 300),
         enviado_en = case when v_aceptados > 0 then coalesce(enviado_en, now()) else enviado_en end,
         proximo_intento = now() + make_interval(mins => power(2, least(v_intentos, 6))::int)
   where id = p_id;
  return jsonb_build_object('actualizado', true, 'estado', v_estado, 'aceptados', v_aceptados,
                            'por_reintentar', v_temporales, 'rechazados', v_rechazados);
end;
$$;

-- ---------- 10. Permisos ----------

revoke all on function
  public.publicar_feria(text), public.cancelar_feria(text, text),
  public.invitar_a_feria(text, text[], text[], boolean), public.responder_invitacion(text, boolean),
  public.registrar_asistencia(text, text, text), public.enviar_aviso_feria(text, text, text, uuid),
  public.participantes_feria(text), public.checklist_feria(text), public.mis_ferias(),
  public.seguir(text, text), public.dejar_de_seguir(text, text), public.guardar_consentimiento(boolean),
  public.registrar_dispositivo(text, text), public.quitar_dispositivo(text), public.marcar_leido(uuid), public.confirmar_aviso(uuid, text, text),
  public.calificar(text, text, int, text), public.resumen_calificaciones(text), public.guardar_avisos_contacto(boolean, time, time),
  public.avisos_por_enviar(int), public.resultado_envio(uuid, text[], text[], text[], text, text[]), public.es_notificador(),
  public.avisos_para_mostrar(text, int)
from public, anon;

grant execute on function public.participantes_feria(text), public.resumen_calificaciones(text) to anon, authenticated;
grant execute on function
  public.publicar_feria(text), public.cancelar_feria(text, text),
  public.invitar_a_feria(text, text[], text[], boolean), public.responder_invitacion(text, boolean),
  public.registrar_asistencia(text, text, text), public.enviar_aviso_feria(text, text, text, uuid),
  public.checklist_feria(text), public.mis_ferias(),
  public.seguir(text, text), public.dejar_de_seguir(text, text), public.guardar_consentimiento(boolean),
  public.registrar_dispositivo(text, text), public.quitar_dispositivo(text), public.marcar_leido(uuid), public.confirmar_aviso(uuid, text, text),
  public.calificar(text, text, int, text), public.guardar_avisos_contacto(boolean, time, time),
  public.avisos_por_enviar(int), public.resultado_envio(uuid, text[], text[], text[], text, text[]), public.es_notificador(),
  public.avisos_para_mostrar(text, int)
to authenticated;

-- Tablas nuevas: lectura según RLS; ninguna escritura directa de clientes (todo por funciones).
grant select on public.tienda_asociaciones to anon, authenticated;
grant select on public.preferencias_tienda, public.seguimientos, public.preferencias_avisos, public.dispositivos, public.avisos,
                public.calificaciones, public.feria_asociaciones, public.bitacora_admin, public.notificadores to authenticated;
revoke insert, update, delete on public.preferencias_tienda, public.seguimientos, public.preferencias_avisos, public.dispositivos,
                public.avisos, public.calificaciones, public.bitacora_admin, public.notificadores from anon, authenticated;
grant insert, update, delete on public.tienda_asociaciones, public.feria_asociaciones, public.participaciones to authenticated;
revoke insert, update, delete on public.tienda_asociaciones, public.feria_asociaciones, public.participaciones from anon;
-- Supabase concede por defecto TODOS los privilegios a anon y authenticated en las tablas
-- nuevas. El visitante sin sesión no necesita ninguno sobre estas tablas privadas: se quitan
-- (RLS ya devolvía 0 filas, pero así no queda ningún permiso de más).
revoke all on public.preferencias_tienda, public.seguimientos, public.preferencias_avisos, public.dispositivos,
              public.avisos, public.entregas_aviso, public.calificaciones, public.feria_asociaciones,
              public.bitacora_admin, public.notificadores from anon;
-- contactos (06): el visitante solo ENVÍA mensajes. La instancia le concedía además leer, editar y borrar.
revoke select, update, delete on public.contactos from anon;
revoke truncate, references, trigger on all tables in schema public from anon, authenticated;

insert into public.migraciones_aplicadas (nombre) values ('07_ferias_seguidores')
on conflict (nombre) do update set aplicada_en = now();

notify pgrst, 'reload schema';
