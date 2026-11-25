-- =====================================================================
-- Revierte 10_emprendedoras_y_tipos.sql (ejecutar ANTES de 09_revertir.sql si se revierten ambas).
-- Las tiendas desactivadas vuelven a verse y a operar: la columna activa se elimina.
-- No toca productos, ventas, tipos de producto ni cuentas.
-- =====================================================================
do $$
declare t text;
begin
  foreach t in array array['productos', 'ventas', 'participaciones', 'contactos', 'tiendas'] loop
    if to_regclass('public.' || t) is not null then
      execute format('drop trigger if exists bloquear_cuenta_desactivada on public.%I', t);
    end if;
  end loop;
end $$;
drop trigger if exists proteger_activa_tienda on public.tiendas;

-- Políticas y función pública tal como las dejan 01/05/07.
drop policy if exists tiendas_ver on public.tiendas;
create policy tiendas_ver on public.tiendas for select using (true);
drop policy if exists productos_ver on public.productos;
create policy productos_ver on public.productos
  for select using (
    public.estado_publico_producto(publicado, disponible, vendida_en, maneja_stock, modalidad, al_agotarse, variantes) <> 'OCULTO'
    or public.es_duena_de(tienda_id)
  );
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
grant execute on function public.participantes_feria(text) to anon, authenticated;

drop function if exists public.eliminar_tipo_producto(text);
drop function if exists public.guardar_tipo_producto(text, text, text, int, jsonb, boolean);
drop function if exists public.uso_tipos_producto();
drop function if exists public.cambiar_estado_emprendedora(uuid, boolean);
drop function if exists public.editar_emprendedora(uuid, text, text);
drop function if exists public.listar_emprendedoras();
drop function if exists public.bloquear_cuenta_desactivada();
drop function if exists public.proteger_activa_tienda();
drop function if exists public.tienda_activa(text);
alter table public.tiendas drop column if exists activa;
alter table public.tipos_producto drop column if exists actualizado_en;
delete from public.migraciones_aplicadas where nombre = '10_emprendedoras_y_tipos';
notify pgrst, 'reload schema';
