-- =====================================================================
-- alerceshopin · Datos FICTICIOS para la demo de la Entrega 6 (opcional)
-- Ejecutar después de 06_vitrina_mapas.sql. Se puede volver a correr.
-- Solo completa datos vacíos: no pisa lo que ya se editó desde la web o la app.
-- =====================================================================

update public.asociaciones set
  descripcion = case id
    when 'as1' then 'Red de artesanas y productoras de Alerce que venden juntas en ferias comunales.'
    when 'as2' then 'Agrupación de emprendedoras gastronómicas de la cuenca del lago Llanquihue.'
    else 'Taller comunitario de oficios y diseño del borde costero de Puerto Montt.' end,
  sector = case id when 'as1' then 'Alerce' when 'as2' then 'Alerce Sur' else 'Puerto Montt' end
where id in ('as1', 'as2', 'as3') and coalesce(descripcion, '') = '';

update public.tiendas set asociacion_id = 'as1'
 where id in ('t_tejidos_austral', 't_minimarket_rosa') and asociacion_id is null;
update public.tiendas set asociacion_id = 'as2'
 where id in ('t_reposteria_surena') and asociacion_id is null;

insert into public.ferias (id, nombre, descripcion, ubicacion, fecha_inicio, fecha_fin, horario, organizador, estado, latitud, longitud)
values
  ('feria_alerce_plaza', 'Feria de Emprendedoras de Alerce',
   'Tejidos, panadería artesanal, cosmética natural y productos de campo hechos en Alerce.',
   'Plaza de Alerce, Puerto Montt', current_date + 4, current_date + 4, '10:00 a 18:00',
   'Municipalidad de Puerto Montt', 'PROXIMA', -41.3959, -72.8955),
  ('feria_costanera_pm', 'Mercado Costanera Puerto Montt',
   'Artesanía, diseño local y gastronomía sureña frente al mar.',
   'Costanera de Puerto Montt', current_date + 11, current_date + 12, '11:00 a 19:00',
   'Red Trama', 'PROXIMA', -41.4717, -72.9396)
on conflict (id) do nothing;
