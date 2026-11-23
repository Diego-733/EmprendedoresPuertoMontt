-- =====================================================================
-- alerceshopin · Entrega 7 (complemento) — Envío automático de avisos
-- Ejecutar DESPUÉS de 07 (no la repite ni la modifica, salvo una línea en
-- avisos_para_mostrar). Se puede volver a ejecutar. No borra datos.
--
-- Qué hace:
--  · Cuando el servidor guarda un aviso PENDIENTE (de la web o de la app, de
--    cualquiera de los 7 tipos), pide UNA ejecución del workflow «Enviar avisos»
--    de GitHub Actions con pg_net (workflow_dispatch). Ese workflow entra con la
--    cuenta del notificador (tabla notificadores, sin service_role), vacía la cola,
--    espera los reintentos pendientes y termina. Sin actividad no consulta nada.
--  · Mientras una ejecución está viva no se pide otra (una por ráfaga de avisos).
--  · Recuperación: si una ejecución no da señales en 5 minutos (GitHub no la
--    inició o se cortó), el siguiente aviso nuevo o la siguiente revisión de la
--    web o la app (avisos_para_mostrar) vuelve a pedirla, solo si hay algo listo.
--  · Vigía (opcional, requiere pg_cron; apagado hasta que vigia_activa = true): al
--    pedir una ejecución se programa un trabajo de pg_cron que cada minuto revisa
--    SOLO esta base. Si la ejecución pedida no dio señales, la vuelve a pedir con
--    espera creciente (5, 10, 20, 40 y luego cada 60 min). Cuando la cola queda
--    vacía, el trabajo se borra a sí mismo: con la cola vacía no existe ni corre.
--  · Quién recibe cada aviso, el consentimiento, el horario y los duplicados
--    siguen decidiéndose en 07 (motivo_no_notificar, avisos_por_enviar,
--    resultado_envio). Este archivo solo decide CUÁNDO despertar al emisor.
--
-- Activación (docs/entrega7/ENVIO_AUTOMATICO.md): requiere la extensión pg_net y
-- un token de GitHub de permisos mínimos guardado en Vault (o en esta tabla).
-- Sin pg_net o sin token, todo sigue funcionando como en 07 (envío manual).
-- =====================================================================

-- ---------- 1. Configuración privada (una sola fila) ----------
create table if not exists public.envio_automatico (
  id               boolean primary key default true check (id),
  activo           boolean not null default false,
  repositorio      text not null default 'Diego-733/EmprendedoresPuertoMontt',
  flujo            text not null default 'notificador.yml',
  rama             text not null default 'main',
  -- Alternativa a Vault: token de GitHub (fine-grained, solo este repositorio, Actions: read and write).
  token_github     text,
  en_curso         boolean not null default false,
  disparado_en     timestamptz,
  latido_en        timestamptz,
  disparos         int not null default 0,
  ultima_solicitud bigint,
  ultimo_resultado text,
  actualizado_en   timestamptz not null default now()
);
alter table public.envio_automatico add column if not exists vigia_activa boolean not null default false;
alter table public.envio_automatico add column if not exists reintentos_disparo int not null default 0;
insert into public.envio_automatico (id) values (true) on conflict (id) do nothing;
alter table public.envio_automatico enable row level security;   -- sin políticas: nadie la lee desde la API
revoke all on public.envio_automatico from public, anon, authenticated;

-- Token para pedir la ejecución: primero Vault (secreto «alerce_disparo_github»), si no, la tabla.
create or replace function public.token_disparo_github()
returns text language plpgsql stable security definer set search_path = public as $$
declare v text;
begin
  if to_regclass('vault.decrypted_secrets') is not null then
    begin
      execute 'select decrypted_secret from vault.decrypted_secrets where name = $1 limit 1' into v using 'alerce_disparo_github';
    exception when others then v := null;
    end;
  end if;
  if coalesce(v, '') = '' then select token_github into v from public.envio_automatico where id; end if;
  return nullif(btrim(coalesce(v, '')), '');
end;
$$;
revoke all on function public.token_disparo_github() from public, anon, authenticated;

create or replace function public.hay_pg_net()
returns boolean language sql stable as $$
  select exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                  where n.nspname = 'net' and p.proname = 'http_post');
$$;

create or replace function public.hay_pg_cron()
returns boolean language sql stable as $$
  select exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                  where n.nspname = 'cron' and p.proname = 'schedule');
$$;

