-- =====================================================================
-- Revierte 09_administracion.sql. No toca perfiles, cuentas, ferias ni
-- asociaciones: solo quita los disparadores, las funciones, la tabla administradores
-- y las columnas que 09 agregó a bitacora_admin. Las acciones anotadas por 09 se borran (respáldalas antes si las necesitas);
-- la bitácora de avisos de feria de 07 se conserva.
-- =====================================================================
do $$
declare t text;
begin
  foreach t in array array['ferias', 'participaciones', 'asociaciones', 'tienda_asociaciones',
                           'cuentas_autorizadas', 'configuracion', 'tipos_producto'] loop
    if to_regclass('public.' || t) is not null then
      execute format('drop trigger if exists bitacora_admin on public.%I', t);
    end if;
  end loop;
end $$;
drop function if exists public.ver_bitacora(int);
drop function if exists public.cambiar_estado_admin(text, boolean);
drop function if exists public.editar_admin(text, text);
drop function if exists public.crear_admin(text, text);
drop function if exists public.listar_admins();
drop function if exists public.bitacora_cambio();
drop function if exists public.columnas_cambiadas(jsonb, jsonb);
drop function if exists public.anotar_bitacora(text, text, text, jsonb);
-- bitacora_admin es de 07: se conservan sus filas de avisos y se quitan las de 09 y sus columnas.
delete from public.bitacora_admin where objeto_tipo is not null;
drop index if exists public.bitacora_admin_creado_idx;
alter table public.bitacora_admin drop column if exists admin_email, drop column if exists objeto_tipo, drop column if exists objeto_id;
drop table if exists public.administradores;
delete from public.migraciones_aplicadas where nombre = '09_administracion';
notify pgrst, 'reload schema';
