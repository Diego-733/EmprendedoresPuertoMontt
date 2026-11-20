-- =====================================================================
-- Respaldo PREVIO a la migración 07 (SOLO LECTURA). Ejecutar en Supabase → SQL Editor
-- justo antes de aplicar 07 y guardar la fila resultante como archivo .json FUERA del
-- repositorio (contiene datos de la instancia).
--
-- Incluye: las filas que 07 puede normalizar o depende (ferias, participaciones,
-- asociación de cada tienda) y las DEFINICIONES que 07 reemplaza: políticas, permisos,
-- triggers y funciones existentes de las tablas afectadas. La recuperación concreta
-- está en 07_revertir.sql y en docs/entrega7/README.md (sección Migración).
-- No reemplaza el respaldo general del host (Auth y archivos de Storage).
-- =====================================================================
select jsonb_build_object(
  'tomado_en', now(),
  'servidor', current_setting('server_version'),
  'migraciones', (select coalesce(jsonb_agg(m order by m.nombre), '[]') from public.migraciones_aplicadas m),
  'datos', jsonb_build_object(
    'ferias', (select coalesce(jsonb_agg(f order by f.id), '[]') from public.ferias f),
    'participaciones', (select coalesce(jsonb_agg(p order by p.feria_id, p.tienda_id), '[]') from public.participaciones p),
    'tiendas_asociacion', (select coalesce(jsonb_agg(jsonb_build_object('id', t.id, 'asociacion_id', t.asociacion_id, 'actualizado_en', t.actualizado_en) order by t.id), '[]') from public.tiendas t),
    'asociaciones', (select coalesce(jsonb_agg(a order by a.id), '[]') from public.asociaciones a)
  ),
  'definiciones', jsonb_build_object(
    'politicas', (select coalesce(jsonb_agg(jsonb_build_object(
        'tabla', tablename, 'nombre', policyname, 'permisiva', permissive, 'roles', roles, 'cmd', cmd, 'using', qual, 'check', with_check)
        order by tablename, policyname), '[]')
      from pg_policies where schemaname = 'public' and tablename in ('ferias', 'participaciones', 'tiendas', 'contactos', 'asociaciones')),
    'permisos', (select coalesce(jsonb_agg(jsonb_build_object('tabla', table_name, 'rol', grantee, 'privilegio', privilege_type)
        order by table_name, grantee, privilege_type), '[]')
      from information_schema.role_table_grants
      where table_schema = 'public' and table_name in ('ferias', 'participaciones', 'tiendas', 'contactos', 'asociaciones')
        and grantee in ('anon', 'authenticated')),
    'triggers', (select coalesce(jsonb_agg(jsonb_build_object('tabla', c.relname, 'nombre', t.tgname, 'definicion', pg_get_triggerdef(t.oid))
        order by c.relname, t.tgname), '[]')
      from pg_trigger t join pg_class c on c.oid = t.tgrelid join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and not t.tgisinternal and c.relname in ('ferias', 'participaciones', 'tiendas', 'contactos')),
    'restricciones', (select coalesce(jsonb_agg(jsonb_build_object('tabla', c.relname, 'nombre', k.conname, 'definicion', pg_get_constraintdef(k.oid))
        order by c.relname, k.conname), '[]')
      from pg_constraint k join pg_class c on c.oid = k.conrelid join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relname in ('ferias', 'participaciones', 'tiendas')),
    -- Funciones que usan los triggers de esas tablas (por si 07 las reemplazara en el futuro).
    'funciones_de_triggers', (select coalesce(jsonb_agg(distinct jsonb_build_object('nombre', p.proname, 'definicion', pg_get_functiondef(p.oid))), '[]')
      from pg_trigger t join pg_class c on c.oid = t.tgrelid join pg_namespace n on n.oid = c.relnamespace join pg_proc p on p.oid = t.tgfoid
      where n.nspname = 'public' and not t.tgisinternal and c.relname in ('ferias', 'participaciones', 'tiendas', 'contactos'))
  )
) as respaldo_previo_07;
