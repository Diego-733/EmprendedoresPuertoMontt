-- =====================================================================
-- Revierte 08_envio_automatico.sql y deja todo exactamente como con 07.
-- No toca avisos, seguidores, dispositivos ni ninguna otra tabla de 07.
-- Para solo PAUSAR el envío automático basta con:
--   update public.envio_automatico set activo = false;
-- Si guardaste el token en Vault, bórralo también desde Studio → Vault.
-- =====================================================================
-- Primero se quita el trabajo del vigía de pg_cron, si existe.
do $$
declare v boolean := false;
begin
  if exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'cron' and p.proname = 'unschedule') then
    execute 'select exists (select 1 from cron.job where jobname = $1)' into v using 'alerce_vigia_envio';
    if v then execute 'select cron.unschedule($1)' using 'alerce_vigia_envio'; end if;
  end if;
end $$;
drop function if exists public.vigilar_envio();
drop function if exists public.programar_vigia(boolean);
drop function if exists public.hay_pg_cron();
drop trigger if exists avisos_despertar_emisor on public.avisos;
drop function if exists public.avisos_despertar_emisor();
drop function if exists public.recuperar_envio();
drop function if exists public.solicitar_envio(text);
drop function if exists public.latido_envio();
drop function if exists public.cerrar_envio(boolean);
drop function if exists public.estado_envio_automatico();
drop function if exists public.token_disparo_github();
drop function if exists public.hay_pg_net();
drop table if exists public.envio_automatico;

-- avisos_para_mostrar tal como la deja 07 (sin la línea de recuperación).
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
revoke all on function public.avisos_para_mostrar(text, int) from public, anon;
grant execute on function public.avisos_para_mostrar(text, int) to authenticated;

delete from public.migraciones_aplicadas where nombre = '08_envio_automatico';
notify pgrst, 'reload schema';
