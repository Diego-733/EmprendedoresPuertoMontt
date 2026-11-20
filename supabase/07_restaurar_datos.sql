-- =====================================================================
-- Restaura los datos que 07 pudo normalizar, desde el JSON de 07_respaldo.sql.
-- Úsalo SOLO si el diagnóstico previo mostró ferias o participaciones que se normalizarían
-- (si eran 0, no hace falta). Ejecutar después de 07_revertir.sql (sin las restricciones
-- de 07, los valores antiguos vuelven a caber).
--
-- Pega el contenido completo del respaldo entre las marcas $respaldo$ … $respaldo$.
-- Solo cambia filas cuyo valor difiere del respaldo; no borra filas nuevas.
-- =====================================================================
with r as (select $respaldo$PEGAR_AQUI_EL_JSON_DEL_RESPALDO$respaldo$::jsonb -> 'datos' as d)
update public.ferias f
   set estado = b->>'estado'
  from r, jsonb_array_elements(r.d -> 'ferias') b
 where f.id = b->>'id' and f.estado is distinct from b->>'estado';

with r as (select $respaldo$PEGAR_AQUI_EL_JSON_DEL_RESPALDO$respaldo$::jsonb -> 'datos' as d)
update public.participaciones p
   set invitacion = b->>'invitacion', asistencia = b->>'asistencia'
  from r, jsonb_array_elements(r.d -> 'participaciones') b
 where p.feria_id = b->>'feria_id' and p.tienda_id = b->>'tienda_id'
   and (p.invitacion is distinct from b->>'invitacion' or p.asistencia is distinct from b->>'asistencia');

with r as (select $respaldo$PEGAR_AQUI_EL_JSON_DEL_RESPALDO$respaldo$::jsonb -> 'datos' as d)
update public.tiendas t
   set asociacion_id = b->>'asociacion_id'
  from r, jsonb_array_elements(r.d -> 'tiendas_asociacion') b
 where t.id = b->>'id' and t.asociacion_id is distinct from b->>'asociacion_id';
