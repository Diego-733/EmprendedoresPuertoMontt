-- =====================================================================
-- alerceshopin · Entrega 6 — Vitrina, modo comprador, difusión y mapas
-- Ejecutar UNA vez en Supabase → SQL Editor, DESPUÉS de 01 a 05.
-- Se puede volver a ejecutar: usa "if not exists" y recrea políticas,
-- funciones y triggers. No borra ni reescribe datos existentes.
--
-- Cubre:
--  RF-CMP-01  directorio con filtro por asociación      → tiendas.asociacion_id
--  RF-ASO-03  vista pública de la asociación              → asociaciones.descripcion/sector/activa
--  RF-CMP-05, RF-CFG-02  forma de contacto                → tiendas.contacto_modo
--  RF-CFG-01  mostrar u ocultar precios                   → tiendas.mostrar_precios (ya existía)
--  RF-CFG-03  bandeja de contactos del formulario         → tabla contactos
--  RF-ADM-02, RF-ACC-06  navegación anónima de la app     → tabla configuracion
--  RF-FER-01, RF-MAP-01/02  ferias con datos y ubicación  → ferias.descripcion/imagen_url/organizador
--  RF-MAP-04  punto de venta o retiro                     → tiendas.punto_lat/punto_lng/punto_nombre
--  RNF-PRI-04 la ubicación de la PERSONA no se guarda: no existe ninguna columna para ella.
-- =====================================================================

-- ---------- 1. Columnas nuevas ----------

alter table public.tiendas add column if not exists asociacion_id text;
alter table public.tiendas add column if not exists contacto_modo text not null default 'AMBOS';
alter table public.tiendas add column if not exists punto_lat     double precision;
alter table public.tiendas add column if not exists punto_lng     double precision;
alter table public.tiendas add column if not exists punto_nombre  text;
alter table public.tiendas add column if not exists mostrar_precios boolean not null default true;

do $$ begin
  alter table public.tiendas add constraint tiendas_asociacion_fk
    foreign key (asociacion_id) references public.asociaciones (id) on delete set null;
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.tiendas add constraint tiendas_contacto_modo_ck
    check (contacto_modo in ('WHATSAPP', 'FORMULARIO', 'AMBOS'));
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.tiendas add constraint tiendas_punto_ck check (
    (punto_lat is null and punto_lng is null) or
    (punto_lat between -90 and 90 and punto_lng between -180 and 180));
exception when duplicate_object then null; end $$;
create index if not exists tiendas_asociacion_idx on public.tiendas (asociacion_id);

alter table public.asociaciones add column if not exists descripcion text not null default '';
alter table public.asociaciones add column if not exists sector      text not null default '';
alter table public.asociaciones add column if not exists whatsapp    text not null default '';
alter table public.asociaciones add column if not exists activa      boolean not null default true;

alter table public.ferias add column if not exists descripcion  text not null default '';
alter table public.ferias add column if not exists imagen_url   text;
alter table public.ferias add column if not exists organizador  text not null default '';
alter table public.ferias add column if not exists actualizado_en timestamptz not null default now();
do $$ begin
  alter table public.ferias add constraint ferias_coordenadas_ck check (
    (latitud is null and longitud is null) or
    (latitud between -90 and 90 and longitud between -180 and 180));
exception when duplicate_object then null; end $$;

-- ---------- 2. La emprendedora no puede cambiarse de asociación ni de dueña ----------
-- Solo el administrador asigna la asociación (RN-02). La política tiendas_editar
-- deja actualizar la fila completa; este trigger conserva las columnas reservadas.

create or replace function public.proteger_tienda()
returns trigger language plpgsql set search_path = public as $$
begin
  -- auth.uid() nulo = proceso del servidor (por ejemplo, el alta de la cuenta
  -- que asigna la dueña). Los clientes siempre llegan con una sesión.
  if auth.uid() is not null and not public.es_admin() then
    new.asociacion_id := old.asociacion_id;
    new.owner_id      := old.owner_id;
    new.id            := old.id;
  end if;
  if new.contacto_modo is null then new.contacto_modo := 'AMBOS'; end if;
  new.punto_nombre := nullif(btrim(coalesce(new.punto_nombre, '')), '');
  new.actualizado_en := now();
  return new;
end;
$$;

