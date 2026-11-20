-- =====================================================================
-- Diagnóstico de SOLO LECTURA para la migración 07 (antes y después de aplicarla).
-- No modifica nada. Ejecutar en Supabase → SQL Editor y guardar el resultado.
-- Devuelve una sola fila JSON: copiarla al informe de aplicación.
-- =====================================================================
select jsonb_pretty(jsonb_build_object(
  'postgres', current_setting('server_version'),
  'zona_america_santiago_disponible', exists (select 1 from pg_timezone_names where name = 'America/Santiago'),
  'migraciones', (select coalesce(jsonb_agg(nombre order by nombre), '[]') from public.migraciones_aplicadas),
  '07_aplicada', exists (select 1 from public.migraciones_aplicadas where nombre = '07_ferias_seguidores'),

  -- Datos que 07 podría tocar (lo esperado antes de aplicar: todo en 0).
  'ferias_total', (select count(*) from public.ferias),
  'ferias_por_estado', (select coalesce(jsonb_object_agg(coalesce(estado, 'NULL'), n), '{}') from (select estado, count(*) n from public.ferias group by estado) x),
  'ferias_que_se_normalizarian', (select count(*) from public.ferias
      where estado is null or upper(btrim(estado)) not in ('BORRADOR', 'PROXIMA', 'EN_CURSO', 'FINALIZADA', 'CANCELADA') or estado <> upper(btrim(estado))),
  'ferias_con_fin_antes_del_inicio', (select count(*) from public.ferias where fecha_fin < fecha_inicio),
  'participaciones_total', (select count(*) from public.participaciones),
  'participaciones_que_se_normalizarian', (select count(*) from public.participaciones
      where invitacion not in ('PENDIENTE', 'CONFIRMADA', 'RECHAZADA', 'AGREGADA') or asistencia not in ('PENDIENTE', 'ASISTIO', 'NO_ASISTIO')),
  'tiendas_con_asociacion', (select count(*) from public.tiendas where asociacion_id is not null),
  'tiendas_con_asociacion_inexistente', (select count(*) from public.tiendas t where t.asociacion_id is not null
      and not exists (select 1 from public.asociaciones a where a.id = t.asociacion_id)),

  -- Definiciones y permisos que 07 cambia (para comparar antes y después).
  'politicas', (select coalesce(jsonb_agg(jsonb_build_object('tabla', tablename, 'politica', policyname, 'cmd', cmd, 'roles', roles, 'using', qual, 'check', with_check)
                 order by tablename, policyname), '[]')
                from pg_policies where schemaname = 'public' and tablename in ('ferias', 'participaciones', 'tiendas', 'contactos')),
  'permisos_participaciones', (select coalesce(jsonb_agg(distinct grantee || ':' || privilege_type), '[]')
                from information_schema.role_table_grants
               where table_schema = 'public' and table_name = 'participaciones' and grantee in ('anon', 'authenticated')),
  'triggers', (select coalesce(jsonb_agg(c.relname || '.' || t.tgname order by c.relname, t.tgname), '[]')
                from pg_trigger t join pg_class c on c.oid = t.tgrelid join pg_namespace n on n.oid = c.relnamespace
               where n.nspname = 'public' and not t.tgisinternal and c.relname in ('ferias', 'participaciones', 'tiendas', 'contactos', 'tienda_asociaciones')),
  -- Funciones con nombres que 07 crea: antes de aplicar no debería existir ninguna.
  'funciones_07_existentes', (select coalesce(jsonb_agg(p.proname || '(' || pg_get_function_identity_arguments(p.oid) || ')' order by p.proname), '[]')
                from pg_proc p join pg_namespace n on n.oid = p.pronamespace
               where n.nspname = 'public' and p.proname in ('fecha_santiago', 'hora_santiago', 'dentro_de_horario', 'feria_publicada', 'feria_activa',
                 'marcar_estado_feria', 'sincronizar_asociacion_principal', 'asociacion_principal_cambiada', 'es_notificador', 'crear_aviso',
                 'texto_fecha_feria', 'avisar_participacion', 'validar_participacion', 'avisar_contacto', 'registrar_bitacora', 'publicar_feria',
                 'cancelar_feria', 'invitar_a_feria', 'responder_invitacion', 'registrar_asistencia', 'enviar_aviso_feria', 'participantes_feria',
                 'checklist_feria', 'mis_ferias', 'seguir', 'dejar_de_seguir', 'guardar_consentimiento', 'registrar_dispositivo', 'quitar_dispositivo',
                 'marcar_aviso', 'guardar_avisos_contacto', 'calificar', 'resumen_calificaciones', 'revalidar_avisos', 'motivo_no_notificar', 'avisos_para_mostrar', 'avisos_por_enviar', 'resultado_envio')),
  'tablas_07_existentes', (select coalesce(jsonb_agg(tablename order by tablename), '[]') from pg_tables where schemaname = 'public'
               and tablename in ('tienda_asociaciones', 'feria_asociaciones', 'preferencias_tienda', 'seguimientos', 'preferencias_avisos',
                 'dispositivos', 'notificadores', 'avisos', 'entregas_aviso', 'bitacora_admin', 'calificaciones')),
  'clientes_con_truncate', (select count(*) from pg_tables t cross join (values ('anon'), ('authenticated')) r(rol)
               where t.schemaname = 'public' and has_table_privilege(r.rol, format('%I.%I', t.schemaname, t.tablename), 'TRUNCATE')),

  -- Después de aplicar 07 (si está aplicada): contenido de las tablas nuevas.
  -- (Consultas dinámicas: no fallan si las tablas de 07 aún no existen.)
  'despues', case when to_regclass('public.avisos') is null then null else jsonb_build_object(
      'pertenencias', (xpath('/row/n/text()', query_to_xml('select count(*) n from public.tienda_asociaciones', false, true, '')))[1]::text::int,
      'avisos_por_estado', (xpath('/row/j/text()', query_to_xml('select coalesce(jsonb_object_agg(estado, n), ''{}'')::text j from (select estado, count(*) n from public.avisos group by estado) x', false, true, '')))[1]::text::jsonb,
      'notificadores', (xpath('/row/n/text()', query_to_xml('select count(*) n from public.notificadores', false, true, '')))[1]::text::int,
      'dispositivos_activos', (xpath('/row/n/text()', query_to_xml('select count(*) n from public.dispositivos where activo', false, true, '')))[1]::text::int) end
)) as diagnostico_07;