-- Programa (p_activar = true) o borra el trabajo del vigía en pg_cron. Nunca falla.
create or replace function public.programar_vigia(p_activar boolean)
returns text language plpgsql security definer set search_path = public as $$
declare v_existe boolean;
begin
  if not public.hay_pg_cron() then return 'SIN_PG_CRON'; end if;
  execute 'select exists (select 1 from cron.job where jobname = $1)' into v_existe using 'alerce_vigia_envio';
  if p_activar and not v_existe then
    execute 'select cron.schedule($1, $2, $3)' using 'alerce_vigia_envio', '* * * * *', 'select public.vigilar_envio()';
    return 'PROGRAMADO';
  elsif not p_activar and v_existe then
    execute 'select cron.unschedule($1)' using 'alerce_vigia_envio';
    return 'QUITADO';
  end if;
  return 'SIN_CAMBIO';
exception when others then
  return left('ERROR: ' || sqlerrm, 200);
end;
$$;

-- ---------- 2. Pedir una ejecución del emisor ----------
-- Devuelve: INACTIVO · EN_CURSO · SIN_PG_NET · SIN_TOKEN · ERROR · DISPARADO.
-- Nunca hace fallar la acción de la persona que provocó el aviso.
create or replace function public.solicitar_envio(p_origen text default 'aviso')
returns text language plpgsql security definer set search_path = public as $$
declare cfg record; v_token text; v_id bigint; v_url text;
begin
  -- Lectura sin bloqueo: si está apagado no se toca nada (caso normal antes de activar).
  select activo into cfg from public.envio_automatico where id;
  if not found or not cfg.activo then return 'INACTIVO'; end if;
  -- Con bloqueo: así cerrar_envio() y este disparo no se cruzan (ningún aviso queda sin emisor).
  select * into cfg from public.envio_automatico where id for update;
  if cfg.en_curso and greatest(cfg.latido_en, cfg.disparado_en) > now() - interval '5 minutes' then
    return 'EN_CURSO';
  end if;
  if not public.hay_pg_net() then
    update public.envio_automatico set ultimo_resultado = 'SIN_PG_NET', actualizado_en = now() where id;
    return 'SIN_PG_NET';
  end if;
  v_token := public.token_disparo_github();
  if v_token is null then
    update public.envio_automatico set ultimo_resultado = 'SIN_TOKEN', actualizado_en = now() where id;
    return 'SIN_TOKEN';
  end if;
  v_url := 'https://api.github.com/repos/' || cfg.repositorio || '/actions/workflows/' || cfg.flujo || '/dispatches';
  begin
    -- pg_net encola la solicitud y la envía DESPUÉS del commit: si la acción se deshace, no sale.
    execute 'select net.http_post(url := $1, body := $2, headers := $3, timeout_milliseconds := 10000)'
       into v_id
      using v_url, jsonb_build_object('ref', cfg.rama),
            jsonb_build_object('Authorization', 'Bearer ' || v_token, 'Accept', 'application/vnd.github+json',
                               'X-GitHub-Api-Version', '2022-11-28', 'User-Agent', 'alerce-avisos',
                               'Content-Type', 'application/json');
  exception when others then
    update public.envio_automatico set ultimo_resultado = left('ERROR: ' || sqlerrm, 200), actualizado_en = now() where id;
    return 'ERROR';
  end;
  update public.envio_automatico
     set en_curso = true, disparado_en = now(), latido_en = null, disparos = disparos + 1,
         reintentos_disparo = reintentos_disparo + 1,
         ultima_solicitud = v_id, ultimo_resultado = 'DISPARADO (' || left(coalesce(p_origen, ''), 40) || ')', actualizado_en = now()
   where id;
  -- El vigía queda programado mientras haya trabajo: si este disparo se pierde, lo repite.
  if cfg.vigia_activa then perform public.programar_vigia(true); end if;
  return 'DISPARADO';
end;
$$;
revoke all on function public.solicitar_envio(text) from public, anon, authenticated;

-- Cada aviso nuevo PENDIENTE (de cualquier tipo, venga de la web o de la app) despierta al emisor.
create or replace function public.avisos_despertar_emisor()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  begin
    perform public.solicitar_envio('aviso ' || new.tipo);
  exception when others then null;   -- el aviso ya quedó guardado; la recuperación lo retomará
  end;
  return null;
end;
$$;
revoke all on function public.avisos_despertar_emisor() from public, anon, authenticated;
drop trigger if exists avisos_despertar_emisor on public.avisos;
create trigger avisos_despertar_emisor
  after insert on public.avisos
  for each row when (new.estado = 'PENDIENTE')
  execute function public.avisos_despertar_emisor();

