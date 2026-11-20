-- =====================================================================
-- Recuperación de la migración 07: vuelve al comportamiento de la entrega 6 SIN borrar
-- datos (seguidores, avisos, pertenencias y calificaciones quedan guardados por si se
-- vuelve a aplicar 07). Probado en tests/sql (revertir, restaurar datos y reaplicar).
--
-- Usar solo si la web o la app nuevas fallan después de aplicar 07 y hay que volver a
-- las anteriores. Después de revertir, la web y la app de la entrega 7 muestran que
-- las funciones de ferias y seguidores no están disponibles.
-- Si 07 normalizó datos (lo dice 07_diagnostico.sql: *_que_se_normalizarian > 0),
-- restaurarlos con 07_restaurar_datos.sql y el respaldo tomado con 07_respaldo.sql.
-- Para volver a aplicar 07, basta ejecutarlo de nuevo.
-- =====================================================================

-- 1. Triggers que agregó 07
drop trigger if exists marcar_estado_feria on public.ferias;
drop trigger if exists asociacion_principal_cambiada on public.tiendas;
drop trigger if exists avisar_participacion on public.participaciones;
drop trigger if exists validar_participacion on public.participaciones;
drop trigger if exists avisar_contacto on public.contactos;
do $$ begin
  if to_regclass('public.tienda_asociaciones') is not null then
    execute 'drop trigger if exists sincronizar_asociacion_principal on public.tienda_asociaciones';
  end if;
end $$;

-- 2. Políticas como las dejaba la entrega 6 (01_esquema.sql)
drop policy if exists ferias_ver on public.ferias;
create policy ferias_ver on public.ferias for select using (true);
drop policy if exists participaciones_ver on public.participaciones;
create policy participaciones_ver on public.participaciones for select using (true);
drop policy if exists participaciones_admin on public.participaciones;
create policy participaciones_admin on public.participaciones
  for all to authenticated using (public.es_admin()) with check (public.es_admin());
drop policy if exists participaciones_responder on public.participaciones;
create policy participaciones_responder on public.participaciones
  for update to authenticated
  using (public.es_duena_de(tienda_id)) with check (public.es_duena_de(tienda_id));

-- 3. Permisos de tabla anteriores (sin devolver TRUNCATE/REFERENCES/TRIGGER)
grant select on public.participaciones to anon;
grant select, insert, update, delete on public.participaciones to authenticated;

-- 4. Restricciones de dominio que agregó 07 (los clientes anteriores no las conocían)
alter table public.ferias drop constraint if exists ferias_estado_ck;
alter table public.ferias drop constraint if exists ferias_fechas_ck;
alter table public.participaciones drop constraint if exists participaciones_invitacion_ck;
alter table public.participaciones drop constraint if exists participaciones_asistencia_ck;
alter table public.participaciones drop constraint if exists participaciones_origen_ck;

-- 5. Las funciones de 07 dejan de estar disponibles para los clientes (no se borran)
do $$
declare f record;
begin
  for f in
    select p.oid::regprocedure as firma from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.proname in ('publicar_feria', 'cancelar_feria', 'invitar_a_feria', 'responder_invitacion',
       'registrar_asistencia', 'enviar_aviso_feria', 'participantes_feria', 'checklist_feria', 'mis_ferias', 'seguir',
       'dejar_de_seguir', 'guardar_consentimiento', 'registrar_dispositivo', 'quitar_dispositivo', 'marcar_aviso',
       'guardar_avisos_contacto', 'calificar', 'resumen_calificaciones', 'avisos_para_mostrar', 'avisos_por_enviar', 'resultado_envio', 'es_notificador')
  loop
    execute format('revoke execute on function %s from anon, authenticated', f.firma);
  end loop;
end $$;

delete from public.migraciones_aplicadas where nombre = '07_ferias_seguidores';

notify pgrst, 'reload schema';