drop trigger if exists proteger_tienda on public.tiendas;
create trigger proteger_tienda
  before update on public.tiendas
  for each row execute function public.proteger_tienda();

-- ---------- 3. Configuración de la plataforma (navegación anónima) ----------

create table if not exists public.configuracion (
  clave          text primary key,
  valor          text not null,
  actualizado_en timestamptz not null default now()
);

alter table public.configuracion enable row level security;

drop policy if exists configuracion_ver on public.configuracion;
create policy configuracion_ver on public.configuracion for select using (true);
drop policy if exists configuracion_admin on public.configuracion;
create policy configuracion_admin on public.configuracion
  for all to authenticated using (public.es_admin()) with check (public.es_admin());

create or replace function public.validar_configuracion()
returns trigger language plpgsql set search_path = public as $$
begin
  if new.clave = 'navegacion_anonima' and new.valor not in ('PERMITIR', 'INVITAR', 'OBLIGAR') then
    raise exception 'Valor no válido para navegacion_anonima: %', new.valor;
  end if;
  new.actualizado_en := now();
  return new;
end;
$$;

drop trigger if exists validar_configuracion on public.configuracion;
create trigger validar_configuracion
  before insert or update on public.configuracion
  for each row execute function public.validar_configuracion();

insert into public.configuracion (clave, valor) values ('navegacion_anonima', 'PERMITIR')
on conflict (clave) do nothing;

-- ---------- 4. Contactos recibidos por formulario (RF-CFG-03) ----------

create table if not exists public.contactos (
  id          uuid primary key default gen_random_uuid(),
  tienda_id   text not null references public.tiendas (id) on delete cascade,
  producto_id text,
  nombre      text not null check (char_length(btrim(nombre)) between 1 and 80),
  medio       text not null default '' check (char_length(medio) <= 120),
  mensaje     text not null check (char_length(btrim(mensaje)) between 1 and 1000),
  leido       boolean not null default false,
  creado_en   timestamptz not null default now()
);
create index if not exists contactos_tienda_idx on public.contactos (tienda_id, creado_en desc);

alter table public.contactos enable row level security;

-- Cualquier visitante (con o sin sesión) puede escribir, pero SOLO a una tienda
-- que tiene el formulario activado. Nadie más que la dueña puede leerlos.
drop policy if exists contactos_enviar on public.contactos;
create policy contactos_enviar on public.contactos
  for insert to anon, authenticated
  with check (
    leido = false and
    exists (select 1 from public.tiendas t
            where t.id = tienda_id and t.contacto_modo in ('FORMULARIO', 'AMBOS'))
  );
drop policy if exists contactos_duena_ver on public.contactos;
create policy contactos_duena_ver on public.contactos
  for select to authenticated using (public.es_duena_de(tienda_id));
drop policy if exists contactos_duena_marcar on public.contactos;
create policy contactos_duena_marcar on public.contactos
  for update to authenticated
  using (public.es_duena_de(tienda_id)) with check (public.es_duena_de(tienda_id));
drop policy if exists contactos_duena_borrar on public.contactos;
create policy contactos_duena_borrar on public.contactos
  for delete to authenticated using (public.es_duena_de(tienda_id));

-- La dueña solo puede cambiar "leído": el resto del mensaje no se reescribe.
create or replace function public.proteger_contacto()
returns trigger language plpgsql set search_path = public as $$
begin
  new.id := old.id; new.tienda_id := old.tienda_id; new.producto_id := old.producto_id;
  new.nombre := old.nombre; new.medio := old.medio; new.mensaje := old.mensaje;
  new.creado_en := old.creado_en;
  return new;
end;
$$;
drop trigger if exists proteger_contacto on public.contactos;
create trigger proteger_contacto
  before update on public.contactos
  for each row execute function public.proteger_contacto();

-- ---------- 5. Permisos ----------

grant select on public.configuracion to anon, authenticated;
grant insert, update, delete on public.configuracion to authenticated;
grant insert on public.contactos to anon;
grant select, insert, update, delete on public.contactos to authenticated;
grant select on public.asociaciones, public.tiendas, public.ferias to anon;
revoke truncate, references, trigger on public.configuracion, public.contactos from anon, authenticated;

insert into public.migraciones_aplicadas (nombre) values ('06_vitrina_mapas')
on conflict (nombre) do update set aplicada_en = now();

notify pgrst, 'reload schema';