-- Recuperación sin consultas periódicas: se aprovecha la actividad que ya existe.
-- Solo pide una ejecución si hay avisos listos hace más de 1 minuto y ningún emisor vivo.
create or replace function public.recuperar_envio()
returns text language plpgsql security definer set search_path = public as $$
declare cfg record;
begin
  select activo, en_curso, latido_en, disparado_en into cfg from public.envio_automatico where id;
  if not found or not cfg.activo then return 'INACTIVO'; end if;
  if cfg.en_curso and greatest(cfg.latido_en, cfg.disparado_en) > now() - interval '5 minutes' then return 'EN_CURSO'; end if;
  if not exists (select 1 from public.avisos
                  where (estado = 'PENDIENTE' and proximo_intento <= now() - interval '1 minute')
                     or (estado = 'ENVIANDO' and proximo_intento < now() - interval '10 minutes')) then
    return 'NADA_PENDIENTE';
  end if;
  return public.solicitar_envio('recuperación');
exception when others then return 'ERROR';
end;
$$;
revoke all on function public.recuperar_envio() from public, anon, authenticated;

-- Vigía (lo ejecuta pg_cron cada minuto, solo mientras está programado). Revisa únicamente
-- esta base; solo sale a GitHub si hay avisos listos y la ejecución pedida no dio señales.
-- Devuelve: APAGADO · EN_CURSO · ESPERANDO_GITHUB · ESPERANDO_REINTENTO · COLA_VACIA · o el resultado del disparo.
create or replace function public.vigilar_envio()
returns text language plpgsql security definer set search_path = public as $$
declare cfg record; v_ventana interval;
begin
  select * into cfg from public.envio_automatico where id;
  if not found or not cfg.activo or not cfg.vigia_activa then
    perform public.programar_vigia(false);
    return 'APAGADO';
  end if;
  -- La ejecución está viva (dio señales hace poco).
  if cfg.en_curso and cfg.latido_en is not null and cfg.latido_en > now() - interval '5 minutes' then return 'EN_CURSO'; end if;
  -- Nada por enviar: el vigía se borra (con la cola vacía no hay ninguna revisión).
  if not exists (select 1 from public.avisos where estado in ('PENDIENTE', 'ENVIANDO')) then
    update public.envio_automatico set en_curso = false, actualizado_en = now() where id and en_curso;
    perform public.programar_vigia(false);
    return 'COLA_VACIA';
  end if;
  -- Hay algo, pero su reintento aún no toca.
  if not exists (select 1 from public.avisos
                  where (estado = 'PENDIENTE' and proximo_intento <= now())
                     or (estado = 'ENVIANDO' and proximo_intento < now() - interval '10 minutes')) then
    return 'ESPERANDO_REINTENTO';
  end if;
  -- Se pidió una ejecución y aún no da señales: se le da tiempo, cada vez más (5, 10, 20, 40, 60 min).
  v_ventana := make_interval(mins => least(60, 5 * power(2, greatest(cfg.reintentos_disparo - 1, 0)))::int);
  if cfg.en_curso and cfg.latido_en is null and cfg.disparado_en > now() - v_ventana then return 'ESPERANDO_GITHUB'; end if;
  return public.solicitar_envio('vigía');
exception when others then
  return left('ERROR: ' || sqlerrm, 200);
end;
$$;
revoke all on function public.vigilar_envio() from public, anon, authenticated;

-- ---------- 3. Señales del emisor (solo la cuenta del notificador) ----------
-- Al empezar y cada ~30 s mientras espera un reintento. Responde si ya hay algo listo para enviar.
create or replace function public.latido_envio()
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if not public.es_notificador() then raise exception 'SOLO_NOTIFICADOR' using errcode = '42501'; end if;
  update public.envio_automatico set en_curso = true, latido_en = now(), reintentos_disparo = 0, actualizado_en = now() where id;
  return jsonb_build_object('listos', exists (
    select 1 from public.avisos where estado = 'PENDIENTE' and proximo_intento <= now()));
end;
$$;

