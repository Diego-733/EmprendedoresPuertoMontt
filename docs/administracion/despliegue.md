# Procedimiento propuesto para un despliegue seguro (NO ejecutado)

Nada de esto se hizo. Requiere tu autorización expresa y, para la base de datos, la de quien administra la instancia del curso.

## Orden

1. **Respaldo y diagnóstico (solo lectura)** en el SQL Editor:
   - ejecutar `supabase/diagnostico_solo_lectura.sql`;
   - guardar el resultado de:

     ```sql
     select * from cuentas_autorizadas;
     select id, email, rol, tienda_id from perfiles where rol <> 'VISITANTE';
     ```
2. **Servidor:** ejecutar `supabase/11_registro_controlado.sql` (es idempotente). Comprobar que queda registrada:

   ```sql
   select * from migraciones_aplicadas where nombre = '11_registro_controlado';
   ```
3. **Prueba de humo con cuentas de prueba** (no con cuentas reales de emprendedoras):
   1. autorizar un correo de prueba;
   2. registrarlo;
   3. activarlo con el código;
   4. confirmar que una compradora no puede usar `crear_mi_tienda`.
4. **Web:** publicar la versión nueva (servicio, catalogo, cuentas y main). Funciona con y sin 11: sin 11 usa las funciones anteriores.
5. **Android:** distribuir el APK 1.10.0-administracion (12) **después** del paso 2.
6. **Revisar las emprendedoras de la vía abierta** (decisión documentada): regularizar o desactivar, nunca borrar.

## Clientes antiguos ya instalados

| Cliente | Con 11 aplicada |
|---|---|
| APK ≤ 1.9 | Puede seguir entrando y vendiendo. Su «Crear mi tienda» y su registro como emprendedora dejan de dar permisos (muestran el error del servidor). Su admin sigue autorizando correos, pero sin código. |
| Web anterior | Igual que el APK ≤ 1.9. |
| APK 1.10 / web nueva **sin** 11 | La lista de cuentas usa la función de 10. Autorizar desde Android avisa «servidor sin registro controlado». El resto funciona igual. |

## Reversión

| Qué revertir | Cómo |
|---|---|
| Servidor | `supabase/11_revertir.sql`. Vuelve a las versiones de 04 y quita las funciones nuevas. **No borra datos**: las cuentas activadas con código conservan su rol y su tienda, y las columnas nuevas quedan sin uso. |
| Apps | Volver a la web anterior y al APK 1.9.0 (copia en `docs/rediseno/apk/alerce-1.9.0-generacion-11.apk`). |

La reversión está probada en local en la misma batería SQL: «Revertir 11 conserva todos los datos y roles» y «11 se vuelve a aplicar después de revertir».

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Alguien registra antes un correo autorizado (correo autoconfirmado) | El código es indispensable. Si pasa, la administración lo ve («Falta activar con código»), revoca y autoriza otro correo. |
| Pérdida del código | «Código nuevo» invalida el anterior. |
| Fuerza bruta del código | 31⁸ ≈ 8,5·10¹¹ combinaciones, 5 intentos por autorización y vence en 14 días. |
| Administración comprometida | Ya era el límite de confianza. Las acciones quedan en la bitácora y nunca se exponen ventas (RN-10). |