-- Cuando una pasada no tomó nada. Si quedan reintentos programados, dice cuándo es el próximo
-- (sin datos de nadie) y el emisor sigue vivo; si no queda nada, se apaga. p_rendirse = el
-- emisor se queda sin tiempo: se apaga igual y lo pendiente espera al próximo disparo.
create or replace function public.cerrar_envio(p_rendirse boolean default false)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_siguiente timestamptz;
begin
  if not public.es_notificador() then raise exception 'SOLO_NOTIFICADOR' using errcode = '42501'; end if;
  perform 1 from public.envio_automatico where id for update;
  perform public.revalidar_avisos(null);
  select min(case when estado = 'PENDIENTE' then proximo_intento else proximo_intento + interval '10 minutes' end)
    into v_siguiente
    from public.avisos where estado in ('PENDIENTE', 'ENVIANDO');
  if v_siguiente is null or p_rendirse then
    update public.envio_automatico set en_curso = false, latido_en = now(), actualizado_en = now(),
           ultimo_resultado = case when v_siguiente is null then 'COLA VACÍA' else 'SIN TIEMPO: quedan reintentos' end
     where id;
    -- Cola vacía: el vigía se borra. Si quedan reintentos, sigue programado y los retomará.
    if v_siguiente is null then perform public.programar_vigia(false); end if;
    return jsonb_build_object('siguiente', null, 'quedan', v_siguiente is not null);
  end if;
  update public.envio_automatico set en_curso = true, latido_en = now(), actualizado_en = now() where id;
  return jsonb_build_object('siguiente', v_siguiente,
                            'espera_seg', greatest(0, ceil(extract(epoch from v_siguiente - now())))::int);
end;
$$;

-- ---------- 4. avisos_para_mostrar (07) + recuperación ----------
-- Idéntica a 07; solo agrega la línea «perform public.recuperar_envio()».
create or replace function public.avisos_para_mostrar(p_destino text, p_limite int default 10)
returns table (id uuid, tipo text, titulo text, cuerpo text, feria_id text, enlace text, creado_en timestamptz)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
begin
  if auth.uid() is null then raise exception 'SIN_SESION' using errcode = '42501'; end if;
  if p_destino is null or char_length(p_destino) not between 8 and 4096 then raise exception 'DESTINO_INVALIDO'; end if;
  perform public.revalidar_avisos(auth.uid());
  perform public.recuperar_envio();
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

-- ---------- 5. Diagnóstico (solo desde Studio / SQL Editor) ----------
-- No muestra el token. La última respuesta de GitHub debe ser 204.
create or replace function public.estado_envio_automatico()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare cfg record; v_status int; v_error text; v_vigia boolean := false;
begin
  select * into cfg from public.envio_automatico where id;
  if public.hay_pg_cron() then
    begin execute 'select exists (select 1 from cron.job where jobname = $1)' into v_vigia using 'alerce_vigia_envio';
    exception when others then v_vigia := null; end;
  end if;
  if public.hay_pg_net() and cfg.ultima_solicitud is not null and to_regclass('net._http_response') is not null then
    begin
      execute 'select status_code, error_msg from net._http_response where id = $1' into v_status, v_error using cfg.ultima_solicitud;
    exception when others then null;
    end;
  end if;
  return jsonb_build_object(
    'activo', cfg.activo, 'pg_net', public.hay_pg_net(), 'tiene_token', public.token_disparo_github() is not null,
    'repositorio', cfg.repositorio, 'flujo', cfg.flujo, 'rama', cfg.rama,
    'en_curso', cfg.en_curso, 'disparado_en', cfg.disparado_en, 'latido_en', cfg.latido_en, 'disparos', cfg.disparos,
    'ultimo_resultado', cfg.ultimo_resultado, 'respuesta_github', v_status, 'error_red', v_error,
    'vigia_activa', cfg.vigia_activa, 'pg_cron', public.hay_pg_cron(), 'vigia_programado', v_vigia,
    'disparos_sin_respuesta', cfg.reintentos_disparo,
    'avisos_listos', (select count(*) from public.avisos where estado = 'PENDIENTE' and proximo_intento <= now()));
end;
$$;

-- ---------- 6. Permisos ----------
revoke all on function public.latido_envio(), public.cerrar_envio(boolean), public.estado_envio_automatico(),
                       public.hay_pg_net(), public.hay_pg_cron(), public.programar_vigia(boolean) from public, anon, authenticated;
grant execute on function public.latido_envio(), public.cerrar_envio(boolean) to authenticated;   -- adentro exigen es_notificador()
grant execute on function public.avisos_para_mostrar(text, int) to authenticated;
revoke all on function public.avisos_para_mostrar(text, int) from public, anon;

insert into public.migraciones_aplicadas (nombre) values ('08_envio_automatico')
on conflict (nombre) do update set aplicada_en = now();

notify pgrst, 'reload schema';
